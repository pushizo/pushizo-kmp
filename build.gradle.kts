import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.2.21"
    id("com.android.library") version "8.7.3"
}

group = "com.pushizo"
version = "0.1.0"

/**
 * iOS hedefleri Pushizo.xcframework'ün Objective-C cephesini cinterop ile sarar (plan 13 Faz 29–30). Framework
 * `sdk/ios/scripts/build-xcframework.sh` ile üretilir; yol `-Ppushizo.xcframework=<yol>` ile değiştirilebilir. iOS
 * hedefleri yalnızca macOS'ta derlenir; framework yoksa iOS derleme görevleri açık bir hatayla durur.
 */
val pushizoXcframework: File = providers.gradleProperty("pushizo.xcframework").orNull?.let(::file)
    ?: rootDir.resolve("../ios/build/xcframework/Pushizo.xcframework")

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    listOf(
        iosArm64() to "ios-arm64",
        iosSimulatorArm64() to "ios-arm64_x86_64-simulator",
        iosX64() to "ios-arm64_x86_64-simulator",
    ).forEach { (target, slice) ->
        val frameworkDir = pushizoXcframework.resolve(slice)
        target.compilations.getByName("main").cinterops.create("Pushizo") {
            defFile(project.file("src/nativeInterop/cinterop/Pushizo.def"))
            compilerOpts("-F${frameworkDir.absolutePath}")
        }
        // Test ikilisi framework'e bağlanır ve simülatörde ondan yüklenir; uygulamalar framework'ü kendileri gömer.
        target.binaries.all {
            linkerOpts("-F${frameworkDir.absolutePath}", "-framework", "Pushizo", "-rpath", frameworkDir.absolutePath)
        }
    }

    compilerOptions {
        // expect/actual sınıflar: Android'de Context ve Activity türlerine bağlanır.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        androidMain.dependencies {
            // `api`: Inbox gibi ortak API dışındaki yerel özellikler androidMain'den doğrudan çağrılabilir.
            api("com.pushizo:pushizo-android:0.2.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.pushizo.kmp"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Saf JVM testleri: android.util.Log gibi çağrılar varsayılan değer döner.
        unitTests.isReturnDefaultValues = true
    }
}
