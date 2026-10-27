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
    "spring.datasource.url=jdbc:h2:mem:industry;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.bootstrap-user=operator",
    "app.bootstrap-password=Test-Operator-2026!",
    "app.demo=false",
    "app.uploads=./target/test-uploads",
    "app.center.enabled=false",
  }
)
@AutoConfigureMockMvc
@Transactional
class IndustryIntegrationTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  Store s;

  @Autowired
  ContentService c;

  String actor() {
    return str(s.one("SELECT id FROM users WHERE username='operator'"), "id");
  }

  Map<String, Object> article(String region, String topic, int priority) {
    var b = new LinkedHashMap<String, Object>();
    b.put("categoryId", "industry");
    b.put("title", region + topic + priority);
    b.put("body", "<p>领域动态测试正文</p>");
    b.put("displayDate", "2026-10-27");
    b.put("industryRegion", region);
    b.put("industryTopic", topic);
    b.put("sort", priority);
    return b;
  }

  Map<String, Object> publish(Map<String, Object> body) {
    var draft = c.save(null, body, actor());
    return c.action(
      str(draft, "id"),
      "publish",
      num(draft, "version", 0),
      actor()
    );
  }

  @Test
  void regionsTopicsAndPriorityAreAppliedBeforePagination() throws Exception {
    for (int i = 0; i < 14; i++) publish(article("domestic", "policy", i));
    publish(article("domestic", "action", 99));
    publish(article("domestic", "science", 99));
    publish(article("domestic", "progress", 99));
    var international = publish(article("international", "science", 90));
    publish(article("international", "progress", 100));
    mvc
      .perform(get("/api/public/industry"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.listing.total").value(14))
      .andExpect(jsonPath("$.listing.items.length()").value(12))
      .andExpect(jsonPath("$.listing.items[0].sort").value(13));
    mvc
      .perform(get("/api/public/industry").param("page", "2"))
      .andExpect(jsonPath("$.listing.items.length()").value(2))
      .andExpect(jsonPath("$.listing.items[0].sort").value(1));
    mvc
      .perform(get("/api/public/industry").param("region", "international"))
      .andExpect(jsonPath("$.listing.total").value(1))
      .andExpect(
        jsonPath("$.listing.items[0].id").value(international.get("id"))
      )
      .andExpect(jsonPath("$.featured.length()").value(0));
    mvc
      .perform(
        get("/api/public/industry")
          .param("region", "international")
          .param("topic", "progress")
      )
      .andExpect(
        jsonPath("$.listing.items[0].industryTopic").value("progress")
      );
    mvc
      .perform(
        get("/api/public/industry")
          .param("region", "international")
          .param("topic", "policy")
      )
      .andExpect(status().isBadRequest());
  }

  @Test
  void recommendationsDateFiltersAndWithdrawalFollowPublishedVersion()
    throws Exception {
    String imageId = id();
    s.update(
      "INSERT INTO media_files VALUES(?,?,?,?,?,?)",
      imageId,
      "feature.png",
      "image/png",
      100,
      actor(),
      now()
    );
    var b = article("domestic", "science", 90);
    b.put("industryRecent", true);
    b.put("industryFeatured", true);
    b.put("homeImage", "/api/media/" + imageId);
    var live = publish(b);
    var older = article("domestic", "policy", 0);
    older.put("industryRecent", true);
    older.put("displayDate", "2026-09-01");
    publish(older);
    mvc
      .perform(
        get("/api/public/industry")
          .param("startDate", "2026-10-27")
          .param("endDate", "2026-10-27")
          .param("view", "recent")
      )
      .andExpect(jsonPath("$.recent.total").value(1))
      .andExpect(jsonPath("$.listing.total").value(1))
      .andExpect(jsonPath("$.featured[0].id").value(live.get("id")));
    live.put("industryRegion", "international");
    live.put("industryRecent", false);
    live.put("industryFeatured", false);
    var draft = c.save(str(live, "id"), live, actor());
    mvc
      .perform(get("/api/public/industry"))
      .andExpect(jsonPath("$.featured[0].id").value(live.get("id")));
    var published = c.action(
      str(draft, "id"),
      "publish",
      num(draft, "version", 0),
      actor()
    );
    mvc
      .perform(get("/api/public/industry"))
      .andExpect(jsonPath("$.featured.length()").value(0))
      .andExpect(jsonPath("$.recent.total").value(1));
    mvc
      .perform(get("/api/public/industry").param("region", "international"))
      .andExpect(jsonPath("$.listing.items[0].id").value(live.get("id")));
    c.action(
      str(published, "id"),
      "withdraw",
      num(published, "version", 0),
      actor()
    );
    mvc
      .perform(get("/api/public/industry").param("region", "international"))
      .andExpect(jsonPath("$.listing.total").value(0));
    mvc
      .perform(
        get("/api/public/industry")
          .param("startDate", "2026-10-28")
          .param("endDate", "2026-10-27")
      )
      .andExpect(status().isBadRequest());
    mvc
      .perform(get("/api/public/industry").param("startDate", "invalid"))
      .andExpect(status().isBadRequest());
    mvc
      .perform(
        get("/api/public/industry")
          .param("view", "recent")
          .param("startDate", "2027-01-01")
      )
      .andExpect(jsonPath("$.listing.total").value(0));
  }

  @Test
  void invalidPublishingChoicesAreRejectedButDraftCanWaitForAnImage() {
    assertThrows(ResponseStatusException.class, () ->
      c.save(null, article("international", "policy", 0), actor())
    );
    var b = article("international", "science", 0);
    b.put("industryRecent", true);
    assertThrows(ResponseStatusException.class, () -> c.save(null, b, actor()));
    var draftBody = article("domestic", "policy", 0);
    draftBody.put("industryFeatured", true);
    var draft = c.save(null, draftBody, actor());
    assertThrows(ResponseStatusException.class, () ->
      c.action(str(draft, "id"), "publish", num(draft, "version", 0), actor())
    );
    assertNull(c.record(str(draft, "id")).get("published_revision_id"));
  }

  @Test
  void legacyArticlesRemainVisibleAndDisabledCategoryIsHidden()
    throws Exception {
    var b = article("domestic", "science", 0);
    var draft = c.save(null, b, actor());
    var row = c.record(str(draft, "id"));
    var revision = s.one(
      "SELECT * FROM content_revisions WHERE id=?",
      row.get("draft_revision_id")
    );
    var payload = s.decode(revision.get("payload"));
    payload.remove("industryRegion");
    payload.remove("industryTopic");
    s.update(
      "UPDATE content_revisions SET payload=? WHERE id=?",
      s.encode(payload),
      revision.get("id")
    );
    c.action(str(draft, "id"), "publish", num(draft, "version", 0), actor());
    mvc
      .perform(get("/api/public/industry").param("topic", "science"))
      .andExpect(
        jsonPath("$.listing.items[0].industryRegion").value("domestic")
      )
      .andExpect(jsonPath("$.listing.items[0].industryTopic").value("science"));
    s.update("UPDATE categories SET enabled=FALSE WHERE id='industry'");
    mvc
      .perform(get("/api/public/industry").param("topic", "science"))
      .andExpect(jsonPath("$.listing.total").value(0));
  }
}
