plugins {
    id("numeris.android.application")
    id("numeris.quality")
    id("org.jetbrains.kotlin.plugin.serialization")
    alias(libs.plugins.owasp.dependency.check)
}

android {
    namespace = "com.emmah.numeris"

    defaultConfig {
        applicationId = "com.emmah.numeris"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    lint {
        checkDependencies = true
    }
}

dependencyCheck {
    formats = listOf("HTML", "JSON", "SARIF")
    outputDirectory = rootProject.layout.projectDirectory.dir("reports")
    data {
        directory =
            providers
                .environmentVariable("DEPENDENCY_CHECK_DATA_DIRECTORY")
                .orElse(
                    rootProject.layout.projectDirectory
                        .dir(".gradle/dependency-check-data")
                        .asFile
                        .absolutePath,
                ).get()
    }
    autoUpdate =
        providers
            .environmentVariable("DEPENDENCY_CHECK_AUTO_UPDATE")
            .map { it.equals("true", ignoreCase = true) || it == "1" || it.equals("yes", ignoreCase = true) }
            .getOrElse(true)
    failBuildOnCVSS =
        providers
            .environmentVariable("DEPENDENCY_CHECK_FAIL_BUILD_ON_CVSS")
            .map { it.toFloatOrNull() ?: 7f }
            .getOrElse(7f)
    scanConfigurations = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath")
    skipTestGroups = true
    analyzers {
        ossIndex {
            enabled = false
        }
    }
    nvd {
        providers.environmentVariable("NVD_API_KEY").orNull?.let { apiKey = it }
        delay =
            providers
                .environmentVariable("NVD_API_DELAY_MS")
                .map { it.toIntOrNull() ?: 6_000 }
                .getOrElse(6_000)
        maxRetryCount =
            providers
                .environmentVariable("NVD_API_MAX_RETRY_COUNT")
                .map { it.toIntOrNull() ?: 5 }
                .getOrElse(5)
    }
}

dependencies {
    constraints {
        implementation(libs.androidx.concurrent.futures) {
            because("Keep the application and Android test runtime on compatible Concurrent versions.")
        }
        implementation(libs.androidx.concurrent.futures.ktx) {
            because("Keep the application and Android test runtime on compatible Concurrent versions.")
        }
    }
    lintChecks(libs.android.security.lints)
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(project(":data"))
    implementation(project(":feature:overview"))
    implementation(project(":feature:traffic"))
    implementation(project(":feature:search"))
    implementation(project(":feature:performance"))
    implementation(project(":feature:health"))
    implementation(project(":feature:sources"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
