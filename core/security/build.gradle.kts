plugins {
    id("numeris.android.library")
    id("numeris.quality")
}

android { namespace = "com.emmah.numeris.core.security" }

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    api(libs.androidx.biometric)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
