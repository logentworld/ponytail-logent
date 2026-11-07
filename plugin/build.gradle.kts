import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val pluginRepository = projectDir.parentFile
val pluginVersion = pluginRepository.resolve("VERSION").readText().trim()
version = pluginVersion
val apiPin = Properties().apply {
    pluginRepository.resolve("plugin-api.lock").inputStream().use { load(it) }
}

android {
    namespace = "com.sleepysoong.logent.ponytail"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "PLUGIN_VERSION", "\"$pluginVersion\"")
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// 원본 스킬 트리를 그대로 자산 스냅샷으로 만들어 테스트와 패키징이 같은 바이트를 써요.
val stagedAssets = tasks.register<Sync>("stagePluginAssets") {
    from(layout.projectDirectory.dir("src/main/assets"))
    into(layout.buildDirectory.dir("staged/pluginAssets"))
}
apply(from = pluginRepository.resolve("scripts/artifacts.gradle.kts"))

dependencies {
    implementation("${apiPin.getProperty("group")}:${apiPin.getProperty("artifact")}:${apiPin.getProperty("version")}")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    dependsOn(stagedAssets)
    systemProperty(
        "logent.plugin.assets",
        layout.buildDirectory.dir("staged/pluginAssets").get().asFile.absolutePath,
    )
    systemProperty(
        "logent.ponytail.assets",
        layout.projectDirectory.dir("src/main/assets").asFile.absolutePath,
    )
    maxParallelForks = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
}
