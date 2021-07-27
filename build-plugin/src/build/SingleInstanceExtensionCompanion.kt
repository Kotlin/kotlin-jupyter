package build

import org.gradle.api.Project

interface SingleInstanceExtensionCompanion<T : Any> {
    val name: String
    fun createInstance(project: Project): T
}
