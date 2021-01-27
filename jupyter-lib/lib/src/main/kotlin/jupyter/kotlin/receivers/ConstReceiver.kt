package jupyter.kotlin.receivers

class ConstReceiver(val value: Int)

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class TempAnnotation
