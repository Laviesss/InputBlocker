plugins {
    kotlin("multiplatform") version "1.9.20" apply false
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.compose") version "1.5.11" apply false
}

plugins {
    kotlin("multiplatform") version "1.9.20" apply false
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.compose") version "1.5.11" apply false
}

tasks.register<Zip>("buildModule") {
    group = "build"
    description = "Builds the Android APK and packs it into a flashable root module ZIP"
    
    // 1. Ensure the APK is built first
    dependsOn("buildAndroid")
    
    // 2. Pack the module metadata
    from("module") {
        into("/")
    }
    
    // 3. Pack the actual compiled APK as the module's core
    from("android-app/app/build/outputs/apk/release/") {
        include("*.apk")
        into("/")
    }
    
    archiveFileName.set("inputblocker.zip")
    destinationDirectory.set(file("build/distributions"))
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds the entire ecosystem: APK, EXE, and Module ZIP"
    dependsOn("buildAndroid", "buildPC", "buildModule")
}

tasks.register("buildAndroid") {
    group = "build"
    description = "Builds the Android APK"
    dependsOn(":android-app:app:assembleRelease")
}

tasks.register("buildPC") {
    group = "build"
    description = "Builds the PC Tool EXE"
    dependsOn(":pc-tool-kotlin:packageDistributionForCurrentOS")
}

tasks.register("buildShared") {
    group = "build"
    description = "Builds the Shared KMP library"
    dependsOn(":shared:assemble")
}

