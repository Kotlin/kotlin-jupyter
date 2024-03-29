package jupyter.kotlin

import java.io.File
import kotlin.reflect.KClass

object Native {
    /**
     * Load library by its absolute path
     */
    fun load(filename: String) = System.load(filename)

    fun load(file: File) = System.load(file.absolutePath)

    /**
     * Load library by its name
     */
    fun loadLibrary(name: String) = System.loadLibrary(name)

    fun loadLibrary(
        name: String,
        path: File,
    ) = load(toAbsolutePath(name, path))

    fun loadLibrary(
        name: String,
        path: String,
    ) = loadLibrary(name, File(path))

    /**
     * Load library by its absolute path from a different classloader
     */
    fun load(
        kClass: KClass<*>,
        filename: String,
    ) = load0(kClass, filename, true)

    fun load(
        kClass: KClass<*>,
        file: File,
    ) = load0(kClass, file.absolutePath, true)

    fun load(
        javaClass: Class<*>,
        filename: String,
    ) = load0(javaClass, filename, true)

    fun load(
        javaClass: Class<*>,
        file: File,
    ) = load0(javaClass, file.absolutePath, true)

    /**
     * Load library by its name from a different classloader
     */
    fun loadLibrary(
        kClass: KClass<*>,
        name: String,
    ) = load0(kClass, name, false)

    fun loadLibrary(
        kClass: KClass<*>,
        name: String,
        path: File,
    ) = load0(kClass, toAbsolutePath(name, path).absolutePath, true)

    fun loadLibrary(
        kClass: KClass<*>,
        name: String,
        path: String,
    ) = loadLibrary(kClass, name, File(path))

    fun loadLibrary(
        javaClass: Class<*>,
        name: String,
    ) = load0(javaClass, name, false)

    fun loadLibrary(
        javaClass: Class<*>,
        name: String,
        path: File,
    ) = load0(javaClass, toAbsolutePath(name, path).absolutePath, true)

    fun loadLibrary(
        javaClass: Class<*>,
        name: String,
        path: String,
    ) = loadLibrary(javaClass, name, File(path))

    private fun toAbsolutePath(
        name: String,
        path: File,
    ): File {
        val relativeFilename = System.mapLibraryName(name)
        return path.resolve(relativeFilename)
    }

    private fun load0(
        kClass: KClass<*>,
        name: String,
        isAbsolute: Boolean,
    ) {
        load0(kClass.java, name, isAbsolute)
    }

    private fun load0(
        javaClass: Class<*>,
        name: String,
        isAbsolute: Boolean,
    ) {
        val clazz = ClassLoader::class.java
        val method =
            clazz.getDeclaredMethod(
                "loadLibrary",
                Class::class.java,
                java.lang.String::class.java,
                Boolean::class.java,
            )
        method.isAccessible = true
        method.invoke(null, javaClass, name, isAbsolute)
    }
}
