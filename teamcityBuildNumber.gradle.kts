

tasks.register("detectVersionsForTC") {
    outputs.upToDateWhen { false }

    doLast {
        println("##teamcity[buildNumber '${ detectVersion() }']")
    }
}

private fun detectVersion(): String {
    val baseVersion = project.property("baseVersion") as String
    val buildCounter = project.property("build.counter") as String
    val devCounter = project.findProperty("build.devCounter") as? String
    val isOnProtectedBranch = (project.property("build.branch") as String) == "master"
    val devAddition = if (isOnProtectedBranch && devCounter == null) "" else ".dev${devCounter ?: "1"}"

    return "$baseVersion.$buildCounter$devAddition"
}
