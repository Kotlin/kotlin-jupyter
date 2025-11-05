package org.jetbrains.kotlinx.jupyter.dependencies.test

import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.jetbrains.amper.dependency.resolution.MavenLocal
import org.jetbrains.amper.dependency.resolution.MavenRepository
import org.jetbrains.amper.dependency.resolution.Repository
import org.jetbrains.kotlinx.jupyter.dependencies.maven.repositories.CENTRAL_REPO
import org.jetbrains.kotlinx.jupyter.dependencies.maven.repositories.amperRepositoryComparator
import org.junit.jupiter.api.Test

class AmperRepositoryComparatorTest {
    private class ComparableRepository(
        val repository: Repository,
    ) : Comparable<ComparableRepository> {
        override fun compareTo(other: ComparableRepository): Int = amperRepositoryComparator.compare(repository, other.repository)
    }

    private fun Repository.toComparable() = ComparableRepository(this)

    private val mavenLocal = MavenLocal.toComparable()
    private val regularRepo =
        MavenRepository(
            "https://example.com/maven",
            null,
            null,
        ).toComparable()
    private val anotherRegularRepo =
        MavenRepository(
            "https://repo.example.org/maven2",
            null,
            null,
        ).toComparable()
    private val centralRepo =
        MavenRepository(
            CENTRAL_REPO.value,
            null,
            null,
        ).toComparable()

    @Test
    fun `should place MavenLocal before regular Maven repositories`() {
        mavenLocal shouldBeLessThan regularRepo
    }

    @Test
    fun `should place Maven Central after regular Maven repositories`() {
        centralRepo shouldBeGreaterThan regularRepo
    }

    @Test
    fun `should order non-local non-central repositories by URL`() {
        // https://example.com/... comes lexicographically before https://repo.example.org/...
        regularRepo shouldBeLessThan anotherRegularRepo
    }

    @Test
    fun `should place MavenLocal before Maven Central`() {
        mavenLocal shouldBeLessThan centralRepo
    }

    @Test
    fun `should sort mixed repositories with MavenLocal first URL-ordered middle and Central last`() {
        val repoA = MavenRepository("https://a.example.com/maven", null, null).toComparable()
        val repoB = MavenRepository("https://b.example.com/maven", null, null).toComparable()

        val shuffled = listOf(centralRepo, repoB, mavenLocal, repoA)
        val sorted = shuffled.sorted()

        sorted.map { (it.repository as? MavenRepository)?.url ?: "local" } shouldBe
            listOf(
                "local",
                "https://a.example.com/maven",
                "https://b.example.com/maven",
                CENTRAL_REPO.value,
            )
    }

    @Test
    fun `repositories with the same URL compare as equal regardless of credentials`() {
        val url = "https://same.example.com/maven"
        val repo1: Repository = MavenRepository(url, "user1", "pass1")
        val repo2: Repository = MavenRepository(url, "user2", "pass2")

        amperRepositoryComparator.compare(repo1, repo2) shouldBe 0
        amperRepositoryComparator.compare(repo2, repo1) shouldBe 0
    }
}
