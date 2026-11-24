plugins {
    id("com.android.application")
}

val thirdPartyDir = file("../../third_party")
val sdlDir = file("../../third_party/SDL2")
val glslangDir = file("../../third_party/glslang")
val spvToolsDir = file("../../third_party/glslang/External/spirv-tools")
val spvHeadersDir = file("../../third_party/glslang/External/spirv-tools/external/spirv-headers")

val sdlTag = "release-2.32.10"
val khrTag = "vulkan-sdk-1.4.363.0"

tasks.register<Exec>("fetchSDL2") {
    onlyIf { !File(sdlDir, "CMakeLists.txt").exists() }
    doFirst { thirdPartyDir.mkdirs() }
    commandLine("git", "clone", "--depth", "1", "--branch", sdlTag, "https://github.com/libsdl-org/SDL.git", sdlDir.absolutePath)
}

tasks.register<Exec>("fetchGlslang") {
    onlyIf { !File(glslangDir, "CMakeLists.txt").exists() }
    doFirst { thirdPartyDir.mkdirs() }
    commandLine("git", "clone", "--depth", "1", "--branch", khrTag, "https://github.com/KhronosGroup/glslang.git", glslangDir.absolutePath)
}

tasks.register<Exec>("fetchSpirvTools") {
    dependsOn("fetchGlslang")
    onlyIf { !File(spvToolsDir, "CMakeLists.txt").exists() }
    commandLine("git", "clone", "--depth", "1", "--branch", khrTag, "https://github.com/KhronosGroup/SPIRV-Tools.git", spvToolsDir.absolutePath)
}

tasks.register<Exec>("fetchSpirvHeaders") {
    dependsOn("fetchSpirvTools")
    onlyIf { !File(spvHeadersDir, "CMakeLists.txt").exists() }
    commandLine("git", "clone", "--depth", "1", "--branch", khrTag, "https://github.com/KhronosGroup/SPIRV-Headers.git", spvHeadersDir.absolutePath)
}

tasks.register("fetchThirdParty") {
    dependsOn("fetchSDL2", "fetchSpirvHeaders")
}

tasks.named("preBuild") {
    dependsOn("fetchThirdParty")
}

android {
    namespace = "com.dod3recomp.android"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.dod3recomp.android"
        minSdk = 29
        targetSdk = 34
        versionCode = if (project.hasProperty("versionCode")) project.property("versionCode").toString().toInt() else 1
        versionName = if (project.hasProperty("versionName")) project.property("versionName").toString() else "0.5.0-android-alpha"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
                val smoke = if (project.hasProperty("dod3RuntimeSmoke")) project.property("dod3RuntimeSmoke").toString() else "ON"
                arguments += "-DDOD3_ANDROID_RUNTIME_SMOKE=$smoke"
                if (project.hasProperty("recompDir")) {
                    arguments += "-DRECOMP_DIR=${project.property("recompDir")}"
                }
                if (project.hasProperty("spuDir")) {
                    arguments += "-DSPU_DIR=${project.property("spuDir")}"
                }
                if (project.hasProperty("libsrePrx")) {
                    arguments += "-DLIBSRE_PRX=${project.property("libsrePrx")}"
                }
            }
        }
    }

    val ksPath = System.getenv("ANDROID_KEYSTORE_PATH")
    signingConfigs {
        if (!ksPath.isNullOrBlank()) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASS") ?: ""
                keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("ANDROID_KEY_PASS") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (!ksPath.isNullOrBlank()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            externalNativeBuild {
                cmake {
                    arguments += "-DCMAKE_BUILD_TYPE=Release"
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../../android_cmake/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir(File(sdlDir, "android-project/app/src/main/java"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
