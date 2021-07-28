tasks.register("detectVersionsForTC") {
    outputs.upToDateWhen { false }

    doLast {
        println("##teamcity[buildNumber '${ detectVersion() }']")
    }
}

fun readProperties(propertiesFile: java.io.File): Map<String, String> =
    propertiesFile.readText().lineSequence()
        .map { it.split("=") }
        .filter { it.size == 2 }
        .map { it[0].trim() to it[1].trim() }.toMap()

fun detectVersion(): String {
    val baseVersion = readProperties(file("../gradle.properties"))["baseVersion"]
    val buildCounter = project.property("build.counter") as String
    val devCounter = project.findProperty("build.devCounter") as? String
    val isOnProtectedBranch = (project.property("build.branch") as String) == "master"
    val devAddition = if (isOnProtectedBranch && devCounter == null) "" else ".dev${devCounter ?: "1"}"

    return "$baseVersion.$buildCounter$devAddition"
}
