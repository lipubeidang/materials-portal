package cn.materials.portal;

import static cn.materials.portal.Store.*;

import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class Auth {

  final Store s;
  final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
  private final SecureRandom random = new SecureRandom();
  private final Map<String, long[]> limits = new ConcurrentHashMap<>();

  @Value("${app.secure-cookie}")
  boolean secure;

  @Value("${app.origin}")
  String origin;

  public Auth(Store s) {
    this.s = s;
  }

  String random() {
    byte[] b = new byte[32];
    random.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  static String hash(String v) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(
          v.getBytes(StandardCharsets.UTF_8)
        )
      );
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  static String cookie(HttpServletRequest r, String name) {
    if (r.getCookies() != null) for (Cookie c : r.getCookies())
      if (name.equals(c.getName())) return c.getValue();
    return "";
  }

  void cookie(HttpServletResponse r, String name, String value, long seconds) {
    r.addHeader(
      "Set-Cookie",
      ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(secure)
        .sameSite("Lax")
        .path("/")
        .maxAge(seconds)
        .build()
        .toString()
    );
  }

  Map<String, Object> session(HttpServletRequest r) {
    String token = cookie(r, "PORTAL_SESSION");
    if (token.isEmpty()) return null;
    return s.one(
      "SELECT x.*,u.role,u.username,u.display_name,u.password_hash FROM sessions x JOIN users u ON u.id=x.user_id WHERE x.token_hash=? AND x.expires_at>? AND u.enabled=TRUE",
      hash(token),
      now()
    );
  }

  Map<String, Object> requireUser(HttpServletRequest r) {
    var u = session(r);
    require(u != null, 401, "请先登录");
    return u;
  }

  Map<String, Object> operator(HttpServletRequest r) {
    var u = requireUser(r);
    require("OPERATOR".equals(u.get("role")), 403, "仅操作员可以执行此操作");
    return u;
  }

  Map<String, Object> view(Map<String, Object> u) {
    if (u == null) return Map.of("authenticated", false);
    return Map.of(
      "authenticated",
      true,
      "id",
      u.get("user_id"),
      "username",
      u.get("username"),
      "name",
      u.get("display_name"),
      "role",
      u.get("role"),
      "source",
      u.get("source"),
      "csrf",
      u.get("csrf"),
      "hasPassword",
      u.get("password_hash") != null
    );
  }

  void checkOrigin(HttpServletRequest r) {
    String incoming = r.getHeader("Origin");
    require(
      incoming != null && incoming.equals(origin),
      403,
      "请求来源不匹配，请从本站操作"
    );
  }

  void csrf(HttpServletRequest r) {
    checkOrigin(r);
    var u = requireUser(r);
    String supplied = Objects.toString(r.getHeader("X-CSRF-Token"), "");
    require(
      MessageDigest.isEqual(
        str(u, "csrf").getBytes(StandardCharsets.UTF_8),
        supplied.getBytes(StandardCharsets.UTF_8)
      ),
      403,
      "登录状态已更新，请刷新页面"
    );
  }

  synchronized void rate(String key, int max) {
    long t = now();
    var item = limits.computeIfAbsent(key, k -> new long[] { t, 0 });
    if (t - item[0] > 600_000) {
      item[0] = t;
      item[1] = 0;
    }
    require(++item[1] <= max, 429, "尝试过于频繁，请稍后再试");
  }

  void validatePassword(String password) {
    require(
      password.length() >= 10 &&
        password.getBytes(StandardCharsets.UTF_8).length <= 72,
      400,
      "密码须为 10 至 72 字节"
    );
  }

  Map<String, Object> createSession(
    String uid,
    String source,
    HttpServletRequest request,
    HttpServletResponse response
  ) {
    var old = session(request);
    if (old != null) s.update(
      "DELETE FROM sessions WHERE token_hash=?",
      old.get("token_hash")
    );
    String token = random(),
      csrf = random();
    s.update(
      "INSERT INTO sessions(token_hash,user_id,csrf,source,expires_at) VALUES(?,?,?,?,?)",
      hash(token),
      uid,
      csrf,
      source,
      now() + 43_200_000
    );
    cookie(response, "PORTAL_SESSION", token, 43200);
    return s.one("SELECT * FROM sessions WHERE token_hash=?", hash(token));
  }

  @Scheduled(fixedDelay = 600000)
  void clean() {
    s.update("DELETE FROM sessions WHERE expires_at<?", now());
    s.update("DELETE FROM auth_transactions WHERE expires_at<?", now());
    limits.entrySet().removeIf(e -> now() - e.getValue()[0] > 600_000);
  }
}
