package cn.materials.portal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class Store {

  final JdbcTemplate db;
  final ObjectMapper json;

  public Store(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  public List<Map<String, Object>> rows(String sql, Object... args) {
    return db.queryForList(sql, args);
  }

  public Map<String, Object> one(String sql, Object... args) {
    var r = rows(sql, args);
    return r.isEmpty() ? null : r.get(0);
  }

  public int update(String sql, Object... args) {
    return db.update(sql, args);
  }

  public String encode(Object v) {
    try {
      return json.writeValueAsString(v);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  public Map<String, Object> decode(Object v) {
    try {
      return json.readValue(
        String.valueOf(v),
        new TypeReference<LinkedHashMap<String, Object>>() {}
      );
    } catch (Exception e) {
      throw new IllegalStateException("Invalid stored JSON", e);
    }
  }

  public void audit(String actor, String action, String object) {
    update(
      "INSERT INTO audit_logs VALUES(?,?,?,?,?)",
      id(),
      actor,
      action,
      object,
      now()
    );
  }

  static String id() {
    return UUID.randomUUID().toString();
  }

  static long now() {
    return System.currentTimeMillis();
  }

  static String str(Map<String, ?> m, String k) {
    return Objects.toString(m.get(k), "");
  }

  static int num(Map<String, ?> m, String k, int fallback) {
    try {
      return Integer.parseInt(str(m, k));
    } catch (Exception e) {
      return fallback;
    }
  }

  static boolean bool(Map<String, ?> m, String k) {
    return Boolean.parseBoolean(str(m, k));
  }

  static ResponseStatusException fail(int status, String text) {
    return new ResponseStatusException(HttpStatus.valueOf(status), text);
  }

  static void require(boolean condition, int status, String text) {
    if (!condition) throw fail(status, text);
  }
}
