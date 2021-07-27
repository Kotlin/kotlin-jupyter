package build

import org.gradle.api.Plugin
import org.gradle.api.Project

class PluginVersionsPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        with(project.plugins) {
            apply("org.jlleitschuh.gradle.ktlint")
            apply("org.gradle.java-gradle-plugin")
            apply("org.jetbrains.kotlin.plugin.serialization")
        }
    }
}
