package org.jetbrains.kotlinx.jupyter.publishing

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.invoke
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.dokka.gradle.DokkaTask
import java.io.File
import java.net.URI
import java.nio.file.Path

fun Project.addPublication(configuration: ArtifactPublication.() -> Unit) {
    val settings = ArtifactPublication().apply(configuration)

    val sourceSets = extensions.getByName("sourceSets") as SourceSetContainer
    val mainSourceSet = sourceSets.named("main").get()
    val publicationName = settings.publicationName!!

    val sonatypeUser = getNexusUser()
    val sonatypePassword = getNexusPassword()
    val signingPrivateKey = System.getenv("SIGN_KEY_PRIVATE")
    val signingKey = System.getenv("SIGN_KEY_ID")
    val signingKeyPassphrase = System.getenv("SIGN_KEY_PASSPHRASE")

    tasks {
        register("sourceJar", Jar::class.java) {
            archiveClassifier.set("sources")

            from(mainSourceSet.allSource)
        }

        named("dokkaHtml", DokkaTask::class.java) {
            outputDirectory.set(File("$buildDir/dokka"))
        }

        val javadocDestDir = File("$buildDir/dokkaJavadoc")

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
            register(publicationName, MavenPublication::class.java) {
                artifactId = settings.artifactId
                groupId = settings.groupId

                from(components["java"])
                artifact(tasks["sourceJar"])
                artifact(tasks["javadocJar"])

                pom {
                    name.set(settings.packageName)
                    description.set(settings.description)
                    url.set("https://github.com/Kotlin/kotlin-jupyter")
                    inceptionYear.set("2021")

                    scm {
                        url.set("https://github.com/Kotlin/kotlin-jupyter")
                        connection.set("scm:https://github.com/Kotlin/kotlin-jupyter.git")
                        developerConnection.set("scm:git://github.com/Kotlin/kotlin-jupyter.git")
                    }

                    licenses {
                        license {
                            name.set("The Apache Software License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }

                    developers {
                        developer {
                            id.set("nikitinas")
                            name.set("Anatoly Nikitin")
                            email.set("Anatoly.Nikitin@jetbrains.com")
                        }

                        developer {
                            id.set("ileasile")
                            name.set("Ilya Muradyan")
                            email.set("Ilya.Muradyan@jetbrains.com")
                        }
                    }
                }
            }
        }

        repositories {
            (rootProject.findProperty("localPublicationsRepo") as? Path)?.let {
                maven {
                    name = "LocalBuild"
                    url = it.toUri()
                }
            }

            maven {
                name = "Sonatype"
                url = URI(NEXUS_REPO_URL)
                credentials {
                    username = sonatypeUser
                    password = sonatypePassword
                }
            }
        }
    }

    val thisProjectName = project.name

    if (rootProject.findProperty("isMainProject") == true) {
        rootProject.tasks {
            named("publishLocal") {
                dependsOn(":$thisProjectName:publishAllPublicationsToLocalBuildRepository")
            }

            if (settings.publishToSonatype) {
                named("publishToSonatype") {
                    dependsOn(":$thisProjectName:publishAllPublicationsToSonatypeRepository")
                }
            }
        }
    }

    if (signingKey != null) {
        extensions.configure<SigningExtension>("signing") {
            sign(extensions.getByName<PublishingExtension>("publishing").publications[publicationName])

            @Suppress("UnstableApiUsage")
            useInMemoryPgpKeys(signingKey, signingPrivateKey, signingKeyPassphrase)
        }
    }
}
