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
        // 7-Zip-JBinding-4Android (extractor 7z / zip / rar) solo se publica en JitPack.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.omicronapps") }
        }
    }
}

rootProject.name = "TopPorts"
include(":app", ":core")
