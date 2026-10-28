# Happy4J 重构架构

## 边界

- `domain`：牌桌聚合、阶段、动作和只读快照。
- `application`：扫码登录、会话编排、鉴权、持久化订阅和用例服务。
- `application.port`：认证、目录、Lotus/GameSvr 传输端口。
- `protocol`：wire/TDR/protobuf/CGI；wire 数字集中在枚举或协议描述符边界。
- `infrastructure`：Socket、配置目录、JPA 实体和仓储。
- `websocket`：鉴权握手、命令总线、用户连接中心和服务端扫码轮询。
- `web`：注册、登录及兼容 REST；实时界面不依赖 REST 轮询或 SSE。
- `frontend`：Vue 3 + Vite SPA；大厅和房间使用独立界面，牌局由 `TableSnapshot`
  驱动，包含倒计时、拖动选牌、智能提示、记牌器和结算弹窗。
- `ServerAiAutomationService`：服务器托管后台任务；从数据库读取启用的服务器 AI 配置，
  直接消费会话牌桌快照并提交动作，浏览器断开后仍可继续运行。

## 多用户模型

`app_user -> auth_token -> game_client_session -> game_event` 构成用户隔离链；
`game_account` 保存用户绑定的 QQ/微信游戏账号、资料与最后余额。运行时会话带 `ownerId`，
HTTP 拦截器和 WebSocket 命令处理器在执行前校验归属。

密码使用 BCrypt cost 12；客户端持有随机 256-bit bearer token，数据库只保存 SHA-256。

## WebSocket 命令

浏览器连接 `/ws?token=...`，收到 `RESULT/BOOT` 后使用 `GameCommand`：

`SESSION_CREATE`, `QR_START`, `MODE_ENTER`, `READY`, `ORDER`, `MULTIPLE`, `PLAY`,
`TRUST`, `CHANGE_TABLE`, `LEAVE_ROOM`, `AI_STOP`, `MISSIONS_REFRESH`, `MISSION_CLAIM` 等。

服务端自行轮询扫码；聚合事件经 `EVENT` 和 `SNAPSHOT` 推送。浏览器断线后指数退避重连，
没有固定频率快照轮询。

## 数据库

Flyway 管理 MySQL 8.4 schema；测试使用 H2 MySQL mode 验证同一迁移与 Hibernate schema。
运行前执行 `docker compose up -d mysql`，或通过 `HAPPY4J_DB_*` 指向已有 MySQL。

- `server_config` 保存动态功能参数与任务扫描范围。
- `game_mode_config` 首次从 APK XML 导入，之后大厅每次从数据库实时读取，管理员可在线修改。
- `mission_definition` 按 JS 的 MissionSvr `7304` 明确 ID 列表查询任务状态，再与
  CGI `32791` 在线详情合并；远端接口不提供无参数目录遍历，数据库 seed IDs 仅用于启动查询。
- `game_login_state` 使用 AES-256-GCM 保存 SafeLogin/Lotus 登录状态，AAD 绑定应用用户 ID，
  WebSocket `SESSION_RESUME` 可在服务重启后恢复登录；失效状态会被标记而非反复重试。
- `runtime.*` 配置限制单实例活跃用户、每用户连接、工作队列和二维码轮询并发；默认上限为
  64 个同时保持 Lotus 登录的不同用户，每用户一个游戏连接。

管理员账号由 `HAPPY4J_ADMIN_USERS` 指定，可通过 `/api/admin/config`、`/api/admin/modes`、
`/api/admin/missions` 管理服务器配置与触发在线任务同步。
