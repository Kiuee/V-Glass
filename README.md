# V-Glass — 液态玻璃底栏

基于 Xposed 的液态玻璃底栏模块：把支持应用的底部导航栏替换为实时折射的液态玻璃
胶囊——GraphicsLayer 逐帧采样屏幕真实内容（真折射，非贴图模拟）。
支持逐应用开关、玻璃样式调节与免适配版本表远程刷新。

## 要求与使用

- Android 13（API 33）及以上，低版本自动跳过注入
- 在 LSPosed 中启用本模块并勾选目标应用作用域，强制停止目标应用后重新打开生效
- 关闭开关后重启目标应用即可还原
- 模块「应用」页顶栏可手动刷新免适配版本表：ETag 条件请求（304 零正文），
  内容有变才落盘；jsDelivr → raw 直链 → 公共镜像多源回退

## 构建

```bash
./gradlew :app:assembleRelease   # 出包（keystore.properties 存在时 release 签名，缺失回退 debug）
```

工具链：AGP 9.4.1 / Gradle 9.6 / Kotlin 2.4.20 / compileSdk 37.1 / minSdk 33 / Java 17 / Jetpack Compose 1.13。
管理器界面基于 [COUI-Kit](https://github.com/ItosEO/COUI-Kit)（`io.github.itoseo:coui` + `coui-material3` + `coui-icons`，0.0.1-alpha16）。

## 开源协议

本项目以 [Apache-2.0](LICENSE) 发布。

致谢与第三方组件：

| 组件 | 版本 | 授权 |
|---|---|---|
| [Kyant0 的玻璃效果开源项目](https://central.sonatype.com/namespace/io.github.kyant0)（玻璃胶囊组件与动效参数移植自其示例代码） | — | Apache-2.0 |
| `io.github.kyant0:backdrop` / `io.github.kyant0:shapes` | 2.0.1 / 1.2.1 | Apache-2.0 |
| [COUI-Kit](https://github.com/ItosEO/COUI-Kit)（`io.github.itoseo:coui` / `coui-material3` / `coui-icons`，管理器界面组件、材质与图标） | 0.0.1-alpha16 | MPL-2.0 |

| COUI-Kit Demo 关于页 Hero 资源（`about_hero.mp4`、首/末帧 webp，来自 demo 模块） | — | MPL-2.0（随 COUI-Kit 仓库分发） |

其余 AndroidX / Jetpack Compose 依赖按其原始 Apache-2.0 授权分发。
