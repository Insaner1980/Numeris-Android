import androidx.room3.gradle.RoomExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("androidx.room3")
        extensions.configure<LibraryExtension> {
            configureAndroid(this)
        }
        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }
        configureQuality()
    }
}
