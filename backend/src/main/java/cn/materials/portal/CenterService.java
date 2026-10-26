package cn.materials.portal;

import static cn.materials.portal.Store.*;

import jakarta.servlet.http.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class CenterService {

  final Store s;
  final Auth a;
  final Environment env;
  final HttpClient http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(8))
    .followRedirects(HttpClient.Redirect.NEVER)
    .build();

  CenterService(Store s, Auth a, Environment env) {
    this.s = s;
    this.a = a;
    this.env = env;
    Security.addProvider(new BouncyCastleProvider());
  }

  String config(String name) {
    return env.getProperty("app.center." + name, "");
  }

  boolean enabled() {
    return Boolean.parseBoolean(config("enabled"));
  }

  void configured() {
    require(enabled(), 503, "中心认证尚未配置，请使用本地账号登录");
    for (String key : List.of(
      "base-url",
      "client-id",
      "client-secret",
      "callback",
      "encryption-key"
    ))
      require(!config(key).isBlank(), 503, "中心认证配置不完整");
    if (env.getProperty("app.production", Boolean.class, false)) require(
      config("base-url").startsWith("https://") &&
        config("callback").startsWith("https://"),
      503,
      "正式环境中心地址必须使用 HTTPS"
    );
  }

  String crypt(String value, boolean encrypt) {
    if (value == null || value.isBlank()) return "";
    try {
      byte[] key = Base64.getDecoder().decode(config("encryption-key"));
      require(key.length == 32, 503, "令牌存储密钥必须是 32 字节 Base64");
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      if (encrypt) {
        byte[] nonce = new byte[12];
        new SecureRandom().nextBytes(nonce);
        cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(key, "AES"),
          new GCMParameterSpec(128, nonce)
        );
        byte[] data = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(
          java.nio.ByteBuffer.allocate(12 + data.length)
            .put(nonce)
            .put(data)
            .array()
        );
      }
      byte[] all = Base64.getDecoder().decode(value);
      cipher.init(
        Cipher.DECRYPT_MODE,
        new SecretKeySpec(key, "AES"),
        new GCMParameterSpec(128, Arrays.copyOfRange(all, 0, 12))
      );
      return new String(
        cipher.doFinal(Arrays.copyOfRange(all, 12, all.length)),
        StandardCharsets.UTF_8
      );
    } catch (org.springframework.web.server.ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw fail(503, "令牌存储配置错误");
    }
  }

  String challenge(String verifier) {
    try {
      String secret = config("client-secret");
      require(secret.length() >= 16, 503, "中心密钥不足 16 位");
      byte[] key = secret.substring(0, 16).getBytes(StandardCharsets.UTF_8);
      require(key.length == 16, 503, "中心 SM4 密钥字节数不正确");
      Cipher cipher = Cipher.getInstance("SM4/ECB/PKCS7Padding", "BC");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "SM4"));
      return Base64.getEncoder().encodeToString(
        cipher.doFinal(verifier.getBytes(StandardCharsets.UTF_8))
      );
    } catch (org.springframework.web.server.ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw fail(503, "SM4 配置错误");
    }
  }

  static String url(String v) {
    return URLEncoder.encode(v, StandardCharsets.UTF_8);
  }

  String start(
    HttpServletRequest request,
    HttpServletResponse response,
    String bind
  ) {
    configured();
    a.rate("center-start:" + request.getRemoteAddr(), 30);
    String transaction = a.random(),
      browser = a.random(),
      verifier = a.random() + a.random();
    String callback = config("callback");
    require(
      !callback.contains("?") && !callback.contains("#"),
      503,
      "回调配置请使用固定无参数地址"
    );
    String redirect = callback + "?tx=" + transaction;
    s.update(
      "INSERT INTO auth_transactions VALUES(?,?,?,?,?,?)",
      transaction,
      Auth.hash(browser),
      verifier,
      now() + 120000,
      bind,
      redirect
    );
    a.cookie(response, "CENTER_BROWSER", browser, 120);
    return (
      config("base-url").replaceAll("/$", "") +
      "/oauth/authorize?responseType=code&clientId=" +
      url(config("client-id")) +
      "&redirectUri=" +
      url(redirect) +
      "&code_challenge=" +
      url(challenge(verifier)) +
      "&code_challenge_method=SM4"
    );
  }

  Map<String, Object> call(
    String path,
    Map<String, String> fields,
    String token
  ) {
    try {
      var request = HttpRequest.newBuilder(
        URI.create(config("base-url").replaceAll("/$", "") + path)
      ).timeout(Duration.ofSeconds(12));
      if (token != null) {
        request
          .header("Authorization", config("authorization-prefix") + token)
          .POST(HttpRequest.BodyPublishers.noBody());
      } else {
        String boundary = "Portal" + a.random();
        StringBuilder body = new StringBuilder();
        for (var entry : fields.entrySet())
          body
            .append("--")
            .append(boundary)
            .append("\r\nContent-Disposition: form-data; name=\"")
            .append(entry.getKey())
            .append("\"\r\n\r\n")
            .append(entry.getValue())
            .append("\r\n");
        body.append("--").append(boundary).append("--\r\n");
        request
          .header("Content-Type", "multipart/form-data; boundary=" + boundary)
          .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
      }
      var response = http.send(
        request.build(),
        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
      );
      require(
        response.statusCode() >= 200 && response.statusCode() < 300,
        502,
        "中心认证服务返回异常"
      );
      require(response.body().length() < 1_000_000, 502, "中心响应过大");
      Map<String, Object> result;
      try {
        result = s.decode(response.body());
      } catch (Exception e) {
        throw fail(502, "中心响应不是有效 JSON");
      }
      require(
        !result.containsKey("code") || num(result, "code", 500) == 200,
        401,
        "中心认证失败或票据失效，请重新认证"
      );
      return result;
    } catch (org.springframework.web.server.ResponseStatusException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw fail(503, "中心认证请求已中断");
    } catch (Exception e) {
      throw fail(503, "暂时无法连接中心认证服务");
    }
  }

  static String at(Map<String, Object> map, String path) {
    Object current = map;
    for (String part : path.split("\\.")) {
      if (!(current instanceof Map<?, ?> m)) return "";
      current = m.get(part);
    }
    return current == null ? "" : String.valueOf(current);
  }

  void callback(
    String tx,
    String code,
    HttpServletRequest request,
    HttpServletResponse response
  ) {
    configured();
    var pending = s.one(
      "SELECT * FROM auth_transactions WHERE id=? AND expires_at>?",
      tx,
      now()
    );
    require(pending != null, 400, "认证已过期或已使用，请重新登录");
    require(
      MessageDigest.isEqual(
        str(pending, "browser_hash").getBytes(StandardCharsets.UTF_8),
        Auth.hash(Auth.cookie(request, "CENTER_BROWSER")).getBytes(
          StandardCharsets.UTF_8
        )
      ),
      400,
      "认证浏览器不匹配"
    );
    require(!code.isBlank() && code.length() < 500, 400, "缺少有效授权码");
    require(
      s.update("DELETE FROM auth_transactions WHERE id=?", tx) == 1,
      400,
      "认证请求已使用"
    );
    a.cookie(response, "CENTER_BROWSER", "", 0);
    var tokens = call(
      "/oauth/token",
      Map.of(
        "grantType",
        "authorization_code",
        "code",
        code,
        "clientId",
        config("client-id"),
        "clientSecret",
        config("client-secret"),
        "redirectUri",
        str(pending, "redirect_uri"),
        "code_verifier",
        str(pending, "verifier")
      ),
      null
    );
    String access = str(tokens, "accessToken");
    require(!access.isBlank(), 502, "中心没有返回 accessToken");
    var info = call("/oauth/userinfo", Map.of(), access);
    String external = at(info, config("user-id-path")),
      name = at(info, config("user-name-path"));
    require(
      !external.isBlank() && external.length() <= 160,
      502,
      "中心用户唯一标识未返回，请核对字段配置"
    );
    String bind = str(pending, "bind_user");
    var identity = s.one(
      "SELECT user_id FROM external_identities WHERE external_id=?",
      external
    );
    String uid;
    if (!bind.isBlank()) {
      var current = a.requireUser(request);
      require(
        bind.equals(current.get("user_id")),
        403,
        "绑定账号的登录状态已变化"
      );
      require(
        identity == null || bind.equals(identity.get("user_id")),
        409,
        "中心身份已绑定其他账号"
      );
      require(
        s.one(
          "SELECT external_id FROM external_identities WHERE user_id=? AND external_id<>?",
          bind,
          external
        ) == null,
        409,
        "该账号已绑定另一中心身份"
      );
      if (identity == null) s.update(
        "INSERT INTO external_identities VALUES(?,?)",
        external,
        bind
      );
      s.audit(bind, "BIND_CENTER", external);
      return;
    }
    if (identity != null) uid = str(identity, "user_id");
    else {
      uid = id();
      if (name.isBlank()) name = "中心用户";
      if (name.length() > 60) name = name.substring(0, 60);
      s.update(
        "INSERT INTO users VALUES(?,?,?,?,?,?,?)",
        uid,
        "center_" + UUID.randomUUID().toString().replace("-", ""),
        name,
        null,
        "MEMBER",
        true,
        now()
      );
      try {
        s.update("INSERT INTO external_identities VALUES(?,?)", external, uid);
      } catch (org.springframework.dao.DuplicateKeyException e) {
        s.update("DELETE FROM users WHERE id=?", uid);
        uid = str(
          s.one(
            "SELECT user_id FROM external_identities WHERE external_id=?",
            external
          ),
          "user_id"
        );
      }
    }
    require(
      s.one("SELECT id FROM users WHERE id=? AND enabled=TRUE", uid) != null,
      403,
      "账号已停用"
    );
    String encrypted = crypt(access, true),
      refresh = crypt(str(tokens, "refreshToken"), true);
    var session = a.createSession(uid, "center", request, response);
    s.update(
      "UPDATE sessions SET access_token=?,refresh_token=?,token_expires=?,external_id=?,checked_at=? WHERE token_hash=?",
      encrypted,
      refresh,
      now() + Math.max(1, num(tokens, "expiresIn", 43200)) * 1000L,
      external,
      now(),
      session.get("token_hash")
    );
    s.audit(uid, "LOGIN_CENTER", external);
  }

  synchronized void check(Map<String, Object> original) {
    var session = s.one(
      "SELECT * FROM sessions WHERE token_hash=?",
      original.get("token_hash")
    );
    require(session != null, 401, "登录已退出");
    long checked = ((Number) session.get("checked_at")).longValue();
    if (now() - checked < 60000) return;
    configured();
    try {
      long expires = ((Number) session.get("token_expires")).longValue();
      String token = crypt(str(session, "access_token"), false);
      if (expires <= now() + 30000) {
        String refresh = crypt(str(session, "refresh_token"), false);
        if (refresh.isBlank()) {
          s.update(
            "DELETE FROM sessions WHERE token_hash=?",
            session.get("token_hash")
          );
          throw fail(401, "中心令牌到期，请重新认证");
        }
        // Consume locally before calling; a timeout must not replay a single-use refresh token.
        s.update(
          "UPDATE sessions SET refresh_token=NULL WHERE token_hash=?",
          session.get("token_hash")
        );
        var result = call(
          "/oauth/token",
          Map.of(
            "grantType",
            "refresh_token",
            "clientId",
            config("client-id"),
            "clientSecret",
            config("client-secret"),
            "refreshToken",
            refresh
          ),
          null
        );
        token = str(result, "accessToken");
        require(!token.isBlank(), 502, "刷新响应缺少令牌");
        s.update(
          "UPDATE sessions SET access_token=?,token_expires=? WHERE token_hash=?",
          crypt(token, true),
          now() + Math.max(1, num(result, "expiresIn", 43200)) * 1000L,
          session.get("token_hash")
        );
      }
      var online = call("/oauth/checkLogin", Map.of(), token);
      require(
        num(online, "isOnline", 0) == 1,
        401,
        "中心用户已离线，请重新认证"
      );
      s.update(
        "UPDATE sessions SET checked_at=? WHERE token_hash=?",
        now(),
        session.get("token_hash")
      );
    } catch (org.springframework.web.server.ResponseStatusException e) {
      if (e.getStatusCode().value() == 401) s.update(
        "DELETE FROM sessions WHERE token_hash=?",
        session.get("token_hash")
      );
      throw e;
    }
  }

  void logout(Map<String, Object> b, HttpServletRequest r) {
    configured();
    String expected = config("logout-key"),
      supplied = Objects.toString(r.getHeader("X-Center-Logout-Key"), "");
    boolean keyOk =
      !expected.isBlank() &&
      MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        supplied.getBytes(StandardCharsets.UTF_8)
      );
    boolean ipOk =
      !config("logout-ips").isBlank() &&
      Arrays.stream(config("logout-ips").split(","))
        .map(String::trim)
        .anyMatch(ip -> ip.equals(r.getRemoteAddr()));
    require(keyOk || ipOk, 403, "退出通知来源未获授权");
    require(
      "logoutRequest".equals(str(b, "request")) &&
        !str(b, "ticket").isBlank() &&
        !str(b, "principal").isBlank(),
      400,
      "退出通知字段不完整"
    );
    for (var session : s.rows(
      "SELECT * FROM sessions WHERE source='center' AND external_id=?",
      str(b, "principal")
    ))
      if (
        MessageDigest.isEqual(
          crypt(str(session, "access_token"), false).getBytes(
            StandardCharsets.UTF_8
          ),
          str(b, "ticket").getBytes(StandardCharsets.UTF_8)
        )
      ) {
        s.update(
          "DELETE FROM sessions WHERE token_hash=?",
          session.get("token_hash")
        );
        s.audit(str(session, "user_id"), "CENTER_LOGOUT", str(b, "id"));
      }
  }
}
