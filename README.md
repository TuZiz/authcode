# AuthCode

AuthCode 是一个 Kotlin + Maven 编写的 Paper / Folia 登录与邀请码白名单插件，目标运行环境为 Java 21+、Paper 1.21+、Folia 1.21+。

## 重要安全说明

本插件支持 `online-mode=false` 的服务器，但必须明确：

> 在 online-mode=false 下，Mojang 用户名查询只能判断这个名字是否存在正版账号，不能证明当前连接者就是该正版账号本人。存在正版名被冒用风险。

因此 `config.yml` 提供了 `premium.auto-pass` 开关。不接受该风险时，请设置：

```yaml
premium:
  auto-pass: false
```

## 构建与安装

1. 使用 Java 21+。
2. 执行 `mvn clean package`。
3. 将 `target/authcode-1.0.0.jar` 放入服务器 `plugins/` 目录。
4. 启动服务器，插件会在 `plugins/AuthCode/` 下生成配置、语言文件、`gui/` 文件夹和 SQLite 数据库。

## 玩家流程

正版自动放行开启时，玩家进服后插件会异步查询 Mojang API。查询到用户名存在正版档案时直接放行；查询失败时按 `premium.failed-action` 处理。

非正版玩家首次进入后会被锁定，需要：

```text
/code 邀请码
/reg 密码 确认密码
```

已注册玩家再次进入需要：

```text
/login 密码
```

## 命令

玩家命令：

- `/code <邀请码>`：提交邀请码。
- `/reg <密码> <确认密码>`：注册密码。
- `/login <密码>`：登录。
- `/changepass <旧密码> <新密码> <确认新密码>`：修改密码。

管理员命令：

- `/authcode create <邀请码> <次数> [过期时间]`
- `/authcode random <次数> [过期时间]`
- `/authcode list`
- `/authcode info <邀请码>`
- `/authcode delete <邀请码>`
- `/authcode premium <玩家名> <true/false>`
- `/authcode reload`

过期时间支持 `30m`、`1h`、`7d`、`30d`、`never`。

## 权限

- `authcode.admin`：全部管理命令。
- `authcode.admin.create`：创建邀请码。
- `authcode.admin.delete`：删除邀请码。
- `authcode.admin.list`：查看邀请码。
- `authcode.admin.reload`：重载配置与语言文件。
- `authcode.admin.premium`：设置玩家正版放行状态。
- `authcode.bypass`：绕过验证。

## 配置

默认配置位于 `src/main/resources/config.yml`，运行后复制到 `plugins/AuthCode/config.yml`。

核心配置：

- `settings.auth-timeout-seconds`：验证超时时间。
- `settings.max-login-attempts`：登录最大错误次数。
- `settings.password-min-length` / `settings.password-max-length`：密码长度限制。
- `premium.auto-pass`：是否允许正版名自动放行。
- `premium.failed-action`：Mojang API 失败后的处理，支持 `REQUIRE_CODE`、`ALLOW`、`KICK`。
- `lock.*`：未验证玩家锁定项。
- `commands.allowed-before-auth`：验证前允许执行的命令。

## 语言文件

所有玩家可见文本均来自：

```text
plugins/AuthCode/lang/zh_cn.yml
```

语言文件支持 MiniMessage 与变量，例如：

```yaml
auth:
  login-failed: "{prefix}<red>密码错误，剩余尝试次数：{times}</red>"
```

## GUI 文件规范

当前版本不实现 GUI，但插件会创建 `gui/` 文件夹。后续 GUI 必须放入：

```text
plugins/AuthCode/gui/*.yml
```

GUI 标题、按钮名、Lore、材质、CustomModelData、槽位和动作不得硬编码在 Kotlin 代码中。

## Folia 注意事项

AuthCode 默认兼容 Folia：

- 数据库、Mojang API、BCrypt 走异步调度。
- 玩家消息、踢出、药水效果等 Player API 操作通过 `SchedulerAdapter#runAtEntity` 切回安全上下文。
- Paper 环境使用 Bukkit Scheduler 作为回退，仅封装在 `PaperSchedulerAdapter` 中。
- `paper-plugin.yml` 包含 `folia-supported: true`。

## 数据库

默认使用 SQLite：

```text
plugins/AuthCode/authcode.db
```

启动时自动创建：

- `players`
- `invite_codes`
- `invite_code_uses`

插件关闭时会清理缓存并关闭 Hikari 连接池。
