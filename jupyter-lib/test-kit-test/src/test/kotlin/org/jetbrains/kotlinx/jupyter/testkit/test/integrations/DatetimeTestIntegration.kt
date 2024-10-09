package org.jetbrains.kotlinx.jupyter.testkit.test.integrations

import org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration
import org.jetbrains.kotlinx.jupyter.api.libraries.dependencies

@Suppress("unused")
class DatetimeTestIntegration : JupyterIntegration() {
    override fun Builder.onLoaded() {
        dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.1")
        }
        import("kotlinx.datetime.Clock")
    }
}
