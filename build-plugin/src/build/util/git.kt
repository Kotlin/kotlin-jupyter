package build.util

import org.gradle.api.Project
import org.gradle.process.ExecSpec
import java.io.ByteArrayOutputStream
import java.io.File

private val NOTHING_TO_COMMIT_REGEX = Regex("((nothing)|(no changes added)) to commit")

data class ProcessExecuteResult(
    val commandLine: List<String>,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

fun ProcessExecuteResult.checkSuccess(messageFactory: () -> String? = { null }) {
    if (exitCode != 0) {
        val message = messageFactory() ?: "Process failed: ${commandLine.joinToString(" ")}"
        error("""
            $message: exit code $exitCode
            --- STANDARD OUTPUT ---
            $stdout
            --- STANDARD ERROR ---
            $stderr
        """.trimIndent())
    }
}

fun Project.executeProcess(
    workingDir: File,
    commandLine: List<String>,
    configure: ExecSpec.() -> Unit = {},
): ProcessExecuteResult {
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()

    val result = exec {
        this.workingDir = workingDir
        this.commandLine = commandLine
        configure()

        isIgnoreExitValue = true
        this.standardOutput = stdout
        this.errorOutput = stderr
    }

    return ProcessExecuteResult(
        commandLine,
        result.exitValue,
        stdout.toString().trim(),
        stderr.toString().trim(),
    )
}

fun Project.executeGitCommand(
    workingDir: File? = null,
    args: List<String>,
    configure: ExecSpec.() -> Unit = {},
): ProcessExecuteResult {
    return executeProcess(
        workingDir ?: rootDir,
        listOf("git") + args,
        configure
    )
}


fun Project.configureGit(
    email: String,
    userName: String,
) {
    executeGitCommand(
        args = listOf(
            "config",
            "user.email",
            email
        )
    ).checkSuccess()
    executeGitCommand(
        args = listOf(
            "config",
            "user.name",
            userName
        )
    ).checkSuccess()
}

fun Project.configureGitRobotCommitter() {
    configureGit(email = "robot@jetbrains.com", userName = "robot")
}


fun Project.gitAddAll(workingDir: File? = null): ProcessExecuteResult {
    return executeGitCommand(
        workingDir = workingDir,
        args = listOf("add", "."),
    )
}

fun Project.gitAdd(paths: List<String>, workingDir: File? = null): ProcessExecuteResult {
    return executeGitCommand(
        workingDir = workingDir,
        args = listOf("add") + paths,
    )
}


sealed interface GitCommitResult {
    object Success : GitCommitResult
    object NoChanges : GitCommitResult
    data class Failure(val throwable: Throwable?) : GitCommitResult
}


fun Project.gitCommit(
    message: String,
    workingDir: File? = null,
    includedPaths: List<String>? = null,
): GitCommitResult {
    if (includedPaths != null) {
        gitAdd(includedPaths, workingDir)
    } else {
        gitAddAll(workingDir)
    }

    return try {
        val result = executeGitCommand(
            workingDir = workingDir,
            args = buildList {
                add("commit")
                if (includedPaths != null) {
                    addAll(includedPaths)
                }

                add("-m")
                add(message)
            },
        )
        when {
            result.exitCode == 0 -> GitCommitResult.Success
            NOTHING_TO_COMMIT_REGEX.containsMatchIn(result.stdout) -> GitCommitResult.NoChanges
            else -> {
                GitCommitResult.Failure(
                    RuntimeException(
                        "Git commit failed:\nSTDERR:\n${result.stderr}\nSTDOUT:\n${result.stdout}"
                    )
                )
            }
        }
    } catch (e: Throwable) {
        GitCommitResult.Failure(e)
    }
}

fun Project.gitPush(
    remote: String = "origin",
    branch: String = "master",
    workingDir: File? = null,
) {
    executeGitCommand(
        workingDir = workingDir,
        args = listOf(
            "push",
            remote,
            branch,
        ),
    ).checkSuccess()
}

