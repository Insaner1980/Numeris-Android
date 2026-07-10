plugins {
    id("numeris.kotlin.library")
    id("numeris.quality")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
