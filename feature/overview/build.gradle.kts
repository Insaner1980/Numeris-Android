plugins {
    id("numeris.android.compose.library")
    id("numeris.quality")
}

android { namespace = "com.emmah.numeris.feature.overview" }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":data"))
    implementation(libs.androidx.lifecycle.runtime.compose)
}
