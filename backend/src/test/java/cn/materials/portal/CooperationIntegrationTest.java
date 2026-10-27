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
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {
  "spring.datasource.url=jdbc:h2:mem:cooperation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
  "app.bootstrap-user=operator",
  "app.bootstrap-password=Test-Operator-2026!",
  "app.demo=false",
  "app.uploads=./target/test-uploads",
  "app.center.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class CooperationIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired Store s;
  @Autowired ContentService c;
  @Autowired MediaController media;

  String actor() { return str(s.one("SELECT id FROM users WHERE username='operator'"), "id"); }
  String image(String mime) {
    String id = id();
    s.update("INSERT INTO media_files VALUES(?,?,?,?,?,?)", id, "test", mime, 100, actor(), now());
    return "/api/media/" + id;
  }
  Map<String, Object> draft(String category, String title) {
    var data = new LinkedHashMap<String, Object>();
    data.put("categoryId", category);
    data.put("title", title);
    data.put("body", "<p>交流合作测试正文</p>");
    data.put("displayDate", "2026-10-27");
    data.put("source", "测试发布单位");
    return data;
  }
  Map<String, Object> publish(Map<String, Object> data) {
    var saved = c.save(null, data, actor());
    return c.action(str(saved, "id"), "publish", num(saved, "version", 0), actor());
  }
  void expectConferenceImage(String image, int total) throws Exception {
    mvc.perform(get("/api/public/cooperation"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.categories[0].id").value("cooperation-conferences"))
      .andExpect(jsonPath("$.categories[0].image").value(image))
      .andExpect(jsonPath("$.categories[0].total").value(total));
  }

  @Test
  void createsSevenOrderedCategoriesAndPreservesExistingOnUpgrade() throws Exception {
    mvc.perform(get("/api/public/cooperation"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.categories.length()").value(7))
      .andExpect(jsonPath("$.categories[0].name").value("学术会议"))
      .andExpect(jsonPath("$.categories[6].name").value("企业合作"))
      .andExpect(jsonPath("$.categories[0].image").value(""));
    s.update("DELETE FROM categories WHERE parent_id='cooperation'");
    s.update("INSERT INTO categories VALUES(?,?,?,?,?,?,?,?,?)", "custom-conference", "学术会议", "custom-meetings", "cooperation", "ARTICLE", "list", 5, false, true);
    for (int repeat = 0; repeat < 2; repeat++) {
      s.db.execute((ConnectionCallback<Void>) connection -> {
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V5__cooperation_categories.sql"));
        return null;
      });
    }
    assertEquals(7, s.rows("SELECT id FROM categories WHERE parent_id='cooperation'").size());
    assertFalse(bool(s.one("SELECT * FROM categories WHERE id='custom-conference'"), "enabled"));
    assertNull(s.one("SELECT id FROM categories WHERE id='cooperation-conferences'"));
  }

  @Test
  void selectionsArePublishedSnapshotsAndWithdrawFallsBack() throws Exception {
    String fallback = image("image/png"), chosen = image("image/jpeg"), replacement = image("image/png");
    var auto = draft("cooperation-conferences", "自动图片");
    auto.put("cover", fallback);
    auto.put("pinned", true);
    publish(auto);
    var selected = draft("cooperation-conferences", "手动选图");
    selected.put("body", "<p>正文</p><img src=\"" + chosen + "\">");
    selected.put("cooperationImage", chosen);
    selected.put("cooperationImageFit", "contain");
    var article = publish(selected);
    expectConferenceImage(chosen, 2);
    mvc.perform(get("/api/public/cooperation")).andExpect(jsonPath("$.categories[0].imageFit").value("contain"));
    assertTrue(media.publiclyReferenced(chosen.substring(11)));

    article.put("body", "<p>更新正文</p><img src=\"" + replacement + "\">");
    article.put("cooperationImage", replacement);
    var edited = c.save(str(article, "id"), article, actor());
    expectConferenceImage(chosen, 2);
    assertFalse(media.publiclyReferenced(replacement.substring(11)));
    var published = c.action(str(edited, "id"), "publish", num(edited, "version", 0), actor());
    expectConferenceImage(replacement, 2);
    assertFalse(media.publiclyReferenced(chosen.substring(11)));
    c.action(str(published, "id"), "withdraw", num(published, "version", 0), actor());
    expectConferenceImage(fallback, 1);
    assertFalse(media.publiclyReferenced(replacement.substring(11)));
  }

  @Test
  void imageMustBelongToTheArticleAndMustBeAnImage() {
    var data = draft("cooperation-conferences", "图片校验");
    String outsider = image("image/png");
    data.put("cooperationImage", outsider);
    assertThrows(ResponseStatusException.class, () -> c.save(null, data, actor()));
    String pdf = image("application/pdf");
    data.put("attachments", List.of(pdf.substring(11)));
    data.put("cooperationImage", pdf);
    assertThrows(ResponseStatusException.class, () -> c.save(null, data, actor()));
    data.put("attachments", List.of(outsider.substring(11)));
    data.put("cooperationImage", outsider);
    var saved = c.save(null, data, actor());
    assertEquals(outsider, saved.get("cooperationImage"));
    data.put("cooperationImageFit", "invalid");
    assertThrows(ResponseStatusException.class, () -> c.save(null, data, actor()));
    data.put("cooperationImage", "https://example.com/image.png");
    assertThrows(ResponseStatusException.class, () -> c.save(null, data, actor()));
  }

  @Test
  void rootCannotBePublishedAndOldRootArticlesRemainReachable() throws Exception {
    var unclassified = c.save(null, draft("cooperation", "待分类"), actor());
    assertThrows(ResponseStatusException.class, () -> c.action(str(unclassified, "id"), "publish", num(unclassified, "version", 0), actor()));
    // Emulate an already published record from before this upgrade.
    s.update("UPDATE contents SET published_revision_id=draft_revision_id WHERE id=?", unclassified.get("id"));
    mvc.perform(get("/api/public/cooperation"))
      .andExpect(jsonPath("$.legacyItems[0].id").value(unclassified.get("id")));
    mvc.perform(get("/api/public/contents/" + unclassified.get("id"))).andExpect(status().isOk());
    unclassified.put("categoryId", "cooperation-journals");
    var edited = c.save(str(unclassified, "id"), unclassified, actor());
    c.action(str(edited, "id"), "publish", num(edited, "version", 0), actor());
    mvc.perform(get("/api/public/cooperation"))
      .andExpect(jsonPath("$.legacyItems.length()").value(0))
      .andExpect(jsonPath("$.categories[1].total").value(1));
  }

  @Test
  void categoriesFilterBeforePaginationAndArticleDetailsKeepSourceAndBody() throws Exception {
    for (int i = 0; i < 12; i++) publish(draft("cooperation-conferences", "会议文章 " + i));
    var journal = publish(draft("cooperation-journals", "期刊文章"));
    c.save(null, draft("cooperation-conferences", "草稿不可见"), actor());
    mvc.perform(get("/api/public/contents").param("category", "cooperation-conferences").param("size", "10").param("page", "2"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(12))
      .andExpect(jsonPath("$.items.length()").value(2))
      .andExpect(jsonPath("$.items[0].categoryId").value("cooperation-conferences"));
    mvc.perform(get("/api/public/contents").param("category", "cooperation-conferences").param("size", "20"))
      .andExpect(jsonPath("$.items.length()").value(12));
    mvc.perform(get("/api/public/contents/" + journal.get("id")))
      .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("测试发布单位"))
      .andExpect(jsonPath("$.body").value("<p>交流合作测试正文</p>"));
  }

  @Test
  void hiddenAndDeletedContentCannotSupplyCoverAndPriorityChoosesSelectedImage() throws Exception {
    String first = image("image/png"), second = image("image/jpeg");
    var lower = draft("cooperation-conferences", "较低优先级");
    lower.put("cover", first);
    lower.put("cooperationImage", first);
    publish(lower);
    var higher = draft("cooperation-conferences", "较高优先级");
    higher.put("attachments", List.of(second.substring(11)));
    higher.put("cooperationImage", second);
    higher.put("sort", 100);
    var selected = publish(higher);
    expectConferenceImage(second, 2);
    c.action(str(selected, "id"), "delete", num(selected, "version", 0), actor());
    expectConferenceImage(first, 1);
    s.update("UPDATE categories SET enabled=FALSE WHERE id='cooperation-conferences'");
    mvc.perform(get("/api/public/cooperation"))
      .andExpect(jsonPath("$.categories.length()").value(6))
      .andExpect(jsonPath("$.categories[0].name").value("学术期刊"));
    assertFalse(media.publiclyReferenced(first.substring(11)));
    s.update("UPDATE categories SET enabled=FALSE WHERE id='cooperation'");
    mvc.perform(get("/api/public/cooperation")).andExpect(status().isNotFound());
  }
}
