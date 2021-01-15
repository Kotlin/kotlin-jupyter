package org.jetbrains.kotlinx.jupyter.libraries

import java.nio.file.Path
import java.nio.file.Paths

const val LibrariesDir = "libraries"
const val LocalCacheDir = "cache"
const val LocalSettingsDir = ".jupyter_kotlin"
const val GitHubApiHost = "api.github.com"
const val GitHubRepoOwner = "kotlin"
const val GitHubRepoName = "kotlin-jupyter"
const val GitHubApiPrefix = "https://$GitHubApiHost/repos/$GitHubRepoOwner/$GitHubRepoName"
const val LibraryDescriptorExt = "json"
const val LibraryPropertiesFile = ".properties"

val LocalSettingsPath: Path = Paths.get(System.getProperty("user.home"), LocalSettingsDir)
