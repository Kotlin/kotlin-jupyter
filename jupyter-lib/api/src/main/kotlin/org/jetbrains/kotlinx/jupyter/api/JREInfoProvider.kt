package org.jetbrains.kotlinx.jupyter.api

/**
 * Provides information about used Java runtime
 */
interface JREInfoProvider {
    /**
     * JRE version, i.e. "1.8" or "11"
     */
    val version: String

    /**
     * JRE version as integer, i.e. 8 or 11
     */
    val versionAsInt: Int

    /**
     * Does nothing if [condition] returns true for current JRE version, throws [AssertionError] otherwise
     *
     * @param message Exception message
     * @param condition Condition to check
     */
    fun assertVersion(message: String = "JRE version requirements are not satisfied", condition: (Int) -> Boolean)

    /**
     * Does nothing if current JRE version is higher or equal than [minVersion], throws [AssertionError] otherwise
     *
     * @param minVersion Minimal accepted version
     */
    fun assertVersionAtLeast(minVersion: Int)

    /**
     * Does nothing if current JRE version is between [minVersion] and [maxVersion], throws [AssertionError] otherwise
     *
     * @param minVersion Minimal accepted version
     * @param maxVersion Maximal accepted version
     */
    fun assertVersionInRange(minVersion: Int, maxVersion: Int)
}
