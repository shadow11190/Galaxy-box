pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        exclusiveContent {
            forRepository { mavenCentral() }
            filter { includeGroup("dev.rikka.shizuku") }
        }
    }
}
rootProject.name = "GalaxyBox"
include(":app")
