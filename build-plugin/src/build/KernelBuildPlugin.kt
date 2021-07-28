package build

import org.gradle.api.Plugin
import org.gradle.api.Project

class KernelBuildPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        KernelBuildConfigurator(project).configure()
    }
}
