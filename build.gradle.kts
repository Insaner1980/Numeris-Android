import com.android.build.api.dsl.ApplicationExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import javax.inject.Inject

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
    jacoco
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

subprojects {
    sonar {
        properties {
            property(
                "sonar.coverage.jacoco.xmlReportPaths",
                "${rootProject.layout.buildDirectory.get()}/reports/jacoco/coverage/coverage.xml",
            )
        }
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

abstract class InstrumentHostCoverage : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classDirectories: ConfigurableFileCollection

    @get:Classpath
    abstract val antClasspath: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun instrument() {
        fileSystemOperations.delete { delete(destinationDirectory) }
        ant.withGroovyBuilder {
            "taskdef"(
                "name" to "instrument",
                "classname" to "org.jacoco.ant.InstrumentTask",
                "classpath" to antClasspath.asPath,
            )
            "instrument"("destdir" to destinationDirectory.get().asFile.path) {
                classDirectories.filter { it.isDirectory }.forEach { directory ->
                    "fileset"("dir" to directory.path) {
                        "include"("name" to "com/emmah/numeris/**/*.class")
                    }
                }
            }
        }
    }
}

val jacocoOfflineAnt = configurations.create("jacocoOfflineAnt")
dependencies.add(jacocoOfflineAnt.name, libs.jacoco.ant)

val coverageClassDirectories =
    files(
        subprojects.flatMap { module ->
            listOf(
                module.layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
                module.layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
                module.layout.buildDirectory.dir("classes/kotlin/main"),
            )
        },
    )
val hostCoverageClasses = layout.buildDirectory.dir("jacoco/host-classes")
val instrumentHostCoverage =
    tasks.register<InstrumentHostCoverage>("instrumentHostCoverage") {
        group = "verification"
        description = "Instruments application classes before Robolectric transforms them."
        dependsOn(
            provider {
                subprojects.flatMap { module ->
                    listOf("compileDebugKotlin", "compileDebugJavaWithJavac", "compileKotlin")
                        .mapNotNull(module.tasks::findByName)
                }
            },
        )
        classDirectories.from(coverageClassDirectories)
        antClasspath.from(jacocoOfflineAnt)
        destinationDirectory.set(hostCoverageClasses)
    }

project(":app") {
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            testOptions.unitTests.all { test ->
                if (test.name == "testDebugUnitTest") {
                    test.dependsOn(instrumentHostCoverage)
                    test.classpath = files(hostCoverageClasses) + test.classpath
                }
            }
        }
    }
}

tasks.register<JacocoReport>("coverageReport") {
    group = "verification"
    description = "Combines JVM and host Android test coverage without an emulator."
    dependsOn(
        ":app:testDebugUnitTest",
        ":core:model:test",
        ":core:network:testDebugUnitTest",
        ":core:security:testDebugUnitTest",
        ":data:testDebugUnitTest",
    )
    classDirectories.setFrom(coverageClassDirectories)
    sourceDirectories.setFrom(subprojects.flatMap { listOf(it.file("src/main/kotlin"), it.file("src/main/java")) })
    executionData.setFrom(
        subprojects.map {
            it.fileTree(it.layout.buildDirectory) {
                include("jacoco/test.exec", "outputs/unit_test_code_coverage/debugUnitTest/*.exec")
            }
        },
    )
    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/coverage/coverage.xml"))
        html.required.set(true)
    }
}
