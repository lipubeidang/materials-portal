package cn.materials.portal;

import static cn.materials.portal.Store.*;

import java.time.LocalDate;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class Bootstrap implements ApplicationRunner {

  final Store s;
  final Auth a;
  final ContentService c;
  final Environment env;

  Bootstrap(Store s, Auth a, ContentService c, Environment env) {
    this.s = s;
    this.a = a;
    this.c = c;
    this.env = env;
  }

  @Override
  public void run(ApplicationArguments args) {
    boolean production = env.getProperty(
        "app.production",
        Boolean.class,
        false
      ),
      demo = env.getProperty("app.demo", Boolean.class, false);
    if (production) {
      require(!demo, 500, "生产环境不能加载演示数据");
      require(
        env.getProperty("app.secure-cookie", Boolean.class, false),
        500,
        "生产环境须使用安全 Cookie"
      );
      require(
        env.getProperty("app.origin", "").startsWith("https://"),
        500,
        "生产环境须配置 HTTPS 来源"
      );
      require(
        env.getProperty("spring.datasource.url", "").startsWith("jdbc:mysql:"),
        500,
        "生产环境请配置 MySQL"
      );
    }
    String username = env
        .getProperty("app.bootstrap-user", "")
        .trim()
        .toLowerCase(Locale.ROOT),
      password = env.getProperty("app.bootstrap-password", "");
    if (
      !username.isBlank() &&
      s.one("SELECT id FROM users WHERE username=?", username) == null
    ) {
      require(
        username.matches("[a-z0-9_]{3,32}"),
        500,
        "初始化操作员用户名不正确"
      );
      a.validatePassword(password);
      String uid = id();
      s.update(
        "INSERT INTO users VALUES(?,?,?,?,?,?,?)",
        uid,
        username,
        "站点操作员",
        a.passwords.encode(password),
        "OPERATOR",
        true,
        now()
      );
      s.audit(uid, "BOOTSTRAP_OPERATOR", uid);
    }
    if (s.one("SELECT id FROM categories LIMIT 1") == null) {
      category("news", "新闻中心", null, "ARTICLE", "list", 1);
      category("headlines", "要闻速递", "news", "ARTICLE", "list", 2);
      category("updates", "中心动态", "news", "ARTICLE", "list", 3);
      category("platform", "平台资讯", "news", "ARTICLE", "list", 4);
      category("notices", "通知公告", null, "ARTICLE", "date", 5);
      category("industry", "领域动态", null, "ARTICLE", "list", 6);
      category("resources", "数据资源", null, "RESOURCE", "cards", 7);
      category("products", "数据产品", null, "PRODUCT", "cards", 8);
      category("cooperation", "交流合作", null, "ARTICLE", "list", 9);
      category("cooperation-conferences", "学术会议", "cooperation", "ARTICLE", "list", 91);
      category("cooperation-journals", "学术期刊", "cooperation", "ARTICLE", "list", 92);
      category("cooperation-international", "国际交流", "cooperation", "ARTICLE", "list", 93);
      category("cooperation-standards", "标委会活动", "cooperation", "ARTICLE", "list", 94);
      category("cooperation-alliance", "联盟活动", "cooperation", "ARTICLE", "list", 95);
      category("cooperation-training", "人才培训", "cooperation", "ARTICLE", "list", 96);
      category("cooperation-enterprise", "企业合作", "cooperation", "ARTICLE", "list", 97);
      category("about", "关于我们", null, "PAGE", "page", 10);
    }
    if (s.one("SELECT id FROM site_settings WHERE id=1") == null) {
      var site = new LinkedHashMap<String, Object>();
      site.put("name", "新材料数据中心");
      site.put("englishName", "NEW MATERIALS DATA CENTER");
      site.put(
        "footer",
        demo
          ? "本地演示站点 · 内容仅用于功能展示，不代表真实机构资讯"
          : "新材料门户 · 让数据连接材料创新"
      );
      site.put("contact", "");
      site.put("registrationNumber", "");
      site.put("registrationUrl", "");
      site.put(
        "banners",
        List.of(
          Map.of(
            "title",
            "汇聚材料数据 · 连接创新未来",
            "description",
            "数据资源、科研成果与行业资讯，在这里交汇",
            "url",
            "/columns/resources",
            "image",
            ""
          ),
          Map.of(
            "title",
            "让每一份研究，都成为创新的起点",
            "description",
            "探索新材料领域动态，发现数据与产业的更多可能",
            "url",
            "/columns/industry",
            "image",
            ""
          )
        )
      );
      site.put(
        "links",
        List.of(
          Map.of(
            "title",
            "数据资源专区",
            "description",
            "发现材料数据资源",
            "url",
            "/columns/resources",
            "image",
            ""
          ),
          Map.of(
            "title",
            "数据产品专区",
            "description",
            "探索材料工具与服务",
            "url",
            "/columns/products",
            "image",
            ""
          )
        )
      );
      site.put("partners", List.of());
      site.put("departments", List.of());
      site.put("navigation", List.of());
      s.update("INSERT INTO site_settings VALUES(1,?,1)", s.encode(site));
    }
    if (demo && s.one("SELECT id FROM contents LIMIT 1") == null) {
      var operator = s.one(
        "SELECT id FROM users WHERE role='OPERATOR' LIMIT 1"
      );
      if (operator != null) seed(str(operator, "id"));
    }
  }

  void category(
    String id,
    String name,
    String parent,
    String type,
    String template,
    int order
  ) {
    s.update(
      "INSERT INTO categories VALUES(?,?,?,?,?,?,?,?,?)",
      id,
      name,
      id,
      parent,
      type,
      template,
      order,
      true,
      true
    );
  }

  void seed(String actor) {
    String[][] entries = {
      { "headlines", "以数据为桥，探索新材料创新的无限可能" },
      { "updates", "材料数据共享与协同创新交流活动" },
      { "platform", "门户内容发布系统正式开启体验" },
      { "headlines", "从实验到应用：材料研究的数据之路" },
      { "notices", "关于开放材料数据资源体验的通知" },
      { "notices", "平台使用与内容发布操作说明" },
      { "notices", "关于站点维护及服务安排的公告" },
      { "industry", "先进功能材料：连接基础研究与产业应用" },
      { "industry", "数据驱动的材料设计与研发新思路" },
      { "resources", "新一代生物医用材料数据资源" },
      { "resources", "先进结构材料研究资源" },
      { "products", "材料数据检索与发现工具" },
      { "products", "材料研究成果展示服务" },
      { "cooperation-enterprise", "共建开放共享的材料创新网络" },
      { "about", "关于新材料数据中心" },
    };
    for (int i = 0; i < entries.length; i++) {
      String title = "[演示] " + entries[i][1];
      var b = new LinkedHashMap<String, Object>();
      b.put("categoryId", entries[i][0]);
      b.put("title", title);
      b.put(
        "summary",
        "汇聚科研信息，推动资源共享。此条内容为本地演示数据，可在内容管理后台编辑、撤回或删除。"
      );
      b.put(
        "body",
        "<h2>" +
          entries[i][1] +
          "</h2><p>本页面用于展示新材料门户的栏目发布和阅读效果，不代表真实机构的新闻或通知。</p><p>平台汇聚材料领域的信息与资源，支持研究成果展示、行业资讯传播以及资源服务介绍。</p><h3>内容管理说明</h3><p>操作员可在后台选择栏目、编辑正文、上传附件并发布。普通人员可以浏览已公开的内容。</p>"
      );
      b.put("source", "站点演示");
      b.put(
        "displayDate",
        LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
          .minusDays(i)
          .toString()
      );
      b.put("recommended", true);
      b.put("pinned", i == 0);
      var item = c.save(null, b, actor);
      c.action(str(item, "id"), "publish", num(item, "version", 0), actor);
    }
  }
}
