pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Duello Quiz"

include(":app")

include(":core:auth")
include(":core:common")
include(":core:data")
include(":core:datastore")
include(":core:designsystem")
include(":core:duel")
include(":core:model")
include(":core:network")
include(":core:testing")

include(":feature:auth")
include(":feature:duel")
include(":feature:home")
include(":feature:profile")
include(":feature:quiz")
include(":feature:settings")
