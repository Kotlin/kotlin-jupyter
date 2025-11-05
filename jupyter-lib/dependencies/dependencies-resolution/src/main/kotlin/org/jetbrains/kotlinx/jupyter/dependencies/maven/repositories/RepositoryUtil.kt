package org.jetbrains.kotlinx.jupyter.dependencies.maven.repositories

import org.jetbrains.amper.dependency.resolution.MavenLocal
import org.jetbrains.amper.dependency.resolution.MavenRepository
import org.jetbrains.kotlinx.jupyter.dependencies.api.MAVEN_LOCAL_NAME
import org.jetbrains.kotlinx.jupyter.dependencies.api.Repository
import java.net.MalformedURLException
import java.net.URL
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.onFailure
import kotlin.script.experimental.api.valueOrNull
import org.jetbrains.amper.dependency.resolution.Repository as AmperRepository

internal fun Repository.convertToAmperRepository(sourceCodeLocation: SourceCode.LocationWithId?): ResultWithDiagnostics<AmperRepository?> {
    if (value == MAVEN_LOCAL_NAME) {
        return MavenLocal.asSuccess()
    }

    val url = value.toRepositoryUrlOrNull() ?: return null.asSuccess()

    val reports = mutableListOf<ScriptDiagnostic>()

    fun getFinalValue(
        optionName: String,
        rawValue: String?,
    ): String? =
        tryResolveEnvironmentVariable(rawValue, optionName, sourceCodeLocation)
            .onFailure { reports.addAll(it.reports) }
            .valueOrNull()

    val usernameSubstituted = getFinalValue("username", username)
    val passwordSubstituted = getFinalValue("password", password)

    if (reports.isNotEmpty()) {
        return ResultWithDiagnostics.Failure(reports)
    }

    return MavenRepository(
        url.toString(),
        usernameSubstituted,
        passwordSubstituted,
    ).asSuccess()
}

internal fun String.toRepositoryUrlOrNull(): URL? =
    try {
        URL(this)
    } catch (_: MalformedURLException) {
        null
    }

private fun tryResolveEnvironmentVariable(
    str: String?,
    optionName: String,
    location: SourceCode.LocationWithId?,
): ResultWithDiagnostics<String?> {
    if (str == null) return null.asSuccess()
    if (!str.startsWith("$")) return str.asSuccess()
    val envName = str.substring(1)
    val envValue: String? = System.getenv(envName)
    if (envValue.isNullOrEmpty()) {
        return ResultWithDiagnostics.Failure(
            ScriptDiagnostic(
                ScriptDiagnostic.unspecifiedError,
                "Environment variable `$envName` for $optionName is not set",
                ScriptDiagnostic.Severity.ERROR,
                location,
            ),
        )
    }
    return envValue.asSuccess()
}
