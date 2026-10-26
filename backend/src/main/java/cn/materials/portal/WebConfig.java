package cn.materials.portal;

import jakarta.servlet.http.*;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  final Auth a;
  final ObjectProvider<CenterService> center;

  WebConfig(Auth a, ObjectProvider<CenterService> center) {
    this.a = a;
    this.center = center;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
      .addInterceptor(
        new HandlerInterceptor() {
          @Override
          public boolean preHandle(
            HttpServletRequest r,
            HttpServletResponse response,
            Object handler
          ) {
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Referrer-Policy", "same-origin");
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("Cache-Control", "no-store");
            String path = r.getRequestURI();
            if (path.startsWith("/api/operator/")) a.operator(r);
            if (path.startsWith("/api/account/")) a.requireUser(r);
            if (
              path.startsWith("/api/") &&
              !Set.of("GET", "HEAD", "OPTIONS").contains(r.getMethod()) &&
              !Set.of("/api/auth/login", "/api/auth/register").contains(path)
            ) {
              if (path.equals("/api/public/visits")) a.checkOrigin(r);
              else a.csrf(r);
            }
            if (
              path.startsWith("/api/") &&
              !path.equals("/api/auth/logout") &&
              !path.equals("/api/auth/login") &&
              !path.equals("/api/auth/register")
            ) {
              var session = a.session(r);
              if (
                session != null && "center".equals(session.get("source"))
              ) center.getObject().check(session);
            }
            return true;
          }
        }
      )
      .addPathPatterns("/api/**", "/auth/center/**");
  }
}
