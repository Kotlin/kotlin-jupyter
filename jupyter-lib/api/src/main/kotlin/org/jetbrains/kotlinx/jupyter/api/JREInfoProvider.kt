package org.jetbrains.kotlinx.jupyter.api

import org.jetbrains.kotlinx.jupyter.api.jvm.JavaVersion

/**
 * Provides information about used Java runtime
 */
interface JREInfoProvider {
    /**
     * JRE version
     */
    val javaVersion: JavaVersion

    /**
     * JRE version, i.e. "1.8" or "11"
     */
    @Deprecated("Use javaVersion instead", ReplaceWith("javaVersion.versionString"))
    val version: String

    /**
     * JRE version as integer, i.e. 8 or 11
     */
    @Deprecated("Use javaVersion instead", ReplaceWith("javaVersion.versionInteger"))
    val versionAsInt: Int

    /**
     * Does nothing if [condition] returns true for the current JRE version, throws [AssertionError] otherwise
     *
     * @param message Exception message
     * @param condition Condition to check
     */
    fun assertVersion(
        message: String = "JRE version requirements are not satisfied",
        condition: (Int) -> Boolean,
    )

    /**
     * Does nothing if the current JRE version is higher or equal than [minVersion], throws [AssertionError] otherwise
     *
     * @param minVersion Minimal-accepted version
     */
    fun assertVersionAtLeast(minVersion: Int)

    /**
     * Does nothing if the current JRE version is between [minVersion] and [maxVersion],
     * throws [AssertionError] otherwise
     *
     * @param minVersion Minimal-accepted version
     * @param maxVersion Maximal accepted version
     */
    fun assertVersionInRange(
        minVersion: Int,
        maxVersion: Int,
    )
}
