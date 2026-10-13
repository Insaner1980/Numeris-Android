import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidComposeLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("com.github.skydoves.compose.stability.analyzer")
        configureComposeStability()
        extensions.configure<LibraryExtension> {
            configureAndroid(this)
            buildFeatures.compose = true
        }
    }
}
