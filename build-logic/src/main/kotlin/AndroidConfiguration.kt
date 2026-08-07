import com.android.build.api.dsl.CommonExtension
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = 37
        defaultConfig.minSdk = 28
        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        lint.apply {
            abortOnError = true
            warningsAsErrors = true
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
