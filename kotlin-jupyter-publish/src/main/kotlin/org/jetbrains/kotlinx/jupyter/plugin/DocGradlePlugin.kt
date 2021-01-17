package org.jetbrains.kotlinx.jupyter.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.jetbrains.dokka.gradle.DokkaMultiModuleTask
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.kotlinx.jupyter.publishing.PublishDocsTask
import java.io.OutputStream

class DocGradlePlugin: Plugin<Project> {
    override fun apply(target: Project) {
        target.applyToProject()
    }

    private fun Project.applyToProject() {
        pluginManager.run {
            apply(DokkaPlugin::class.java)
        }

        val dokkaTask = tasks.named<DokkaMultiModuleTask>("dokkaHtmlMultiModule").get()
        val dokkaOutput = dokkaTask.outputDirectory.get()
        val docRepoDir = buildDir.resolve("docRepo").absoluteFile
        docRepoDir.deleteRecursively()

        fun execGit(vararg args: String, configure: ExecSpec.() -> Unit = {}): ExecResult {
            return exec {
                this.executable = "git"
                this.args = args.asList()
                this.workingDir = docRepoDir

                configure()
            }
        }

        tasks.register<PublishDocsTask>("publishDocs") {
            group = "publishing"
            dependsOn(dokkaTask)

            doLast {
                val repoUrl = docsRepoUrl.get()
                val branchName = "master"

                docRepoDir.mkdirs()
                execGit("init")
                execGit("config", "user.email", "robot@jetbrains.com")
                execGit("config", "user.name", "robot")
                execGit("pull", repoUrl, branchName)

                val copyDestDir = docRepoDir.resolve("docs")
                copyDestDir.deleteRecursively()
                copy {
                    from(dokkaOutput)
                    into(copyDestDir)
                }

                execGit("add", ".")
                val commitResult = execGit("commit", "-m", "[AUTO] Update docs: $version") {
                    isIgnoreExitValue = true
                }
                if (commitResult.exitValue == 0) {
                    execGit("push", "-u", repoUrl, branchName) {
                        this.standardOutput = object: OutputStream() {
                            override fun write(b: Int) { }
                        }
                    }
                }
            }
        }
    }
}
