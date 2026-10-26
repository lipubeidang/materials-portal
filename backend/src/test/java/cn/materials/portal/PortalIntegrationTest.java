package cn.materials.portal;

import static cn.materials.portal.Store.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.bootstrap-user=operator",
    "app.bootstrap-password=Test-Operator-2026!",
    "app.demo=false",
    "app.uploads=./target/test-uploads",
    "app.center.enabled=false",
  }
)
@AutoConfigureMockMvc
class PortalIntegrationTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  Store s;

  @Autowired
  ContentService content;

  @Autowired
  CenterService center;

  record Login(Cookie cookie, String csrf) {}

  Login login(String username, String password) throws Exception {
    var result = mvc
      .perform(
        post("/api/auth/login")
          .header("Origin", "http://localhost:5173")
          .contentType("application/json")
          .content(s.encode(Map.of("username", username, "password", password)))
      )
      .andExpect(status().isOk())
      .andReturn();
    String raw = result.getResponse().getHeader("Set-Cookie");
    var cookie = new Cookie(
      "PORTAL_SESSION",
      raw.split(";", 2)[0].substring("PORTAL_SESSION=".length())
    );
    var me = mvc
      .perform(get("/api/auth/me").cookie(cookie))
      .andExpect(status().isOk())
      .andReturn();
    return new Login(
      cookie,
      str(s.decode(me.getResponse().getContentAsString()), "csrf")
    );
  }

  MockHttpServletRequestBuilder write(
    String url,
    Login user,
    Map<String, Object> b
  ) {
    return post(url)
      .cookie(user.cookie())
      .header("Origin", "http://localhost:5173")
      .header("X-CSRF-Token", user.csrf())
      .contentType("application/json")
      .content(s.encode(b));
  }

  Map<String, Object> article(String title) {
    var b = new LinkedHashMap<String, Object>();
    b.put("title", title);
    b.put("categoryId", "notices");
    b.put(
      "body",
      "<p>公开测试内容</p><script>alert(1)</script><img src=\"https://evil.example/pixel\">"
    );
    b.put("displayDate", "2026-10-25");
    b.put("recommended", true);
    return b;
  }

  Map<String, Object> uploadMedia(
    Login user,
    String name,
    String mime,
    byte[] bytes
  ) throws Exception {
    var result = mvc
      .perform(
        multipart("/api/operator/media")
          .file(new MockMultipartFile("file", name, mime, bytes))
          .cookie(user.cookie())
          .header("Origin", "http://localhost:5173")
          .header("X-CSRF-Token", user.csrf())
      )
      .andExpect(status().isOk())
      .andReturn();
    return s.decode(result.getResponse().getContentAsString());
  }

  @Test
  void optionalSummariesProduceExcerptsWithoutChangingStoredContent()
    throws Exception {
    var operator = login("operator", "Test-Operator-2026!");
    var body = article("摘要与发布版本测试");
    String first160Characters = "材料质量追溯" + "材".repeat(153) + "🧪";
    String expected = first160Characters + "…";
    body.put(
      "body",
      "<p>" + first160Characters + "后续正文</p><script>hidden()</script>"
    );
    var created = mvc
      .perform(write("/api/operator/contents", operator, body))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.summary").value(""))
      .andExpect(jsonPath("$.excerpt").value(expected))
      .andReturn();
    var draft = s.decode(created.getResponse().getContentAsString());
    String id = str(draft, "id");
    var stored = s.decode(
      s
        .one(
          "SELECT payload FROM content_revisions WHERE id=?",
          content.record(id).get("draft_revision_id")
        )
        .get("payload")
    );
    assertEquals("", stored.get("summary"));
    assertFalse(stored.containsKey("excerpt"));
    mvc
      .perform(
        write(
          "/api/operator/contents/" + id + "/publish",
          operator,
          Map.of("version", 1)
        )
      )
      .andExpect(status().isOk());
    mvc
      .perform(get("/api/public/contents/" + id))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.excerpt").value(expected));
    mvc
      .perform(get("/api/public/contents").param("q", "材料质量追溯"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.total").value(1))
      .andExpect(jsonPath("$.items[0].id").value(id));

    draft.put("version", 2);
    draft.put("summary", "人工撰写的列表介绍");
    mvc
      .perform(
        put("/api/operator/contents/" + id)
          .cookie(operator.cookie())
          .header("Origin", "http://localhost:5173")
          .header("X-CSRF-Token", operator.csrf())
          .contentType("application/json")
          .content(s.encode(draft))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.summary").value("人工撰写的列表介绍"))
      .andExpect(jsonPath("$.excerpt").value("人工撰写的列表介绍"));
    mvc
      .perform(get("/api/public/contents/" + id))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.excerpt").value(expected));
    mvc
      .perform(
        write(
          "/api/operator/contents/" + id + "/publish",
          operator,
          Map.of("version", 3)
        )
      )
      .andExpect(status().isOk());
    mvc
      .perform(get("/api/public/contents/" + id))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.excerpt").value("人工撰写的列表介绍"));
  }

  @Test
  void bodyCoverAndAttachmentImagesFollowPublishedRevision() throws Exception {
    var operator = login("operator", "Test-Operator-2026!");
    var image = new java.awt.image.BufferedImage(
      2,
      2,
      java.awt.image.BufferedImage.TYPE_INT_RGB
    );
    var bytes = new java.io.ByteArrayOutputStream();
    assertTrue(javax.imageio.ImageIO.write(image, "png", bytes));
    var cover = uploadMedia(
      operator,
      "cover.png",
      "image/png",
      bytes.toByteArray()
    );
    var inline = uploadMedia(
      operator,
      "inline.png",
      "image/png",
      bytes.toByteArray()
    );
    var attachment = uploadMedia(
      operator,
      "attachment.png",
      "image/png",
      bytes.toByteArray()
    );
    var images = List.of(cover, inline, attachment);
    for (var media : images) {
      mvc.perform(get(str(media, "url"))).andExpect(status().isNotFound());
    }
    mvc
      .perform(get(str(inline, "url")).cookie(operator.cookie()))
      .andExpect(status().isOk());
    var body = article("发布图片链路测试");
    body.put("cover", cover.get("url"));
    body.put(
      "body",
      "<p>正文图片</p><img src=\"" +
        inline.get("url") +
        "\" alt=\"材料图片\" onerror=\"alert(1)\">" +
        "<img src=\"https://evil.example/track.png\"><img src=\"//evil.example/track.png\"><img src=\"data:image/png;base64,AAAA\">"
    );
    body.put("attachments", List.of(attachment.get("id")));
    var result = mvc
      .perform(write("/api/operator/contents", operator, body))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.attachmentInfo[0].mime").value("image/png"))
      .andReturn();
    var draft = s.decode(result.getResponse().getContentAsString());
    String id = str(draft, "id");
    var html = org.jsoup.Jsoup.parse(str(draft, "body"));
    assertEquals(1, html.select("img").size());
    assertEquals(inline.get("url"), html.selectFirst("img").attr("src"));
    assertFalse(html.selectFirst("img").hasAttr("onerror"));
    mvc
      .perform(
        write(
          "/api/operator/contents/" + id + "/publish",
          operator,
          Map.of("version", 1)
        )
      )
      .andExpect(status().isOk());
    for (var media : images) {
      mvc
        .perform(get(str(media, "url")))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(
          header().string(
            "Content-Disposition",
            org.hamcrest.Matchers.startsWith("inline")
          )
        );
    }

    draft.put("version", 2);
    draft.put("cover", "");
    draft.put("body", "<p>尚未发布的纯文字版本</p>");
    draft.put("attachments", List.of());
    mvc
      .perform(
        put("/api/operator/contents/" + id)
          .cookie(operator.cookie())
          .header("Origin", "http://localhost:5173")
          .header("X-CSRF-Token", operator.csrf())
          .contentType("application/json")
          .content(s.encode(draft))
      )
      .andExpect(status().isOk());
    for (var media : images) {
      mvc.perform(get(str(media, "url"))).andExpect(status().isOk());
    }
    mvc
      .perform(
        write(
          "/api/operator/contents/" + id + "/withdraw",
          operator,
          Map.of("version", 3)
        )
      )
      .andExpect(status().isOk());
    for (var media : images) {
      mvc.perform(get(str(media, "url"))).andExpect(status().isNotFound());
    }
  }

  @Test
  void documentsCannotBeUsedAsCoverOrInlineImages() throws Exception {
    var operator = login("operator", "Test-Operator-2026!");
    var media = uploadMedia(
      operator,
      "notes.txt",
      "text/plain",
      "notes".getBytes()
    );
    var body = article("非图片文件不能作为配图");
    body.put("cover", media.get("url"));
    mvc
      .perform(write("/api/operator/contents", operator, body))
      .andExpect(status().isBadRequest());
    body.put("cover", "");
    body.put("body", "<p>内容</p><img src=\"" + media.get("url") + "\">");
    mvc
      .perform(write("/api/operator/contents", operator, body))
      .andExpect(status().isBadRequest());
  }

  long totalVisits() {
    return s.db.queryForObject(
      "SELECT total_visits FROM site_statistics WHERE id=1",
      Long.class
    );
  }

  @Test
  void anonymousVisitsAreStoredAndReadsDoNotCount() throws Exception {
    long before = totalVisits();
    mvc
      .perform(
        post("/api/public/visits").header("Origin", "http://localhost:5173")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalVisits").value(before + 1))
      .andExpect(header().string("Cache-Control", "no-store"));
    mvc
      .perform(get("/api/public/visits"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalVisits").value(before + 1));
    mvc.perform(get("/api/public/site")).andExpect(status().isOk());
    mvc.perform(get("/api/public/home")).andExpect(status().isOk());
    assertEquals(before + 1, totalVisits());
    mvc
      .perform(
        post("/api/public/visits").header("Origin", "http://localhost:5173")
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.totalVisits").value(before + 2));
    assertEquals(before + 2, totalVisits());
  }

  @Test
  void visitsRejectMissingAndForeignOrigins() throws Exception {
    long before = totalVisits();
    mvc.perform(post("/api/public/visits")).andExpect(status().isForbidden());
    mvc
      .perform(
        post("/api/public/visits").header("Origin", "https://evil.example")
      )
      .andExpect(status().isForbidden());
    assertEquals(before, totalVisits());
  }

  @Test
  void concurrentVisitsDoNotLoseUpdatesOrOverflowIntegers() throws Exception {
    long before = Integer.MAX_VALUE;
    s.update("UPDATE site_statistics SET total_visits=? WHERE id=1", before);
    var executor = Executors.newFixedThreadPool(8);
    try {
      var requests = new ArrayList<Future<Long>>();
      for (int i = 0; i < 16; i++) {
        requests.add(
          executor.submit(() -> {
            var response = mvc
              .perform(
                post("/api/public/visits").header(
                  "Origin",
                  "http://localhost:5173"
                )
              )
              .andExpect(status().isOk())
              .andReturn()
              .getResponse();
            return (
              (Number) s
                .decode(response.getContentAsString())
                .get("totalVisits")
            ).longValue();
          })
        );
      }
      var totals = new HashSet<Long>();
      for (var request : requests)
        totals.add(request.get(15, TimeUnit.SECONDS));
      assertEquals(16, totals.size());
      assertEquals(before + 16, totalVisits());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void rolesAndCsrfEnforced() throws Exception {
    mvc
      .perform(
        post("/api/auth/register")
          .header("Origin", "http://localhost:5173")
          .contentType("application/json")
          .content(
            s.encode(
              Map.of(
                "username",
                "member_test",
                "name",
                "普通人员",
                "password",
                "Member-Test-2026!",
                "role",
                "OPERATOR"
              )
            )
          )
      )
      .andExpect(status().isOk());
    var member = login("member_test", "Member-Test-2026!");
    mvc
      .perform(get("/api/operator/contents").cookie(member.cookie()))
      .andExpect(status().isForbidden());
    mvc
      .perform(write("/api/operator/contents", member, article("越权")))
      .andExpect(status().isForbidden());
    var operator = login("operator", "Test-Operator-2026!");
    mvc
      .perform(
        post("/api/operator/contents")
          .cookie(operator.cookie())
          .header("Origin", "http://localhost:5173")
          .contentType("application/json")
          .content(s.encode(article("CSRF")))
      )
      .andExpect(status().isForbidden());
    mvc
      .perform(
        post("/api/operator/contents")
          .cookie(operator.cookie())
          .header("Origin", "https://evil.example")
          .header("X-CSRF-Token", operator.csrf())
          .contentType("application/json")
          .content(s.encode(article("来源检查")))
      )
      .andExpect(status().isForbidden());
  }

  @Test
  void revisionIsolationWithdrawalAndConflict() throws Exception {
    var operator = login("operator", "Test-Operator-2026!");
    var created = mvc
      .perform(write("/api/operator/contents", operator, article("测试公告")))
      .andExpect(status().isOk())
      .andReturn();
    var draft = s.decode(created.getResponse().getContentAsString());
    String id = str(draft, "id");
    assertFalse(str(draft, "body").contains("script"));
    assertFalse(str(draft, "body").contains("evil.example"));
    mvc
      .perform(get("/api/public/contents/" + id))
      .andExpect(status().isNotFound());
    var published = mvc
      .perform(
        write(
          "/api/operator/contents/" + id + "/publish",
          operator,
          Map.of("version", 1)
        )
      )
      .andExpect(status().isOk())
      .andReturn();
    draft.put("version", 2);
    draft.put("title", "尚未发布的新标题");
    mvc
      .perform(
        put("/api/operator/contents/" + id)
          .cookie(operator.cookie())
          .header("Origin", "http://localhost:5173")
          .header("X-CSRF-Token", operator.csrf())
          .contentType("application/json")
          .content(s.encode(draft))
      )
      .andExpect(status().isOk());
    var publicItem = mvc
      .perform(get("/api/public/contents/" + id))
      .andExpect(status().isOk())
      .andReturn();
    assertEquals(
      "测试公告",
      s.decode(publicItem.getResponse().getContentAsString()).get("title")
    );
    mvc
      .perform(
        write(
          "/api/operator/contents/" + id + "/publish",
          operator,
          Map.of("version", 2)
        )
      )
      .andExpect(status().isConflict());
    mvc
      .perform(
        write(
          "/api/operator/contents/" + id + "/withdraw",
          operator,
          Map.of("version", 3)
        )
      )
      .andExpect(status().isOk());
    mvc
      .perform(get("/api/public/contents/" + id))
      .andExpect(status().isNotFound());
    var search = mvc
      .perform(get("/api/public/contents").param("q", "测试公告"))
      .andExpect(status().isOk())
      .andReturn();
    assertEquals(
      0,
      num(s.decode(search.getResponse().getContentAsString()), "total", -1)
    );
  }

  @Test
  void attachmentsFollowPublishedRevision() throws Exception {
    var operator = login("operator", "Test-Operator-2026!");
    var file = new MockMultipartFile(
      "file",
      "notes.txt",
      "text/plain",
      "private draft".getBytes()
    );
    var uploaded = mvc
      .perform(
        multipart("/api/operator/media")
          .file(file)
          .cookie(operator.cookie())
          .header("Origin", "http://localhost:5173")
          .header("X-CSRF-Token", operator.csrf())
      )
      .andExpect(status().isOk())
      .andReturn();
    var media = s.decode(uploaded.getResponse().getContentAsString());
    String mid = str(media, "id");
    mvc.perform(get("/api/media/" + mid)).andExpect(status().isNotFound());
    var b = article("带附件的公告");
    b.put("attachments", List.of(mid));
    var result = mvc
      .perform(write("/api/operator/contents", operator, b))
      .andExpect(status().isOk())
      .andReturn();
    String id = str(s.decode(result.getResponse().getContentAsString()), "id");
    mvc
      .perform(
        write(
          "/api/operator/contents/" + id + "/publish",
          operator,
          Map.of("version", 1)
        )
      )
      .andExpect(status().isOk());
    mvc.perform(get("/api/media/" + mid)).andExpect(status().isOk());
    mvc
      .perform(
        write(
          "/api/operator/contents/" + id + "/withdraw",
          operator,
          Map.of("version", 2)
        )
      )
      .andExpect(status().isOk());
    mvc.perform(get("/api/media/" + mid)).andExpect(status().isNotFound());
  }

  @Test
  void categoryCycleAndUnsafeUrlRejected() throws Exception {
    var op = login("operator", "Test-Operator-2026!");
    var c = s.one("SELECT * FROM categories WHERE id='news'");
    c.put("parent_id", "headlines");
    mvc
      .perform(write("/api/operator/categories", op, c))
      .andExpect(status().isBadRequest());
    var b = article("不安全外链");
    b.put("url", "javascript:alert(1)");
    mvc
      .perform(write("/api/operator/contents", op, b))
      .andExpect(status().isBadRequest());
  }

  @Test
  void unavailableCenterDoesNotBreakLocalLogin() throws Exception {
    mvc
      .perform(get("/auth/center/login"))
      .andExpect(status().is3xxRedirection());
    var op = login("operator", "Test-Operator-2026!");
    mvc
      .perform(get("/api/auth/me").cookie(op.cookie()))
      .andExpect(status().isOk());
    mvc
      .perform(
        post("/auth/center/logout")
          .contentType("application/json")
          .content("{}")
      )
      .andExpect(status().isServiceUnavailable());
  }
}
