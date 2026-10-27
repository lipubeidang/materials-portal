package cn.materials.portal;

import static cn.materials.portal.Store.*;

import java.util.*;
import org.jsoup.Jsoup;
import org.springframework.web.bind.annotation.*;

@RestController
public class CooperationController {
  final Store s;
  final ContentService c;

  CooperationController(Store s, ContentService c) {
    this.s = s;
    this.c = c;
  }

  String articleImage(Map<String, Object> article) {
    for (String key : List.of("cooperationImage", "cover")) {
      if (!str(article, key).isBlank()) return str(article, key);
    }
    var image = Jsoup.parseBodyFragment(str(article, "body")).selectFirst("img[src]");
    if (image != null) return image.attr("src");
    if (article.get("attachmentInfo") instanceof List<?> files) {
      for (Object file : files) {
        if (file instanceof Map<?, ?> info && Objects.toString(info.get("mime"), "").startsWith("image/"))
          return "/api/media/" + info.get("id");
      }
    }
    return "";
  }

  @GetMapping("/api/public/cooperation")
  Object overview() {
    require(c.visibleCategory("cooperation"), 404, "交流合作栏目未开放");
    var all = c.publicItems();
    var categories = new ArrayList<Map<String, Object>>();
    for (var category : s.rows("SELECT id,name,slug FROM categories WHERE parent_id=? ORDER BY sort_order,id", "cooperation")) {
      String id = str(category, "id");
      if (!c.visibleCategory(id)) continue;
      var ids = c.descendants(id);
      var articles = all.stream().filter(article -> ids.contains(str(article, "categoryId"))).toList();
      // An editor's explicit selection wins over automatic article-image fallbacks.
      // Both groups retain the normal pinned / priority / date ordering.
      var selected = articles.stream().filter(article -> !str(article, "cooperationImage").isBlank()).findFirst()
        .orElseGet(() -> articles.stream().filter(article -> !articleImage(article).isBlank()).findFirst().orElse(null));
      var tile = new LinkedHashMap<String, Object>(category);
      tile.put("total", articles.size());
      tile.put("image", selected == null ? "" : articleImage(selected));
      tile.put("imageAlt", selected == null ? "" : str(selected, "title"));
      tile.put("imageFit", selected == null ? "cover" : selected.getOrDefault("cooperationImageFit", "cover"));
      categories.add(tile);
    }
    // Keep old root-level publications reachable until an editor classifies them.
    var legacy = all.stream().filter(article -> "cooperation".equals(str(article, "categoryId")))
      .map(article -> Map.of("id", article.get("id"), "title", article.get("title"), "displayDate", article.get("displayDate")))
      .toList();
    return Map.of("categories", categories, "legacyItems", legacy);
  }
}
