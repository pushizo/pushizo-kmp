import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.2.21"
    id("com.android.library") version "8.7.3"
    `maven-publish`
    signing
}

group = "com.pushizo"
version = "0.3.0"

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
        // Maven Central: yalnızca release varyantı yayımlanır (`pushizo-kmp-android`).
        publishLibraryVariants("release")
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
            api("com.pushizo:pushizo-android:0.3.0")
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

// ---------------------------------------------------------------------------------------------------------------------
// Maven Central yayını (plan 13 Faz 30). KMP eklentisi her hedef için yayın üretir: `pushizo-kmp` (kök, Gradle modül
// meta verisi), `pushizo-kmp-android`, `pushizo-kmp-iosarm64`, `pushizo-kmp-iossimulatorarm64`, `pushizo-kmp-iosx64`.
// iOS klib'leri yalnızca macOS'ta üretilir; eksik paket Central'a gitmesin diye `mavenCentralBundle` macOS dışında durur.
// İmza ve paketleme Android SDK'sıyla aynıdır (sdk/android/pushizo/build.gradle.kts, docs/sdk/publishing.md).

val mavenCentralStagingDir = layout.buildDirectory.dir("maven-central/staging")

publishing {
    publications.withType<MavenPublication>().configureEach {
        // Central her bileşende -javadoc jar'ı ister; Kotlin kaynaklı paket için boş jar kabul edilir.
        val publicationName = name
        artifact(tasks.register<Jar>("${publicationName}JavadocJar") {
            archiveClassifier.set("javadoc")
            archiveAppendix.set(publicationName)
        })
        pom {
            name.set("Pushizo Kotlin Multiplatform SDK")
            description.set("Pushizo KMP SDK: ortak Kotlin kodundan yerel Android ve iOS SDK'larını çağırır (push, kullanıcı, olaylar, in-app, gelen kutusu)")
            url.set("https://docs.pushizo.com/docs/sdk/kmp")
            licenses {
                license {
                    name.set("MIT")
                    url.set("https://github.com/pushizo/pushizo-kmp/blob/main/LICENSE")
                }
            }
            developers {
                developer {
                    id.set("pushizo")
                    name.set("Pushizo")
                    url.set("https://github.com/pushizo/pushizo-kmp")
                }
            }
            scm {
                url.set("https://github.com/pushizo/pushizo-kmp")
                connection.set("scm:git:https://github.com/pushizo/pushizo-kmp.git")
                developerConnection.set("scm:git:ssh://git@github.com/pushizo/pushizo-kmp.git")
            }
        }
    }
    repositories {
        maven {
            name = "MavenCentralStaging"
            url = uri(mavenCentralStagingDir)
        }
    }
}

// İmza anahtarı yalnızca yayında verilir (ORG_GRADLE_PROJECT_signingInMemoryKey / signingInMemoryKeyPassword).
val signingKey: String? = providers.gradleProperty("signingInMemoryKey").orNull?.takeIf { it.isNotBlank() }
val signingKeyPassword: String? = providers.gradleProperty("signingInMemoryKeyPassword").orNull

signing {
    isRequired = signingKey != null
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingKeyPassword)
        sign(publishing.publications)
    }
}

val cleanMavenCentralStaging by tasks.registering(Delete::class) {
    delete(mavenCentralStagingDir)
}

tasks.withType<PublishToMavenRepository>().configureEach {
    // Her yayın görevi tüm imzaları kullanır (KMP yayınları ortak kök meta veriye bağlıdır).
    dependsOn(tasks.withType<Sign>())
    if (name.endsWith("ToMavenCentralStagingRepository")) dependsOn(cleanMavenCentralStaging)
}

val isMacHost = System.getProperty("os.name").startsWith("Mac")

tasks.register<Zip>("mavenCentralBundle") {
    group = "publishing"
    description = "Maven Central (Central Portal) için tüm hedefleri içeren imzalı yükleme paketini üretir (macOS)."
    dependsOn("publishAllPublicationsToMavenCentralStagingRepository")
    from(mavenCentralStagingDir)
    exclude("**/maven-metadata*", "**/*.asc.md5", "**/*.asc.sha1", "**/*.asc.sha256", "**/*.asc.sha512")
    destinationDirectory.set(layout.buildDirectory.dir("maven-central"))
    archiveFileName.set("pushizo-kmp-central-bundle.zip")
    val signed = signingKey != null
    doFirst {
        if (!isMacHost) throw GradleException("pushizo-kmp iOS klib'leri macOS ister; Maven Central paketi yalnızca macOS'ta üretilir.")
        if (!signed) {
            throw GradleException(
                "Maven Central paketi imzalı olmalı: signingInMemoryKey ve signingInMemoryKeyPassword Gradle özelliklerini verin.",
            )
        }
    }
}
