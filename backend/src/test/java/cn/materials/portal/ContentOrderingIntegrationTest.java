package cn.materials.portal;

import static cn.materials.portal.Store.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:ordering;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.bootstrap-user=operator",
    "app.bootstrap-password=Test-Operator-2026!",
    "app.demo=false",
    "app.uploads=./target/test-uploads",
    "app.center.enabled=false",
  }
)
@AutoConfigureMockMvc
@Transactional
class ContentOrderingIntegrationTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  Store s;

  @Autowired
  ContentService c;

  String actor() {
    return str(s.one("SELECT id FROM users WHERE username='operator'"), "id");
  }

  Map<String, Object> publish(
    String title,
    int priority,
    boolean pinned,
    String date,
    String category,
    String tag
  ) {
    var data = new LinkedHashMap<String, Object>();
    data.put("categoryId", "products");
    data.put("title", title);
    data.put("body", "<p>材料产品介绍</p>");
    data.put("displayDate", date);
    data.put("sort", priority);
    data.put("pinned", pinned);
    data.put("recommended", true);
    data.put("productCategory", category);
    data.put("productTag", tag);
    var saved = c.save(null, data, actor());
    return c.action(
      str(saved, "id"),
      "publish",
      num(saved, "version", 0),
      actor()
    );
  }

  Map<String, Object> publishInCategory(String title, String categoryId) {
    var data = new LinkedHashMap<String, Object>();
    data.put("categoryId", categoryId);
    data.put("title", title);
    data.put("body", "<p>首页最新内容测试</p>");
    data.put("displayDate", "2026-11-16");
    data.put("recommended", true);
    var saved = c.save(null, data, actor());
    return c.action(
      str(saved, "id"),
      "publish",
      num(saved, "version", 0),
      actor()
    );
  }

  @Test
  void homeLatestContainsOnlyNews() throws Exception {
    publishInCategory("不应出现在最新中的产品", "products");
    var news = publishInCategory("应出现在最新中的新闻", "headlines");

    mvc
      .perform(get("/api/public/home"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.latest.length()").value(1))
      .andExpect(jsonPath("$.latest[0].id").value(news.get("id")))
      .andExpect(jsonPath("$.latest[0].categoryId").value("headlines"));
  }

  @Test
  void prioritiesApplyToListsAndHomeOnlyAfterPublishing() throws Exception {
    var low = publish(
      "较新普通产品",
      0,
      false,
      "2026-10-27",
      "数据集",
      "中心自研"
    );
    var high = publish(
      "较早优先产品",
      90,
      false,
      "2026-01-01",
      "数据集",
      "中心自研"
    );
    var pinned = publish(
      "置顶产品",
      -20,
      true,
      "2025-01-01",
      "数据报告",
      "联合发布"
    );
    mvc
      .perform(
        get("/api/public/contents")
          .param("category", "products")
          .param("size", "2")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.total").value(3))
      .andExpect(jsonPath("$.items[0].id").value(pinned.get("id")))
      .andExpect(jsonPath("$.items[1].id").value(high.get("id")));
    mvc
      .perform(get("/api/public/home"))
      .andExpect(jsonPath("$.items[1].id").value(high.get("id")));
    low.put("sort", 100);
    var draft = c.save(str(low, "id"), low, actor());
    assertEquals(0, c.publicDetail(str(low, "id")).get("sort"));
    mvc
      .perform(get("/api/public/home"))
      .andExpect(jsonPath("$.items[1].id").value(high.get("id")));
    c.action(str(draft, "id"), "publish", num(draft, "version", 0), actor());
    mvc
      .perform(get("/api/public/home"))
      .andExpect(jsonPath("$.items[0].id").value(pinned.get("id")))
      .andExpect(jsonPath("$.items[1].id").value(low.get("id")));
    mvc
      .perform(
        get("/api/public/contents")
          .param("category", "products")
          .param("page", "2")
          .param("size", "2")
      )
      .andExpect(jsonPath("$.items[0].id").value(high.get("id")));
  }

  @Test
  void productFiltersAndExplicitSortRunBeforePagination() throws Exception {
    var first = publish(
      "A 工具",
      10,
      false,
      "2026-01-01",
      "数据管理工具",
      "中心自研"
    );
    var second = publish(
      "B 工具",
      0,
      false,
      "2026-10-27",
      "数据管理工具",
      "中心自研"
    );
    publish("C 工具", 100, true, "2026-10-01", "数据管理工具", "联合发布");
    publish("D 数据集", 100, false, "2026-10-01", "数据集", "中心自研");
    mvc
      .perform(
        get("/api/public/contents")
          .param("category", "products")
          .param("productCategory", "数据管理工具")
          .param("tag", "中心自研")
          .param("sort", "date")
          .param("size", "1")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.total").value(2))
      .andExpect(jsonPath("$.items[0].id").value(second.get("id")))
      .andExpect(jsonPath("$.items[0].productTag").value("中心自研"));
    mvc
      .perform(
        get("/api/public/contents")
          .param("category", "products")
          .param("q", "工具")
          .param("sort", "title")
          .param("size", "1")
      )
      .andExpect(jsonPath("$.total").value(3))
      .andExpect(jsonPath("$.items[0].id").value(first.get("id")));
    mvc
      .perform(get("/api/public/contents").param("tag", "无此标签"))
      .andExpect(jsonPath("$.total").value(0));
    mvc
      .perform(get("/api/public/contents").param("sort", "invalid"))
      .andExpect(status().isBadRequest());
  }

  @Test
  void invalidPrioritiesAreRejectedAndLegacyContentsDefaultToZero() {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("title", "旧内容");
    payload.put("displayDate", "2026-10-27");
    assertEquals(0, c.clean(payload).get("sort"));
    for (Object invalid : List.of(10001, -10001, 1.5, "invalid", "")) {
      payload.put("sort", invalid);
      assertThrows(ResponseStatusException.class, () -> c.clean(payload));
    }
  }

  @Test
  void richTextFormattingIsPreservedButUntrustedStylesAreRemoved() {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("title", "富文本内容");
    payload.put("displayDate", "2026-11-16");
    payload.put(
      "body",
      "<h2 data-text-align='center' style='position:fixed'>标题</h2>" +
        "<p data-text-align='invalid'><span data-text-color='purple' data-highlight='orange' " +
        "data-font-size='xl' data-font-family='kaiti'>重点</span>" +
        "<span data-text-color='expression(alert(1))' data-font-size='99px' " +
        "data-font-family='unknown'>风险</span></p><hr>"
    );

    String body = str(c.clean(payload), "body");

    assertTrue(body.contains("<h2 data-text-align=\"center\">标题</h2>"));
    assertTrue(body.contains("data-text-color=\"purple\""));
    assertTrue(body.contains("data-highlight=\"orange\""));
    assertTrue(body.contains("data-font-size=\"xl\""));
    assertTrue(body.contains("data-font-family=\"kaiti\""));
    assertTrue(body.contains("<hr>"));
    assertFalse(body.contains("style="));
    assertFalse(body.contains("data-text-align=\"invalid\""));
    assertFalse(body.contains("expression"));
    assertFalse(body.contains("99px"));
    assertFalse(body.contains("unknown"));
  }
}
