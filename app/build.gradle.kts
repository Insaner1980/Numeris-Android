plugins {
    id("numeris.android.application")
    id("numeris.quality")
    id("org.jetbrains.kotlin.plugin.serialization")
    alias(libs.plugins.owasp.dependency.check)
}

android {
    namespace = "com.emmah.numeris"
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("test-fixtures/parity"))
    sourceSets.getByName("test").apply {
        kotlin.directories.add(file("src/androidTest/java").absolutePath)
        resources.directories.add(rootProject.file("test-fixtures/parity").absolutePath)
        resources.directories.add(rootProject.file("core/database/schemas").absolutePath)
        resources.directories.add(rootProject.file("config/compose").absolutePath)
    }
    testOptions.unitTests.isIncludeAndroidResources = true

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

// Yksi lähde OWASP-tietokannan polulle: security-check antaa sen
// DEPENDENCY_CHECK_DATA_DIRECTORY-muuttujassa, muuten käytetään projektin omaa oletusta.
val dependencyCheckDataDirectory: String =
    providers
        .environmentVariable("DEPENDENCY_CHECK_DATA_DIRECTORY")
        .orElse(
            rootProject.layout.projectDirectory
                .dir(".gradle/dependency-check-data")
                .asFile
                .absolutePath,
        ).get()

dependencyCheck {
    formats = listOf("HTML", "JSON", "SARIF")
    outputDirectory = rootProject.layout.projectDirectory.dir("reports")
    data {
        directory = dependencyCheckDataDirectory
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
    suppressionFiles = listOf(rootProject.file("config/dependency-check/suppressions.xml").absolutePath)
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

// Plugin 13.0.0: DataExtension asettaa hakemiston jo konstruktorissaan set()-kutsulla, joten
// ConfiguredTaskin convention(defaults.data.directory) ei pure eikä yllä oleva data.directory
// mene perille — tietokanta päätyisi polkuun $GRADLE_USER_HOME/dependency-check-data/11.0.
// Asetetaan sama polku suoraan taskeille. Korjattu upstreamissa, poistettavissa kun julkaistaan.
tasks.withType<org.owasp.dependencycheck.gradle.tasks.ConfiguredTask>().configureEach {
    data.directory.set(dependencyCheckDataDirectory)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.okhttp)
    testImplementation(libs.play.services.auth)
    constraints {
        implementation(libs.androidx.concurrent.futures) {
            because("Keep the application and Android test runtime on compatible Concurrent versions.")
        }
        implementation(libs.androidx.concurrent.futures.ktx) {
            because("Keep the application and Android test runtime on compatible Concurrent versions.")
        }
    }
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.okhttp)
    androidTestImplementation(libs.play.services.auth)
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
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqlite.framework)
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.withType<Test>().configureEach {
    // App unit tests use *HostTest entry points; shared androidTest classes run through AndroidHostSuite.
    include("**/*HostTest.class")
    maxParallelForks = 1
    maxHeapSize = "1g"
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.security=ALL-UNNAMED",
        "--add-opens=java.base/java.text=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    )
}
