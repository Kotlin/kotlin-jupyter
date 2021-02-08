package org.jetbrains.kotlinx.jupyter.publishing

import com.jfrog.bintray.gradle.BintrayExtension
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.delegateClosureOf
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.invoke
import org.gradle.plugins.signing.SigningExtension
import org.jetbrains.dokka.gradle.DokkaTask
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.Date

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
                    name.set(settings.bintrayPackageName)
                    description.set(settings.bintrayDescription)
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
                desc = settings.bintrayDescription

                val projectUrl = project.findProperty("projectRepoUrl") as String? ?: ""
                websiteUrl = projectUrl
                vcsUrl = projectUrl
                issueTrackerUrl = "$projectUrl/issues"

                setLicenses("Apache-2.0")
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

    val isDevVersion = (rootProject.version as String).contains("dev")
    if (signingKey != null && !isDevVersion) {
        extensions.configure<SigningExtension>("signing") {
            sign(extensions.getByName<PublishingExtension>("publishing").publications[publicationName])

            @Suppress("UnstableApiUsage")
            useInMemoryPgpKeys(signingKey, signingPrivateKey, signingKeyPassphrase)
        }
    }
}
