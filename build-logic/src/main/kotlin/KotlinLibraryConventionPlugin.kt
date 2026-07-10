import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure(KotlinJvmProjectExtension::class.java) {
            jvmToolchain(17)
            compilerOptions.allWarningsAsErrors.set(true)
        }
    }
}
