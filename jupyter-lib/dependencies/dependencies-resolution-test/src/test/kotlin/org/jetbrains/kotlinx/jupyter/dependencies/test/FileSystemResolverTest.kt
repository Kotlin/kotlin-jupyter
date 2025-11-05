package org.jetbrains.kotlinx.jupyter.dependencies.test

import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.jupyter.dependencies.api.ArtifactRequest
import org.jetbrains.kotlinx.jupyter.dependencies.api.Repository
import org.jetbrains.kotlinx.jupyter.dependencies.api.ResolvedArtifacts
import org.jetbrains.kotlinx.jupyter.dependencies.local.LocalFileSystemSourceAwareDependenciesResolver
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.script.experimental.api.ResultWithDiagnostics

class FileSystemResolverTest {
    @TempDir
    private lateinit var temp: Path
    private val resolver = LocalFileSystemSourceAwareDependenciesResolver()

    @Test
    fun `accepts repository - existing directory and file URL`() {
        val repoDir = Files.createDirectory(temp.resolve("repo")).toFile()
        resolver.acceptsRepository(Repository(repoDir.absolutePath)) shouldBe true

        val fileUrl = repoDir.toURI().toURL().toString() // e.g., file:/.../repo
        resolver.acceptsRepository(Repository(fileUrl)) shouldBe true

        val nonExisting = File(repoDir, "no_such").absolutePath
        resolver.acceptsRepository(Repository(nonExisting)) shouldBe false
    }

    @Test
    fun `addRepository returns Success(false) for missing directory and Success(true) for valid one`() {
        val repoDir = Files.createDirectory(temp.resolve("repo2")).toFile()

        val invalid = resolver.addRepository(Repository(File(repoDir, "missing").absolutePath), null)
        val invalidSuccess = invalid.shouldBeTypeOf<ResultWithDiagnostics.Success<Boolean>>()
        invalidSuccess.value shouldBe false

        val valid = resolver.addRepository(Repository(repoDir.absolutePath), null)
        val validSuccess = valid.shouldBeTypeOf<ResultWithDiagnostics.Success<Boolean>>()
        validSuccess.value shouldBe true
    }

    @Test
    fun `resolves absolute file path`() {
        val file = createFile(temp, "a.jar", "bin1")

        val result = runResolve(file.absolutePath)
        val success = result.shouldBeSuccess()
        success.value.binaries shouldBe listOf(file)
        success.value.sources shouldBe emptyList()
    }

    @Test
    fun `resolves relative path inside added repository`() {
        val repo = Files.createDirectory(temp.resolve("repo3")).toFile()
        val lib = createFile(repo.toPath(), "lib.jar", "bin2")
        val repoAdd = resolver.addRepository(Repository(repo.absolutePath), null)
        val repoAddSuccess = repoAdd.shouldBeTypeOf<ResultWithDiagnostics.Success<Boolean>>()
        repoAddSuccess.value shouldBe true

        val result = runResolve("lib.jar")
        val success = result.shouldBeSuccess()
        success.value.binaries shouldBe listOf(lib)
    }

    @Test
    fun `accepts directory as binary artifact`() {
        val repo = Files.createDirectory(temp.resolve("repo4")).toFile()
        val dirBinary = Files.createDirectory(repo.toPath().resolve("classes"))
        val repoAdd = resolver.addRepository(Repository(repo.absolutePath), null)
        repoAdd.shouldBeTypeOf<ResultWithDiagnostics.Success<Boolean>>()

        val result = runResolve("classes")
        val success = result.shouldBeSuccess()
        success.value.binaries shouldBe listOf(dirBinary.toFile())
    }

    @Test
    fun `aggregates 'not found' diagnostics and fails when file is missing`() {
        val repo = Files.createDirectory(temp.resolve("repo5")).toFile()
        val repoAdd = resolver.addRepository(Repository(repo.absolutePath), null)
        repoAdd.shouldBeTypeOf<ResultWithDiagnostics.Success<Boolean>>()

        val result = runResolve("missing.jar")
        val failure = result.shouldBeTypeOf<ResultWithDiagnostics.Failure>()
        // Expect at least two not-found messages: from CWD and from repo dir
        failure.reports.count {
            it.message.contains("not found", ignoreCase = true)
        } shouldBeGreaterThanOrEqual 2
    }

    private fun runResolve(coordinates: String): ResultWithDiagnostics<ResolvedArtifacts> =
        runBlocking {
            resolver.resolve(
                listOf(
                    ArtifactRequest(coordinates, null),
                ),
                resolveSources = false,
            )
        }

    private fun createFile(
        dir: Path,
        name: String,
        content: String = "",
    ): File {
        val file = dir.resolve(name).toFile()
        file.writeText(content)
        return file
    }
}
