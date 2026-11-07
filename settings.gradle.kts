pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "pushizo-kmp"

// Monorepo'da Android SDK kaynaktan derlenir; yayımlanan POM yine `com.pushizo:pushizo-android` koordinatını taşır. Açık
// `pushizo-kmp` deposunda `../android` yoktur ve bağımlılık Maven Central'dan gelir.
if (file("../android/settings.gradle.kts").exists()) {
    includeBuild("../android") {
        dependencySubstitution {
            substitute(module("com.pushizo:pushizo-android")).using(project(":pushizo"))
        }
    }
}
