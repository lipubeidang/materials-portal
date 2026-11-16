package cn.materials.portal;

import static cn.materials.portal.Store.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class FeedbackController {

  final Store s;
  final Auth a;

  FeedbackController(Store s, Auth a) {
    this.s = s;
    this.a = a;
  }

  @PostMapping("/api/public/feedback")
  Object submit(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    String subject = str(body, "subject").trim();
    String description = str(body, "description").trim();
    require(!subject.isBlank() && subject.length() <= 100, 400, "请填写不超过 100 字的意见主题");
    require(!description.isBlank() && description.length() <= 200, 400, "请填写不超过 200 字的意见描述");
    a.rate("feedback:" + request.getRemoteAddr(), 10);

    String feedbackId = id();
    s.update(
      "INSERT INTO feedbacks(id,subject,description,created_at) VALUES(?,?,?,?)",
      feedbackId,
      subject,
      description,
      now()
    );
    return Map.of("id", feedbackId);
  }
}
