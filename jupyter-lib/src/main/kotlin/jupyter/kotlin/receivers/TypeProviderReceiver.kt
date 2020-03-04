package jupyter.kotlin.receivers

class TypeProviderReceiver {

    fun generateCode(values: List<Int>): List<String> {
        val properties = (0 until values.size)
                .map { "val value$it : Int get() = list[$it]" }
                .joinToString("\n")

        val classDeclaration = """
                class TypedIntList###(val list: List<Int>): List<Int> by list {
                    $properties                    
                }
            """

        val converter = "TypedIntList###(\$it)"
        return listOf(classDeclaration, converter)
    }
}
