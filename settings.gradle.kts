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

// Android SDK kaynaktan derlenir; yayımlanan POM yine `com.pushizo:pushizo-android` koordinatını taşır.
includeBuild("../android") {
    dependencySubstitution {
        substitute(module("com.pushizo:pushizo-android")).using(project(":pushizo"))
    }
}
