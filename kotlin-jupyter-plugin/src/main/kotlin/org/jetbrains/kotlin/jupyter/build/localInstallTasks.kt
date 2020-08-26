package org.jetbrains.kotlin.jupyter.build

import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.register

fun ProjectWithOptions.prepareLocalTasks() {
    tasks.register<Copy>("copyRunKernelPy") {
        group = localGroup
        dependsOn("cleanInstallDirLocal")
        from(distributionPath.resolve(runKernelDir).resolve(runKernelPy))
        into(installPathLocal)
    }

    tasks.register<Copy>("copyNbExtension") {
        group = localGroup
        from(nbExtensionPath)
        into(installPathLocal)
    }

    createInstallTasks(true, installPathLocal, installPathLocal)

    task("uninstall") {
        group = localGroup
        dependsOn("cleanInstallDirLocal")
    }
}
