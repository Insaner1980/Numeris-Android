plugins {
    id("numeris.kotlin.library")
    id("numeris.quality")
}

dependencies {
    api(project(":core:model"))
    api(libs.junit)
}
