package cn.materials.portal;

import static cn.materials.portal.Store.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class SiteController {

  final Store s;
  final Auth a;
  final ContentService c;

  SiteController(Store s, Auth a, ContentService c) {
    this.s = s;
    this.a = a;
    this.c = c;
  }

  @GetMapping("/api/public/categories")
  Object categories() {
    return s
      .rows("SELECT * FROM categories ORDER BY sort_order,id")
      .stream()
      .filter(v -> c.visibleCategory(str(v, "id")))
      .toList();
  }

  @GetMapping("/api/operator/categories")
  Object allCategories() {
    return s.rows("SELECT * FROM categories ORDER BY sort_order,id");
  }

  @PostMapping("/api/operator/categories")
  @Transactional
  Object saveCategory(
    @RequestBody Map<String, Object> b,
    HttpServletRequest r
  ) {
    String cid = str(b, "id");
    if (cid.isBlank()) cid = id();
    String name = str(b, "name"),
      slug = str(b, "slug"),
      type = str(b, "type"),
      template = str(b, "template"),
      parent = str(b, "parent_id");
    require(
      !name.isBlank() &&
        name.length() <= 80 &&
        slug.matches("[a-z][a-z0-9-]{0,79}"),
      400,
      "栏目名称或路径标识不正确"
    );
    require(
      Set.of("ARTICLE", "PAGE", "RESOURCE", "PRODUCT", "LINK").contains(type),
      400,
      "内容类型不正确"
    );
    require(
      Set.of("list", "date", "cards", "page").contains(template),
      400,
      "模板不正确"
    );
    if (!parent.isBlank()) {
      var parentRow = s.one("SELECT * FROM categories WHERE id=?", parent);
      require(
        parentRow != null && !c.descendants(cid).contains(parent),
        400,
        "上级栏目不正确，不能形成循环"
      );
      require(
        str(parentRow, "parent_id").isBlank() &&
          s.rows("SELECT id FROM categories WHERE parent_id=?", cid).isEmpty(),
        400,
        "当前支持两级栏目，请选择顶级栏目作为上级"
      );
    }
    var previous = s.one("SELECT * FROM categories WHERE id=?", cid);
    if (previous != null && !type.equals(previous.get("type"))) require(
      s.rows("SELECT id FROM contents WHERE category_id=?", cid).isEmpty(),
      409,
      "已有内容的栏目不能直接更改类型"
    );
    if (previous == null) s.update(
      "INSERT INTO categories VALUES(?,?,?,?,?,?,?,?,?)",
      cid,
      name,
      slug,
      parent.isBlank() ? null : parent,
      type,
      template,
      num(b, "sort_order", 0),
      bool(b, "enabled"),
      bool(b, "navigation")
    );
    else s.update(
      "UPDATE categories SET name=?,slug=?,parent_id=?,type=?,template=?,sort_order=?,enabled=?,navigation=? WHERE id=?",
      name,
      slug,
      parent.isBlank() ? null : parent,
      type,
      template,
      num(b, "sort_order", 0),
      bool(b, "enabled"),
      bool(b, "navigation"),
      cid
    );
    s.audit(str(a.operator(r), "user_id"), "SAVE_CATEGORY", cid);
    return Map.of("id", cid);
  }

  @DeleteMapping("/api/operator/categories/{id}")
  @Transactional
  Object deleteCategory(@PathVariable String id, HttpServletRequest r) {
    require(
      s.rows("SELECT id FROM categories WHERE parent_id=?", id).isEmpty() &&
        s.rows("SELECT id FROM contents WHERE category_id=?", id).isEmpty() &&
        s
          .rows("SELECT id FROM content_revisions WHERE category_id=?", id)
          .isEmpty(),
      409,
      "栏目包含子栏目或历史内容，建议停用"
    );
    s.update("DELETE FROM categories WHERE id=?", id);
    s.audit(str(a.operator(r), "user_id"), "DELETE_CATEGORY", id);
    return Map.of("ok", true);
  }

  Map<String, Object> site() {
    var row = s.one("SELECT * FROM site_settings WHERE id=1");
    var config = s.decode(row.get("payload"));
    config.put("version", row.get("version"));
    return config;
  }

  @GetMapping({ "/api/public/site", "/api/operator/site" })
  Object readSite() {
    return site();
  }

  @GetMapping("/api/public/visits")
  Map<String, Long> visits() {
    long total = s.db.queryForObject(
      "SELECT total_visits FROM site_statistics WHERE id=1",
      Long.class
    );
    return Map.of("totalVisits", total);
  }

  @PostMapping("/api/public/visits")
  @Transactional
  Map<String, Long> recordVisit() {
    s.update(
      "UPDATE site_statistics SET total_visits=total_visits+1 WHERE id=1"
    );
    return visits();
  }

  @GetMapping("/api/public/home")
  Object home() {
    var all = c.publicItems();
    return Map.of(
      "items",
      all
        .stream()
        .filter(v -> bool(v, "recommended"))
        .limit(60)
        .toList(),
      "latest",
      all.stream().limit(5).toList(),
      "statistics",
      List.of(
        Map.of("label", "公开内容", "value", all.size()),
        Map.of("label", "内容栏目", "value", ((List<?>) categories()).size()),
        Map.of(
          "label",
          "资源介绍",
          "value",
          all
            .stream()
            .filter(v -> "RESOURCE".equals(v.get("type")))
            .count()
        ),
        Map.of(
          "label",
          "产品介绍",
          "value",
          all
            .stream()
            .filter(v -> "PRODUCT".equals(v.get("type")))
            .count()
        )
      )
    );
  }

  @PutMapping("/api/operator/site")
  @Transactional
  Object saveSite(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    var clean = new LinkedHashMap<String, Object>();
    for (String key : List.of("name", "englishName", "footer", "contact")) {
      String value = str(b, key).trim();
      require(value.length() <= 500, 400, "站点文字过长");
      clean.put(key, value);
    }
    require(!str(clean, "name").isBlank(), 400, "请填写站点名称");
    for (String key : List.of("banners", "links", "partners", "navigation")) {
      List<Map<String, Object>> result = new ArrayList<>();
      Object raw = b.get(key);
      if (raw instanceof List<?> list) {
        require(list.size() <= 30, 400, "配置项过多");
        for (Object item : list) {
          require(item instanceof Map<?, ?>, 400, "配置格式不正确");
          var m = new LinkedHashMap<String, Object>();
          ((Map<?, ?>) item).forEach((k, v) -> m.put(String.valueOf(k), v));
          String title = str(m, "title"),
            url = str(m, "url"),
            image = str(m, "image"),
            description = str(m, "description");
          require(
            !title.isBlank() &&
              title.length() <= 100 &&
              description.length() <= 300 &&
              ContentService.safeUrl(url),
            400,
            "标题、描述或链接不正确"
          );
          require(
            image.isBlank() || image.matches("/api/media/[a-f0-9-]{36}"),
            400,
            "请使用上传图片"
          );
          if (!image.isBlank()) require(
            s.one(
              "SELECT id FROM media_files WHERE id=? AND mime LIKE 'image/%'",
              image.substring(11)
            ) != null,
            400,
            "图片不存在"
          );
          result.add(
            Map.of(
              "title",
              title,
              "url",
              url,
              "image",
              image,
              "description",
              description
            )
          );
        }
      }
      clean.put(key, result);
    }
    require(
      s.update(
        "UPDATE site_settings SET payload=?,version=version+1 WHERE id=1 AND version=?",
        s.encode(clean),
        num(b, "version", -1)
      ) == 1,
      409,
      "配置已被更新，请刷新后再保存"
    );
    s.audit(str(a.operator(r), "user_id"), "SAVE_SITE", "1");
    return site();
  }

  @GetMapping("/api/operator/audit")
  Object audit() {
    return s.rows(
      "SELECT l.*,u.display_name FROM audit_logs l LEFT JOIN users u ON u.id=l.actor ORDER BY created_at DESC LIMIT 200"
    );
  }
}
