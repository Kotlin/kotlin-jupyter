package org.jetbrains.kotlinx.jupyter.test.util

import org.junit.jupiter.api.condition.EnabledIfSystemProperty

@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
)
@EnabledIfSystemProperty(named = "tests.flaky", matches = "true")
annotation class DisabledFlakyTest

@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
)
@EnabledIfSystemProperty(named = "tests.heavy", matches = "true")
annotation class DisabledHeavyTest
