import org.gradle.api.Plugin
import org.gradle.api.Project

class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = target.configureQuality()
}
