package cn.materials.portal;

import static cn.materials.portal.Store.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MediaController {

  final Store s;
  final Auth a;
  final ContentService c;
  final Path directory;

  MediaController(
    Store s,
    Auth a,
    ContentService c,
    @Value("${app.uploads}") String dir
  ) {
    this.s = s;
    this.a = a;
    this.c = c;
    directory = Path.of(dir).toAbsolutePath().normalize();
  }

  @PostMapping("/api/operator/media")
  Object upload(
    @RequestParam("file") MultipartFile file,
    HttpServletRequest request
  ) throws IOException {
    require(
      !file.isEmpty() && file.getSize() <= 10 * 1024 * 1024,
      400,
      "文件不能为空且不得超过 10 MB"
    );
    byte[] bytes = file.getBytes();
    String name = Objects.toString(
      file.getOriginalFilename(),
      "file"
    ).replaceAll("[\\\\/\\r\\n]", "_");
    require(name.length() <= 240, 400, "文件名过长");
    String lower = name.toLowerCase(Locale.ROOT),
      mime;
    byte[] stored = bytes;
    if (
      lower.endsWith(".png") ||
      lower.endsWith(".jpg") ||
      lower.endsWith(".jpeg")
    ) {
      try (
        var in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))
      ) {
        var readers = ImageIO.getImageReaders(in);
        require(readers.hasNext(), 400, "图片格式无效");
        var reader = readers.next();
        try {
          reader.setInput(in);
          require(
            (long) reader.getWidth(0) * reader.getHeight(0) <= 24_000_000,
            400,
            "图片像素过大"
          );
          var image = reader.read(0);
          var out = new ByteArrayOutputStream();
          boolean png = lower.endsWith(".png");
          if (!png && image.getColorModel().hasAlpha()) {
            var opaque = new java.awt.image.BufferedImage(
              image.getWidth(),
              image.getHeight(),
              java.awt.image.BufferedImage.TYPE_INT_RGB
            );
            var graphics = opaque.createGraphics();
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, opaque.getWidth(), opaque.getHeight());
            graphics.drawImage(image, 0, 0, null);
            graphics.dispose();
            image = opaque;
          }
          require(
            ImageIO.write(image, png ? "png" : "jpg", out),
            400,
            "图片编码失败"
          );
          stored = out.toByteArray();
          mime = png ? "image/png" : "image/jpeg";
        } finally {
          reader.dispose();
        }
      }
    } else if (lower.endsWith(".pdf")) {
      require(
        bytes.length >= 5 &&
          new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"),
        400,
        "PDF 文件格式无效"
      );
      mime = "application/pdf";
    } else if (lower.endsWith(".txt")) {
      require(bytes.length <= 2 * 1024 * 1024, 400, "文本文件不能超过 2 MB");
      try {
        StandardCharsets.UTF_8.newDecoder().decode(
          java.nio.ByteBuffer.wrap(bytes)
        );
      } catch (Exception e) {
        throw fail(400, "文本附件请使用 UTF-8 编码");
      }
      mime = "text/plain";
    } else throw fail(400, "目前支持 PNG、JPG、PDF 和 UTF-8 TXT 文件");
    String id = id();
    Files.createDirectories(directory);
    Path target = directory.resolve(id);
    Files.write(target, stored, StandardOpenOption.CREATE_NEW);
    try {
      s.update(
        "INSERT INTO media_files VALUES(?,?,?,?,?,?)",
        id,
        name,
        mime,
        stored.length,
        a.operator(request).get("user_id"),
        now()
      );
    } catch (RuntimeException e) {
      Files.deleteIfExists(target);
      throw e;
    }
    return Map.of(
      "id",
      id,
      "url",
      "/api/media/" + id,
      "name",
      name,
      "mime",
      mime
    );
  }

  boolean publiclyReferenced(String id) {
    for (var row : s.rows(
      "SELECT DISTINCT r.category_id FROM revision_media m JOIN content_revisions r ON r.id=m.revision_id JOIN contents c ON c.published_revision_id=r.id WHERE m.media_id=? AND c.deleted=FALSE",
      id
    ))
      if (c.visibleCategory(str(row, "category_id"))) return true;
    var setting = s.one("SELECT payload FROM site_settings WHERE id=1");
    if (setting != null) {
      var config = s.decode(setting.get("payload"));
      for (String key : List.of(
        "banners",
        "links",
        "partners",
        "departments",
        "navigation"
      )) {
        Object raw = config.get(key);
        if (raw instanceof List<?> list) for (Object x : list)
          if (
            x instanceof Map<?, ?> m &&
            ("/api/media/" + id).equals(m.get("image"))
          ) return true;
      }
    }
    return false;
  }

  @GetMapping("/api/media/{id}")
  ResponseEntity<?> download(
    @PathVariable String id,
    HttpServletRequest request
  ) {
    require(id.matches("[a-f0-9-]{36}"), 404, "文件不存在");
    var row = s.one("SELECT * FROM media_files WHERE id=?", id);
    require(row != null, 404, "文件不存在");
    var user = a.session(request);
    require(
      (user != null && "OPERATOR".equals(user.get("role"))) ||
        publiclyReferenced(id),
      404,
      "文件未公开或已撤回"
    );
    String mime = str(row, "mime");
    Path path = directory.resolve(id);
    require(Files.exists(path), 404, "文件不存在");
    var disposition = (
      mime.startsWith("image/")
        ? ContentDisposition.inline()
        : ContentDisposition.attachment()
    )
      .filename(str(row, "original_name"), StandardCharsets.UTF_8)
      .build();
    return ResponseEntity.ok()
      .header("Content-Disposition", disposition.toString())
      .header("Content-Security-Policy", "default-src 'none'; sandbox")
      .cacheControl(CacheControl.noStore())
      .contentType(MediaType.parseMediaType(mime))
      .body(new FileSystemResource(path));
  }
}
