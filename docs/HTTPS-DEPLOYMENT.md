# materials-portal HTTPS 部署

正式入口为 `https://www.biomatbd.cn`。路由器将公网 TCP `443` 和旧入口 `50010` 都转发到 `192.168.3.15:443`。宿主机 Nginx 将旧 HTTPS 地址重定向到标准地址，并将正式入口代理到 Compose 的 `127.0.0.1:8090`。DNS-01 签发独立于公网端口映射。SSH 仍使用公网 `50009`。

## 证书和 Nginx

1. 用 Let’s Encrypt DNS-01 为 `www.biomatbd.cn` 签发证书。Certbot 显示 TXT 后，在新网的 `biomatbd.cn` 解析区域添加 `_acme-challenge.www`，核对 `ns11.xincache.com` 与 `ns12.xincache.com` 均返回本次生成的值，再继续验证。
2. 在确认宿主机 `443` 空闲并备份相关配置后，将[独立站点配置](../deploy/nginx/materials-portal-https.conf)安装为 `/etc/nginx/conf.d/materials-portal-https.conf`。运行 `sudo nginx -t`，通过后执行 `sudo systemctl reload nginx`。
3. 从服务器本地验证证书与代理。`--connect-to` 只替换 TCP 目标，保留正式域名和 SNI。

   ~~~bash
   curl --noproxy '*' --fail --show-error \
     --connect-to www.biomatbd.cn:443:192.168.3.15:443 \
     https://www.biomatbd.cn/api/public/site
   ~~~

## 应用配置及切换

- 在运行中的 `/opt/materials-portal/deploy/.env` 设置 `APP_ORIGIN=https://www.biomatbd.cn` 和 `CENTER_CALLBACK=https://www.biomatbd.cn/auth/center/callback`。启用 Compose 后端的 `SECURE_COOKIE=true`，保留已有的端口映射、项目名称、镜像配置和卷。
- 用 `docker compose -f compose.yml config --quiet` 校验。配置生效时只重建后端。若 Web 容器仍缓存旧后端地址，在容器内通过 `nginx -t` 后平滑重载。
- 中心统一认证侧登记同一个 HTTPS 回调。站点只接受配置中的精确来源。
- 保留旧规则的截图。公网 TCP `443` 和 `50010` 都转发到 `192.168.3.15:443`。分别检查标准入口和旧地址跳转。
- 从外网检查首页、后台和 API 的证书链、登录注册及退出、中心回跳、上传下载，以及浏览器控制台是否有混合内容。若公网 TCP 连接超时，比较内网探测结果，再核对路由器的入接口、规则状态、协议和内部目标。

## 最近一次验证

- 宿主机 Nginx 配置检查通过。服务器内部使用正式域名和受信任证书访问首页、后台及 API 均返回 `200`。
- 后端接受标准 HTTPS 来源，统一认证回调使用标准地址，Cookie 包含 `Secure`、`HttpOnly` 和 `SameSite=Lax`。
- 首次切换时，截图显示外部端口是 `443`、内部端口是 `8090`。修正为公网 `50010` → 内网 `443` 后，公网首页、后台入口和公开 API 均返回 `200`，SSH `50009` 可达。
- 切换到标准 `443` 后，公网 API 返回 `200`，旧 `:50010` HTTPS 地址返回 `301`，保留原路径和查询参数。Chrome 验证了 TLS 1.3、证书链、匿名后台拦截、中心回调和公开媒体分段读取，抽测页面无混合内容警告。完整登录、注册、后台上传、退出和真实中心回跳需要使用验收账号测试。

## 手动续期

证书由 Certbot 手动 DNS 验证签发。本次证书有效至北京时间 2027 年 2 月 26 日 13:19。用 `sudo certbot certificates` 查看当前有效期，在到期前留出时间重新验证。

~~~bash
sudo certbot certonly --manual --preferred-challenges dns \
  --cert-name www.biomatbd.cn -d www.biomatbd.cn
~~~

按照本次提示更新 TXT。两台权威 DNS 返回新值并签发成功后，运行 `sudo nginx -t`，平滑重载 Nginx，再从外网确认新的到期时间。定时器无法替代手动 DNS 更新。有可用的受限 DNS API 时，可以改用自动认证钩子。

## 回滚

从标准 `443` 回到旧 HTTPS `:50010` 时，恢复标准入口切换前备份中的 `.env` 和宿主机站点文件。校验 Compose 后只重建后端，校验 Nginx 后平滑重载，并同步中心侧回调。两条 NAT 规则可以保持原样。

如需回到 HTTPS 改造前的 HTTP 部署，再执行以下步骤。

1. 按切换前截图恢复路由器公网 `50010` 的原转发规则。
2. 恢复应用改动前的 `.env` 和 `compose.yml`。运行 `docker compose -f compose.yml config --quiet` 后仅重建后端。

   ~~~bash
   docker compose -f compose.yml up -d --no-deps --no-build \
     --pull never --force-recreate backend
   ~~~

3. 如需撤回宿主机 HTTPS，移出本次新增的独立站点配置，通过 `nginx -t` 后平滑重载。恢复中心侧之前的回调并验证 HTTP 首页和登录。保留证书、数据库、上传文件和 Docker 卷。
