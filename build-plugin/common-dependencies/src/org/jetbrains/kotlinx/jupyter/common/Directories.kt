package org.jetbrains.kotlinx.jupyter.common

import java.io.File

val userHomeDir = File(System.getProperty("user.home"))

/**
 * Contains persistent kernel caches, i.e., for library descriptors
 * and Maven resolution results
 */
val kernelCacheDir = userHomeDir.resolve(".jupyter_kotlin")

/**
 * Contains Maven resolver cache
 */
val kernelMavenCacheDir = kernelCacheDir.resolve("maven_repository")
