package build.util

import org.gradle.api.Project
import org.gradle.process.ExecSpec
import java.io.File

private val NOTHING_TO_COMMIT_REGEX = Regex("((nothing)|(no changes added)) to commit")

data class ProcessExecuteResult(
    val commandLine: List<String>,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

/**
 * Checks if a process execution was successful based on the exit code.
 * If the process fails (non-zero exit code), this method throws an error containing
 * the command line, exit code, and the captured standard output and error streams.
 *
 * @param messageFactory A lambda function that generates a custom error message
 * if the process fails.
 * It defaults to `null`, in which case the error message
 * includes the failed command line.
 */
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
    val execTask = providers.exec {
        this.workingDir = workingDir
        this.commandLine = commandLine

        // Makes `exec` not to throw exceptions
        this.isIgnoreExitValue = true

        configure()
    }

    val result = execTask.result.get()

    return ProcessExecuteResult(
        commandLine,
        result.exitValue,
        execTask.standardOutput.asText.get(),
        execTask.standardError.asText.get(),
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

/**
 * Configures artificial Git user. Intended to be used in CI environments to perform commits.
 */
fun Project.configureGitRobotCommitter() {
    configureGit(email = "robot@jetbrains.com", userName = "robot")
}

/**
 * Adds all changed/added files to the Git index for commit.
 */
fun Project.gitAddAll(workingDir: File? = null): ProcessExecuteResult {
    return executeGitCommand(
        workingDir = workingDir,
        args = listOf("add", "."),
    )
}

/**
 * Adds the given files to the Git index for commit.
 */
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

/**
 * Commits changes to the Git repository.
 *
 * @param message The commit message to use for the commit.
 * @param workingDir The directory in which the Git command should be executed. If null, the root directory is used.
 * @param includedPaths Specific file paths to include in the commit. If null, all changes will be committed.
 * Paths should be relative to [workingDir]
 * @return The result of the commit operation as a [GitCommitResult].
 */
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

/**
 * Pushes the specified branch to the given remote repository using Git.
 * Optionally, it can update the remote URL before pushing.
 *
 * @param remote The name of the remote repository.
 * @param remoteUrl The URL for the remote repository. If specified, the remote URL will be updated before pushing. It
 * could include push credentials in a form of `https://<username>:<token>@github.com/your/repo.git`
 * @param branch The name of the branch to push, should match the remote branch.
 * @param workingDir The working directory for executing the Git commands. If not specified, the project root directory will be used.
 */
fun Project.gitPush(
    remote: String = "origin",
    remoteUrl: String? = null,
    branch: String = "master",
    workingDir: File? = null,
) {
    if (remoteUrl != null) {
        executeGitCommand(
            workingDir = workingDir,
            args = listOf(
                "remote",
                "set-url",
                remote,
                remoteUrl,
            )
        ).checkSuccess()
    }

    executeGitCommand(
        workingDir = workingDir,
        args = listOf(
            "push",
            remote,
            branch,
        ),
    ).checkSuccess()
}
