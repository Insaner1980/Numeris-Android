pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NumerisAndroid"

includeBuild("build-logic")

include(
    ":app",
    ":core:model",
    ":core:common",
    ":core:designsystem",
    ":core:database",
    ":core:network",
    ":core:security",
    ":core:testing",
    ":data",
    ":feature:overview",
    ":feature:traffic",
    ":feature:search",
    ":feature:performance",
    ":feature:health",
    ":feature:sources",
)
