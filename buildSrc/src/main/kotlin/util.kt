import groovy.json.JsonOutput
import org.gradle.api.Project
import org.gradle.kotlin.dsl.provideDelegate
import java.nio.file.Path

fun makeTaskName(prefix: String, local: Boolean) = prefix + (if (local) "Local" else "Distrib")

fun makeDirs(path: Path) {
    val dir = path.toFile()
    if (!dir.exists()) {
        dir.mkdirs()
    }
}

fun getSubDir(dir: Path, vararg subDir: String): Path  = subDir.fold(dir, Path::resolve)

fun writeJson(json: Map<String, Any>, path: Path) {
    val str = JsonOutput.prettyPrint(JsonOutput.toJson(json))
    path.toFile().writeText(str, Charsets.UTF_8)
}

fun Project.kotlinDep(dependency: String): String {
    val kotlinVersion: String by project
    return "org.jetbrains.kotlin:$dependency:$kotlinVersion"
}

fun Path.deleteDir() = toFile().deleteRecursively()

fun Project.stringPropOrEmpty(name: String) = rootProject.findProperty(name) as String? ?: ""

interface AllOptions: BuildOptions, InstallOptions, DistribOptions
interface ProjectWithOptions : ProjectWithBuildOptions, ProjectWithInstallOptions, ProjectWithDistribOptions

class ProjectWithOptionsImpl(private val p: Project, private val opt: AllOptions):
        Project by p, InstallOptions by opt, DistribOptions by opt, BuildOptions by opt, ProjectWithOptions
