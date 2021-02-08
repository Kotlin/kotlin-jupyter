package org.jetbrains.kotlinx.jupyter.plugin

import com.jfrog.bintray.gradle.BintrayPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.plugins.signing.SigningPlugin
import org.jetbrains.dokka.gradle.DokkaPlugin

class ApiPublishGradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.run {
            apply(DokkaPlugin::class.java)
            apply(MavenPublishPlugin::class.java)
            apply(BintrayPlugin::class.java)
            apply(SigningPlugin::class.java)
        }
    }
}
