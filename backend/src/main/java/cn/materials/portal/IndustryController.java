package cn.materials.portal;

import static cn.materials.portal.Store.*;

import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class IndustryController {

  final ContentService c;

  IndustryController(ContentService c) {
    this.c = c;
  }

  @GetMapping("/api/public/industry")
  Object list(
    @RequestParam(defaultValue = "domestic") String region,
    @RequestParam(defaultValue = "") String topic,
    @RequestParam(defaultValue = "categories") String view,
    @RequestParam(defaultValue = "") String startDate,
    @RequestParam(defaultValue = "") String endDate,
    @RequestParam(defaultValue = "1") int page
  ) {
    require(
      Set.of("domestic", "international").contains(region),
      400,
      "请选择有效的动态区域"
    );
    if (topic.isBlank()) topic = region.equals("domestic")
      ? "policy"
      : "science";
    require(
      ContentService.industryTopics(region).contains(topic),
      400,
      "此区域不支持该内容分类"
    );
    require(
      Set.of("categories", "recent").contains(view),
      400,
      "不支持的展示方式"
    );
    require(
      !view.equals("recent") || region.equals("domestic"),
      400,
      "近期要闻仅用于国内动态"
    );
    for (String date : List.of(startDate, endDate)) {
      if (date.isBlank()) continue;
      try {
        LocalDate.parse(date);
      } catch (Exception e) {
        throw fail(400, "请选择有效日期");
      }
    }
    require(
      startDate.isBlank() ||
        endDate.isBlank() ||
        startDate.compareTo(endDate) <= 0,
      400,
      "开始日期不能晚于结束日期"
    );
    var ids = c.descendants("industry");
    var all = c
      .publicItems()
      .stream()
      .filter(v -> ids.contains(str(v, "categoryId")))
      .filter(v -> region.equals(str(v, "industryRegion")))
      .toList();
    var recent = all
      .stream()
      .filter(v -> bool(v, "industryRecent"))
      .filter(
        v ->
          startDate.isBlank() || str(v, "displayDate").compareTo(startDate) >= 0
      )
      .filter(
        v -> endDate.isBlank() || str(v, "displayDate").compareTo(endDate) <= 0
      )
      .toList();
    String selectedTopic = topic;
    var items = view.equals("recent")
      ? recent
      : all
          .stream()
          .filter(v -> selectedTopic.equals(str(v, "industryTopic")))
          .toList();
    return Map.of(
      "listing",
      ContentController.paginate(items, page, 12),
      "recent",
      ContentController.paginate(recent, 1, 4),
      "featured",
      region.equals("domestic")
        ? all
            .stream()
            .filter(v -> bool(v, "industryFeatured"))
            .filter(
              v -> !str(v, "homeImage").isBlank() || !str(v, "cover").isBlank()
            )
            .limit(6)
            .toList()
        : List.of()
    );
  }
}
