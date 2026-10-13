import com.android.build.api.dsl.CommonExtension
import com.skydoves.compose.stability.gradle.StabilityAnalyzerExtension
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

internal fun Project.configureComposeStability() {
    val stabilityConfig = rootProject.layout.projectDirectory.file("config/compose/stability.conf")
    extensions.configure<ComposeCompilerGradlePluginExtension> {
        stabilityConfigurationFiles.add(stabilityConfig)
    }
    extensions.configure<StabilityAnalyzerExtension> {
        stabilityConfigurationFiles.add(stabilityConfig)
    }
}

internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = 37
        defaultConfig.minSdk = 28
        buildTypes.getByName("debug").enableUnitTestCoverage = true
        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        lint.apply {
            abortOnError = true
            warningsAsErrors = true
            disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
        }
    }
}

internal fun Project.configureQuality() {
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")
    pluginManager.apply("dev.detekt")

    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    dependencies.add("detektPlugins", libs.findLibrary("compose-rules-detekt").get())
    dependencies.add("ktlintRuleset", libs.findLibrary("compose-rules-ktlint").get())

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        parallel.set(true)
    }
}
