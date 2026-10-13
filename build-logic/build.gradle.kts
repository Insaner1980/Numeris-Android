plugins {
    `kotlin-dsl`
}

group = "com.emmah.numeris.buildlogic"

val securityBuildModules =
    mapOf(
        "com.fasterxml.jackson.core:jackson-core" to libs.versions.buildJackson.get(),
        "com.fasterxml.jackson.core:jackson-databind" to libs.versions.buildJackson.get(),
        "org.jsoup:jsoup" to libs.versions.buildJsoup.get(),
        "org.bouncycastle:bcpkix-jdk18on" to libs.versions.buildBouncyCastle.get(),
        "org.bouncycastle:bcprov-jdk18on" to libs.versions.buildBouncyCastle.get(),
        "org.bouncycastle:bcutil-jdk18on" to libs.versions.buildBouncyCastle.get(),
        "org.bitbucket.b_c:jose4j" to libs.versions.buildJose4j.get(),
        "org.jdom:jdom2" to libs.versions.buildJdom.get(),
        "org.apache.commons:commons-lang3" to libs.versions.buildCommonsLang.get(),
        "io.netty:netty-codec" to libs.versions.buildNetty.get(),
        "io.netty:netty-codec-http" to libs.versions.buildNetty.get(),
        "io.netty:netty-codec-http2" to libs.versions.buildNetty.get(),
        "io.netty:netty-common" to libs.versions.buildNetty.get(),
        "io.netty:netty-handler" to libs.versions.buildNetty.get(),
        "io.netty:netty-handler-proxy" to libs.versions.buildNetty.get(),
        "ch.qos.logback:logback-core" to libs.versions.buildLogback.get(),
        "ch.qos.logback:logback-classic" to libs.versions.buildLogback.get(),
        "org.apache.httpcomponents:httpclient" to libs.versions.buildHttpclient.get(),
    )

configurations.configureEach {
    resolutionStrategy.eachDependency {
        val secureVersion = securityBuildModules["${requested.group}:${requested.name}"]
        if (secureVersion != null) {
            useVersion(secureVersion)
            because("Use patched build-tool dependencies reported by OSV.")
        }
    }
}

dependencies {
    implementation(libs.plugins.android.application.toDependency())
    implementation(libs.plugins.kotlin.compose.toDependency())
    implementation(libs.plugins.detekt.toDependency())
    implementation(libs.plugins.ktlint.toDependency())
    implementation(libs.plugins.ksp.toDependency())
    implementation(libs.plugins.room3.toDependency())
    implementation(libs.plugins.stability.analyzer.toDependency())
}

fun Provider<PluginDependency>.toDependency(): Provider<String> = map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "numeris.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "numeris.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidComposeLibrary") {
            id = "numeris.android.compose.library"
            implementationClass = "AndroidComposeLibraryConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "numeris.kotlin.library"
            implementationClass = "KotlinLibraryConventionPlugin"
        }
        register("quality") {
            id = "numeris.quality"
            implementationClass = "QualityConventionPlugin"
        }
        register("androidRoom") {
            id = "numeris.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }
    }
}
