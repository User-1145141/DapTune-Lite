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

rootProject.name = "DapTune"

include(
    ":app",
    ":core:model",
    ":core:eq",
    ":core:designsystem",
    ":domain",
    ":data",
    ":platform:dap",
    ":platform:routing",
    ":feature:editor",
    ":feature:profiles",
    ":feature:automation",
    ":feature:about",
)
