# 中心统一认证接入说明

## 应用登记信息

由中心实施人员完成 PDF 2.1 的应用注册，第三方应用负责实现 2.2。正式登记时提供：

| 项目 | 登记值 |
|---|---|
| 应用名称 | 【单位】＋【业务系统名称】 |
| 应用固定入口 | `https://你的域名/auth/center/login` |
| 认证回调基础地址 | `https://你的域名/auth/center/callback` |
| 退出通知地址 | `https://你的域名/auth/center/logout`，POST |

认证事务使用回调参数 `tx`，如 `/auth/center/callback?tx=随机值`。请由中心按其受控参数规则登记，确认回调会保留该参数。不能在未确认的情况下把回调放宽为任意外部地址。

## 后端环境变量

| 变量 | 用途 |
|---|---|
| CENTER_ENABLED | 默认 false，实际接入时开启 |
| CENTER_BASE_URL | 中心认证服务基础地址，不含末尾接口路径 |
| CENTER_CLIENT_ID | 中心注册后提供的应用编号 |
| CENTER_CLIENT_SECRET | 中心提供的应用密钥，仅后端使用 |
| CENTER_CALLBACK | 本站固定回调地址，不含查询参数或片段 |
| CENTER_AUTH_PREFIX | 默认空字符串；若中心要求 Bearer，设置为带尾随空格的 `Bearer ` |
| CENTER_USER_ID_PATH | 默认 `id`；按正式字段确定，可设置 `userAttribute.userId` 等明确路径 |
| CENTER_USER_NAME_PATH | 默认 `username`，同样支持明确的嵌套路径 |
| CENTER_STORAGE_KEY | 自行生成 32 随机字节的 Base64，用于数据库中令牌的 AES-GCM 加密 |
| CENTER_LOGOUT_KEY | 与可信网关／中心约定的退出通知头 `X-Center-Logout-Key` |
| CENTER_LOGOUT_IPS | 或配置逗号分隔的可信直接连接源 IP |

应用密钥和令牌存储密钥用途不同。令牌存储密钥变更会使旧令牌无法解密，应先注销旧中心会话，再轮换密钥。

## 当前明确采用的协议

- 授权请求：`responseType`、`clientId`、`redirectUri`、`code_challenge`、`code_challenge_method=SM4`。
- verifier 每次认证重新生成，86 位 URL 安全随机字符，服务端保存 120 秒。
- code_challenge：clientSecret 前 16 字符的 UTF-8 字节，要求恰好 16 字节；SM4/ECB/PKCS7Padding，Base64 编码后 URL 编码。此参数组合是当前明确实现的互通配置，需与中心测试向量核对。
- 交换令牌使用 POST multipart/form-data，不发送 JSON；字段按 PDF 正式表格使用驼峰名称。
- 用户信息与在线检查使用 POST，请求头 `Authorization`；前缀通过配置指定。
- 令牌响应按正式字段从最外层读取，expiresIn 按秒解释。
- 刷新使用后不期待新的 refreshToken。并发刷新在本实例内串行化，失败不重复使用已消费的刷新票据。
- 用户操作触发在线检查，默认最多每分钟一次，不通过无条件心跳让闲置会话一直存活。
- 本地会话有 12 小时上限，不因中心刷新无限延长。

## 统一退出的可信来源

PDF 给出了退出报文，但未定义签名验证。项目默认拒绝所有未获信任的退出请求，不能仅凭 principal=userId 就注销用户。

可选接入方式：

1. 中心或受控网关发送 `X-Center-Logout-Key`，与后端配置匹配。
2. 专用反向代理仅允许中心固定源 IP 访问退出路径，清除外来同名头，并向后端注入共享密钥。密钥存放在受控配置中，不提交仓库。
3. 中心直接连接后端时，配置明确的直接源 IP 白名单。若后端看到的是公共反向代理 IP，不能只把该代理 IP 加入白名单，否则普通外部请求也会被信任。

本站不盲目信任客户端提供的 X-Forwarded-For。验证来源之后，还要同时匹配中心用户标识与当前 accessToken，只注销对应中心会话。重复通知幂等返回成功。本地账号会话不会被连带注销。

## 真实联调清单

- 核对 Authorization 前缀、用户 ID 字段路径、期限单位及 SM4 测试向量。
- 确认受控回调参数 tx 可原样往返。
- 中心已登录后，从固定入口无需再次输入密码进入本站。
- 首次中心用户建立普通账号；已绑定用户进入同一个本地账号。
- 认证过期、同一 code 重放、浏览器错配均失败。
- 中心离线、票据到期、一次刷新以及后续重新认证符合实际服务行为。
- 退出通知通过可信来源校验，并正确注销对应本站会话。
- 中心异常时，本地账号仍可登录和操作。

模拟服务仅存在于测试源码，不存在生产运行时“假登录开关”。通过模拟测试不能替代真实中心验收。
