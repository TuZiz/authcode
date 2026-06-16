# AuthCode

AuthCode 是一个 Kotlin + Maven 编写的 Velocity + Paper/Folia 混合认证插件项目。

现在会生成两个服务端插件：

1. AuthCode Velocity 插件：`authcode-velocity/target/authcode-velocity-1.0.0.jar`
2. AuthCode Paper/Folia 子服插件：`authcode-paper/target/authcode-paper-1.0.0.jar`

## 构建

```bash
mvn clean package
```

## 安装步骤

1. 把 `authcode-velocity/target/authcode-velocity-1.0.0.jar` 放入 Velocity 的 `plugins/`。
2. 把 `authcode-paper/target/authcode-paper-1.0.0.jar` 放入 Paper/Folia 子服的 `plugins/`。
3. Velocity `velocity.toml` 必须设置：

```toml
online-mode = false
player-info-forwarding-mode = "modern"
```

4. Paper/Folia 子服 `server.properties` 必须设置：

```properties
online-mode=false
```

5. Paper/Folia 必须开启 Velocity modern forwarding，并设置 Velocity forwarding secret。
6. 设置 AuthCode Velocity 与 AuthCode Paper 两边相同的 `forward.secret` / `proxy.secret`。
7. 禁止玩家直连 Paper/Folia 子服。
8. 启动 Velocity 和子服。

## 认证流程

玩家连接 Velocity 后，AuthCode Velocity 只用 Mojang 用户名查询判断这个名字是否存在正版档案：

- 名字存在正版档案：对该连接执行 `forceOnlineMode()`，由 Velocity 交给 Mojang 真正验证。
- 名字不存在正版档案：对该连接执行 `forceOfflineMode()`，允许离线账号进入。

真正的正版证明只来自：

```text
Velocity forceOnlineMode()
Mojang 验证成功
玩家成功进入 Velocity PostLogin 阶段
Velocity 标记 premium=true
Velocity 通过 HMAC 签名 payload 发给子服
子服验证签名、timestamp、nonce、uuid、username 后放行
```

如果名字存在正版档案，但连接者不是该正版账号号主，Mojang 验证会失败，玩家不会进入 PostLogin，也不会降级成离线玩家。

## 安全说明

后端 Paper / Folia 子服不能暴露公网。玩家必须只能连接 Velocity，必须使用防火墙或只监听本地地址限制后端访问。

Velocity modern forwarding 不是防火墙替代品。如果子服允许直连，攻击者可能绕过 Velocity 认证。

## 正版/离线共存模式

AuthCode 支持正版玩家和离线玩家共存，并明确区分三类名字：

- `originalName`：玩家客户端输入的原始名字，例如 `Steve`
- `internalName`：服务器、数据库、权限、经济、领地等核心数据使用的内部名字，例如 `o_Steve`
- `displayName`：聊天、TAB、GUI、Placeholder 使用的显示名字，例如 `Steve`

默认规则：

- 正版玩家 `Steve` 内部身份仍然是 `Steve`
- 离线玩家输入 `abc123` 后，内部身份会变成 `o_abc123`
- 正版聊天显示：`[正版] Steve`
- 离线聊天显示：`[离线] Steve`
- 数据库、登录状态、邀请码记录使用 UUID 或 internalName
- 聊天、TAB、GUI 使用 identity display 格式化后的显示名

这样可以做到：

1. 正版玩家不会被离线玩家抢占内部身份。
2. 离线玩家不会直接占用未加前缀的正版名。
3. 聊天中能清楚区分正版和离线身份。

### 为什么需要 Velocity 模块？

如果只安装 Folia/Paper 后端插件，玩家进入服务器时名字已经确定，插件无法安全地把 `Steve` 改成 `o_Steve`。

所以如果你想实现：

```text
离线玩家客户端输入 Steve
服务器内部身份变成 o_Steve
```

必须安装 AuthCode 的 Velocity 模块，并让玩家只能通过 Velocity 进入后端。

默认安全策略下，Velocity 会对已存在 Mojang 正版档案的输入名执行 `forceOnlineMode()`。这能保证 `premium=true` 只来自 Mojang 正版验证成功，但也意味着非号主不能用同一个正版名降级进入。若关闭 `security.deny-premium-name-offline-spoof`，该名字可走离线前缀身份，但同名正版连接也不会在这一条自动路径中被证明为 `premium=true`。

## 正版/离线身份显示

AuthCode 支持正版/离线身份标签显示：

- 正版玩家 Steve 显示为 `[正版] Steve`
- 离线玩家 o_Steve 显示为 `[离线] Steve`
- 离线玩家内部名仍然是 `o_Steve`
- 显示名只用于聊天、TAB、GUI 和 Placeholder
- 登录、注册、邀请码、数据库仍然使用 UUID/internalName

默认情况下，正版名仍由 Velocity 执行 Mojang 在线验证。
如果一个名字存在正版档案，非号主不能直接以离线身份使用这个正版名。
离线玩家想显示为 Steve，建议实际登录名使用 o_Steve，插件会在显示层隐藏 o_ 前缀。

如果使用其他聊天插件，可以开启：

```yml
display:
  identity-tag:
    placeholder-only: true
```

开启后 AuthCode 不修改聊天事件，只保留 TAB/displayName 显示和 Placeholder/API。
聊天插件中可以使用：

```text
%authcode_identity_prefix% %authcode_display_name%
```

## Velocity 配置

默认配置位于 `authcode-velocity/src/main/resources/config.yml`，运行后复制到 Velocity 的 `plugins/authcode/config.yml`。

关键项：

- `premium.enabled`：是否启用 Velocity 端正版名检查。
- `premium.check-mode`：当前支持 `MOJANG_NAME_LOOKUP`。
- `offline-name.prefix`：离线玩家内部名前缀，默认 `o_`。
- `offline-name.overflow-mode`：前缀后超长的处理方式，默认 `HASH_SUFFIX`。
- `offline-name.uuid-source`：离线 UUID 生成依据，默认 `PREFIXED_INTERNAL_NAME`。
- `forward.channel`：发送给子服的 plugin message 通道，默认 `authcode:auth`。
- `forward.secret`：HMAC 密钥，必须与子服 `proxy.secret` 一致。
- `forward.payload-ttl-seconds`：payload 有效期。
- `forward.send-delay-ticks`：玩家连接后端后延迟发送认证包。

## Paper/Folia 配置

默认配置位于 `authcode-paper/src/main/resources/config.yml`，运行后复制到 `plugins/AuthCode/config.yml`。

关键项：

- `proxy.enabled: true`
- `proxy.mode: VELOCITY_PROXY_PLUGIN`
- `proxy.channel: "authcode:auth"`
- `proxy.secret`：必须与 Velocity `forward.secret` 一致。
- `proxy.wait-timeout-seconds`：等待 Velocity 认证包的最长时间，默认 12 秒。
- `proxy.require-proxy-assertion: true`：未收到 Velocity 认证包时踢出。
- `offline-name.prefix: "o_"`：离线身份内部名前缀。
- `display.identity-tag.*`：聊天、TAB、显示名、Placeholder 使用的身份显示格式。
- `offline-auth.require-code: true`：离线玩家是否必须输入邀请码。
- `offline-auth.require-register: true`：离线玩家是否必须注册密码。
- `premium.legacy-mojang-name-lookup-enabled: false`：旧模式只查名字，有冒名风险，不推荐。
- `invite-code.defaults.max-uses: 1`：默认一个邀请码只允许一名玩家使用。
- `invite-code.defaults.expire-after: "7d"`：默认邀请码 7 天后过期。
- `invite-code.random.min-length/max-length`：随机邀请码默认 4 到 6 位。
- `invite-code.random.digits-only: true`：随机邀请码默认只生成数字。
- `gui.enabled: true`：启用邀请码管理 GUI，默认文件位于 `plugins/AuthCode/gui/*.yml`。

## 玩家命令

- `/code <邀请码>`：提交邀请码。
- `/reg <密码> <确认密码>`：注册密码。
- `/login <密码>`：登录。
- `/changepass <旧密码> <新密码> <确认新密码>`：修改密码。

管理员命令：

- `/authcode create [邀请码] [次数] [过期时间]`
- `/authcode random [次数] [过期时间]`
- `/authcode list`
- `/authcode info <邀请码>`
- `/authcode delete <邀请码>`
- `/authcode gui`
- `/authcode premium <玩家名> <true/false>`
- `/authcode identity <玩家>`
- `/authcode namecheck <名字>`
- `/authcode reload`

## 数据库

Paper/Folia 子服默认使用 SQLite：`plugins/AuthCode/authcode.db`。

保留表：

- `players`
- `invite_codes`
- `invite_code_uses`

新增/迁移：

- `players.original_name`
- `players.internal_name`
- `players.lower_internal_name`
- `players.display_name`
- `players.auth_source`
- `players.last_proxy_premium`
- `players.last_proxy_verify_time`
- `proxy_auth_logs.original_name`
- `proxy_auth_logs.internal_name`
- `proxy_auth_logs.display_name`

启动时会自动创建缺失表，并对 `players` 缺失字段执行 `ALTER TABLE` 迁移。数据库、网络请求、BCrypt 均走异步路径。

## Folia 注意事项

AuthCode Paper/Folia 子服插件保留 Folia 兼容：

- `paper-plugin.yml` 包含 `folia-supported: true`。
- 玩家消息、踢出、药水效果等 Player API 操作通过 `SchedulerAdapter#runAtEntity` 切回安全上下文。
- 数据库和网络请求不在主线程执行。

## 测试建议

正版玩家：使用真实正版账号连接 Velocity，Velocity 应执行在线验证，子服收到签名 payload 后自动放行。

离线玩家：使用不存在正版档案的名字连接 Velocity，子服应提示 `/code 邀请码`，之后走 `/reg` 或 `/login`。

伪造正版名：使用非号主客户端连接一个存在正版档案的名字，Velocity 会执行 `forceOnlineMode()`，Mojang 验证失败后不会进入子服。
