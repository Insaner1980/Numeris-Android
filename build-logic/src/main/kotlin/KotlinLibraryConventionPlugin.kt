import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("jacoco")
        tasks.withType<JacocoReport>().configureEach {
            dependsOn("test")
            reports.xml.required.set(true)
        }
        extensions.configure(KotlinJvmProjectExtension::class.java) {
            jvmToolchain(17)
            compilerOptions.allWarningsAsErrors.set(true)
        }
    }
}
