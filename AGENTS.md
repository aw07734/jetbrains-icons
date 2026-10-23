# AGENTS.md

Catppuccin Icons — a pastel icon pack for JetBrains IDEs. Kotlin plugin that patches IDE icon paths, with a TypeScript generator that derives the icon set from the `catppuccin/vscode-icons` theme.

## Setup commands

```shell
git submodule update --init --recursive   # generate/vscode-icons — required for any icon generation
corepack enable                          # provides pnpm@12.10.1
pnpm install
pnpm build                                # generates Icons.kt + src/main/resources/jetbrains_icons/icons/ — REQUIRED before Gradle
```

```shell
./gradlew buildPlugin        # Windows: gradlew.bat buildPlugin — compiles, runs tests, produces the .jar
./gradlew test               # JUnit 5 tests only
./gradlew checkFormatting    # ktfmt (googleStyle) — must pass
./gradlew format             # ktfmt (googleStyle) — rewrites the sources in place
./gradlew runStaticAnalysis  # detekt
./gradlew runIde             # sandbox IDE with the plugin loaded
```

## Project layout

- `src/main/kotlin/com/github/catppuccin/jetbrains_icons/` — plugin source
  - `IconProvider.kt` — generic file/folder icon provider
  - `providers/` — language-specific providers (currently `JavaIconProvider`)
  - `patcher/GeneralIconPatcher.kt` — `IconPathPatcher` that rewrites platform icon paths per variant
  - `activity/IconPathPatcherActivity.kt` — `postStartupActivity` that installs the patchers
  - `settings/` — `Configurable`, `PersistentStateComponent`, and Swing views
  - `model/` — kotlinx.serialization models for `icon_patchers.json`
  - `util/PsiClassUtils.kt` — PSI modifier helpers
  - `bundles/PluginSettingsBundle.kt` — resource-bundle access
- `src/main/resources/` — `META-INF/plugin.xml`, optional-dependency descriptors, `icon_patchers.json`, `messages/`
- `src/test/kotlin/` — tests mirroring the main package layout
- `generate/scripts/generate.ts` — icon generator (TypeScript, run via `pnpm build`)
- `generate/vscode-icons/` — git submodule (upstream `catppuccin/vscode-icons`), source of SVGs and file/folder name maps
- `.github/workflows/` — `build.yml` (CI), `release.yml` (marketplace publish), `vscode-icons.yml` (submodule bump)

## Architecture

Icon lookup has three layers, in priority order:

1. `providers/*` handle language-specific elements (e.g. Java class icons with static/visibility overlays).
2. `IconProvider` handles everything else: filename override → folder map → filename map → extension map (longest extension first) → binary/default.
3. `GeneralIconPatcher` rewrites the IDE's *own* platform icon paths at runtime, driven by `src/main/resources/icon_patchers.json` and the active variant.

`IconPack.icons` is a lazy singleton over the generated `Icons` class; the variant comes from `PluginSettingsState.instance.variant`.

## Code style

- Kotlin: ktfmt `googleStyle` — 2-space indent, 100-column. Enforced by `./gradlew checkFormatting`.
- detekt static analysis, config in `detekt.yaml` (`PackageNaming` disabled). Run `./gradlew runStaticAnalysis`.
- `.editorconfig`: UTF-8, LF endings (CRLF for `.cmd`/`.bat`/`.ps1`), final newline, trimmed trailing whitespace, 2-space indent.
- KDoc on public APIs — match the existing `@param`/`@return` doc-comment style.
- TypeScript lives in the submodule and follows that project's own eslint config. Root `tsconfig.json` maps `@/*` → `generate/vscode-icons/src/*`.

## Testing instructions

- JUnit 5 (`junit-jupiter` 6.x) on the JUnit Platform, running against the IntelliJ platform test framework.
- `./gradlew test` — `buildPlugin` depends on it, so it runs automatically on every build.
- Add tests for new behavior in `src/test/kotlin/...`, mirroring the main package layout (e.g. `providers/JavaIconProviderTest.kt`).
- Provider tests need the IntelliJ test fixtures; do not refactor them into plain unit tests without a reason.
- **Compare icons by resource path, not object identity.** Use the `assertIconEquals` helper in `src/test/kotlin/com/github/com/catppuccin/jetbrains_icons/IconAssertions.kt`. The tests build their own `Icons` instance while the providers use `IconPack.icons`, and the platform may hand out distinct `CachedImageIcon` objects for the same resource — `CachedImageIcon.equals` also compares lambda-valued fields such as the tooltip supplier, so identity comparison is flaky and order dependent.
- The test sources live in the `com.github.com.catppuccin.jetbrains_icons` package (note the doubled `com.github.com`), while the plugin sources use `com.github.catppuccin.jetbrains_icons`. Keep the existing test package as-is.

## Gotchas

- **`pnpm build` is mandatory before any Gradle compile.** `Icons.kt` and `src/main/resources/jetbrains_icons/icons/` are gitignored generated output. Without them the Kotlin build fails on a missing `Icons` class.
- **Never hand-edit or reformat generated output.** `Icons.kt` and the generated icons are excluded from both ktfmt and detekt for that reason. Change `generate/scripts/generate.ts` instead and regenerate.
- **The submodule must be populated.** `pnpm build` reads `generate/vscode-icons/icons/<variant>/` and the submodule's `src/defaults/{fileIcons,folderIcons}.ts`. If the submodule is empty, generation produces nothing instead of failing loudly.
- **Generated names are normalized**: `-` → `_`, and a leading digit gets a `_` prefix (`generateJvmField` / `generateExtensionsToIcons`). Icon resources are named `<variant>_<file>.svg`, e.g. `mocha_docker.svg`.
- **A typo in `icon_patchers.json` fails silently.** Each config's `enabledSetting` is resolved by reflection against `PluginSettingsState`; an unknown name resolves to `false` via `runCatching { }.getOrDefault(false)`. Verify the setting name exists.
- **Changing the variant requires an IDE restart.** `PluginSettings.apply` calls `restart()` when the pack changed, and only refreshes project views for the support toggles.
- **`javaSupport` defaults to whether the Java plugin is installed** (`isPluginInstalled`), not to a constant.
- **The build must run on a JetBrains Runtime (JBR), not a plain JDK.** The `instrumentCode` task needs IntelliJ's instrumentation agent, which only the JBR ships (`<JBR>/bin/instrument.dll`). On a normal JDK (e.g. the Microsoft JDK) the plugin falls back to hunting for a JBR package and dies with `<java.home>\Packages does not exist`. Check `java -version` reports `OpenJDK Runtime Environment JBR-…` before building.
- **The target IDE is pinned to a local install.** `build.gradle.kts` uses `intellijPlatform { local("D:\\Program Files\\JetBrains\\IntelliJ IDEA Community Edition") }` instead of `create(platformType, platformVersion)`, so no IDE is downloaded for `./gradlew buildPlugin`, `test`, or `runIde`. Consequences: the path is machine-specific and **will not resolve on the Linux CI runners** in `.github/workflows/build.yml`; `gradle.properties` `platformType`/`platformVersion` are now unused (only `platformPlugins` still matters). The `runLatestIde` task is separate — it pins its own `2026.1.4` IDE and *will* download it. Note the install folder says "Community Edition" but the unified distribution since 2025.3 reports itself as **IntelliJ IDEA Ultimate** (`productCode=IU`, e.g. `IU-262.10968.63`).
- **A `maven.aliyun.com` mirror is declared ahead of `mavenCentral()`** for faster dependency resolution on CN networks. Leave it first in the `repositories` block.
- **The Gradle heap must be raised.** The generated `Icons.kt` declares one property per icon (~2600 of them), so the default 1 GB Gradle heap and the Kotlin daemon both run out of memory (`OutOfMemoryError: Java heap space` in `FastJarHandler`). `gradle.properties` sets `org.gradle.jvmargs=-Xmx3g` and `kotlin.daemon.jvmargs=-Xmx3g` for this reason — do not lower them.
- **The lint tasks must scope their source tree to `src`.** `checkFormatting` and `runStaticAnalysis` used `project.fileTree(rootDir)`, which swept up `build/` and the sandbox. Gradle 9 then fails the build with "Property has implicit dependency" as soon as those outputs exist in the same invocation. Keep `source = project.fileTree("src")`.
- **The test fixture must exclude `com.intellij.modules.ultimate`.** IntelliJ IDEA Ultimate ships a paid-features module that registers a `postStartupActivity` whose constructor is package-private; the test fixture instantiates extensions through a path that requires an accessible constructor, so the activity cannot be created and *every* test that opens a project fails with `Cannot find suitable constructor for class ...`. The `test` task therefore points the platform at a disabled-plugins list via the `disabled.plugins.file.path` system property. The IDE sandbox is untouched — the module is required there.
- **`PluginId` is referenced fully qualified, not static-imported.** `PluginId.getId("com.intellij.java")` is used in `PluginSettingsState` and `SettingsAdditionalSupportView`; do not reintroduce the `import com.intellij.openapi.extensions.PluginId.getId` / `.findId` form — it does not resolve against the pinned local IDE.
- **Optional plugin dependencies** (`com.intellij.java`, `org.jetbrains.kotlin`) are declared in `plugin.xml` with separate config files (`*-withJava.xml`, `*-withKotlin.xml`). Register new extension points in the matching file.

## PR & commit conventions

- Branch from `main`; CI runs on pushes and PRs touching `src/`, `generate/`, Gradle files, or the pnpm manifests.
- Conventional commit prefixes (`feat:`, `fix:`, `docs:`, `refactor:`). The release workflow commits the changelog as `docs: update changelog`.
- Changelog lives in `CHANGELOG.md` and is rendered by the Gradle changelog plugin; keep the `[Unreleased]` section and the group order (Added, Changed, Deprecated, Removed, Fixed, Security).
- Releases are cut by pushing a `v*` tag — `release.yml` publishes to the JetBrains Marketplace and opens a draft GitHub release.

## Security

- Signing and publishing credentials come from environment variables only: `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`, `PUBLISH_TOKEN`. Never commit, echo, or log them.
- Do not run `signPlugin` / `publishPlugin` outside the release workflow.

## Code navigation

This repo is indexed by [codegraph](https://github.com/colbymchenry/codegraph) (69 files, 515 nodes).

- Use the `codegraph_explore` MCP tool to understand how a symbol works, find its callers, or see what a change would affect — prefer it over grep/read loops for navigation.
- After editing code, refresh the index with `codegraph sync`; use `codegraph index` for a full rebuild and `codegraph status` to check freshness.
- The index respects `.gitignore`, so the generated `Icons.kt` and the 2600+ icon resources stay out of it — the graph covers hand-written code only.
- The `.codegraph/.gitignore` file is safe to commit; the index database stays local and untracked.
