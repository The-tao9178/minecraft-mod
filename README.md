# Shared Stock Exchange（共享证券交易所）

一个 Minecraft Java 版模组：全服玩家共享一份股票行情，用等价交换（ProjectE）的 EMC 当钱来炒股。谁买入就推高市值，谁卖出就压低市值；亏光了会背上**负数 EMC**——转化桌里的 EMC 也能为负，必须不断卖回等量物品才能归零，这是给"股灾受害者"的惩罚。

English: A NeoForge mod with a server-wide shared stock market denominated in ProjectE EMC, including a negative-EMC penalty mechanic and an optional [MCphone](https://github.com/november521/mcphone) app integration.

## 基本信息

| 项目 | 值 |
|---|---|
| 游戏版本 | Minecraft **1.21.1** |
| 加载器 | **NeoForge** 21.1.x（加载器：给游戏装模组的底座） |
| Java | 21 |
| 模组 ID | `shared_stock_exchange` |
| 许可 | MIT |

## 依赖

- **等价交换 ProjectE（强烈推荐）**：钱就是玩家的 EMC，股价锚定物品 EMC。没装也能跑——模组退回内置测试钱包和手写发行价，方便单人体验。
- **MCphone（可选）**：装了会多一个手机里的「炒股」App，可以把玩法说明导出到手机书城；没装则完全无感，不影响任何原有功能。
- 两者都是**软依赖**：本模组通过反射和公开 API 调用它们，不绑定任何版本内部实现，更新对方模组一般不会弄坏本模组（见下文"契约自检"）。

## 安装

把 `build/libs/shared_stock_exchange-<版本号>.jar`（或发布页下载的 jar）放进 `.minecraft/mods` 即可。服务端也要放一份（联机时行情由服务器保管）。

## 玩法速览

- 合成 **股票交易终端**，右键打开交易所界面，潜行右键打开玩法手册（材料在游戏内配方书 / JEI / REI 里可查）。
- 行情界面有 K 线图、持仓、分红；买入会推高全服共享的价格，卖出会压低。
- 常用聊天命令：
  - `/stock list` 看全部股票；`/stock status` 看大盘
  - `/stock buy <股票> <数量>`、`/stock sell <股票> <数量>`
  - `/stock mine` 我的持仓，`/stock claim` 领分红
  - `/stock top` 排行榜，`/stock chart <股票>` 命令行看走势图
  - `/stock wallet` 查余额；`/stock refund` 清仓退款
  - 管理员：`/stock interval <秒>` 行情推进间隔，`/stock debt on|off` 负 EMC 惩罚开关，`/stock liquidation on|off` 强平开关，`/stock clear` 重置行情

## 负数 EMC 惩罚是怎么实现的

对等价交换的转化桌库存类打了一个小 Mixin（运行时字节码补丁，见 `src/main/java/.../mixin/`），让它在结算时保留负数 EMC 而不是抹成 0。整个补丁只在装了 ProjectE 时加载，且只影响"往负数走"这一种情况。炒股亏成负数后，得持续卖出等量价值才能回到 0——这个状态会随世界存档持久化，重进游戏不重置。

## 与 MCphone 联动的合规说明

- 只使用 MCphone 公开的 `com.november.mcphone.api.*` 接口层，不碰它的内部实现；通过 Java SPI（`META-INF/services`）注册 App。
- `libs/mcphone-api-stub.jar` 是**仅编译期**使用的接口桩，运行时由玩家自己安装的 MCphone 提供实现，也不会被打进本模组的 jar。
- 改了联动代码后，可运行 `python3 tools/check-mcphone-contract.py` 自检：它会检查我们没有越界引用 MCphone 内部类、SPI 注册完好、产物 jar 里没有混入对方的类。

## 从源码构建

需要 JDK 21 和 Gradle 8.12.1+（本仓库不含 gradle wrapper，可自行 `gradle wrapper` 生成或全局安装）。

```bash
gradle build
# 产物在 build/libs/shared_stock_exchange-<版本号>.jar
```

首次构建会从 NeoForge Maven 下载开发环境，需要联网，可能跑几分钟。

## 目录结构

```
src/main/java/.../sharedstockexchange/
  market/    行情与交易的核心数学、数据、管理（版本无关）
  compat/    等价交换反射桥（找 EMC 能力、读写 EMC）
  client/    交易所/详情/设置界面、K 线绘制、手册
  client/mcphone/  手机 App 联动（可选依赖）
  net/       自定义网络包（动作上行、行情快照下行）
  registry/  物品与创造标签页注册
  command/   /stock 命令
  event/     存档读写等服务端事件
  item/      交易终端、手册两个物品的行为
```

## 致谢与声明

- [ProjectE](https://github.com/ProjectE-Team/ProjectE)（等价交换）与 [MCphone](https://github.com/november521/mcphone) 的作者们：本模组与它们联动，但不隶属于它们。
- 本模组代码由 [AutoMods](https://automods.cn) AI 辅助编写。
- 本模组是游戏内的模拟交易玩法，与现实金融产品无关。
- Minecraft 是 Mojang Studios 的商标；本项目与 Mojang / Microsoft 无关。

## 许可

MIT License，详见 [LICENSE](LICENSE)。
