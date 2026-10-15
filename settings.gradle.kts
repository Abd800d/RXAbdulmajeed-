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
include(":core:scanner")
include(":core:ai")
include(":core:designsystem")
include(":feature:auth")
include(":feature:scan")
include(":feature:home")
include(":feature:support")
