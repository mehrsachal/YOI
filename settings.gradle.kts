pluginManagement { repositories { google(); gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "P-Quest"
include(":app", ":coords", ":cot", ":missions", ":mesh", ":symbology", ":map", ":security")
