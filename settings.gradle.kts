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
include(":core:matching")
include(":core:database")
include(":core:files")
include(":core:data")
include(":core:designsystem")
include(":core:network")
include(":core:datastore")
include(":feature:auth")
include(":feature:scan")
include(":feature:home")
include(":feature:drugs")
include(":feature:importexport")
include(":feature:history")
include(":feature:support")
include(":feature:search")
include(":feature:settings")
