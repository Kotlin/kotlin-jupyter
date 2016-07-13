@file:Suppress("unused")

package jupyter.kotlin

@Target(AnnotationTarget.FILE, AnnotationTarget.EXPRESSION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class DependsOn(val value: String)

@Target(AnnotationTarget.FILE, AnnotationTarget.EXPRESSION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Repository(val value: String)

