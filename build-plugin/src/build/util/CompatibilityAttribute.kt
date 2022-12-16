package build.util

data class CompatibilityAttribute (
    val tcPropertyName: String,
    val mdDescription: String,
    private val getValue: () -> String,
) {
    val value: String get() = getValue()
}
