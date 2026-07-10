plugins {
    `kotlin-dsl`
}

group = "com.emmah.numeris.buildlogic"

dependencies {
    implementation(libs.plugins.android.application.toDependency())
    implementation(libs.plugins.kotlin.compose.toDependency())
    implementation(libs.plugins.detekt.toDependency())
    implementation(libs.plugins.ktlint.toDependency())
    implementation(libs.plugins.ksp.toDependency())
    implementation(libs.plugins.room3.toDependency())
}

fun Provider<PluginDependency>.toDependency(): Provider<String> = map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "numeris.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "numeris.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidComposeLibrary") {
            id = "numeris.android.compose.library"
            implementationClass = "AndroidComposeLibraryConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "numeris.kotlin.library"
            implementationClass = "KotlinLibraryConventionPlugin"
        }
        register("quality") {
            id = "numeris.quality"
            implementationClass = "QualityConventionPlugin"
        }
        register("androidRoom") {
            id = "numeris.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
    }
}
