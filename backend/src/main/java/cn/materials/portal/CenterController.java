package cn.materials.portal;

import static cn.materials.portal.Store.*;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class CenterController {

  final CenterService center;
  final Auth a;

  CenterController(CenterService center, Auth a) {
    this.center = center;
    this.a = a;
  }

  @GetMapping("/api/public/auth-config")
  Object config() {
    return Map.of("centerEnabled", center.enabled());
  }

  @GetMapping("/auth/center/login")
  void login(HttpServletRequest r, HttpServletResponse response)
    throws IOException {
    try {
      response.sendRedirect(center.start(r, response, null));
    } catch (org.springframework.web.server.ResponseStatusException e) {
      response.sendRedirect(
        "/login?error=" +
          CenterService.url(Objects.toString(e.getReason(), "认证失败"))
      );
    }
  }

  @PostMapping("/api/account/link-center")
  Object link(
    @RequestBody Map<String, Object> b,
    HttpServletRequest r,
    HttpServletResponse response
  ) {
    var user = a.requireUser(r);
    a.rate("link:" + user.get("user_id"), 10);
    require(
      user.get("password_hash") != null &&
        a.passwords.matches(str(b, "password"), str(user, "password_hash")),
      403,
      "请验证当前账号密码"
    );
    return Map.of("url", center.start(r, response, str(user, "user_id")));
  }

  @GetMapping("/auth/center/callback")
  void callback(
    @RequestParam(defaultValue = "") String tx,
    @RequestParam(defaultValue = "") String code,
    HttpServletRequest r,
    HttpServletResponse response
  ) throws IOException {
    try {
      center.callback(tx, code, r, response);
      response.sendRedirect("/account");
    } catch (org.springframework.web.server.ResponseStatusException e) {
      response.sendRedirect(
        "/login?error=" +
          CenterService.url(Objects.toString(e.getReason(), "认证失败"))
      );
    }
  }

  @PostMapping("/auth/center/logout")
  Object logout(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    center.logout(b, r);
    return Map.of("code", 200, "msg", "ok");
  }
}
