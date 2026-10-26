# AGENTS.md

Catppuccin Icons —— 面向 JetBrains IDE 的粉彩图标包。Kotlin 插件，通过改写 IDE 的图标路径生效；配套一个 TypeScript 生成器，从 `catppuccin/vscode-icons` 主题派生整套图标。

## 准备命令

```shell
git submodule update --init --recursive   # generate/vscode-icons —— 任何图标生成的前提
corepack enable                          # 提供 pnpm@12.10.1
pnpm install
pnpm build                                # 生成 Icons.kt + src/main/resources/jetbrains_icons/icons/ —— Gradle 构建前必须执行
```

```shell
./gradlew buildPlugin        # Windows：gradlew.bat buildPlugin —— 编译、跑测试、产出 .jar
./gradlew test               # 只跑 JUnit 5 测试
./gradlew checkFormatting    # ktfmt（googleStyle）—— 必须通过
./gradlew format             # ktfmt（googleStyle）—— 就地重写源码
./gradlew runStaticAnalysis  # detekt
./gradlew runIde             # 带插件的沙箱 IDE
```

## 项目结构

- `src/main/kotlin/com/github/catppuccin/jetbrains_icons/` —— 插件源码
  - `IconProvider.kt` —— 通用的文件/文件夹图标 provider
  - `providers/` —— 各语言专用 provider（目前只有 `JavaIconProvider`）
  - `patcher/GeneralIconPatcher.kt` —— 按 variant 改写平台图标路径的 `IconPathPatcher`
  - `activity/IconPathPatcherActivity.kt` —— 安装各 patcher 的 `postStartupActivity`
  - `settings/` —— `Configurable`、`PersistentStateComponent` 与 Swing 视图
  - `model/` —— `icon_patchers.json` 的 kotlinx.serialization 模型
  - `util/PsiClassUtils.kt` —— PSI 修饰符辅助方法
  - `bundles/PluginSettingsBundle.kt` —— 资源包访问
- `src/main/resources/` —— `META-INF/plugin.xml`、可选依赖描述文件、`icon_patchers.json`、`messages/`
- `src/test/kotlin/` —— 与主包结构对应的测试
- `generate/scripts/generate.ts` —— 图标生成器（TypeScript，通过 `pnpm build` 运行）
- `generate/vscode-icons/` —— git submodule（上游 `catppuccin/vscode-icons`），SVG 与文件/文件夹名映射的来源
- `.github/workflows/` —— `build.yml`（CI）、`release.yml`（打包并发布到本仓库的 GitHub Release）、`vscode-icons.yml`（更新 submodule）

## 架构

图标查找分三层，按优先级排列：

1. `providers/*` 处理语言专用元素（例如带 static/可见性叠加层的 Java class 图标）。
2. `IconProvider` 处理其余全部：文件名覆盖 → 文件夹映射 → 文件名映射 → 扩展名映射（最长扩展名优先）→ binary/默认。
3. `GeneralIconPatcher` 在运行时改写 IDE *自身* 的平台图标路径，由 `src/main/resources/icon_patchers.json` 和当前 variant 驱动。

`IconPack.icons` 为 `PluginSettingsState.instance.variant` 解析生成的 `Icons` 类，并按 variant 各缓存一份实例，因此切换 variant 无需重启 IDE 即可生效。

## 代码风格

- Kotlin：ktfmt `googleStyle` —— 2 空格缩进，100 列宽。由 `./gradlew checkFormatting` 强制。
- detekt 静态分析，配置在 `detekt.yaml`（`PackageNaming` 已禁用）。运行 `./gradlew runStaticAnalysis`。
- `.editorconfig`：UTF-8、LF 换行（`.cmd`/`.bat`/`.ps1` 用 CRLF）、文件末尾保留换行、行尾空白裁剪、2 空格缩进。
- 公开 API 要写 KDoc —— 沿用现有的 `@param`/`@return` 文档注释风格。
- TypeScript 在 submodule 里，遵循该子项目自己的 eslint 配置。根目录 `tsconfig.json` 把 `@/*` 映射到 `generate/vscode-icons/src/*`。

## 测试说明

- JUnit 5（`junit-jupiter` 6.x）跑在 JUnit Platform 上，测试基于 IntelliJ platform 测试框架。
- `./gradlew test` —— `buildPlugin` 依赖它，所以每次构建都会自动执行。
- 新行为的测试写在 `src/test/kotlin/...`，与主包结构对应（例如 `providers/JavaIconProviderTest.kt`）。
- provider 测试需要 IntelliJ 测试夹具；没有充分理由不要把它们改写成普通单元测试。
- **按资源路径比较图标，不要比较对象身份。** 用 `src/test/kotlin/com/github/com/catppuccin/jetbrains_icons/IconAssertions.kt` 里的 `assertIconEquals` 辅助函数。测试自建一份 `Icons` 实例，而 provider 走 `IconPack.icons`，平台对同一资源可能给出不同的 `CachedImageIcon` 对象 —— `CachedImageIcon.equals` 还会比较 tooltip supplier 这类 lambda 字段，所以身份比较既 flaky 又依赖执行顺序。
- **测试源码必须能在 CI 的平台上编译。** CI 用 `gradle.properties` 里的 `IC 2023.1` 编译测试，本机开发用的是 2026.2 —— 后者才有 `com.intellij.ui.icons.IconPathProvider`，2023.1 没有。`assertIconEquals` 因此用反射读 `getOriginalPath()`，平台不提供该访问器时回退到平台自身的相等比较（旧平台本来就按路径比较）。**不要在测试里直接 import 只有新平台才有的类型**，否则 `compileTestKotlin` 会在 CI 上失败（主源码不受影响）。改完测试要两个平台各验一次：`./gradlew test`（本机）和 `./gradlew -PlocalIdePath= test`（CI 的 2023.1）。
- 测试源码位于 `com.github.com.catppuccin.jetbrains_icons` 包（注意 `com.github.com` 重复了一段），插件源码则是 `com.github.catppuccin.jetbrains_icons`。保持现有测试包名不变。

## 坑点

- **任何 Gradle 编译之前都必须先 `pnpm build`。** `Icons.kt` 和 `src/main/resources/jetbrains_icons/icons/` 都是被 gitignore 的生成产物。缺了它们，Kotlin 构建会因为找不到 `Icons` 类而失败。
- **永远不要手工编辑或重排生成产物。** 正因如此，`Icons.kt` 和生成的图标都被排除在 ktfmt 和 detekt 之外。要改就去改 `generate/scripts/generate.ts`，然后重新生成。
- **submodule 必须是完整的。** `pnpm build` 会读 `generate/vscode-icons/icons/<variant>/` 以及 submodule 里的 `src/defaults/{fileIcons,folderIcons}.ts`。submodule 为空时，生成过程不会报错，而是静默地产出空结果。
- **生成的命名会做归一化**：`-` → `_`，以数字开头的加 `_` 前缀（`generateJvmField` / `generateExtensionsToIcons`）。图标资源名为 `<variant>_<file>.svg`，例如 `mocha_docker.svg`。
- **`icon_patchers.json` 里写错名字会静默失败。** 每份配置的 `enabledSetting` 是通过反射在 `PluginSettingsState` 上解析的；名字不存在时会被 `runCatching { }.getOrDefault(false)` 解析成 `false`。提交前确认设置名真实存在。
- **切换 variant 立即生效，不需要重启 IDE。** `PluginSettings.apply` 用 `IconLoader.clearCache()` 清掉平台图标缓存，再用 `LafManager.updateUI()` 重绘 —— 与 Material Theme 插件用的是同两个调用。路径 patcher 本来就每次调用都读设置，需要变成 variant 感知的只有平台图标缓存和 `IconPack.icons` 实例。设置页仍保留一个手动 "Restart IDE" 按钮，应对个别界面残留旧图标的情况。
- **`javaSupport` 的默认值取决于 Java 插件是否安装**（`isPluginInstalled`），不是常量。
- **构建必须跑在 JetBrains Runtime (JBR) 上，不能用普通 JDK。** `instrumentCode` 任务需要 IntelliJ 的插桩 agent，只有 JBR 才带（`<JBR>/bin/instrument.dll`）。用普通 JDK（比如 Microsoft JDK）时插件会退回去找 JBR 安装包，然后死在 `<java.home>\Packages does not exist`。构建前先确认 `java -version` 输出里有 `OpenJDK Runtime Environment JBR-…`。
- **目标 IDE 的选择是自动的。** `build.gradle.kts` 先看 `localIdePath`（`gradle.properties` 里的可选路径）：指向一个真实存在的安装目录就走 `local(...)` 用本机 IDE，否则回落到 `create(platformType, platformVersion)`，即 `gradle.properties` 里的 `IC` / `2023.1`。CI 上 `localIdePath` 不存在，于是走 `create(...)` —— `.github/workflows/build.yml` 与 `release.yml` 都能直接跑通，代价是首次构建下载一份 IDE。想强制指定路径用 `-PlocalIdePath=...`；传空值（`-PlocalIdePath=`）则强制走 `create(...)`。注意本地那个安装目录虽然叫 "Community Edition"，自 2025.3 起的统一发行版实际对外报告的是 **IntelliJ IDEA Ultimate**（`productCode=IU`，例如 `IU-262.10968.63`）。`runLatestIde` 任务是独立的 —— 它自己钉住了 `2026.1.4` 版本，*会*去下载。
- **`maven.aliyun.com` 镜像声明在 `mavenCentral()` 前面**，用于在国内网络下加快依赖解析。保持它在 `repositories` 块的第一位。
- **必须调高 Gradle 堆内存。** 生成的 `Icons.kt` 每个图标声明一个属性（约 2600 个），默认的 1 GB Gradle 堆和 Kotlin 守护进程都会内存溢出（`FastJarHandler` 里报 `OutOfMemoryError: Java heap space`）。`gradle.properties` 因此设置了 `org.gradle.jvmargs=-Xmx3g` 和 `kotlin.daemon.jvmargs=-Xmx3g` —— 不要往回调。
- **lint 任务的源文件树必须限定在 `src`。** `checkFormatting` 和 `runStaticAnalysis` 原来用 `project.fileTree(rootDir)`，会把 `build/` 和沙箱目录一起扫进来；Gradle 9 一旦在同一次调用里看到这些产物就会以 "Property has implicit dependency" 让构建失败。保持 `source = project.fileTree("src")`。
- **测试夹具必须排除 `com.intellij.modules.ultimate`。** IntelliJ IDEA Ultimate 自带一个付费功能模块，它注册了一个构造器为包级私有的 `postStartupActivity`；测试夹具实例化扩展走的路径要求构造器可访问，于是该 activity 无法创建，*所有*会打开项目的测试都会以 `Cannot find suitable constructor for class ...` 失败。因此 `test` 任务通过 `disabled.plugins.file.path` 系统属性让平台读一份禁用插件列表。用 `IC 2023.1` 时该模块不存在，这份列表是空操作；只有切到本地 Ultimate 安装时它才真正起作用。IDE 沙箱始终不受影响 —— 那里需要这个模块。
- **`PluginId` 用全限定方式引用，不要静态导入。** `PluginSettingsState` 和 `SettingsAdditionalSupportView` 里用的是 `PluginId.getId("com.intellij.java")`；不要改回 `import com.intellij.openapi.extensions.PluginId.getId` / `.findId` 的静态导入形式 —— 当前平台下解析不了。
- **可选插件依赖**（`com.intellij.java`、`org.jetbrains.kotlin`）在 `plugin.xml` 中声明，并各自配有独立的配置文件（`*-withJava.xml`、`*-withKotlin.xml`）。新增扩展点要注册到对应的文件里。

## PR 与提交约定

- 从 `main` 切分支；CI 会在 push 或 PR 触及 `src/`、`generate/`、Gradle 文件或 pnpm manifest 时运行。
- 提交信息用 conventional commit 前缀（`feat:`、`fix:`、`docs:`、`refactor:`）。发布工作流会以 `docs: update changelog` 提交更新日志。
- 更新日志在 `CHANGELOG.md`，由 Gradle changelog 插件渲染；保留 `[Unreleased]` 段落以及分组顺序（Added、Changed、Deprecated、Removed、Fixed、Security）。
- 发版方式是推送 `v*` tag —— `release.yml` 只构建 jar 并在**本仓库**创建一个 GitHub Release（默认草稿），**不会**发布到 JetBrains Marketplace：插件 ID 属于上游 `catppuccin/jetbrains-icons`，fork 没有 Marketplace 权限。要改成直接发布，在仓库 Settings → Secrets and variables → Actions → Variables 里加一个 `RELEASE_DRAFT=false` 变量。

## 安全

- 签名凭据只从环境变量读取：`CERTIFICATE_CHAIN`、`PRIVATE_KEY`、`PRIVATE_KEY_PASSWORD`。绝不提交、回显或记录它们。本 fork 不发布到 Marketplace，`PUBLISH_TOKEN` 已不再使用。
- `signPlugin` 只在 `release.yml` 中、且仓库配置了上述签名 secrets 时才会执行；`publishPlugin`（Marketplace 上传）已废弃，不要运行它。

## 代码导航

本仓库已由 [codegraph](https://github.com/colbymchenry/codegraph) 建立索引（70 个文件、530 个节点）。

- 用 `codegraph_explore` MCP 工具理解某个符号的原理、查找调用方、评估改动影响面 —— 代码导航优先用它，而不是 grep/read 循环。
- 改完代码后用 `codegraph sync` 刷新索引；`codegraph index` 做全量重建，`codegraph status` 检查新鲜度。
- 索引遵循 `.gitignore`，所以生成的 `Icons.kt` 和 2600+ 图标资源不会进图 —— 图谱只覆盖手写代码。
- `.codegraph/.gitignore` 可以安全提交；索引数据库留在本地，不纳入版本管理。
