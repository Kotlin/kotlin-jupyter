package org.jetbrains.kotlin.jupyter.test

import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.dependencies.ExternalDependenciesResolver
import kotlin.script.experimental.dependencies.addRepository
import kotlin.script.experimental.dependencies.maven.MavenDependenciesResolver

class ResolverTests {

    fun ExternalDependenciesResolver.doResolve(artifact: String): List<File> {
        this.addRepository("https://jcenter.bintray.com/")
        this.addRepository("https://repo.maven.apache.org/maven2/")
        this.addRepository("https://jitpack.io")
        Assert.assertTrue(acceptsArtifact(artifact))
        val result = runBlocking { resolve(artifact) }
        Assert.assertTrue(result is ResultWithDiagnostics.Success)
        return (result as ResultWithDiagnostics.Success).value
    }

    @Test
    fun GetGgplotTest() {
        val files = MavenDependenciesResolver().doResolve("org.apache.spark:spark-mllib_2.11:2.4.4")
        println("Downloaded files: ${files.count()}")
        files.forEach {
            println(it)
        }

    }
}
