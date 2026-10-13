plugins {
    id("numeris.kotlin.library")
    id("numeris.quality")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}

sourceSets.test { resources.srcDir(rootProject.file("test-fixtures/parity")) }
