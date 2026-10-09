# 维护版本启动与退出验证

验证日期：2026-10-09。范围为七个维护模块的开发客户端启动、单人世界退出及重新加载；不检查耳鸣、命令或 legacy 模块。

七个模块均完成 `build`、实际 `runClient`、主菜单与世界渲染、三轮退出重进、最终存档复验及正常关闭。最终一轮共 28 次退出，耗时 150.72–1046.62 ms；未复现超过五秒的退出、服务器挂起或已检查的世界引用残留。

## 实际修复

`fabric26.3/build.gradle` 的 Loom 客户端运行配置补充 `-XX:StackShadowPages=32`，同时覆盖 Gradle 与生成的 IDE 客户端配置。编译仍使用 Java 25，未新增依赖或处理器数量限制。

未修复时，Java 25.0.3 两次在初始资源重载阶段以 `0xC0000005` 退出；仅换为 Java 26.0.2 仍然失败。保持原 JDK、模组和渲染后端，仅加入上述参数后启动成功。将参数移入正式构建配置并撤掉测试注入后，再次完成整个世界生命周期测试，以及两次资源重载。

本机缓存的 Mojang 26.3 版本元数据包含该参数，NeoForge 26.3 的生成启动参数也已包含它，所以不向其他模块重复移植。[上游启动器问题及修复记录](https://github.com/PrismLauncher/PrismLauncher/issues/6073)提供了相同阶段原生崩溃及该参数的修复证据。

历史 `fabric26.3/run/hs_err_pid41216.log` 的崩溃线程位于 JVM/LWJGL shaderc 回调及 Minecraft 着色器编译路径，相关帧未出现 First Aid 类。当前对照试验支持“开发启动缺少官方 JVM 参数”的归因；不能据此断言所有历史原生崩溃均有同一个底层原因。此次修复不改业务逻辑，也没有更换系统 JVM、显卡驱动或 LWJGL。

## 最终逐模块结果

以下均为真实图形客户端测试。JVM 列记录实际开发运行版本，不代表发布模组的最低 Java 版本。每行的构建、启动、存档复验和正常关闭均通过。

| 模块与原始结果 | 客户端 JVM | 退出 1 / 2 / 3 / 最终复验（ms） | 服务器停止最大值（ms） | 保存阶段最大值（ms） |
| --- | --- | --- | --- | --- |
| [forge1.20.1](../forge1.20.1/build/client-lifecycle-qa/result.txt) | 17.0.12 | 1046.62 / 826.45 / 824.06 / 790.57 | 962.29 | 776.32 |
| [fabric1.21.1](../fabric1.21.1/build/client-lifecycle-qa/result.txt) | 25.0.3 | 695.09 / 186.50 / 169.64 / 191.48 | 569.28 | 537.15 |
| [neoforge1.21.1](../neoforge1.21.1/build/client-lifecycle-qa/result.txt) | 21.0.11 | 777.90 / 227.14 / 224.25 / 225.62 | 673.85 | 642.23 |
| [fabric26.2](../fabric26.2/build/client-lifecycle-qa/result.txt) | 25.0.3 | 721.12 / 184.32 / 167.63 / 150.72 | 666.24 | 629.29 |
| [neoforge26.2](../neoforge26.2/build/client-lifecycle-qa/result.txt) | 25.0.3 | 775.92 / 205.68 / 239.33 / 222.64 | 692.24 | 638.32 |
| [fabric26.3](../fabric26.3/build/client-lifecycle-qa/result.txt) | 25.0.3 | 727.59 / 183.76 / 167.64 / 167.40 | 649.38 | 649.09（上界） |
| [neoforge26.3](../neoforge26.3/build/client-lifecycle-qa/result.txt) | 25.0.3 | 718.12 / 239.40 / 206.85 / 222.27 | 670.43 | 670.27（上界） |

退出时间从向客户端提交原生断开操作开始，至其返回菜单结束，包含服务器保存与客户端清理。服务器停止时间从 `Stopping server` 日志到实际服务器线程终止；保存时间从 `Saving players` 到 `All dimensions are saved`。26.3 不提供后一日志，因此使用服务器线程终止作为保存阶段的上界，包含保存之后的服务器收尾。这些阶段互相包含，不能相加；线程观察与日志采集存在少量调度误差。

## 验证方式与证据

- 各模块使用独立的 `build/client-lifecycle-qa/game`，创建独立超平坦生存测试世界，不操作原 `run` 目录中的世界或其他会话客户端。
- 使用各模块自己的 wrapper 执行 `build --offline --console=plain` 和实际 `runClient`；测试初始化脚本仅隔离游戏目录及标记本次客户端。
- 通过不含耳鸣或命令检查的 JVM 附加探针调用原生建世界、断开、重新加载和关闭流程；关闭后 wrapper 均返回成功。
- 三轮分别将服务器玩家经验等级从 0 增至 1、2、3；每次重进检查之前的等级，第四次加载确认等级 3，再正常退出。该检查直接检验玩家存档是否持久化。
- 每次退出确认客户端世界、玩家、内置服务器引用为空，回到 `TitleScreen`，`FirstAid.isSynced` 重置，注册表分发与 debuff 缓存为空，重载监听集合为空；并等待真实服务器线程终止。
- 探针在退出超过五秒时采集 JVM 线程栈。本次最终测试均未达到阈值，没有可供定位的退出挂起栈。
- 保存并人工检查原生主菜单和世界截图；最终成功日志未见 Mixin 应用失败或退出异常。Fabric 26.3 额外验证两次完整资源重载，包括等待加载遮罩正常消失。
- 七个模块的 Java 源码哈希在最终测试前后及报告汇总时一致。测试期间保留其他会话改动；曾在测试中发生源码变化的 Fabric 26.2 已重新构建、运行并验证。

本地证据保存在各模块的忽略目录 `build/client-lifecycle-qa/`：`build.log`、`client.log`、`result.txt`、源码哈希和三张原生截图。汇总为 [results.json](../fabric26.3/build/client-lifecycle-qa/results.json)，截图汇总为 [1.20/1.21](../fabric26.3/build/client-lifecycle-qa/screenshots-1.png) 和 [26.x](../fabric26.3/build/client-lifecycle-qa/screenshots-2.png)。这些本地证据不随 Git 源码分发。

Fabric 26.3 对照日志为 [Java 25 首次失败](../fabric26.3/build/client-lifecycle-qa/native-crash-baseline.log)、[Java 25 重复失败](../fabric26.3/build/client-lifecycle-qa/native-crash-repeat.log)、[Java 26 失败](../fabric26.3/build/client-lifecycle-qa/native-crash-java26.log)、[仅补参数后的完整通过结果](../fabric26.3/build/client-lifecycle-qa/stack-shadow-control-result.txt)；官方参数快照为 [official-launch-jvm-arguments.json](../fabric26.3/build/client-lifecycle-qa/official-launch-jvm-arguments.json)。最终 Fabric 26.3 成功日志使用正式构建配置提供参数。

## 未复现与验证边界

本次未复现属于 First Aid 的退出卡死，不修改退出事件、注册表缓存或渲染资源释放逻辑。较长的一次退出主要位于首次保存阶段；重复进入后通常更快。

Windows 性能计数器读取错误及开发账号的 Realms/证书认证失败属于本机开发环境日志，未阻止本次单人世界测试；没有修改系统环境处理它们。早期测试中三个旧版本的模拟距离配置低于允许下限，已修正测试配置并复测通过。

这些结果覆盖本机、小型独立测试世界及当前源码快照。未验证大型旧存档、第三方整合包、远程服务器断开，或受伤/用药效果与退出同时发生的所有组合；耳鸣、命令及 legacy 模块完全排除。
