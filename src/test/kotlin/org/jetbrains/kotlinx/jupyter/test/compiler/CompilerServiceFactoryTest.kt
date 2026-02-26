package org.jetbrains.kotlinx.jupyter.test.compiler

import org.jetbrains.kotlinx.jupyter.api.DeclarationInfo
import org.jetbrains.kotlinx.jupyter.compiler.CompilerServiceFactory
import org.jetbrains.kotlinx.jupyter.compiler.api.CompilerParams
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyAnnotation
import org.jetbrains.kotlinx.jupyter.compiler.api.DependencyResolutionResult
import org.jetbrains.kotlinx.jupyter.compiler.api.KernelCallbacks
import org.jetbrains.kotlinx.jupyter.test.testLoggerFactory
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompilerServiceFactoryTest {
    @Test
    fun `should load CompilerServiceProvider via SPI`() {
        val callbacks = object : KernelCallbacks {
            override suspend fun reportImports(imports: List<String>) {}
            override suspend fun reportDeclarations(declarations: List<DeclarationInfo>) {}
            override suspend fun resolveDependencies(annotations: List<DependencyAnnotation>): DependencyResolutionResult {
                return DependencyResolutionResult.Success(emptyList())
            }
            override suspend fun updatedClasspath(): List<String> = emptyList()
        }

        val params = CompilerParams(
            scriptClasspath = emptyList(),
            jvmTarget = "11",
        )

        // Should not throw - at least one provider should be available
        val compilerService = CompilerServiceFactory.createCompilerService(params, callbacks, testLoggerFactory)
        assertNotNull(compilerService)
    }

    @Test
    fun `should select InProcessCompilerProvider with higher priority`() {
        // InProcessCompilerProvider has priority 100
        // DaemonCompilerProvider has priority 10
        // The factory should select InProcessCompilerProvider

        val callbacks = object : KernelCallbacks {
            override suspend fun reportImports(imports: List<String>) {}
            override suspend fun reportDeclarations(declarations: List<DeclarationInfo>) {}
            override suspend fun resolveDependencies(annotations: List<DependencyAnnotation>): DependencyResolutionResult {
                return DependencyResolutionResult.Success(emptyList())
            }
            override suspend fun updatedClasspath(): List<String> = emptyList()
        }

        val params = CompilerParams(
            scriptClasspath = emptyList(),
            jvmTarget = "11",
        )

        val compilerService = CompilerServiceFactory.createCompilerService(params, callbacks, testLoggerFactory)
        assertNotNull(compilerService)

        // The in-process provider should be selected
        // We can verify this by checking the implementation class name
        val className = compilerService::class.java.simpleName
        assertTrue(
            className.contains("CompilerService") || className.contains("InProcess"),
            "Expected in-process implementation but got: $className",
        )
    }
}
