[![JetBrains official project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![PyPI](https://img.shields.io/pypi/v/kotlin-jupyter-kernel?label=PyPi)](https://pypi.org/project/kotlin-jupyter-kernel/)
[![Anaconda](https://anaconda.org/jetbrains/kotlin-jupyter-kernel/badges/version.svg)](https://anaconda.org/jetbrains/kotlin-jupyter-kernel)
[![Gradle plugin](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/org/jetbrains/kotlin/kotlin-jupyter-api-gradle-plugin/maven-metadata.xml.svg?label=Gradle+plugin)](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jupyter.api)
[![Maven Central](https://img.shields.io/maven-central/v/org.jetbrains.kotlinx/kotlin-jupyter-kernel?color=blue&label=Maven%20artifacts)](https://search.maven.org/search?q=kotlin-jupyter)
[![GitHub](https://img.shields.io/github/license/Kotlin/kotlin-jupyter)](https://www.apache.org/licenses/LICENSE-2.0)
[![Binder](https://mybinder.org/badge_logo.svg)](https://mybinder.org/v2/gh/kotlin/kotlin-jupyter/master?filepath=samples)

# Kotlin kernel for IPython/Jupyter

[Kotlin](https://kotlinlang.org/) (1.5.30-dev-2630) kernel for [Jupyter](https://jupyter.org).

Beta version. Tested with Jupyter Notebook 6.0.3, Jupyter Lab 1.2.6 and Jupyter Console 6.1.0
on Windows, Ubuntu Linux and macOS.

![Screenshot in Jupyter](Screenshot.png)

To start using Kotlin kernel for Jupyter take a look at [introductory guide](https://github.com/cheptsov/kotlin-jupyter-demo/blob/master/index.ipynb).

Example notebooks can be found in the [samples](../samples) folder

Try samples online: [![Binder](https://mybinder.org/badge_logo.svg)](https://mybinder.org/v2/gh/kotlin/kotlin-jupyter/master?filepath=samples)

## Installation

There are three ways to install the kernel:

### Conda

If you have `conda` installed, just run the following command to install stable package version:
 
`conda install -c jetbrains kotlin-jupyter-kernel` ([package home](https://anaconda.org/jetbrains/kotlin-jupyter-kernel))

To install conda package from the dev channel:

`conda install -c jetbrains-dev kotlin-jupyter-kernel` ([package home](https://anaconda.org/jetbrains-dev/kotlin-jupyter-kernel))

Uninstall: `conda remove kotlin-jupyter-kernel`

### Pip

You can also install this package through `pip`:
 
Stable:
`pip install kotlin-jupyter-kernel` ([package home](https://pypi.org/project/kotlin-jupyter-kernel/))

Dev:
`pip install -i https://test.pypi.org/simple/ kotlin-jupyter-kernel` ([package home](https://test.pypi.org/project/kotlin-jupyter-kernel/))

Uninstall: `pip uninstall kotlin-jupyter-kernel`

### From sources

```bash
git clone https://github.com/Kotlin/kotlin-jupyter.git
cd kotlin-jupyter
./gradlew install
```

Default installation path is `~/.ipython/kernels/kotlin/`.
To install to some other location use option `-PinstallPath=`, but note that Jupyter
looks for the kernel specs files only in predefined places. For more detailed info
see [Jupyter docs](https://jupyter-client.readthedocs.io/en/stable/kernels.html#kernel-specs).

Uninstall: `./gradlew uninstall`

### Troubleshooting

There could be a problem with kernel spec detection because of different
python environments and installation modes. If you are using pip or conda
to install the package, try running post-install fixup script:
```bash
python -m kotlin_kernel fix-kernelspec-location
```

This script replaces kernel specs to the "user" path where they are always detected.
Don't forget to re-run this script on the kernel update.

## Usage

- `jupyter console --kernel=kotlin`
- `jupyter notebook`
- `jupyter lab`

To start using `kotlin` kernel inside Jupyter Notebook or JupyterLab create a new notebook with `kotlin` kernel.

The default kernel will use the JDK pointed to by the environment variable `KOTLIN_JUPYTER_JAVA_HOME`,
or `JAVA_HOME` if the first is not set.

JVM arguments will be set from the environment variable `KOTLIN_JUPYTER_JAVA_OPTS` or `JAVA_OPTS` if the first is not set.
Additionally, arguments from `KOTLIN_JUPYTER_JAVA_OPS_EXTRA` will be added.
Arguments are parsed using [`shlex.split`](https://docs.python.org/3/library/shlex.html).

### Creating Kernels

To create a kernel for a specific JDK, JVM arguments, and environment variables, you can use the `add-kernel` script:
```bash
python -m kotlin_kernel add-kernel [--name name] [--jdk jdk_home_dir] [--set-jvm-args] [--jvm-arg arg]* [--env KEY VALUE]* [--force]
```
The command uses `argparse`, so `--help`, `@argfile` (you will need to escape the `@` in powershell), and `--opt=value` are all supported.  `--jvm-arg=arg` in particular
is needed when passing JVM arguments that start with `-`.

If `jdk` not specified, `name` is required.  If `name` is not specified but `jdk` is the name will be 
`JDK $vendor $version` detected from the JDK.  Regardless, the actual name of the kernel will be `Kotlin ($name)`, 
and the directory will be `kotlin_$name` with the spaces in `name` replaced by underscores 
(so make sure it's compatible with your file system).

JVM arguments are joined with a `' '`, so multiple JVM arguments in the same argument are supported.
The arguments will be added to existing ones (see above section) unless `--set-jvm-args` is present, in which case they
will be set to `KOTLIN_JUPYTER_JAVA_OPTS`.  Note that both adding and setting work fine alongside `KOTLIN_JUPYTER_JAVA_OPS_EXTRA`.

While jupyter kernel environment variable substitutions are supported in `env`, note that if the used environment 
variable doesn't exist, nothing will be replaced.

An example:
```bash
python -m kotlin_kernel add-kernel --name "JDK 15 Big 2 GPU" --jdk ~/.jdks/openjdk-15.0.2 --jvm-arg=-Xmx8G --env CUDA_VISIBLE_DEVICES 0,1
```

## Supported functionality

### REPL commands

The following REPL commands are supported:
 - `:help` - display help
 - `:classpath` - show current classpath
 
### Dependencies resolving annotations

It is possible to add dynamic dependencies to the notebook using the following annotations:
 - `@file:DependsOn(<coordinates>)` - adds artifacts to classpath. Supports absolute and relative paths to class 
   directories or jars, ivy and maven artifacts represented by the colon separated string
 - `@file:Repository(<absolute-path>)` - adds a directory for relative path resolution or ivy/maven repository.
 To specify Maven local, use `@file:Repository("*mavenLocal")`.
 
Note that dependencies in remote repositories are resolved via Ivy resolver.
Caches are stored in `~/.ivy2/cache` folder by default. Sometimes, due to network
issues or running several artifacts resolutions in parallel, caches may get corrupted.
If you have some troubles with artifacts resolution, please remove caches, restart kernel
and try again.
 
### Default repositories

The following maven repositories are included by default:
 - [Maven Central](https://repo.maven.apache.org/maven2)
 - [JitPack](https://jitpack.io/)

### Line Magics

The following line magics are supported:
 - `%use` - injects code for supported libraries: artifact resolution, default imports, initialization code, type renderers. Usage example: `%use klaxon(5.5), lets-plot`
 - `%trackClasspath` - logs any changes of current classpath. Useful for debugging artifact resolution failures.
 - `%trackExecution` - logs pieces of code that are going to be executed. Useful for debugging of libraries support.
 - `%useLatestDescriptors` - use latest versions of library descriptors available. By default, bundled descriptors are used. Usage example: `%useLatestDescriptors -[on|off]`
 - `%output` - output capturing settings. Usage example: `%output --max-cell-size=1000 --no-stdout --max-time=100 --max-buffer=400`
 - `%logLevel` - set logging level. Usage example: `%logLevel [off|error|warn|info|debug]`
 
 See detailed info about line magics [here](magics.md).
 
### Supported Libraries

When a library is included with `%use` keyword, the following functionality is added to the notebook:
 - repositories to search for library artifacts
 - artifact dependencies
 - default imports
 - library initialization code
 - renderers for special types, e.g. charts and data frames

This behavior is defined by `json` library descriptor. Descriptors for all supported libraries can be found in [libraries](../libraries) directory.
A library descriptor may provide a set of properties with default values that can be overridden when library is included.
The major use case for library properties is to specify a particular version of library. If descriptor has only one property, it can be 
defined without naming:
```
%use krangl(0.10)
```
If library descriptor defines more than one property, property names should be used:
```
%use spark(scala=2.11.10, spark=2.4.2)
```
Several libraries can be included in single `%use` statement, separated by `,`:
```
%use lets-plot, krangl, mysql(8.0.15)
```
You can also specify the source of library descriptor. By default, it's taken from the `libraries` directory
of kernel installation. If you want to try descriptor from another revision, use the following syntax:
```
// Specify some git tag from this repository
%use lets-plot@0.8.2.5
// Specify commit sha, with more verbose syntax
%use lets-plot@ref[24a040fe22335648885b106e2f4ddd63b4d49469]
// Specify git ref along with library arguments
%use krangl@dev(0.10)
```
Other options are resolving library descriptor from a local file or from remote URL:
```
// Load library from file
%use mylib@file[/home/user/lib.json]
// Load library from file: kernel will guess it's a file actually
%use @/home/user/libs/lib.json
// Or use another approach: specify a directory and file name without 
// extension (it should be JSON in such case) before it
%use lib@/home/user/libs
// Load library descriptor from a remote URL
%use herlib@url[https://site.com/lib.json]
// If your URL responds with 200(OK), you may skip `url[]` part:
%use @https://site.com/lib.json
// You may omit library name for file and URL resolution:
%use @file[lib.json]
```

#### List of supported libraries:
 - [dataframe](https://github.com/nikitinas/dataframe) - Kotlin framework for structured data processing
 - [deeplearning4j](https://github.com/eclipse/deeplearning4j) - Deep learning library for the JVM
 - [deeplearning4j-cuda](https://github.com/eclipse/deeplearning4j) - Deep learning library for the JVM (CUDA support)
 - default - Default imports: dataframe and Lets-Plot libraries
 - [exposed](https://github.com/JetBrains/Exposed) - Kotlin SQL framework
 - [fuel](https://github.com/kittinunf/fuel) - HTTP networking library
 - [gral](https://github.com/eseifert/gral) - Java library for displaying plots
 - [jdsp](https://github.com/psambit9791/jDSP) - Java library for signal processing
 - [kaliningraph](https://github.com/breandan/kaliningraph) - Graph library with a DSL for constructing graphs and visualizing the behavior of graph algorithms
 - [khttp](https://github.com/jkcclemens/khttp) - HTTP networking library
 - [klaxon](https://github.com/cbeust/klaxon) - JSON parser for Kotlin
 - [kmath](https://github.com/mipt-npm/kmath) - Experimental Kotlin algebra-based mathematical library
 - [kotlin-dl](https://github.com/JetBrains/KotlinDL) - KotlinDL library which provides Keras-like API for deep learning
 - [kotlin-statistics](https://github.com/thomasnield/kotlin-statistics) - Idiomatic statistical operators for Kotlin
 - [krangl](https://github.com/holgerbrandl/krangl) - Kotlin DSL for data wrangling
 - [kravis](https://github.com/holgerbrandl/kravis) - Kotlin grammar for data visualization
 - [lets-plot](https://github.com/JetBrains/lets-plot-kotlin) - ggplot-like interactive visualization for Kotlin
 - [lets-plot-dataframe](https://github.com/JetBrains/lets-plot-kotlin) - A bridge between Lets-Plot and dataframe libraries
 - [lets-plot-gt](https://github.com/JetBrains/lets-plot-kotlin) - Lets-Plot visualisation for GeoTools toolkit
 - [lib-ext](https://github.com/Kotlin/kotlin-jupyter) - Extended functionality for Jupyter kernel
 - [londogard-nlp-toolkit](https://github.com/londogard/londogard-nlp-toolkit) - A Natural Language Processing (NLP) toolkit for Kotlin on the JVM
 - [multik](https://github.com/Kotlin/multik) - Multidimensional array library for Kotlin
 - [mysql](https://github.com/mysql/mysql-connector-j) - MySql JDBC Connector
 - [plotly](https://github.com/mipt-npm/plotly.kt) - [beta] Plotly.kt jupyter integration for static plots.
 - [plotly-server](https://github.com/mipt-npm/plotly.kt) - [beta] Plotly.kt jupyter integration for dynamic plots.
 - [rdkit](https://www.rdkit.org/) - Open-Source Cheminformatics Software
 - [serialization](https://github.com/Kotlin/kotlinx.serialization) - Kotlin multi-format reflection-less serialization
 - [smile](https://github.com/haifengl/smile) - Statistical Machine Intelligence and Learning Engine
 - [spark](https://github.com/apache/spark) - Unified analytics engine for large-scale data processing

### Rich output
  
By default, the return values from REPL statements are displayed in the text form. To use richer representations, e.g.
 to display graphics or html, it is possible to send MIME-encoded result to the client using the `MIME` helper function: 
```kotlin
fun MIME(vararg mimeToData: Pair<String, Any>): MimeTypedResult 
```
E.g.:
```kotlin
MIME("text/html" to "<p>Some <em>HTML</em></p>", "text/plain" to "No HTML for text clients")

```
HTML outputs can also be rendered with `HTML` helper function:
```kotlin
fun HTML(text: String): MimeTypedResult
```

### Autocompletion

Press `TAB` to get the list of suggested items for completion. In Jupyter Notebook, you don't need to press `TAB`,
completion is requested automatically. Completion works for all globally defined symbols and for local symbols 
which were loaded into notebook during cells evaluation. 

### Error analysis

If you use Jupyter Notebook as Jupyter client, you will also see that compilation errors and warnings are underlined in
red and in yellow correspondingly. This is achieved by kernel-level extension of Jupyter notebook which sends
error-analysis requests to kernel and renders their results. If you hover the cursor over underlined text, you will get 
an error message which can help you to fix the error.

## Debugging

1. Run `./gradlew installDebug`. Use option `-PdebugPort=` to specify port address for the debugger. Default port is 1044.
2. Run `jupyter-notebook`
3. Attach a remote debugger to JVM with specified port 

## Adding new libraries

Read [this article](libraries.md) if you want to support new `JVM` library in the kernel.

## Documentation

There is a [site](https://ileasile.github.io/kotlin-jupyter-docs) with rendered KDoc comments from the codebase.
If you are a library author you may be interested in `api` module
(see [adding new libraries](#adding-new-libraries)). There is also a `lib` module which contains entities
available from the Notebook cells and `shared-compiler` module which may be used for Jupyter REPL integration
into standalone application or IDEA plugin.
