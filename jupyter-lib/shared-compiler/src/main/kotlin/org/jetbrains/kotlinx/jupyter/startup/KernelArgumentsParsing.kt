package org.jetbrains.kotlinx.jupyter.startup

import org.jetbrains.kotlinx.jupyter.api.DEFAULT
import org.jetbrains.kotlinx.jupyter.api.ReplCompilerMode
import java.io.File
import kotlin.reflect.KMutableProperty0

/**
 * Builder class for kernel arguments that handles parsing and serialization of command-line parameters.
 *
 * @property classpath List of script classpath entries, null if not specified
 * @property homeDir Home directory for the kernel, null if not specified
 * @property debugPort Debug port for remote debugging, null if debugging is not enabled
 * @property clientType Type of client connecting to the kernel, null if not specified
 * @property jvmTargetForSnippets JVM target version for compiled snippets, null if using default
 * @property replCompilerMode Compiler mode for the REPL, null if using the default mode
 * @property extraCompilerArgs Additional compiler arguments, null if none specified
 * @property cfgFile Configuration file path, null if not specified
 */
class KernelArgumentsBuilder(
    var classpath: List<File>? = null,
    var homeDir: File? = null,
    var debugPort: Int? = null,
    var clientType: String? = null,
    var jvmTargetForSnippets: String? = null,
    var replCompilerMode: ReplCompilerMode? = null,
    var extraCompilerArgs: List<String>? = null,
    var cfgFile: File? = null,
) {
    constructor(kernelArgs: KernelArgs): this(
        classpath = kernelArgs.scriptClasspath,
        homeDir = kernelArgs.homeDir,
        debugPort = kernelArgs.debugPort,
        clientType = kernelArgs.clientType,
        jvmTargetForSnippets = kernelArgs.jvmTargetForSnippets,
        replCompilerMode = kernelArgs.replCompilerMode,
        extraCompilerArgs = kernelArgs.extraCompilerArguments,
        cfgFile = kernelArgs.cfgFile,
    )

    /**
     * List of bound parameters that connect parameter definitions with their property references.
     * This allows for automatic parsing and serialization of command-line arguments.
     */
    private val boundParameters = listOf(
        MutableBoundKernelParameter(classpathParameter, ::classpath),
        MutableBoundKernelParameter(homeDirParameter, ::homeDir),
        MutableBoundKernelParameter(debugPortParameter, ::debugPort),
        MutableBoundKernelParameter(clientTypeParameter, ::clientType),
        MutableBoundKernelParameter(jvmTargetParameter, ::jvmTargetForSnippets),
        MutableBoundKernelParameter(replCompilerModeParameter, ::replCompilerMode),
        MutableBoundKernelParameter(extraCompilerArgsParameter, ::extraCompilerArgs),
        MutableBoundKernelParameter(configFileParameter, ::cfgFile),
    )

    /**
     * Parses command-line arguments and updates the builder's properties accordingly.
     * Each argument is processed by the appropriate parameter handler.
     *
     * @param args Array of command-line arguments to parse
     * @return Resulting [KernelArgs]
     */
    fun parseArgs(args: Array<out String>): KernelArgs {
        parseKernelParameters(args, boundParameters)

        val cfgFileValue = cfgFile ?: throw IllegalArgumentException("config file is not provided")
        if (!cfgFileValue.exists() || !cfgFileValue.isFile) throw IllegalArgumentException("invalid config file $cfgFileValue")

        return KernelArgs(
            cfgFileValue,
            classpath ?: emptyList(),
            homeDir,
            debugPort,
            clientType,
            jvmTargetForSnippets,
            replCompilerMode ?: ReplCompilerMode.DEFAULT,
            extraCompilerArgs ?: emptyList(),
        )
    }

    /**
     * Converts the current state of the builder into a list of command-line arguments.
     * Only non-null properties are included in the result.
     *
     * @return List of command-line arguments representing the current state
     */
    fun argsList(): List<String> {
        return serializeKernelParameters(boundParameters)
    }
}

/**
 * Parameter handler for the configuration file path.
 * This is a positional parameter (not prefixed with a name) that specifies the path to the configuration file.
 */
val configFileParameter = object : KernelParameter<File> {
    override fun tryParse(arg: String, previousValue: File?): File? {
        if (previousValue != null){
            throw IllegalArgumentException("config file already set to $previousValue")
        }
        // Config file is a positional parameter, not a named one
        if (arg.startsWith("-")) return null
        return File(arg)
    }

    override fun serialize(value: File): String {
        return value.absolutePath
    }
}

/**
 * Parameter handler for the classpath entries.
 * Accepts multiple path entries separated by the platform-specific path separator.
 */
val classpathParameter = object : NamedKernelParameter<List<File>>(
    aliases = listOf("cp", "classpath")
) {
    override fun parseValue(argValue: String, previousValue: List<File>?): List<File> {
        return argValue.split(File.pathSeparator).map { File(it) }
    }

    override fun serializeValue(value: List<File>): String? {
        if (value.isEmpty()) return null
        return value.joinToString(File.pathSeparator) { it.absolutePath }
    }
}

/**
 * Parameter handler for the kernel home directory.
 * Specifies the base directory where the kernel will look for resources and configurations.
 */
val homeDirParameter = object : NamedKernelParameter<File>(
    aliases = listOf("home")
) {
    override fun parseValue(argValue: String, previousValue: File?): File {
        return File(argValue)
    }

    override fun serializeValue(value: File): String {
        return value.absolutePath
    }
}

/**
 * Parameter handler for the debug port.
 * Specifies the port number to use for remote debugging of the kernel.
 */
val debugPortParameter = object : NamedKernelParameter<Int>(
    aliases = listOf("debugPort")
) {
    override fun parseValue(argValue: String, previousValue: Int?): Int {
        return argValue.toInt()
    }

    override fun serializeValue(value: Int): String {
        return value.toString()
    }
}

/**
 * Parameter handler for the client type.
 * Specifies the type of client that is connecting to the kernel.
 */
val clientTypeParameter = SimpleNamedKernelStringParameter("client")

/**
 * Parameter handler for the JVM target version.
 * Specifies the target JVM version for compiled snippets (e.g., "1.8", "11", "17").
 */
val jvmTargetParameter = SimpleNamedKernelStringParameter("jvmTarget")

/**
 * Parameter handler for the REPL compiler mode.
 * Specifies the compilation mode to use for the REPL.
 */
val replCompilerModeParameter = object : NamedKernelParameter<ReplCompilerMode>(
    aliases = listOf("replCompilerMode")
) {
    override fun parseValue(argValue: String, previousValue: ReplCompilerMode?): ReplCompilerMode {
        return ReplCompilerMode.entries.find {
            it.name == argValue
        } ?: throw IllegalArgumentException("Invalid replCompilerMode: $argValue")
    }

    override fun serializeValue(value: ReplCompilerMode): String {
        return value.name
    }
}

/**
 * Parameter handler for additional compiler arguments.
 * Specifies extra arguments to pass to the Kotlin compiler when compiling snippets.
 */
val extraCompilerArgsParameter = object : NamedKernelParameter<List<String>>(
    aliases = listOf("extraCompilerArgs")
) {

    override fun parseValue(argValue: String, previousValue: List<String>?): List<String> {
        return argValue.split(",")
    }

    override fun serializeValue(value: List<String>): String? {
        if (value.isEmpty()) return null
        return value.joinToString(",")
    }
}

/**
 * Parses an array of command-line arguments using the provided parameter handlers.
 * For each argument, tries each parameter handler in order until one successfully parses it.
 * 
 * @param args Array of command-line arguments to parse
 * @param parameters List of parameter handlers to use for parsing
 */
fun parseKernelParameters(
    args: Array<out String>,
    parameters: List<MutableBoundKernelParameter<*>>,
) {
    for (arg in args) {
        for (parameter in parameters) {
            if (parameter.tryParse(arg)) break
        }
    }
}

/**
 * Serializes kernel parameters into a list of command-line arguments.
 * Each parameter is serialized only if it has a non-null value.
 * 
 * @param parameters List of parameter handlers to use for serialization
 * @return List of command-line arguments representing the parameters' values
 */
fun serializeKernelParameters(
    parameters: List<BoundKernelParameter<*>>,
): List<String> {
    return buildList {
        for (parameter in parameters) {
            parameter.serialize(this)
        }
    }
}

/**
 * Interface for handling the parsing and serialization of a specific kernel parameter type.
 * 
 * @param T The type of value that this parameter represents
 */
interface KernelParameter<T> {
    /**
     * Attempts to parse a command-line argument into a value of type T.
     * 
     * @param arg The command-line argument to parse
     * @param previousValue The previously parsed value, if any
     * @return The parsed value, or null if the argument couldn't be parsed by this handler
     * @throws Exception if the argument is recognized but invalid
     */
    fun tryParse(arg: String, previousValue: T?): T?

    /**
     * Serializes a value of type T to a string representation for command-line usage.
     * 
     * @param value The value to serialize
     * @return The string representation of the value, or null if the value shouldn't be included
     */
    fun serialize(value: T): String?
}

/**
 * Abstract base class for named kernel parameters that follow the format "-name=value".
 * Provides a common implementation for parsing and serializing named parameters.
 * 
 * @param T The type of value that this parameter represents
 * @property aliases List of alternative names for this parameter
 */
abstract class NamedKernelParameter<T: Any>(
    val aliases: List<String>,
): KernelParameter<T> {
    /**
     * Parses a string value into a value of type T.
     * This method is called after the parameter name has been matched and the value extracted.
     * 
     * @param argValue The string value to parse (without the parameter name prefix)
     * @param previousValue The previously parsed value, if any
     * @return The parsed value
     * @throws Exception if the value is invalid
     */
    abstract fun parseValue(argValue: String, previousValue: T?): T

    /**
     * Serializes a value of type T to a string representation (without the parameter name prefix).
     * 
     * @param value The value to serialize
     * @return The string representation of the value, or null if the value shouldn't be included
     */
    abstract fun serializeValue(value: T): String?

    /**
     * Attempts to parse a command-line argument if it matches one of this parameter's aliases.
     * Checks if the argument starts with "-alias=" for any of the aliases.
     * 
     * @param arg The command-line argument to parse
     * @param previousValue The previously parsed value, if any
     * @return The parsed value, or null if the argument doesn't match any of this parameter's aliases
     */
    override fun tryParse(arg: String, previousValue: T?): T? {
        for (alias in aliases) {
            val prefix = "-$alias="
            if (arg.startsWith(prefix)) {
                val value = arg.substringAfter(prefix)
                return parseValue(value, previousValue)
            }
        }
        return null
    }

    /**
     * Serializes a value to a command-line argument with the parameter's primary alias.
     * 
     * @param value The value to serialize
     * @return The command-line argument in the format "-alias=value", or null if the value shouldn't be included
     */
    override fun serialize(value: T): String? {
        val serializedValue = serializeValue(value) ?: return null
        return "-${aliases.first()}=$serializedValue"
    }
}

/**
 * Represents a simple named kernel parameter with a string value.
 * This parameter is identified by a single alias provided via the `name` property.
 * It inherits functionality to parse and serialize command-line arguments in the format "-name=value".
 *
 * @param name The name of the parameter, which serves as its alias.
 */
class SimpleNamedKernelStringParameter(
    val name: String,
): NamedKernelParameter<String>(listOf(name)) {
    override fun parseValue(argValue: String, previousValue: String?): String {
        return argValue
    }

    override fun serializeValue(value: String): String {
        return value
    }
}

/**
 * Class that binds a parameter handler to a value provider function.
 * This allows for serializing parameter values without directly accessing the storage mechanism.
 * 
 * @param T The type of value that this parameter represents
 * @property parameter The parameter handler to use for serialization
 * @property valueProvider Function that provides the current value of the parameter
 */
open class BoundKernelParameter<T: Any>(
    val parameter: KernelParameter<T>,
    open val valueProvider: () -> T?,
) {
    /**
     * Serializes the current parameter value and adds it to the argument list.
     * If the value is null or the parameter handler returns null, nothing is added.
     * 
     * @param argsBuilder The mutable list to add the serialized argument to
     */
    fun serialize(argsBuilder: MutableList<String>) {
        val value = valueProvider() ?: return
        val argValue = parameter.serialize(value) ?: return
        argsBuilder.add(argValue)
    }
}

/**
 * Class that extends BoundKernelParameter with the ability to update the parameter value.
 * This allows for both parsing and serializing parameter values.
 * 
 * @param T The type of value that this parameter represents
 * @property valueUpdater Function that updates the stored value of the parameter
 */
class MutableBoundKernelParameter<T: Any>(
    parameter: KernelParameter<T>,
    val valueUpdater: (T?) -> Unit,
    override val valueProvider: () -> T?,
): BoundKernelParameter<T>(parameter, valueProvider) {
    /**
     * Convenience constructor that uses a mutable property for both value updating and providing.
     * 
     * @param parameter The parameter handler to use
     * @param property The mutable property to bind to
     */
    constructor(
        parameter: KernelParameter<T>,
        property: KMutableProperty0<T?>,
    ) : this(parameter, property::set, property::get)

    /**
     * Attempts to parse a command-line argument and update the parameter value if successful.
     * 
     * @param arg The command-line argument to parse
     * @return true if the argument was successfully parsed and the value updated, false otherwise
     */
    fun tryParse(arg: String): Boolean {
        val newValue = parameter.tryParse(arg, valueProvider()) ?: return false
        valueUpdater(newValue)
        return true
    }
}
