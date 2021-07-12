package org.jetbrains.kotlinx.jupyter.build

import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.register

fun ProjectWithOptions.prepareLocalTasks() {
    tasks.register<Copy>("copyRunKernelPy") {
        group = localGroup
        dependsOn("cleanInstallDirLocal")
        from(distributionPath.resolve(runKernelDir).resolve(runKernelPy))
        from(distributionPath.resolve(kotlinKernelModule)) {
            into(kotlinKernelModule)
        }
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
