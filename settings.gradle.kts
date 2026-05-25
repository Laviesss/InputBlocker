pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://api.xposed.info/")
    }
}

rootProject.name = "InputBlockerKMP"
include(":shared")
include(":pc-tool-kotlin")
include(":android-app:app")
