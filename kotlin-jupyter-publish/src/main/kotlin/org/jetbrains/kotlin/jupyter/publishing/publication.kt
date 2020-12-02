package org.jetbrains.kotlin.jupyter.publishing

import com.jfrog.bintray.gradle.BintrayExtension
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.kotlin.dsl.delegateClosureOf
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.maven
import org.jetbrains.dokka.gradle.DokkaTask
import java.io.File
import java.nio.file.Path
import java.util.Date

fun Project.addPublication(configuration: ArtifactPublication.() -> Unit) {
    val settings = ArtifactPublication().apply(configuration)

    val sourceSets = extensions.getByName("sourceSets") as SourceSetContainer
    val mainSourceSet = sourceSets.named("main").get()

    tasks {
        register("sourceJar", Jar::class.java) {
            archiveClassifier.set("sources")

            from(mainSourceSet.allSource)
        }

        named("dokkaHtml", DokkaTask::class.java) {
            outputDirectory.set(File("$buildDir/dokka"))
        }

        val javadocDestDir = named("javadoc", Javadoc::class.java).get().destinationDir

        val dokkaJavadoc = named("dokkaJavadoc", DokkaTask::class.java) {
            outputDirectory.set(javadocDestDir)
            inputs.dir("src/main/kotlin")
        }

        @Suppress("UNUSED_VARIABLE")
        register("javadocJar", Jar::class.java) {
            group = "documentation"
            dependsOn(dokkaJavadoc)
            archiveClassifier.set("javadoc")
            from(javadocDestDir)
        }
    }

    extensions.configure<PublishingExtension>("publishing") {
        publications {
            register(settings.publicationName!!, MavenPublication::class.java) {
                artifactId = settings.artifactId
                groupId = settings.groupId

                from(components["java"])
                artifact(tasks["sourceJar"])
                artifact(tasks["javadocJar"])
            }
        }

        repositories {
            (rootProject.findProperty("localPublicationsRepo") as? Path)?.let { maven(it.toUri()) }
        }
    }

    extensions.configure<BintrayExtension>("bintray") {
        // property must be set in ~/.gradle/gradle.properties
        user = project.findProperty("bintray_user") as String? ?: ""
        key = project.findProperty("bintray_key") as String? ?: ""
        val bintrayRepo = project.findProperty("bintray_repo") as String? ?: ""
        val bintrayUserOrg = project.findProperty("bintray_user_org") as String? ?: ""

        setPublications(settings.publicationName) // When uploading configuration files

        dryRun = false // Whether to run this as dry-run, without deploying
        publish = true // If version should be auto published after an upload

        pkg(
            delegateClosureOf<BintrayExtension.PackageConfig> {
                repo = bintrayRepo
                name = settings.bintrayPackageName
                userOrg = bintrayUserOrg

                vcsUrl = project.findProperty("projectRepoUrl") as String? ?: ""

                setLicenses("MIT")
                publicDownloadNumbers = true

                // Optional version descriptor
                version(
                    delegateClosureOf<BintrayExtension.VersionConfig> {
                        val projVersion = project.version as String
                        name = projVersion // Bintray logical version name
                        desc = settings.bintrayDescription
                        released = Date().toString()
                        vcsTag = projVersion
                    }
                )
            }
        )
    }
}
