# MCphone 接口桩（仅编译期）

`mcphone-api-stub.jar` 里只有 MCphone 公开的 `com.november.mcphone.api.*` 这一层接口，
从 MCphone **1.12.1（1.20.1-forge）** 的发布 jar 里原样导出，**不会被放进本模组的成品 jar**。

- 构建时由 `build.gradle` 的 `compileOnly files('libs/mcphone-api-stub.jar')` 引用。
- 玩家没装 MCphone 时，本模组不会加载任何引用这些接口的类（SPI 名单也没人读），照常运行。
- MCphone 升级后，只要它遵守自己公开的兼容承诺（接口只加 default 方法），这里不用动；
  真需要重新导出时，从新版本的 `mcphone-*-1.20.1-forge.jar` 里取出 `com/november/mcphone/api/` 重新打包即可。
- 改了联动代码后跑 `python3 tools/check-mcphone-contract.py` 自检：只会用 api 层、SPI 完好、jar 里没有混入对方的类。
