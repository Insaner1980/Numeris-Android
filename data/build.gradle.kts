plugins {
    id("numeris.android.library")
    id("numeris.quality")
}

android { namespace = "com.emmah.numeris.data" }

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.serialization.json)
}
