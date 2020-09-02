package org.jetbrains.kotlin.jupyter.api

interface JavaVersionHelper {
    val version: String
    val versionAsInt: Int
    fun assertVersion(message: String = "JRE version requirements are not satisfied", assertion: (Int) -> Boolean)
    fun assertVersionAtLeast(minVersion: Int)
    fun assertVersionInRange(minVersion: Int, maxVersion: Int)
}
