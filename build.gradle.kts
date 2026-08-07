plugins {
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.owasp.dependency.check) apply false
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
