package org.jetbrains.kotlinx.jupyter.build

fun ProjectWithOptions.preparePropertiesTask() {
    tasks.register("buildProperties") {
        group = buildGroup
        val outputDir = file(getSubDir(buildDir.toPath(), resourcesDir, mainSourceSetDir))

        inputs.property("version", version)
        inputs.property("currentBranch", getCurrentBranch())
        inputs.property("currentSha", getCurrentCommitSha())
        rootProject.findProperty("jvmTargetForSnippets")?.let {
            inputs.property("jvmTargetForSnippets", it)
        }

        inputs.file(librariesPropertiesPath)

        outputs.dir(outputDir)

        doLast {
            outputDir.mkdirs()
            val propertiesFile = file(getSubDir(outputDir.toPath(), runtimePropertiesFile))

            val properties = inputs.properties.entries.map { it.toPair() }.toMutableList()
            properties.apply {
                val librariesProperties = readProperties(librariesPropertiesPath)
                add("librariesFormatVersion" to librariesProperties["formatVersion"])
            }

            propertiesFile.writeText(properties.joinToString("") { "${it.first}=${it.second}\n" })
        }
    }
}
