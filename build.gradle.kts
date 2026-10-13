buildscript {
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

    configurations.classpath {
        resolutionStrategy.eachDependency {
            val secureVersion = securityBuildModules["${requested.group}:${requested.name}"]
            if (secureVersion != null) {
                useVersion(secureVersion)
                because("Use patched build-tool dependencies reported by OSV.")
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.owasp.dependency.check) apply false
}

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

allprojects {
    buildscript.configurations.configureEach {
        resolutionStrategy.eachDependency {
            val secureVersion = securityBuildModules["${requested.group}:${requested.name}"]
            if (secureVersion != null) {
                useVersion(secureVersion)
                because("Use patched build-tool dependencies reported by OSV.")
            }
        }
    }
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            val secureVersion = securityBuildModules["${requested.group}:${requested.name}"]
            if (secureVersion != null) {
                useVersion(secureVersion)
                because("Use patched build-tool dependencies reported by OSV.")
            }
        }
    }
}

tasks.register("detektCheck") {
    group = "verification"
    description = "Runs Detekt in every module."
    dependsOn(provider { subprojects.mapNotNull { it.tasks.findByName("detekt") } })
}

tasks.register("ktlintCheck") {
    group = "verification"
    description = "Runs ktlint in every module."
    dependsOn(provider { subprojects.mapNotNull { it.tasks.findByName("ktlintCheck") } })
}

sonar {
    properties {
        val settings = java.util.Properties()
        rootProject.file("sonar-project.properties").reader(Charsets.UTF_8).use(settings::load)
        settings.forEach { key, value -> property(key.toString(), value.toString()) }
    }
}
