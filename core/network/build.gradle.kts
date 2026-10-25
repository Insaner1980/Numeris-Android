plugins {
    id("numeris.android.library")
    id("numeris.quality")
}

android { namespace = "com.emmah.numeris.core.network" }

dependencies {
    api(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
