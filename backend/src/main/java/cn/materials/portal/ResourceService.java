package cn.materials.portal;

import static cn.materials.portal.Store.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResourceService {

  public record Directory(String id, String name, List<Directory> children) {}

  public record Statistic(String key, String label, String value) {}

  private final ObjectMapper json;
  private final Environment env;
  private final HttpClient http = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(5))
    .followRedirects(HttpClient.Redirect.NEVER)
    .build();
  private final Cache<List<Directory>> directories = new Cache<>();
  private final Cache<List<Statistic>> statistics = new Cache<>();

  ResourceService(ObjectMapper json, Environment env) {
    this.json = json;
    this.env = env;
  }

  String config(String key) {
    return env.getProperty("app.resources." + key, "");
  }

  public List<Directory> directories() {
    return directories.get(() -> {
      var data = request(
        "/database-search/search/fieldStat",
        "{\"statType\":\"1\"}"
      );
      return readDirectories(data.path("treeStat"), 0);
    }, cacheMillis());
  }

  public List<Statistic> statistics() {
    return statistics.get(() -> {
      var data = request(config("statistics-path"), null);
      return List.of(
        statistic(data, "templateCount", "模板量"),
        statistic(data, "fragmentCount", "模板片段量"),
        statistic(data, "datasetCount", "数据集量"),
        statistic(data, "dataCount", "数据量"),
        statistic(data, "publicDataCount", "已发布数据量"),
        statistic(data, "underReleaseDataCount", "发布中数据量")
      );
    }, cacheMillis());
  }

  private long cacheMillis() {
    return (
      Math.max(
        0,
        env.getProperty("app.resources.cache-seconds", Long.class, 60L)
      ) * 1000
    );
  }

  private Statistic statistic(JsonNode data, String key, String label) {
    String value = data.path(key).asText("");
    require(value.matches("[0-9]+"), 502, "统计数据格式异常，请稍后重试");
    // Return decimal strings so large counts retain precision in the browser.
    return new Statistic(
      key,
      label,
      new java.math.BigInteger(value).toString()
    );
  }

  private List<Directory> readDirectories(JsonNode nodes, int depth) {
    require(
      nodes.isArray() && depth <= 20,
      502,
      "目录数据格式异常，请稍后重试"
    );
    var result = new ArrayList<Directory>();
    for (var node : nodes) {
      String id = node.path("key").asText("");
      String name = node.path("value").asText("");
      require(
        !id.isBlank() && !name.isBlank(),
        502,
        "目录数据格式异常，请稍后重试"
      );
      var children = node.get("children");
      result.add(
        new Directory(
          id,
          name,
          children == null || children.isNull()
            ? List.of()
            : readDirectories(children, depth + 1)
        )
      );
    }
    return List.copyOf(result);
  }

  private JsonNode request(String path, String body) {
    require(
      path.startsWith("/") && !path.startsWith("//"),
      503,
      "资源服务配置不完整"
    );
    try {
      var builder = HttpRequest.newBuilder(
        URI.create(config("base-url").replaceAll("/$", "") + path)
      )
        .timeout(Duration.ofSeconds(10))
        .header("Accept", "application/json");
      if (!config("authorization").isBlank()) builder.header(
        "Authorization",
        config("authorization")
      );
      if (body == null) builder.GET();
      else builder
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body));
      var response = http.send(
        builder.build(),
        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
      );
      require(
        response.statusCode() != 401 && response.statusCode() != 403,
        503,
        "资源服务暂不可用，请稍后重试"
      );
      require(
        response.statusCode() >= 200 && response.statusCode() < 300,
        502,
        "资源服务响应异常，请稍后重试"
      );
      require(
        response.body().length() <= 4_000_000,
        502,
        "资源服务响应异常，请稍后重试"
      );
      JsonNode result;
      try {
        result = json.readTree(response.body());
      } catch (Exception e) {
        throw fail(502, "资源服务响应格式异常，请稍后重试");
      }
      require(
        result != null && result.isObject(),
        502,
        "资源服务响应格式异常，请稍后重试"
      );
      String code = result.path("code").asText();
      require(
        !Set.of("401", "403").contains(code),
        503,
        "资源服务暂不可用，请稍后重试"
      );
      require(
        (!result.has("code") || Set.of("0", "200").contains(code)) &&
          (!result.has("result") ||
            "success".equals(result.path("result").asText())),
        502,
        "资源服务查询失败，请稍后重试"
      );
      require(
        result.path("data").isObject(),
        502,
        "资源服务响应格式异常，请稍后重试"
      );
      return result.path("data");
    } catch (ResponseStatusException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw fail(503, "资源服务请求已中断，请稍后重试");
    } catch (Exception e) {
      throw fail(503, "暂时无法连接资源服务，请稍后重试");
    }
  }

  private static class Cache<T> {

    private T value;
    private long expiresAt;

    synchronized T get(Supplier<T> fetch, long duration) {
      if (value != null && System.currentTimeMillis() < expiresAt) return value;
      T next = fetch.get();
      value = next;
      expiresAt = System.currentTimeMillis() + duration;
      return next;
    }
  }
}
