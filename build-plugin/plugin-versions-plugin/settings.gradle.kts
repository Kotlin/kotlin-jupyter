@file:Suppress("UnstableApiUsage")

rootProject.name = "plugin-versions"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}
