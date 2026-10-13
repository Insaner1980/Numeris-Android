plugins {
    id("numeris.android.compose.library")
    id("numeris.quality")
}

android { namespace = "com.emmah.numeris.core.designsystem" }

dependencies {
    implementation(project(":core:model"))
    implementation(libs.vico.compose)
    api(libs.androidx.compose.ui)
    api(libs.androidx.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
