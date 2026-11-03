import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.0.21"
    id("com.android.library") version "8.7.3"
}

group = "com.pushizo"
version = "0.1.0"

@OptIn(ExperimentalKotlinGradlePluginApi::class)
kotlin {
    // iOS hedefleri (iosArm64, iosSimulatorArm64, iosX64) Objective-C köprüsüyle (Faz 29) macOS'ta eklenir.
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
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
