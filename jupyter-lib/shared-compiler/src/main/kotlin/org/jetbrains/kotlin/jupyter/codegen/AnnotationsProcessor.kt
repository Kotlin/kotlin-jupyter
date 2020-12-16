import org.jetbrains.kotlin.jupyter.api.Code
import org.jetbrains.kotlin.jupyter.api.GenerativeTypeHandler
import kotlin.reflect.KClass

interface AnnotationsProcessor {

    fun register(handler: GenerativeTypeHandler): Code

    fun process(kClass: KClass<*>): List<Code>
}
