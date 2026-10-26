package cn.materials.portal;

import static cn.materials.portal.Store.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:h2:mem:center;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.demo=false",
    "app.center.enabled=true",
    "app.center.client-id=test-client",
    "app.center.client-secret=0123456789abcdefEXTRA",
    "app.center.callback=http://localhost:5173/auth/center/callback",
    "app.center.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "app.center.logout-key=test-webhook-secret",
    "app.center.logout-ips=",
  }
)
@AutoConfigureMockMvc
class CenterIntegrationTest {

  @Autowired
  MockMvc mvc;

  @Autowired
  Store s;

  @Autowired
  Auth a;

  @Autowired
  CenterService center;

  static HttpServer server;
  static String lastRequest;
  static AtomicInteger refreshes = new AtomicInteger();

  static {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/oauth/token", exchange -> {
        lastRequest = new String(
          exchange.getRequestBody().readAllBytes(),
          StandardCharsets.UTF_8
        );
        boolean refresh = lastRequest.contains("refresh_token");
        if (refresh) refreshes.incrementAndGet();
        String body = refresh
          ? "{\"code\":200,\"accessToken\":\"AT-refreshed\",\"expiresIn\":43200,\"refreshToken\":null}"
          : "{\"code\":200,\"accessToken\":\"AT-initial\",\"expiresIn\":43200,\"refreshToken\":\"RT-once\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
      });
      server.createContext("/oauth/userinfo", exchange -> {
        byte[] bytes =
          "{\"id\":\"center-1001\",\"username\":\"中心测试用户\"}".getBytes(
            StandardCharsets.UTF_8
          );
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
      });
      server.createContext("/oauth/checkLogin", exchange -> {
        byte[] bytes = "{\"code\":200,\"isOnline\":1}".getBytes(
          StandardCharsets.UTF_8
        );
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
      });
      server.start();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @DynamicPropertySource
  static void config(DynamicPropertyRegistry r) {
    r.add(
      "app.center.base-url",
      () -> "http://127.0.0.1:" + server.getAddress().getPort()
    );
  }

  @AfterAll
  static void close() {
    server.stop(0);
  }

  record Start(String tx, Cookie browser) {}

  static Map<String, String> query(String url) {
    var map = new HashMap<String, String>();
    String q = URI.create(url).getRawQuery();
    for (String p : q.split("&")) {
      String[] parts = p.split("=", 2);
      map.put(
        URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
        URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
      );
    }
    return map;
  }

  Start start() throws Exception {
    MvcResult response = mvc
      .perform(get("/auth/center/login"))
      .andExpect(status().is3xxRedirection())
      .andReturn();
    var params = query(response.getResponse().getRedirectedUrl());
    assertEquals("test-client", params.get("clientId"));
    assertEquals("SM4", params.get("code_challenge_method"));
    String tx = query(params.get("redirectUri")).get("tx");
    String raw = response
      .getResponse()
      .getHeader("Set-Cookie")
      .split(";", 2)[0];
    return new Start(
      tx,
      new Cookie("CENTER_BROWSER", raw.substring("CENTER_BROWSER=".length()))
    );
  }

  Cookie complete(Start start) throws Exception {
    var result = mvc
      .perform(
        get("/auth/center/callback")
          .param("tx", start.tx())
          .param("code", "OC-test")
          .cookie(start.browser())
      )
      .andExpect(redirectedUrl("/account"))
      .andReturn();
    String cookie = result
      .getResponse()
      .getHeaders("Set-Cookie")
      .stream()
      .filter(x -> x.startsWith("PORTAL_SESSION="))
      .findFirst()
      .orElseThrow();
    return new Cookie(
      "PORTAL_SESSION",
      cookie.split(";", 2)[0].substring("PORTAL_SESSION=".length())
    );
  }

  @Test
  void fullLoginPkceAndReplayProtection() throws Exception {
    var pending = start();
    var context = s.one(
      "SELECT * FROM auth_transactions WHERE id=?",
      pending.tx()
    );
    assertTrue(str(context, "verifier").length() >= 43);
    Cookie cookie = complete(pending);
    var result = mvc
      .perform(get("/api/auth/me").cookie(cookie))
      .andExpect(status().isOk())
      .andReturn();
    var me = s.decode(result.getResponse().getContentAsString());
    assertEquals("MEMBER", me.get("role"));
    assertEquals("center", me.get("source"));
    assertTrue(lastRequest.contains("name=\"code_verifier\""));
    assertTrue(lastRequest.contains(str(context, "verifier")));
    mvc
      .perform(
        get("/auth/center/callback")
          .param("tx", pending.tx())
          .param("code", "OC-test")
          .cookie(pending.browser())
      )
      .andExpect(redirectedUrlPattern("/login?error=*"));
  }

  @Test
  void browserMismatchCannotConsumeTransaction() throws Exception {
    var pending = start();
    mvc
      .perform(
        get("/auth/center/callback")
          .param("tx", pending.tx())
          .param("code", "OC-test")
          .cookie(new Cookie("CENTER_BROWSER", "wrong-browser"))
      )
      .andExpect(redirectedUrlPattern("/login?error=*"));
    assertNotNull(
      s.one("SELECT id FROM auth_transactions WHERE id=?", pending.tx())
    );
    complete(pending);
  }

  @Test
  void expiryIsRejected() throws Exception {
    var pending = start();
    s.update(
      "UPDATE auth_transactions SET expires_at=0 WHERE id=?",
      pending.tx()
    );
    mvc
      .perform(
        get("/auth/center/callback")
          .param("tx", pending.tx())
          .param("code", "OC-test")
          .cookie(pending.browser())
      )
      .andExpect(redirectedUrlPattern("/login?error=*"));
  }

  @Test
  void encryptedStorageAndSingleRefresh() throws Exception {
    var cookie = complete(start());
    String hash = Auth.hash(cookie.getValue());
    var row = s.one("SELECT * FROM sessions WHERE token_hash=?", hash);
    assertNotEquals("AT-initial", row.get("access_token"));
    assertEquals("AT-initial", center.crypt(str(row, "access_token"), false));
    s.update(
      "UPDATE sessions SET checked_at=0,token_expires=0 WHERE token_hash=?",
      hash
    );
    int before = refreshes.get();
    mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isOk());
    assertEquals(before + 1, refreshes.get());
    row = s.one("SELECT * FROM sessions WHERE token_hash=?", hash);
    assertNull(row.get("refresh_token"));
    assertEquals("AT-refreshed", center.crypt(str(row, "access_token"), false));
    s.update(
      "UPDATE sessions SET checked_at=0,token_expires=0 WHERE token_hash=?",
      hash
    );
    mvc
      .perform(get("/api/auth/me").cookie(cookie))
      .andExpect(status().isUnauthorized());
    assertNull(s.one("SELECT * FROM sessions WHERE token_hash=?", hash));
  }

  @Test
  void logoutRequiresTrustedSenderAndMatchesTicket() throws Exception {
    Cookie cookie = complete(start());
    String hash = Auth.hash(cookie.getValue());
    var payload = Map.of(
      "principal",
      "center-1001",
      "request",
      "logoutRequest",
      "ticket",
      "AT-initial",
      "id",
      "logout-1"
    );
    mvc
      .perform(
        post("/auth/center/logout")
          .contentType("application/json")
          .content(s.encode(payload))
      )
      .andExpect(status().isForbidden());
    assertNotNull(
      s.one("SELECT token_hash FROM sessions WHERE token_hash=?", hash)
    );
    mvc
      .perform(
        post("/auth/center/logout")
          .header("X-Center-Logout-Key", "test-webhook-secret")
          .contentType("application/json")
          .content(s.encode(payload))
      )
      .andExpect(status().isOk());
    assertNull(
      s.one("SELECT token_hash FROM sessions WHERE token_hash=?", hash)
    );
    mvc
      .perform(
        post("/auth/center/logout")
          .header("X-Center-Logout-Key", "test-webhook-secret")
          .contentType("application/json")
          .content(s.encode(payload))
      )
      .andExpect(status().isOk());
  }

  @Test
  void sm4RoundTripMatchesConfiguredProfile() throws Exception {
    String input = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMN";
    String result = center.challenge(input);
    var cipher = javax.crypto.Cipher.getInstance("SM4/ECB/PKCS7Padding", "BC");
    cipher.init(
      javax.crypto.Cipher.DECRYPT_MODE,
      new javax.crypto.spec.SecretKeySpec(
        "0123456789abcdef".getBytes(StandardCharsets.UTF_8),
        "SM4"
      )
    );
    assertEquals(
      input,
      new String(
        cipher.doFinal(Base64.getDecoder().decode(result)),
        StandardCharsets.UTF_8
      )
    );
  }
}
