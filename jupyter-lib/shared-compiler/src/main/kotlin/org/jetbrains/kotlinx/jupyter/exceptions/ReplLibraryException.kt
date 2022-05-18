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
    THROWABLE_RENDERERS("throwable renderers"),
    CONVERTERS("converters (fields callbacks)"),
    CLASS_ANNOTATIONS("class annotations callbacks"),
    FILE_ANNOTATIONS("file annotations callbacks"),
    BEFORE_CELL_CALLBACKS("initCell codes (before-cell-execution callbacks)"),
    AFTER_CELL_CALLBACKS("after-cell-execution callbacks"),
    INTERNAL_VARIABLES_MARKERS("internal variables markers"),
    SHUTDOWN("shutdown callbacks/codes"),
    INTERRUPTION_CALLBACKS("interruption callbacks"),
}

fun <T> rethrowAsLibraryException(part: LibraryProblemPart, action: () -> T): T {
    return try {
        action()
    } catch (e: Throwable) {
        throw ReplLibraryException(part = part, cause = e)
    }
}
