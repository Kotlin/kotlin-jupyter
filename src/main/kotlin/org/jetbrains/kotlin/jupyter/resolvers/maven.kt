package org.jetbrains.kotlin.jupyter.resolvers

import com.jcabi.aether.Aether
import jupyter.kotlin.DependsOn
import org.jetbrains.kotlin.jupyter.log
import java.io.File
import java.util.*
import org.sonatype.aether.repository.RemoteRepository
import org.sonatype.aether.resolution.DependencyResolutionException
import org.sonatype.aether.util.artifact.DefaultArtifact
import org.sonatype.aether.util.artifact.JavaScopes

val mavenCentral = RemoteRepository("maven-central", "default", "http://repo1.maven.org/maven2/")

class MavenResolver: Resolver {

    // TODO: make robust
    val localRepo = File(File(System.getProperty("user.home")!!, ".m2"), "repository")

    val repos: ArrayList<RemoteRepository> = arrayListOf()

    private fun currentRepos() = if (repos.isEmpty()) arrayListOf(mavenCentral) else repos

    override fun tryResolve(dependsOn: DependsOn): Iterable<File>? {
        if (dependsOn.value.count { it == ':' } == 2) {
            try {
                val deps = Aether(currentRepos(), localRepo).resolve(
                        DefaultArtifact(dependsOn.value),
                        JavaScopes.RUNTIME)
                if (deps != null)
                    return deps.map { it.file }
                else {
                    log.error("resolving [${dependsOn.value}] failed: no results")
                }
            }
            catch (e: DependencyResolutionException) {
                log.error("resolving [${dependsOn.value}] failed: $e")
            }
            return listOf()
        }
        return null
    }
}
