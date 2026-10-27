package cn.materials.portal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ResourceIntegrationTest {

  static HttpServer server;
  static volatile String response;
  static volatile int status;
  static volatile String requestMethod, requestPath, requestBody, authorization;
  static AtomicInteger requests = new AtomicInteger();
  MockEnvironment env;
  MockMvc mvc;

  @BeforeAll
  static void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      requests.incrementAndGet();
      requestMethod = exchange.getRequestMethod();
      requestPath = exchange.getRequestURI().getPath();
      authorization = exchange.getRequestHeaders().getFirst("Authorization");
      requestBody = new String(
        exchange.getRequestBody().readAllBytes(),
        StandardCharsets.UTF_8
      );
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
  }

  @AfterAll
  static void stopServer() {
    server.stop(0);
  }

  @BeforeEach
  void setup() {
    status = 200;
    requests.set(0);
    env = new MockEnvironment()
      .withProperty(
        "app.resources.base-url",
        "http://127.0.0.1:" + server.getAddress().getPort()
      )
      .withProperty(
        "app.resources.statistics-path",
        "/dynamics/homePage/getHomePage"
      )
      .withProperty("app.resources.cache-seconds", "0");
    mvc = MockMvcBuilders.standaloneSetup(
      new ResourceController(new ResourceService(new ObjectMapper(), env))
    )
      .setControllerAdvice(new Errors())
      .build();
  }

  @Test
  void preservesDirectoryHierarchyAndZeroCountNodesWithoutExposingData()
    throws Exception {
    response = """
    {"result":"success","data":{"total":66734,"treeStat":[
      {"key":"2015266286371758081","value":"生物医用材料（应用）","count":0,"children":[
        {"key":"child","value":"手术器械材料","count":0,"children":[
          {"key":"leaf","value":"叶子目录","children":[]}
        ]}
      ]}
    ]}}
    """;
    mvc
      .perform(get("/api/public/resources/directories"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items[0].id").value("2015266286371758081"))
      .andExpect(jsonPath("$.items[0].name").value("生物医用材料（应用）"))
      .andExpect(
        jsonPath("$.items[0].children[0].children[0].name").value("叶子目录")
      )
      .andExpect(jsonPath("$.items[0].count").doesNotExist())
      .andExpect(jsonPath("$.total").doesNotExist());
    assertEquals("POST", requestMethod);
    assertEquals("/database-search/search/fieldStat", requestPath);
    assertEquals("{\"statType\":\"1\"}", requestBody);
  }

  @Test
  void mapsAllSixStatisticsIncludingZeroAndLargeCounts() throws Exception {
    response = """
    {"code":200,"data":{"templateCount":131,"fragmentCount":85,"datasetCount":42,
      "dataCount":"9007199254740993","publicDataCount":0,"underReleaseDataCount":0}}
    """;
    mvc
      .perform(get("/api/public/resources/statistics"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items.length()").value(6))
      .andExpect(
        jsonPath("$.items[*].label").value(
          org.hamcrest.Matchers.contains(
            "模板量",
            "模板片段量",
            "数据集量",
            "数据量",
            "已发布数据量",
            "发布中数据量"
          )
        )
      )
      .andExpect(jsonPath("$.items[0].value").value("131"))
      .andExpect(jsonPath("$.items[3].value").value("9007199254740993"))
      .andExpect(jsonPath("$.items[4].value").value("0"));
    assertEquals("GET", requestMethod);
    assertEquals("/dynamics/homePage/getHomePage", requestPath);
    assertEquals("", requestBody);
  }

  @Test
  void supportsServerSideConfigurationWithoutReturningAuthorization()
    throws Exception {
    env
      .withProperty(
        "app.resources.statistics-path",
        "/dynamics/dict/getSearchType"
      )
      .withProperty(
        "app.resources.authorization",
        "Bearer test-resource-token"
      );
    response =
      "{\"code\":401,\"msg\":\"internal upstream authentication details\"}";
    mvc
      .perform(get("/api/public/resources/statistics"))
      .andExpect(status().isServiceUnavailable())
      .andExpect(jsonPath("$.message").value("资源服务暂不可用，请稍后重试"));
    assertEquals("/dynamics/dict/getSearchType", requestPath);
    assertEquals("Bearer test-resource-token", authorization);
  }

  @Test
  void emptyDirectoryIsSuccessfulButMalformedDirectoryIsNot() throws Exception {
    response = "{\"result\":\"success\",\"data\":{\"treeStat\":[]}}";
    mvc
      .perform(get("/api/public/resources/directories"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items").isEmpty());
    response = "{\"result\":\"success\",\"data\":{\"treeStat\":null}}";
    mvc
      .perform(get("/api/public/resources/directories"))
      .andExpect(status().isBadGateway());
    response =
      "{\"result\":\"success\",\"data\":{\"treeStat\":[{\"key\":\"1\"}]}}";
    mvc
      .perform(get("/api/public/resources/directories"))
      .andExpect(status().isBadGateway());
  }

  @Test
  void upstreamErrorsAreNotShownAsEmptyDirectoriesOrZeroStatistics()
    throws Exception {
    response =
      "{\"result\":\"error\",\"msg\":\"无法匹配聚合类型\",\"data\":null}";
    mvc
      .perform(get("/api/public/resources/directories"))
      .andExpect(status().isBadGateway());
    response = "{\"code\":200,\"data\":{\"templateCount\":131}}";
    mvc
      .perform(get("/api/public/resources/statistics"))
      .andExpect(status().isBadGateway());
    response = "<html>gateway error</html>";
    mvc
      .perform(get("/api/public/resources/statistics"))
      .andExpect(status().isBadGateway());
    status = 500;
    mvc
      .perform(get("/api/public/resources/statistics"))
      .andExpect(status().isBadGateway());
  }

  @Test
  void cachesSuccessfulResponsesAndRetriesFailures() throws Exception {
    env.withProperty("app.resources.cache-seconds", "60");
    response = "{\"result\":\"error\",\"data\":null}";
    mvc
      .perform(get("/api/public/resources/directories"))
      .andExpect(status().isBadGateway());
    response = "{\"result\":\"success\",\"data\":{\"treeStat\":[]}}";
    mvc
      .perform(get("/api/public/resources/directories"))
      .andExpect(status().isOk());
    mvc
      .perform(get("/api/public/resources/directories"))
      .andExpect(status().isOk());
    assertEquals(2, requests.get());
  }
}
