plugins {
    id("numeris.android.compose.library")
    id("numeris.quality")
}

android { namespace = "com.emmah.numeris.feature.search" }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":data"))
}
