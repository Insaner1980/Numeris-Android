plugins {
    id("numeris.android.compose.library")
    id("numeris.quality")
}

android { namespace = "com.emmah.numeris.feature.sources" }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":data"))
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
}
