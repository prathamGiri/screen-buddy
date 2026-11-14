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
rootProject.name = "screen-buddy"
include(":core")
// The app module needs the Android SDK; skip it with -PnoAndroid to work on :core alone.
if (!providers.gradleProperty("noAndroid").isPresent) include(":app")
