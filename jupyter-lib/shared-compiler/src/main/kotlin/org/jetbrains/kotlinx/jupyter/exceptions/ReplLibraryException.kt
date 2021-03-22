package org.jetbrains.kotlinx.jupyter.exceptions

class ReplLibraryException(
    libraryName: String? = null,
    val part: LibraryProblemPart,
    cause: Throwable? = null,
) : ReplException("The problem is found in ${libraryName?.let { "library $it" } ?: "one of the loaded libraries"}: check library ${part.message}", cause)

enum class LibraryProblemPart(val message: String) {
    PREBUILT("imports, dependencies and repositories"),
    INIT("init codes"),
    RESOURCES("resources definitions"),
    RENDERERS("renderers"),
    CONVERTERS("converters (fields callbacks)"),
    CLASS_ANNOTATIONS("class annotations callbacks"),
    FILE_ANNOTATIONS("file annotations callbacks"),
    BEFORE_CELL_CALLBACKS("initCell codes (before-cell-execution callbacks)"),
    AFTER_CELL_CALLBACKS("after-cell-execution callbacks"),
    SHUTDOWN("shutdown callbacks/codes"),
}

fun <T> rethrowAsLibraryException(part: LibraryProblemPart, action: () -> T): T {
    return try {
        action()
    } catch (e: Throwable) {
        throw ReplLibraryException(part = part, cause = e)
    }
}
