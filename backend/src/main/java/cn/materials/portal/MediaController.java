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

  static final long IMAGE_LIMIT = 10L * 1024 * 1024;
  static final long ATTACHMENT_LIMIT = 1024L * 1024 * 1024;

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
    @RequestParam(value = "purpose", defaultValue = "image") String purpose,
    HttpServletRequest request
  ) throws IOException {
    require(
      Set.of("image", "attachment", "video").contains(purpose),
      400,
      "上传用途无效"
    );
    boolean video = purpose.equals("video");
    long limit = purpose.equals("image") ? IMAGE_LIMIT : ATTACHMENT_LIMIT;
    require(
      !file.isEmpty() && file.getSize() <= limit,
      400,
      video ? "视频不能为空且不得超过 1 GB"
        : purpose.equals("attachment") ? "附件不能为空且不得超过 1 GB"
        : "图片不能为空且不得超过 10 MB"
    );
    String name = validName(file.getOriginalFilename());
    String lower = name.toLowerCase(Locale.ROOT),
      mime;
    String id = id();
    Files.createDirectories(directory);
    Path target = directory.resolve(id);
    Path temporary = Files.createTempFile(directory, "upload-", ".tmp");
    try {
      try (
        var input = file.getInputStream();
        var output = Files.newOutputStream(
          temporary,
          StandardOpenOption.TRUNCATE_EXISTING
        )
      ) {
        input.transferTo(output);
      }
      long receivedSize = Files.size(temporary);
      require(
        receivedSize > 0 && receivedSize <= limit,
        400,
        "文件为空或大小超过限制"
      );
      if (video) {
        require(lower.endsWith(".mp4") && isMp4(temporary), 400, "请上传有效的 MP4 视频");
        mime = "video/mp4";
        Files.move(temporary, target);
      } else if (
        lower.endsWith(".png") ||
        lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg")
      ) {
        var imageInput = ImageIO.createImageInputStream(temporary.toFile());
        require(imageInput != null, 400, "图片格式无效");
        try (imageInput) {
          var readers = ImageIO.getImageReaders(imageInput);
          require(readers.hasNext(), 400, "图片格式无效");
          var reader = readers.next();
          try {
            reader.setInput(imageInput);
            require(
              (long) reader.getWidth(0) * reader.getHeight(0) <= 24_000_000,
              400,
              "图片像素过大"
            );
            var image = reader.read(0);
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
              ImageIO.write(image, png ? "png" : "jpg", target.toFile()),
              400,
              "图片编码失败"
            );
            mime = png ? "image/png" : "image/jpeg";
          } finally {
            reader.dispose();
          }
        }
      } else if (lower.endsWith(".pdf")) {
        byte[] header;
        try (var input = Files.newInputStream(temporary)) {
          header = input.readNBytes(5);
        }
        require(
          header.length == 5 &&
            new String(header, StandardCharsets.US_ASCII).equals("%PDF-"),
          400,
          "PDF 文件格式无效"
        );
        mime = "application/pdf";
        Files.move(temporary, target);
      } else if (lower.endsWith(".txt")) {
        var decoder = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
          .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        try (
          var input = Files.newInputStream(temporary);
          var reader = new InputStreamReader(input, decoder)
        ) {
          char[] buffer = new char[8192];
          while (reader.read(buffer) != -1) {}
        } catch (java.nio.charset.CharacterCodingException e) {
          throw fail(400, "文本附件请使用 UTF-8 编码");
        }
        mime = "text/plain";
        Files.move(temporary, target);
      } else if (
        lower.endsWith(".zip") ||
        lower.endsWith(".rar") ||
        lower.endsWith(".7z")
      ) {
        byte[] header;
        try (var input = Files.newInputStream(temporary)) {
          header = input.readNBytes(8);
        }
        if (lower.endsWith(".zip")) {
          require(
            startsWith(header, 0x50, 0x4b, 0x03, 0x04) ||
              startsWith(header, 0x50, 0x4b, 0x05, 0x06) ||
              startsWith(header, 0x50, 0x4b, 0x07, 0x08),
            400,
            "ZIP 压缩包格式无效"
          );
          mime = "application/zip";
        } else if (lower.endsWith(".rar")) {
          require(
            startsWith(header, 0x52, 0x61, 0x72, 0x21, 0x1a, 0x07),
            400,
            "RAR 压缩包格式无效"
          );
          mime = "application/vnd.rar";
        } else {
          require(
            startsWith(header, 0x37, 0x7a, 0xbc, 0xaf, 0x27, 0x1c),
            400,
            "7Z 压缩包格式无效"
          );
          mime = "application/x-7z-compressed";
        }
        Files.move(temporary, target);
      } else throw fail(
        400,
        "目前支持 PNG、JPG、PDF、UTF-8 TXT、ZIP、RAR 和 7Z 文件"
      );
      long storedSize = Files.size(target);
      s.update(
        "INSERT INTO media_files VALUES(?,?,?,?,?,?)",
        id,
        name,
        mime,
        storedSize,
        a.operator(request).get("user_id"),
        now()
      );
      return Map.of(
        "id",
        id,
        "url",
        "/api/media/" + id,
        "name",
        name,
        "mime",
        mime,
        "size",
        storedSize
      );
    } catch (RuntimeException | IOException e) {
      Files.deleteIfExists(target);
      throw e;
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private boolean isMp4(Path path) throws IOException {
    try (var input = new DataInputStream(Files.newInputStream(path))) {
      long boxSize = Integer.toUnsignedLong(input.readInt());
      if (input.readInt() != 0x66747970 || boxSize < 16 || boxSize > 4096 || boxSize % 4 != 0)
        return false;
      byte[] brands = input.readNBytes((int) boxSize - 8);
      if (brands.length != boxSize - 8) return false;
      for (int i = 0; i < brands.length; i += 4) {
        if (i == 4) continue; // Minor version, not a brand.
        String brand = new String(brands, i, 4, StandardCharsets.US_ASCII);
        if (brand.matches("iso[m2-9]|mp4[12]|avc1|M4V |dash")) return true;
      }
      return false;
    } catch (EOFException e) {
      return false;
    }
  }

  boolean startsWith(byte[] actual, int... expected) {
    if (actual.length < expected.length) return false;
    for (int i = 0; i < expected.length; i++) if (
      (actual[i] & 0xff) != expected[i]
    ) return false;
    return true;
  }

  String validName(Object value) {
    String name = Objects.toString(value, "")
      .trim()
      .replaceAll("[\\\\/\\r\\n\\t]", "_");
    require(!name.isBlank(), 400, "附件名称不能为空");
    require(name.length() <= 240, 400, "附件名称不能超过 240 个字符");
    return name;
  }

  @PatchMapping("/api/operator/media/{id}")
  Object rename(
    @PathVariable String id,
    @RequestBody Map<String, Object> body,
    HttpServletRequest request
  ) {
    require(id.matches("[a-f0-9-]{36}"), 404, "附件不存在");
    a.operator(request);
    String name = validName(body.get("name"));
    require(
      s.update("UPDATE media_files SET original_name=? WHERE id=?", name, id) ==
        1,
      404,
      "附件不存在"
    );
    return Map.of("id", id, "name", name);
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
      mime.startsWith("image/") || mime.equals("video/mp4")
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
