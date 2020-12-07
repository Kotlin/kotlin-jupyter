package jupyter.kotlin.receivers

class TypeProviderReceiver {

    fun generateCode(values: List<Int>): List<String> {
        val properties = (values.indices).joinToString("\n") { "val value$it : Int get() = list[$it]" }

        val classDeclaration =
            """
                class TypedIntList###(val list: List<Int>): List<Int> by list {
                    $properties                    
                }
            """

        val converter = "TypedIntList###(\$it)"
        return listOf(classDeclaration, converter)
    }
}
