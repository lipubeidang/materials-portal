# 数据资源目录与首页统计

门户通过后端访问资源服务，浏览器只请求本站 `/api/public/resources/*`，兼容 HTTPS 门户、同源 CSP 和跨域限制。目录与统计独立加载，外部服务失败不会阻塞新闻、公告等本地内容。

## 已核对的上游接口

基础地址默认 `http://171.221.175.91:50003`。

- 目录：`POST /database-search/search/fieldStat`，JSON 请求体 `{"statType":"1"}`。成功响应为 `{"result":"success","data":{"treeStat":[...]}}`，节点字段是 `key`、`value`、`children`。保留所有层级、原始顺序和零数据目录；只返回目录 ID、名称和子目录，不展示上游数据记录或数量。
- 六项统计：`GET /dynamics/homePage/getHomePage`。成功响应为 `{"code":200,"data":{...}}`。字段映射如下。

| 上游字段 | 首页名称 |
| --- | --- |
| templateCount | 模板量 |
| fragmentCount | 模板片段量 |
| datasetCount | 数据集量 |
| dataCount | 数据量 |
| publicDataCount | 已发布数据量 |
| underReleaseDataCount | 发布中数据量 |

2026-10-26 实测目录返回 2 个一级目录、275 个节点、最深 4 层；统计返回 123、85、41、66062、0、0。以上是联调时的数据，程序没有硬编码这些数值。

最初提供的 `GET /dynamics/dict/getSearchType` 匿名调用返回 HTTP 200、业务 `code:401`。检查该系统公开发布的首页脚本，图中六项指标实际请求的是 `GET /dynamics/homePage/getHomePage`，随后已验证该接口可匿名读取上述六个字段，因此默认采用此地址。若上游另行提供同样六项数据，可通过下面的配置修改统计路径；若响应结构不同，需要同步修改字段适配，不能直接把检索类型字典当成统计值。

## 配置

| 后端环境变量 | 默认值 / 用途 |
| --- | --- |
| RESOURCE_BASE_URL | `http://171.221.175.91:50003` |
| RESOURCE_STATISTICS_PATH | `/dynamics/homePage/getHomePage` |
| RESOURCE_AUTHORIZATION | 默认空；如上游要求鉴权，填写完整请求头值，例如 `Bearer ...`，仅保存在后端环境配置中 |
| RESOURCE_CACHE_SECONDS | `60`，两类成功响应分别缓存；设为 `0` 可禁用 |

Docker Compose 已透传上述变量，参考 `deploy/.env.example`。这套配置与中心统一登录配置独立。

## 门户行为

- 首页原统计区域显示六项实时指标，数值添加千分位，移动端为两列。未取得数据时显示 `—`，不会把失败当作 0。
- 首页“数据资源”展示一级目录入口；“更多”和主导航进入数据资源页面。左侧目录树支持展开、收起、搜索科学分类；URL 的 `directory` 参数支持直接打开子目录和刷新，自动展开选中目录的祖先节点。
- 右侧按数据集查询页展示筛选表单、八列表格和分页空状态，当前不接入具体数据集记录，固定显示 0 条。数据集名称、模板名称及创建人条件可查询、重置并通过 URL 保留；尚无数据来源的产业分类、数据类别和部门下拉框禁用，不提供编造选项。
- 目录只用于前台展示，不写入内容管理的栏目表，也不受后台栏目两级限制影响。
- 空目录、加载中、接口失败分别显示。失败可点击重试；HTTP 错误及 HTTP 200 中的业务错误均被识别，不缓存失败，不向前端返回上游内部错误或鉴权信息。
- 数量以十进制字符串从后端返回，避免浏览器对大整数取值丢失精度。缺失或格式异常的统计字段显示请求失败。

## 验证

`ResourceIntegrationTest` 使用本地模拟上游验证目录 POST 参数、层级和零数据目录保留、六项统计映射、大整数、空响应、错误响应、后端鉴权配置及缓存重试。运行 `cd backend && mvn test`；前端运行 `cd frontend && npm run build`。
