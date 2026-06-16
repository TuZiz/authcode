# AuthCode

AuthCode 是一个 Kotlin + Maven 编写的 Velocity + Paper/Folia 混合认证插件项目。

构建后会生成两个服务端插件：

1. `authcode-velocity/target/authcode-velocity-1.0.0.jar`
2. `authcode-paper/target/authcode-paper-1.0.0.jar`

## 构建

```bash
mvn clean package
```

## 安装

1. 将 `authcode-velocity/target/authcode-velocity-1.0.0.jar` 放入 Velocity 的 `plugins/`。
2. 将 `authcode-paper/target/authcode-paper-1.0.0.jar` 放入 Paper/Folia 子服的 `plugins/`。
3. Velocity `velocity.toml` 必须设置：

```toml
online-mode = false
player-info-forwarding-mode = "modern"
```

4. Paper/Folia 子服 `server.properties` 必须设置：

```properties
online-mode=false
```

5. Paper/Folia 必须开启 Velocity modern forwarding，并配置 Velocity forwarding secret。
6. AuthCode Velocity 的 `forward.secret` 必须与 AuthCode Paper 的 `proxy.secret` 一致。
7. 后端 Paper/Folia 子服必须禁止公网直连，只允许玩家从 Velocity 入口进入。

## 同 ID 离线/正版兼容登录

AuthCode 支持同一个客户端输入名进入，但后端身份分离。

示例：

- 正版玩家输入 `Steve`：后端名字 `Steve`，显示 `[正版] Steve`
- 离线玩家输入 `Steve`：后端名字 `o_Steve`，显示 `[离线] Steve`

核心字段：

- `clientName`：玩家启动器输入名，例如 `Steve`
- `originalName`：登录路由使用的原始输入名，例如 `Steve`
- `internalName`：后端真实身份名，例如 `Steve` 或 `o_Steve`
- `displayName`：显示层名字，例如 `Steve`

注意：

单入口模式下，同一个 `clientName` 不能同时无条件支持正版和离线。Velocity 在 `PreLoginEvent` 阶段必须先决定 `forceOnlineMode()` 或 `forceOfflineMode()`。

因此本插件采用数据库绑定关系优先：

- 已绑定正版的名字，走 Mojang 正版验证。
- 已存在离线映射的名字，走离线映射。
- 没有记录的名字，默认按离线身份创建映射。

如果真正正版玩家第一次进服被创建为离线映射，管理员可以在 Velocity 端使用：

```text
/authcode premium bind <name>
```

将该名字绑定为正版。绑定会查询 Mojang 档案并写入正版 UUID；查询失败或名字不存在时不会写入。

如果服主要让同一个名字既能正版进，又能离线进，需要使用：

1. 双入口 / 不同域名 / 不同端口区分正版入口和离线入口
2. 明确的离线意图标记
3. 管理员手动切换绑定状态

当前版本实现的是“数据库绑定优先 + 离线内部改名 + 管理员正版绑定”方案。

## 首次离线进入体验

默认 `unknown-name-policy: "OFFLINE_PENDING"`。

首次离线进入时，插件会创建 `pending_offline_rename` 映射并踢出一次：

```text
你是离线用户，账号名已映射为 o_Steve，请重新加入服务器。
```

玩家只需要用原来的名字重新加入服务器。第二次进入时，Velocity 会自动把后端名字改为 `o_名字`，并写入 `auth_profile`。

已存在离线映射后，同一个离线玩家再次进入不会再被踢出。

## 登录路由

Velocity `PreLoginEvent` 规则：

1. 校验用户名：`^[A-Za-z0-9_]{3,16}$`
2. 拒绝客户端直接使用保留前缀，例如 `o_`
3. 查询 Velocity 数据库 `auth_profile.original_name_lower`
4. 若 `auth_type=PREMIUM` 且 `premium_bound=true`，执行 `forceOnlineMode()`
5. 若 `auth_type=OFFLINE`，执行 `forceOfflineMode()`，并在 `GameProfileRequestEvent` 改为 `internalName`
6. 若存在未过期且 IP 匹配的 pending，确认 pending、写入 `auth_profile`，然后执行离线改名
7. 若没有记录，按配置创建 pending 并踢出，或直接创建离线档案

`premium=true` 只会在 Velocity `forceOnlineMode()` 成功并进入 `PostLoginEvent` 后产生。Paper/Folia 子服不会信任 Mojang 用户名查询，只信任 Velocity 签名 payload。

## Velocity 配置

默认配置位于 `authcode-velocity/src/main/resources/config.yml`。

关键节点：

```yml
same-name-login:
  enabled: true
  route-mode: "DATABASE_FIRST"
  unknown-name-policy: "OFFLINE_PENDING"
  allow-offline-fallback-for-premium-bound-name: false
  block-client-reserved-prefix: true
  reserved-prefix: "o_"
  pending:
    ttl-seconds: 120
    match-ip: true
    cleanup-interval-seconds: 60

offline-name:
  enabled: true
  prefix: "o_"
  avoid-double-prefix: true
  strip-display-prefix: true
  max-name-length: 16
  overflow-mode: "KICK"
  hash-length: 4
  uuid-source: "PREFIXED_INTERNAL_NAME"
```

Velocity 路由数据库默认位于：

```text
plugins/authcode/authcode.db
```

可通过 `storage.sqlite-file` 修改。

## Paper/Folia 配置

默认配置位于 `authcode-paper/src/main/resources/config.yml`。

关键节点：

```yml
proxy:
  enabled: true
  mode: VELOCITY_PROXY_PLUGIN
  channel: "authcode:auth"
  secret: "change-this-random-long-secret"
  require-proxy-assertion: true

display:
  identity-tag:
    enabled: true
    apply-chat: true
    apply-tab-list: true
    apply-player-display-name: true
    apply-join-quit-message: false
    strip-internal-prefix: true
    placeholder-only: false
```

最终显示：

- 正版 `Steve` -> `[正版] Steve`
- 离线 `o_Steve` -> `[离线] Steve`

Paper/Folia 子服收到 Velocity payload 后会验证 HMAC 签名、timestamp、nonce、uuid、internalName，并根据 `premium/authType` 决定自动放行或进入 `/code`、`/reg`、`/login`。

## 管理命令

Velocity 端命令：

```text
/authcode profile <name>
/authcode namecheck <name>
/authcode pending list
/authcode pending clear <name>
/authcode premium bind <name>
/authcode premium unbind <name>
/authcode premium info <name>
/authcode identity <player>
```

`premium bind` 会把名字写为正版绑定：

```text
original_name = name
internal_name = name
display_name = name
auth_type = PREMIUM
premium_bound = true
uuid = Mojang UUID
```

如果原来存在离线映射，会写入 `auth_profile_migration_log`。不会自动删除 Paper/Folia 子服上的离线注册密码。

`premium unbind` 当前策略是删除 Velocity 的 `auth_profile` 正版档案。后续该名字再次登录时，会按未知名字重新创建离线 pending。

Paper/Folia 端玩家命令：

```text
/code <邀请码>
/reg <密码> <确认密码>
/login <密码>
/changepass <旧密码> <新密码> <确认新密码>
```

Paper/Folia 端仍提供邀请码、GUI、reload、identity/namecheck 等子服管理命令，但同 ID 登录路由和正版绑定以 Velocity 端命令为准。

## 数据库

Velocity 路由库包含：

```sql
CREATE TABLE IF NOT EXISTS auth_profile (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  original_name TEXT NOT NULL,
  original_name_lower TEXT NOT NULL UNIQUE,
  internal_name TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL,
  uuid TEXT NOT NULL UNIQUE,
  auth_type TEXT NOT NULL,
  premium_bound INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
```

```sql
CREATE TABLE IF NOT EXISTS pending_offline_rename (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  username TEXT NOT NULL,
  username_lower TEXT NOT NULL,
  offline_name TEXT NOT NULL,
  ip TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);
```

身份原则：

- 登录路由优先查 `original_name_lower`
- 后端核心数据使用 `uuid` 或 `internalName`
- `displayName` 只用于显示，不作为身份依据

Paper/Folia 子服数据库继续保存邀请码、注册密码、登录状态和最近一次 Velocity 身份断言。

## Folia 注意事项

AuthCode Paper/Folia 子服保留 Folia 兼容：

- `paper-plugin.yml` 包含 `folia-supported: true`
- 玩家消息、踢出、药水效果、移动锁定、TAB/displayName 修改通过 `SchedulerAdapter#runAtEntity`
- 数据库、网络请求、BCrypt 不在主线程或 region 线程执行

## 测试建议

至少测试：

1. 未知名字第一次离线进入：创建 pending 并踢出提示重进
2. 未知名字第二次离线进入：改名为 `o_名字`，写入 `auth_profile OFFLINE`
3. 已存在离线映射再次进入：不再踢出，直接离线改名
4. Velocity 执行 `/authcode premium bind <name>` 后，档案切换为 `PREMIUM`
5. 已绑定正版使用正版客户端进入：`forceOnlineMode()` 成功，Paper 自动放行
6. 已绑定正版使用离线客户端进入：Mojang 验证失败，不能降级为离线
7. 客户端直接使用 `o_名字`：被拒绝
8. `o_ + username` 超过 16 且 `overflow-mode=KICK`：被拒绝

