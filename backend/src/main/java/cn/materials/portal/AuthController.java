package cn.materials.portal;

import static cn.materials.portal.Store.*;

import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {

  final Auth a;
  final Store s;

  AuthController(Auth a, Store s) {
    this.a = a;
    this.s = s;
  }

  @GetMapping("/api/auth/me")
  Object me(HttpServletRequest r) {
    return a.view(a.session(r));
  }

  @PostMapping("/api/auth/register")
  @Transactional
  Object register(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    a.checkOrigin(r);
    a.rate("register:" + r.getRemoteAddr(), 12);
    String name = str(b, "username").trim().toLowerCase(Locale.ROOT),
      password = str(b, "password");
    require(
      name.matches("[a-z0-9_]{3,32}"),
      400,
      "用户名为 3—32 位字母、数字或下划线"
    );
    a.validatePassword(password);
    String display = str(b, "name").trim();
    require(
      !display.isEmpty() && display.length() <= 60,
      400,
      "请填写姓名或昵称（最多 60 字）"
    );
    String uid = id();
    s.update(
      "INSERT INTO users VALUES(?,?,?,?,?,?,?)",
      uid,
      name,
      display,
      a.passwords.encode(password),
      "MEMBER",
      true,
      now()
    );
    s.audit(uid, "REGISTER", uid);
    return Map.of("message", "注册成功，请登录");
  }

  @PostMapping("/api/auth/login")
  Object login(
    @RequestBody Map<String, Object> b,
    HttpServletRequest r,
    HttpServletResponse response
  ) {
    a.checkOrigin(r);
    a.rate("login:" + r.getRemoteAddr(), 30);
    String name = str(b, "username").trim().toLowerCase(Locale.ROOT);
    var u = s.one(
      "SELECT * FROM users WHERE username=? AND enabled=TRUE",
      name
    );
    String p = str(b, "password");
    require(
      p.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72,
      400,
      "账号或密码不正确"
    );
    require(
      u != null &&
        u.get("password_hash") != null &&
        a.passwords.matches(p, str(u, "password_hash")),
      401,
      "账号或密码不正确"
    );
    a.createSession(str(u, "id"), "local", r, response);
    s.audit(str(u, "id"), "LOGIN_LOCAL", str(u, "id"));
    return Map.of("message", "登录成功");
  }

  @PostMapping("/api/auth/logout")
  Object logout(HttpServletRequest r, HttpServletResponse response) {
    var u = a.requireUser(r);
    s.update("DELETE FROM sessions WHERE token_hash=?", u.get("token_hash"));
    a.cookie(response, "PORTAL_SESSION", "", 0);
    return Map.of("message", "已退出");
  }

  @PostMapping("/api/account/password")
  @Transactional
  Object password(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    var u = a.requireUser(r);
    a.rate("password:" + u.get("user_id"), 10);
    require(
      u.get("password_hash") != null &&
        a.passwords.matches(str(b, "oldPassword"), str(u, "password_hash")),
      400,
      "原密码不正确"
    );
    a.validatePassword(str(b, "password"));
    s.update(
      "UPDATE users SET password_hash=? WHERE id=?",
      a.passwords.encode(str(b, "password")),
      u.get("user_id")
    );
    s.update(
      "DELETE FROM sessions WHERE user_id=? AND token_hash<>?",
      u.get("user_id"),
      u.get("token_hash")
    );
    s.audit(str(u, "user_id"), "CHANGE_PASSWORD", str(u, "user_id"));
    return Map.of("message", "密码已更新，其他会话已退出");
  }

  @GetMapping("/api/account/identity")
  Object identity(HttpServletRequest r) {
    var u = a.requireUser(r);
    return Map.of(
      "linked",
      s.one(
        "SELECT external_id FROM external_identities WHERE user_id=?",
        u.get("user_id")
      ) != null
    );
  }
}
