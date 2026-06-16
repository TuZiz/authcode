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

## Velocity 配置

默认配置位于 `authcode-velocity/src/main/resources/config.yml`，运行后复制到 Velocity 的 `plugins/authcode/config.yml`。

关键项：

- `premium.enabled`：是否启用 Velocity 端正版名检查。
- `premium.check-mode`：当前支持 `MOJANG_NAME_LOOKUP`。
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
- `/authcode reload`

## 数据库

Paper/Folia 子服默认使用 SQLite：`plugins/AuthCode/authcode.db`。

保留表：

- `players`
- `invite_codes`
- `invite_code_uses`

新增/迁移：

- `players.auth_source`
- `players.last_proxy_premium`
- `players.last_proxy_verify_time`
- `proxy_auth_logs`

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
