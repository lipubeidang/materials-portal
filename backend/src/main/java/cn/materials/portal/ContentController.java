package cn.materials.portal;

import static cn.materials.portal.Store.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ContentController {

  final ContentService c;
  final Store s;
  final Auth a;

  ContentController(ContentService c, Store s, Auth a) {
    this.c = c;
    this.s = s;
    this.a = a;
  }

  @GetMapping("/api/public/contents")
  Object list(
    @RequestParam(defaultValue = "") String category,
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "12") int size
  ) {
    require(q.length() <= 200, 400, "搜索词过长");
    var ids = c.descendants(category);
    var all = c
      .publicItems()
      .stream()
      .filter(v -> category.isBlank() || ids.contains(str(v, "categoryId")))
      .filter(v ->
        (str(v, "title") + str(v, "excerpt") + str(v, "keywords"))
          .toLowerCase(Locale.ROOT)
          .contains(q.toLowerCase(Locale.ROOT))
      )
      .toList();
    return paginate(all, page, size);
  }

  static Object paginate(List<Map<String, Object>> all, int page, int size) {
    size = Math.min(100, Math.max(1, size));
    page = Math.max(1, page);
    int start = (int) Math.min(all.size(), ((long) page - 1) * size);
    return Map.of(
      "items",
      all.subList(start, Math.min(all.size(), start + size)),
      "total",
      all.size(),
      "page",
      page,
      "size",
      size
    );
  }

  @GetMapping("/api/public/contents/{id}")
  Object detail(@PathVariable String id) {
    return c.publicDetail(id);
  }

  @GetMapping("/api/operator/contents")
  Object adminList(
    @RequestParam(defaultValue = "") String category,
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "") String status,
    @RequestParam(defaultValue = "1") int page
  ) {
    var ids = c.descendants(category);
    var all = s
      .rows("SELECT * FROM contents ORDER BY updated_at DESC")
      .stream()
      .map(r -> c.view(r, false))
      .filter(Objects::nonNull)
      .filter(v -> category.isBlank() || ids.contains(str(v, "categoryId")))
      .filter(v -> str(v, "title").contains(q))
      .filter(v ->
        status.isBlank()
          ? !"DELETED".equals(v.get("status"))
          : status.equals(v.get("status"))
      )
      .toList();
    return paginate(all, page, 10);
  }

  @GetMapping("/api/operator/contents/{id}")
  Object edit(@PathVariable String id) {
    return c.view(c.record(id), false);
  }

  @PostMapping("/api/operator/contents")
  Object create(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    return c.save(null, b, str(a.operator(r), "user_id"));
  }

  @PutMapping("/api/operator/contents/{id}")
  Object save(
    @PathVariable String id,
    @RequestBody Map<String, Object> b,
    HttpServletRequest r
  ) {
    return c.save(id, b, str(a.operator(r), "user_id"));
  }

  @PostMapping("/api/operator/contents/{id}/{action}")
  Object action(
    @PathVariable String id,
    @PathVariable String action,
    @RequestBody Map<String, Object> b,
    HttpServletRequest r
  ) {
    return c.action(
      id,
      action,
      num(b, "version", -1),
      str(a.operator(r), "user_id")
    );
  }

  @PostMapping("/api/operator/batch")
  Object batch(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    require(b.get("items") instanceof List<?>, 400, "请选择内容");
    var items = (List<?>) b.get("items");
    require(items.size() <= 100, 400, "一次最多 100 条");
    var result = new ArrayList<Map<String, Object>>();
    for (Object item : items) {
      if (!(item instanceof Map<?, ?> raw)) continue;
      var v = new LinkedHashMap<String, Object>();
      raw.forEach((k, x) -> v.put(String.valueOf(k), x));
      String id = str(v, "id");
      try {
        c.action(
          id,
          str(b, "action"),
          num(v, "version", -1),
          str(a.operator(r), "user_id")
        );
        result.add(Map.of("id", id, "ok", true));
      } catch (org.springframework.web.server.ResponseStatusException e) {
        result.add(
          Map.of(
            "id",
            id,
            "ok",
            false,
            "message",
            Objects.toString(e.getReason(), "操作失败")
          )
        );
      }
    }
    return result;
  }
}
