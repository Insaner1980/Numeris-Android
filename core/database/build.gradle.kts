plugins {
    id("numeris.android.room")
}

android { namespace = "com.emmah.numeris.core.database" }

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    api(libs.room3.runtime)
    api(libs.sqlite.framework)
    ksp(libs.room3.compiler)
}
