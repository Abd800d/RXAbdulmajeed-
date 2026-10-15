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
rootProject.name = "RxReader"
include(":app")
include(":core:auth")
include(":core:designsystem")
include(":feature:auth")
include(":feature:home")
include(":feature:support")
