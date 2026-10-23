import com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask
import com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.date
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = project.findProperty(key).toString()

plugins {
  // Java support
  id("java")
  // Kotlin support
  id("org.jetbrains.kotlin.jvm") version "2.4.21"
  // Gradle IntelliJ Plugin
  id("org.jetbrains.intellij.platform") version "2.18.1"
  // Gradle Changelog Plugin
  id("org.jetbrains.changelog") version "2.5.0"

  // Code Quality
  // ktfmt
  id("com.ncorti.ktfmt.gradle") version "0.27.0"
  // detekt
  id("io.gitlab.arturbosch.detekt").version("1.23.8")

  // Kotlin Serialization
  id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21"
}

group = properties("pluginGroup")

version = properties("pluginVersion")

repositories {
  maven { url = uri("https://maven.aliyun.com/repository/public/") }
  mavenCentral()
  intellijPlatform { defaultRepositories() }
}

dependencies {
  intellijPlatform {
    local("D:\\Program Files\\JetBrains\\IntelliJ IDEA Community Edition")
    bundledPlugins(providers.gradleProperty("platformPlugins").map { it.split(',') })
    pluginVerifier()
    testFramework(TestFrameworkType.Platform)
    testFramework(TestFrameworkType.Plugin.Java)
  }

  testImplementation(platform("org.junit:junit-bom:6.1.3"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher") {
    because("Only needed to run tests in a version of IntelliJ IDEA that bundles older versions")
  }
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testRuntimeOnly("org.junit.vintage:junit-vintage-engine")

  // kotlinx-serialization for JSONC
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

intellijPlatform {
  pluginConfiguration {
    id.set(properties("pluginGroup"))
    name.set(properties("pluginName"))
    version.set(properties("pluginVersion"))

    changelog {
      version.set(properties("pluginVersion"))
      path.set(file("CHANGELOG.md").canonicalPath)
      header.set(provider { "${version.get()} - ${date()}" })
      headerParserRegex.set("""(\d\.\d+\.\d+)""".toRegex())
      itemPrefix.set("-")
      keepUnreleasedSection.set(true)
      unreleasedTerm.set("[Unreleased]")
      groups.set(listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"))
    }
  }

  pluginVerification { ides { recommended() } }
}

intellijPlatformTesting {
  runIde {
    register("runLatestIde") {
      // The separate Community (IC) distribution is no longer published since 2025.3;
      // the unified IntelliJ IDEA distribution replaces it.
      type = IntelliJPlatformType.IntellijIdea
      version = "2026.1.4"
      task { jvmArgs("-Xmx2048m") }
    }
  }
}

tasks {
  // Set the JVM compatibility versions
  properties("javaVersion").let {
    withType<JavaCompile> {
      sourceCompatibility = it
      targetCompatibility = it
    }
    withType<KotlinCompile> {
      compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_2
        jvmTarget = JvmTarget.fromTarget(properties("javaVersion"))
      }
    }
  }

  wrapper { gradleVersion = "9.8.1" }

  // Keep the sandbox heap above the 750MB threshold of MemorySizeConfigurator, which otherwise
  // fails with an IOException trying to write vmoptions the sandbox does not have.
  runIde { jvmArgs("-Xmx2048m") }

  patchPluginXml {
    pluginVersion.set(properties("pluginVersion"))
    sinceBuild.set(properties("pluginSinceBuild"))
    untilBuild.set(properties("pluginUntilBuild"))

    // Get the latest available change notes from the changelog file
    changeNotes.set(
      provider { changelog.renderItem(changelog.getLatest(), Changelog.OutputType.HTML) }
    )
  }

  test {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }

    // IntelliJ IDEA Ultimate — the local install this project builds against — ships a
    // paid-features module (com.intellij.modules.ultimate) that registers a postStartupActivity
    // with a package-private constructor. The test fixture instantiates extensions through a
    // path that requires an accessible constructor, so the activity cannot be created and every
    // test that opens a project fails with "Cannot find suitable constructor for class ...".
    // Point the fixture at a disabled-plugins list that excludes that module. The IDE sandbox is
    // left untouched, because the module is required there.
    val disabledPluginsFile = project.layout.buildDirectory.file("test-disabled-plugins.txt")
    doFirst {
      disabledPluginsFile.get().asFile.apply {
        parentFile.mkdirs()
        writeText("com.intellij.modules.ultimate\n")
      }
    }
    systemProperty("disabled.plugins.file.path", disabledPluginsFile.get().asFile.absolutePath)
  }

  buildPlugin { dependsOn(test) }

  signPlugin {
    certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
    privateKey.set(System.getenv("PRIVATE_KEY"))
    password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
  }

  publishPlugin {
    dependsOn("patchChangelog")
    token.set(System.getenv("PUBLISH_TOKEN"))
    channels.set(listOf("default"))
  }
}

tasks.buildSearchableOptions { enabled = false }

// Code quality settings

// Formatting settings
ktfmt { googleStyle() }

// This is used over the "ktfmtCheck" task to exclude the autogenerated file Icons.kt.
// The source tree is scoped to "src" so that the task does not pick up build outputs and
// sandbox files, which Gradle rejects as implicit task dependencies.
tasks.register<KtfmtCheckTask>("checkFormatting") {
  source = project.fileTree("src")
  include("**/*.kt")
  exclude("**/Icons.kt")
}

// Counterpart of "checkFormatting": formats the hand-written sources without touching the
// autogenerated Icons.kt. The plugin's own "ktfmtFormat" task works per Kotlin source set and
// would reformat the generated file.
tasks.register<KtfmtFormatTask>("format") {
  source = project.fileTree("src")
  include("**/*.kt")
  exclude("**/Icons.kt")
}

// Static Analysis config
detekt {
  parallel = true
  config.setFrom("detekt.yaml")
  buildUponDefaultConfig = true
}

// This is used over the "detekt" task to exclude the autogenerated file Icons.kt.
tasks.withType<Detekt>().configureEach {
  source = project.fileTree("src")
  include("**/*.kt")
  exclude("**/Icons.kt")
}

tasks.register<Detekt>("runStaticAnalysis") {
  parallel = true
  config.setFrom("detekt.yaml")
  buildUponDefaultConfig = true

  source = project.fileTree("src")
  include("**/*.kt")
  exclude("**/Icons.kt")
}
