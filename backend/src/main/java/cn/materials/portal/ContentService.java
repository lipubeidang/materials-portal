package cn.materials.portal;

import static cn.materials.portal.Store.*;

import java.util.*;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentService {

  final Store s;

  ContentService(Store s) {
    this.s = s;
  }

  static boolean safeUrl(String u) {
    if (u.isBlank()) return true;
    if (u.matches("/[a-zA-Z0-9][a-zA-Z0-9/_?=&#.%-]*")) return true;
    try {
      var uri = java.net.URI.create(u);
      return (
        Set.of("http", "https").contains(uri.getScheme()) &&
        uri.getHost() != null &&
        uri.getUserInfo() == null
      );
    } catch (Exception e) {
      return false;
    }
  }

  Map<String, Object> clean(Map<String, Object> b) {
    var out = new LinkedHashMap<String, Object>();
    for (String k : List.of(
      "title",
      "subtitle",
      "source",
      "summary",
      "body",
      "cover",
      "homeImage",
      "cooperationImage",
      "displayDate",
      "documentNumber",
      "department",
      "keywords",
      "productCategory",
      "productTag",
      "industryRegion",
      "industryTopic",
      "url",
      "domain"
    )) {
      String v = str(b, k).trim();
      require(
        v.length() <=
          (k.equals("body") ? 200000 : k.equals("summary") ? 2000 : 1000),
        400,
        "字段过长：" + k
      );
      out.put(k, v);
    }
    require(
      !str(out, "title").isBlank() && str(out, "title").length() <= 200,
      400,
      "标题须为 1—200 字"
    );
    String date = str(out, "displayDate");
    try {
      java.time.LocalDate.parse(date);
    } catch (Exception e) {
      throw fail(400, "请选择有效的显示日期");
    }
    require(
      safeUrl(str(out, "url")),
      400,
      "链接必须是本站路径或 http/https 地址"
    );
    var safe = Safelist.relaxed()
      .addAttributes("*", "style")
      .removeAttributes("*", "style")
      .addTags("hr")
      .addAttributes("a", "target", "rel")
      .preserveRelativeLinks(true);
    // A parsing base lets Jsoup validate relative URLs without stripping local image sources.
    // The managed-file check below still excludes every external image URL.
    var doc = Jsoup.parseBodyFragment(
      Jsoup.clean(str(out, "body"), "https://portal.invalid/", safe)
    );
    // Embedded images are always managed files; never accept arbitrary remote tracking images.
    doc.select("img").forEach(img -> {
      if (!img.attr("src").matches("/api/media/[a-f0-9-]{36}")) img.remove();
    });
    doc.select("a").forEach(link -> {
      if (!safeUrl(link.attr("href"))) link.removeAttr("href");
      link.attr("rel", "noopener noreferrer");
    });
    out.put("body", doc.body().html());
    out.put("pinned", bool(b, "pinned"));
    out.put("recommended", bool(b, "recommended"));
    out.put("industryRecent", bool(b, "industryRecent"));
    out.put("industryFeatured", bool(b, "industryFeatured"));
    String priority = Objects.toString(b.getOrDefault("sort", 0), "");
    require(
      priority.matches("-?\\d{1,5}"),
      400,
      "展示优先级须为 -10000 到 10000 的整数"
    );
    int sort = Integer.parseInt(priority);
    require(
      sort >= -10000 && sort <= 10000,
      400,
      "展示优先级须为 -10000 到 10000 的整数"
    );
    out.put("sort", sort);
    var attachments = new ArrayList<String>();
    Object raw = b.get("attachments");
    if (raw instanceof List<?> list) for (Object x : list) {
      String file = String.valueOf(x);
      require(file.matches("[a-f0-9-]{36}"), 400, "附件编号不正确");
      attachments.add(file);
    }
    require(attachments.size() <= 20, 400, "最多 20 个附件");
    out.put("attachments", attachments);
    for (String key : List.of("cover", "homeImage", "cooperationImage")) {
      String image = str(out, key);
      require(
        image.isBlank() || image.matches("/api/media/[a-f0-9-]{36}"),
        400,
        "封面和首页图片必须使用上传的图片"
      );
    }
    String cooperationImage = str(out, "cooperationImage");
    require(
      cooperationImage.isBlank() ||
        cooperationImage.equals(str(out, "cover")) ||
        doc
          .select("img[src]")
          .stream()
          .anyMatch(img -> cooperationImage.equals(img.attr("src"))) ||
        attachments.contains(cooperationImage.substring(11)),
      400,
      "交流合作首页图片须从本文的正文、封面或图片附件中选择"
    );
    String imageFit = str(b, "cooperationImageFit");
    if (imageFit.isBlank()) imageFit = "cover";
    require(
      Set.of("cover", "contain").contains(imageFit),
      400,
      "图片展示方式不正确"
    );
    out.put("cooperationImageFit", imageFit);
    return out;
  }

  static Set<String> industryTopics(String region) {
    return region.equals("international")
      ? Set.of("science", "progress")
      : Set.of("policy", "action", "science", "progress");
  }

  static void industryDefaults(Map<String, Object> data) {
    if (str(data, "industryRegion").isBlank()) data.put(
      "industryRegion",
      "domestic"
    );
    if (str(data, "industryTopic").isBlank()) data.put(
      "industryTopic",
      "science"
    );
  }

  void validateIndustry(Map<String, Object> data) {
    industryDefaults(data);
    String region = str(data, "industryRegion");
    require(
      Set.of("domestic", "international").contains(region),
      400,
      "请选择有效的动态区域"
    );
    require(
      industryTopics(region).contains(str(data, "industryTopic")),
      400,
      "此区域不支持该内容分类"
    );
    require(
      region.equals("domestic") ||
        (!bool(data, "industryRecent") && !bool(data, "industryFeatured")),
      400,
      "国际动态不支持国内近期要闻或图片推荐"
    );
  }

  Set<String> imageFiles(Map<String, Object> data) {
    Set<String> ids = new HashSet<>();
    for (String key : List.of("cover", "homeImage", "cooperationImage")) {
      String image = str(data, key);
      if (!image.isBlank()) ids.add(image.substring(11));
    }
    for (var img : Jsoup.parse(str(data, "body")).select("img[src]"))
      ids.add(img.attr("src").substring(11));
    return ids;
  }

  Set<String> files(Map<String, Object> data) {
    Set<String> ids = imageFiles(data);
    Object raw = data.get("attachments");
    if (raw instanceof List<?> list) for (Object id : list)
      ids.add(String.valueOf(id));
    return ids;
  }

  String excerpt(Map<String, Object> data) {
    String summary = str(data, "summary");
    if (!summary.isBlank()) return summary;
    String text = Jsoup.parseBodyFragment(str(data, "body")).text();
    if (text.codePointCount(0, text.length()) <= 160) return text;
    return (
      text.substring(0, text.offsetByCodePoints(0, 160)).stripTrailing() + "…"
    );
  }

  Map<String, Object> record(String id) {
    var row = s.one("SELECT * FROM contents WHERE id=?", id);
    require(row != null, 404, "内容不存在");
    return row;
  }

  boolean visibleCategory(String id) {
    Set<String> seen = new HashSet<>();
    while (id != null && !id.isBlank()) {
      if (!seen.add(id)) return false;
      var c = s.one("SELECT * FROM categories WHERE id=?", id);
      if (c == null || !bool(c, "enabled")) return false;
      id = str(c, "parent_id");
    }
    return true;
  }

  Map<String, Object> view(Map<String, Object> row, boolean published) {
    String rev = str(
      row,
      published ? "published_revision_id" : "draft_revision_id"
    );
    if (rev.isBlank()) return null;
    var revision = s.one("SELECT * FROM content_revisions WHERE id=?", rev);
    var payload = s.decode(revision.get("payload"));
    payload.put("id", row.get("id"));
    payload.put("categoryId", revision.get("category_id"));
    if (descendants("industry").contains(str(payload, "categoryId"))) {
      industryDefaults(payload);
      payload.put("industryContent", true);
    }
    payload.put("version", row.get("version"));
    payload.put("publishedAt", row.get("published_at"));
    payload.put("updatedAt", row.get("updated_at"));
    payload.put(
      "status",
      bool(row, "deleted")
        ? "DELETED"
        : row.get("published_revision_id") == null
          ? "DRAFT"
          : "PUBLISHED"
    );
    payload.put(
      "hasChanges",
      row.get("published_revision_id") != null &&
        !Objects.equals(
          row.get("published_revision_id"),
          row.get("draft_revision_id")
        )
    );
    var c = s.one(
      "SELECT name,type,template FROM categories WHERE id=?",
      revision.get("category_id")
    );
    payload.put("categoryName", c.get("name"));
    payload.put("type", c.get("type"));
    payload.put("template", c.get("template"));
    List<Map<String, Object>> attachments = new ArrayList<>();
    Object raw = payload.get("attachments");
    if (raw instanceof List<?> list) for (Object id : list) {
      var file = s.one(
        "SELECT id,original_name,size_bytes,mime FROM media_files WHERE id=?",
        id
      );
      if (file != null) attachments.add(file);
    }
    payload.put("attachmentInfo", attachments);
    payload.put("excerpt", excerpt(payload));
    return payload;
  }

  List<Map<String, Object>> publicItems() {
    return s
      .rows(
        "SELECT * FROM contents WHERE deleted=FALSE AND published_revision_id IS NOT NULL"
      )
      .stream()
      .map(row -> view(row, true))
      .filter(v -> visibleCategory(str(v, "categoryId")))
      .sorted(
        Comparator.<Map<String, Object>, Boolean>comparing(v ->
          bool(v, "pinned")
        )
          .reversed()
          .thenComparing(
            Comparator.<Map<String, Object>>comparingInt(v ->
              num(v, "sort", 0)
            ).reversed()
          )
          .thenComparing(v -> str(v, "displayDate"), Comparator.reverseOrder())
          .thenComparing(v -> str(v, "id"))
      )
      .collect(Collectors.toList());
  }

  Set<String> descendants(String root) {
    Set<String> ids = new HashSet<>();
    ids.add(root);
    boolean changed;
    do {
      changed = false;
      for (var c : s.rows("SELECT id,parent_id FROM categories"))
        if (ids.contains(str(c, "parent_id"))) changed |= ids.add(str(c, "id"));
    } while (changed);
    return ids;
  }

  Map<String, Object> publicDetail(String id) {
    var row = record(id);
    require(
      !bool(row, "deleted") && row.get("published_revision_id") != null,
      404,
      "内容不存在或已撤回"
    );
    var v = view(row, true);
    require(visibleCategory(str(v, "categoryId")), 404, "栏目未开放");
    return v;
  }

  @Transactional
  public Map<String, Object> save(
    String id,
    Map<String, Object> b,
    String actor
  ) {
    String category = str(b, "categoryId");
    var cat = s.one("SELECT * FROM categories WHERE id=? FOR UPDATE", category);
    require(cat != null && bool(cat, "enabled"), 400, "请选择可用栏目");
    var payload = clean(b);
    if (descendants("industry").contains(category)) validateIndustry(payload);
    boolean fresh = id == null;
    long time = now();
    if (fresh) id = id();
    if ("PAGE".equals(cat.get("type"))) require(
      s
        .rows(
          "SELECT id FROM contents WHERE category_id=? AND deleted=FALSE AND id<>?",
          category,
          id
        )
        .isEmpty(),
      409,
      "此单页栏目已有内容，请编辑原内容"
    );
    if (fresh) s.update(
      "INSERT INTO contents(id,category_id,version,created_by,created_at,updated_at) VALUES(?,?,0,?,?,?)",
      id,
      category,
      actor,
      time,
      time
    );
    var row = record(id);
    require(!bool(row, "deleted"), 409, "请先从回收站恢复");
    int version = num(b, "version", 0);
    require(
      num(row, "version", 0) == version,
      409,
      "内容已被其他操作更新，请重新打开"
    );
    String revision = id();
    s.update(
      "INSERT INTO content_revisions VALUES(?,?,?,?,?,?)",
      revision,
      id,
      category,
      s.encode(payload),
      actor,
      time
    );
    Set<String> images = imageFiles(payload);
    for (String file : files(payload)) {
      var media = s.one("SELECT mime FROM media_files WHERE id=?", file);
      require(media != null, 400, "文件不存在");
      require(
        !images.contains(file) || str(media, "mime").startsWith("image/"),
        400,
        "封面、首页图片和正文图片必须使用图片文件"
      );
      s.update("INSERT INTO revision_media VALUES(?,?)", revision, file);
    }
    require(
      s.update(
        "UPDATE contents SET category_id=?,draft_revision_id=?,version=version+1,updated_at=? WHERE id=? AND version=?",
        category,
        revision,
        time,
        id,
        version
      ) == 1,
      409,
      "内容已变化，请刷新"
    );
    s.audit(actor, "SAVE_CONTENT", id);
    return view(record(id), false);
  }

  @Transactional
  public Map<String, Object> action(
    String id,
    String action,
    int version,
    String actor
  ) {
    var row = s.one("SELECT * FROM contents WHERE id=? FOR UPDATE", id);
    require(row != null, 404, "内容不存在");
    require(num(row, "version", 0) == version, 409, "内容已变化，请刷新后操作");
    switch (action) {
      case "publish" -> {
        require(!bool(row, "deleted"), 409, "请先恢复内容");
        var v = view(row, false);
        require(
          !"cooperation".equals(str(v, "categoryId")),
          400,
          "请先选择交流合作下的具体分类，再发布文章"
        );
        if (bool(v, "industryContent")) {
          validateIndustry(v);
          require(
            !bool(v, "industryFeatured") ||
              !str(v, "homeImage").isBlank() ||
              !str(v, "cover").isBlank(),
            400,
            "图片推荐需要先上传推荐图片或内容封面"
          );
        }
        require(visibleCategory(str(v, "categoryId")), 400, "栏目尚未启用");
        var cat = s.one(
          "SELECT * FROM categories WHERE id=? FOR UPDATE",
          v.get("categoryId")
        );
        if ("PAGE".equals(cat.get("type"))) require(
          s
            .rows(
              "SELECT id FROM contents WHERE category_id=? AND deleted=FALSE AND id<>?",
              v.get("categoryId"),
              id
            )
            .isEmpty(),
          409,
          "此栏目已有单页"
        );
        if (
          Set.of("RESOURCE", "PRODUCT", "LINK").contains(str(v, "type"))
        ) require(
          !str(v, "body").isBlank() || !str(v, "url").isBlank(),
          400,
          "请填写介绍正文或访问地址"
        );
        else require(
          !Jsoup.parse(str(v, "body")).text().isBlank() || !files(v).isEmpty(),
          400,
          "请填写正文或上传附件"
        );
        s.update(
          "UPDATE contents SET published_revision_id=draft_revision_id,published_at=? WHERE id=?",
          now(),
          id
        );
      }
      case "withdraw" -> s.update(
        "UPDATE contents SET published_revision_id=NULL WHERE id=?",
        id
      );
      case "delete" -> s.update(
        "UPDATE contents SET deleted=TRUE,published_revision_id=NULL WHERE id=?",
        id
      );
      case "restore" -> {
        require(bool(row, "deleted"), 409, "内容不在回收站");
        var cat = s.one(
          "SELECT * FROM categories WHERE id=? FOR UPDATE",
          row.get("category_id")
        );
        if ("PAGE".equals(cat.get("type"))) require(
          s
            .rows(
              "SELECT id FROM contents WHERE category_id=? AND deleted=FALSE AND id<>?",
              row.get("category_id"),
              id
            )
            .isEmpty(),
          409,
          "单页栏目已有内容，无法恢复"
        );
        s.update(
          "UPDATE contents SET deleted=FALSE,published_revision_id=NULL WHERE id=?",
          id
        );
      }
      default -> throw fail(400, "不支持的操作");
    }
    s.update(
      "UPDATE contents SET version=version+1,updated_at=? WHERE id=?",
      now(),
      id
    );
    s.audit(actor, action.toUpperCase(Locale.ROOT), id);
    return view(record(id), false);
  }
}
