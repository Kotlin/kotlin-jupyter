package org.jetbrains.kotlinx.jupyter.common

import java.io.File

val userHomeDir = File(System.getProperty("user.home"))
val kernelCacheDir = userHomeDir.resolve(".jupyter_kotlin")
val kernelMavenCacheDir = kernelCacheDir.resolve("maven_repository")
