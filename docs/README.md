[![JetBrains official project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Kotlin beta stability](https://img.shields.io/badge/project-beta-kotlin.svg?colorA=555555&colorB=AC29EC&label=&logo=kotlin&logoColor=ffffff&logoWidth=10)](https://kotlinlang.org/docs/components-stability.html)
[![PyPI](https://img.shields.io/pypi/v/kotlin-jupyter-kernel?label=PyPi)](https://pypi.org/project/kotlin-jupyter-kernel/)
[![Anaconda](https://anaconda.org/jetbrains/kotlin-jupyter-kernel/badges/version.svg)](https://anaconda.org/jetbrains/kotlin-jupyter-kernel)
[![Gradle plugin](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/org/jetbrains/kotlin/kotlin-jupyter-api-gradle-plugin/maven-metadata.xml.svg?label=Gradle+plugin)](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jupyter.api)
[![Maven Central](https://img.shields.io/maven-central/v/org.jetbrains.kotlinx/kotlin-jupyter-kernel?color=blue&label=Maven%20artifacts)](https://search.maven.org/search?q=kotlin-jupyter)
[![GitHub](https://img.shields.io/github/license/Kotlin/kotlin-jupyter)](https://www.apache.org/licenses/LICENSE-2.0)
[![Binder](https://mybinder.org/badge_logo.svg)](https://mybinder.org/v2/gh/kotlin/kotlin-jupyter/master?filepath=samples)

# Kotlin kernel for IPython/Jupyter

[Kotlin](https://kotlinlang.org/) (1.8.20-Beta) kernel for [Jupyter](https://jupyter.org).

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
git clone --recurse-submodules https://github.com/Kotlin/kotlin-jupyter.git
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

## Updating

Depending on the platform you're using, updating the Kotlin Jupyter kernel can be done in the following ways:

### Datalore

To update the kernel in Datalore, simply add an `environment.yml` to the Notebook files containing:
```yaml
datalore-env-format-version: "0.2"
datalore-package-manager: "pip"
datalore-base-env: "default"
dependencies:
- pip:
  - kotlin-jupyter-kernel=={VERSION}
```
where `{VERSION}` should be replaced by the latest PyPi version of the Kotlin Jupyter kernel, such as `0.11.0.198`.
Stop and restart the machine afterwards.

### Conda

If you have `conda` installed, just run the following command to update the stable package version:

`conda update -c jetbrains kotlin-jupyter-kernel`

To update the conda package from the dev channel:

`conda update -c jetbrains-dev kotlin-jupyter-kernel`

If you want to change to a specific version of the kernel, take the `install` command from above and add `={VERSION}` to `kotlin-jupyter-kernel` where `{VERSION}` should be replaced by the latest PyPi version of the Kotlin Jupyter kernel, such as `0.11.0.198`.

For example, for the stable version:

`conda install -c jetbrains kotlin-jupyter-kernel={VERSION}`

### Pip

To update the kernel using Pip, simply run:

Stable:
`pip install kotlin-jupyter-kernel --upgrade`

Dev:
`pip install -i https://test.pypi.org/simple/ kotlin-jupyter-kernel --upgrade`

If you want to change to a specific version of the kernel, take the `install` command from above and add `=={VERSION}` to `kotlin-jupyter-kernel` where `{VERSION}` should be replaced by the latest PyPi version of the Kotlin Jupyter kernel, such as `0.11.0.198`.

For example, for the stable version:

`pip install kotlin-jupyter-kernel=={VERSION} --ignore-installed`

### Kotlin Notebook

Kotlin Notebook plugin is provided with built-in kernel. To update the kernel, update plugin in IDEA (`File` -> `Settings...` -> `Plugins` -> `Kotlin Notebook`) and restart the IDE.

## Usage

- `jupyter console --kernel=kotlin`
- `jupyter notebook`
- `jupyter lab`

To start using `kotlin` kernel inside Jupyter Notebook or JupyterLab create a new notebook with `kotlin` kernel.

The default kernel will use the JDK pointed to by the environment variable `KOTLIN_JUPYTER_JAVA_HOME`,
or `JAVA_HOME` if the first is not set.

JVM arguments will be set from the environment variable `KOTLIN_JUPYTER_JAVA_OPTS` or `JAVA_OPTS` if the first is not set.
Additionally, arguments from `KOTLIN_JUPYTER_JAVA_OPTS_EXTRA` will be added.
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
will be set to `KOTLIN_JUPYTER_JAVA_OPTS`.  Note that both adding and setting work fine alongside `KOTLIN_JUPYTER_JAVA_OPTS_EXTRA`.

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
 - `:vars` - get visible variables values
 
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
 - `%trackClasspath` - logs any changes of current classpath. Useful for debugging artifact resolution failures. Usage example: `%trackClasspath [on|off]`
 - `%trackExecution` - logs pieces of code that are going to be executed. Useful for debugging of libraries support. Usage example: `%trackExecution [all|generated|off]`
 - `%useLatestDescriptors` - use latest versions of library descriptors available. By default, bundled descriptors are used. Usage example: `%useLatestDescriptors [on|off]`
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

This behavior is defined by `json` library descriptor. Descriptors for all supported libraries can be found in [libraries](https://github.com/Kotlin/kotlin-jupyter-libraries) repository.
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
You can also specify the source of library descriptor. By default, it's taken from the [libraries repository](https://github.com/Kotlin/kotlin-jupyter-libraries). If you want to try descriptor from another revision, use the following syntax:
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
 - [biokotlin](https://bitbucket.org/bucklerlab/biokotlin) - BioKotlin aims to be a high-performance bioinformatics library that brings the power and speed of compiled programming languages to scripting and big data environments.
 - [combinatoricskt](https://github.com/shiguruikai/combinatoricskt) - A combinatorics library for Kotlin
 - [coroutines](https://github.com/Kotlin/kotlinx.coroutines) - Asynchronous programming and reactive streams support
 - [dataframe](https://github.com/Kotlin/dataframe) - Kotlin framework for structured data processing
 - [datetime](https://github.com/Kotlin/kotlinx-datetime) - Kotlin date/time library
 - [deeplearning4j](https://github.com/eclipse/deeplearning4j) - Deep learning library for the JVM
 - [deeplearning4j-cuda](https://github.com/eclipse/deeplearning4j) - Deep learning library for the JVM (CUDA support)
 - default - Default imports: dataframe and Lets-Plot libraries
 - [exposed](https://github.com/JetBrains/Exposed) - Kotlin SQL framework
 - [fuel](https://github.com/kittinunf/fuel) - HTTP networking library
 - [ggdsl](https://github.com/AndreiKingsley/lib-ggdsl) - Kotlin plotting DSL for Lets-Plot
 - [ggdsl-echarts](https://github.com/AndreiKingsley/lib-ggdsl) - Kotlin plotting DSL for Apache ECharts
 - [gral](https://github.com/eseifert/gral) - Java library for displaying plots
 - [jdsp](https://github.com/psambit9791/jDSP) - Java library for signal processing
 - [kalasim](https://www.kalasim.org) - Discrete event simulator
 - [kaliningraph](https://github.com/breandan/kaliningraph) - Graph library with a DSL for constructing graphs and visualizing the behavior of graph algorithms
 - [khttp](https://github.com/jkcclemens/khttp) - HTTP networking library
 - [klaxon](https://github.com/cbeust/klaxon) - JSON parser for Kotlin
 - [kmath](https://github.com/mipt-npm/kmath) - Experimental Kotlin algebra-based mathematical library
 - [kotlin-dl](https://github.com/Kotlin/kotlindl) - KotlinDL library which provides Keras-like API for deep learning
 - [kotlin-statistics](https://github.com/thomasnield/kotlin-statistics) - Idiomatic statistical operators for Kotlin
 - [krangl](https://github.com/holgerbrandl/krangl) - Kotlin DSL for data wrangling
 - [kraphviz](https://github.com/nidi3/graphviz-java) - Graphviz wrapper for JVM
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
 - [reflection](https://kotlinlang.org/docs/reflection.html) - Imports for Kotlin Reflection
 - [roboquant](https://roboquant.org) - Algorithmic trading platform written in Kotlin
 - [serialization](https://github.com/Kotlin/kotlinx.serialization) - Kotlin multi-format reflection-less serialization
 - [smile](https://github.com/haifengl/smile) - Statistical Machine Intelligence and Learning Engine
 - [spark](https://github.com/JetBrains/kotlin-spark-api) - Kotlin API for Apache Spark: unified analytics engine for large-scale data processing
 - [spark-streaming](https://github.com/JetBrains/kotlin-spark-api) - Kotlin API for Apache Spark Streaming: scalable, high-throughput, fault-tolerant stream processing of live data streams

### Rich output
  
By default, the return values from REPL statements are displayed in the text form. To use richer representations, e.g.
 to display graphics or html, it is possible to send MIME-encoded result to the client using the `MIME` helper function: 
```kotlin
fun MIME(vararg mimeToData: Pair<String, String>): MimeTypedResult 
```
E.g.:
```kotlin
MIME("text/html" to "<p>Some <em>HTML</em></p>", "text/plain" to "No HTML for text clients")

```
HTML outputs can also be rendered with `HTML` helper function:
```kotlin
fun HTML(text: String): MimeTypedResult
```

### Rendering

Rendering is a procedure of transforming of the value to the form that is appropriate for displaying in Jupyter client. Kernel supports several features that allow you to render values.

#### Renderers

Renderers can transform a value into another value. Library can define one or several renderers. Rendering with renderers is controlled via `RenderersProcessor`. You can access it via `notebook`. Renderers are applied until at least one renderer can be applied.

#### DisplayResult and Renderable

If object implements `DisplayResult` or `Renderable`, it will be rendered to output `JsonObject` via its own corresponding method.

#### Text rendering

Text renderers render objects to strings. Library can define one or several text renderers. Rendering with text renderers is controlled via `TextRenderersProcessor`. You can access it via `notebook`. Text renderers are applied until at least one renderer returns non-null string for a passed argument. This kind of renderers can be easily composed with each other. I.e. text renderer for iterables can render its elements with text renderers processor recursively.

#### Common rendering semantics

Evaluated value is firstly transformed with RenderersProcessor. Resulting value is checked. If it's Renderable or DisplayResult, it is transformed into output JSON using `toJson()` method. If it's Unit, the cell won't have result at all. Otherwise, value is passed to `TextRenderersProcessor`. It tries to render the value to string using defined text renderers having in mind their priority. If all the renderers returned null, value is transformed to string using `toString()`. Resulting string is wrapped to `text/plain` MIME JSON.  

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

1. Run `./gradlew installDebug`. Debugger port is selected automatically.
   Default port is 1044, consequent ports will be used if it's in use. If you want an exact port, specify `-PdebugPort=<port>` Gradle option.
2. Run `jupyter notebook`, open the desired notebook.
3. Attach a remote debugger to JVM with corresponding port (debug port number will be printed in terminal on kernel startup).

## Adding new libraries

Read [this article](libraries.md) if you want to support new `JVM` library in the kernel.

## Documentation

There is a [site](https://ileasile.github.io/kotlin-jupyter-docs) with rendered KDoc comments from the codebase.
If you are a library author you may be interested in `api` module
(see [adding new libraries](#adding-new-libraries)). There is also a `lib` module which contains entities
available from the Notebook cells and `shared-compiler` module which may be used for Jupyter REPL integration
into standalone application or IDEA plugin.
