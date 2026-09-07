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

rootProject.name = "KivoArchive"

include(
    ":app",
    ":core:model",
    ":core:data",
    ":core:designsystem",
    ":core:content",
    ":core:media",
    ":feature:home",
    ":feature:account",
    ":feature:character",
    ":feature:organization",
)
