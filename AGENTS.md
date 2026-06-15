# AuthCode Agent 开发规范

## Agent 基本原则

1. 所有修改必须优先保证 Folia 线程安全。
2. 任何异步任务中禁止直接操作 Player / Entity / World / Bukkit API。
3. 玩家相关操作必须通过 `SchedulerAdapter#runAtEntity` 或等价方法切回安全上下文。
4. 数据库、网络请求、BCrypt 必须异步。
5. 不允许为了方便牺牲 Folia 兼容。
6. 不允许引入会破坏 Paper / Folia 双端兼容的 API。
7. 不允许硬编码玩家可见文本。
8. 不允许硬编码 GUI 文本。
9. 不允许创建超大胖类。
10. 不允许把多个功能模块混在一个包里。

## Agent 修改流程

每次修改前必须先做：

1. 阅读 `README.md`。
2. 阅读 `AGENTS.md`。
3. 阅读 `src/main/resources/config.yml`。
4. 阅读 `src/main/resources/lang/zh_cn.yml`。
5. 确认修改是否涉及 Folia 调度。
6. 确认修改是否涉及数据库。
7. 确认修改是否涉及语言文本。
8. 确认修改是否涉及 GUI。

## Agent 输出要求

每次完成修改后必须说明：

1. 修改了哪些文件。
2. 新增了哪些配置项。
3. 新增了哪些语言 key。
4. 是否涉及数据库迁移。
5. 是否涉及 Folia 调度。
6. 是否可能影响 Paper 兼容。
7. 如何测试。

## Agent 代码规范

1. 主类只负责生命周期：
   - `onEnable`
   - `onDisable`
   - 初始化模块
   - 注册监听器
   - 注册命令
2. 业务逻辑必须进入 `service` 包。
3. 数据库逻辑必须进入 `storage` 包。
4. 调度逻辑必须进入 `scheduler` 包。
5. 消息逻辑必须进入 `message` / `lang` 包。
6. 命令逻辑必须进入 `command` 包。
7. 事件逻辑必须进入 `listener` 包。
8. 工具类必须按用途拆分，不允许万能 Utils。
9. 一个 PR / 一次改动尽量只做一类事情。
10. 不允许随意改包名和主类名。

## Agent 禁止事项

禁止：

1. 在 Kotlin 代码中写死中文提示。
2. 在 Kotlin 代码中写死 GUI 标题。
3. 在 Kotlin 代码中写死按钮 Lore。
4. 在异步线程调用 Player API。
5. 在异步线程调用 World API。
6. 使用 `Thread.sleep`。
7. 阻塞主线程等待 `CompletableFuture`。
8. 把数据库操作写进 Listener。
9. 把业务流程写进 Command。
10. 把所有子命令写进一个巨大 `when`。
11. 引入不必要的大型框架。
12. 删除 Folia 支持。
13. 删除语言文件。
14. 删除配置注释里的 offline-mode 风险提示。
15. 省略 README 说明。

## Agent 测试清单

每次修改后至少检查：

1. Maven 是否能编译。
2. Paper 是否能启动。
3. Folia 是否能启动。
4. `paper-plugin.yml` 是否包含 `folia-supported: true`。
5. `/code` 是否正常。
6. `/reg` 是否正常。
7. `/login` 是否正常。
8. 未验证玩家是否真的被锁定。
9. 正版自动放行是否受配置控制。
10. 语言文件修改后是否能热重载。
11. 数据库连接是否正常关闭。
12. 插件 disable 是否无报错。

## GUI 约束

当前版本不实现 GUI。后续若添加 GUI：

1. 所有 GUI 文件必须放入 `src/main/resources/gui/*.yml`。
2. GUI 标题、行数、按钮材质、CustomModelData、名称、Lore、槽位、动作、翻页、返回、关闭都必须来自配置。
3. 推荐默认文件：
   - `gui/main.yml`
   - `gui/code_list.yml`
   - `gui/player_info.yml`
4. 不允许在 Kotlin 中硬编码 GUI 文本。
