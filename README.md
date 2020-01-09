[![JetBrains official project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![PyPI](https://img.shields.io/pypi/v/kotlin-jupyter-kernel?label=PyPi)](https://pypi.org/project/kotlin-jupyter-kernel/)
[![Anaconda](https://img.shields.io/conda/v/jetbrains/kotlin-jupyter-kernel?label=Anaconda)](https://anaconda.org/jetbrains/kotlin-jupyter-kernel)
[![GitHub](https://img.shields.io/github/license/Kotlin/kotlin-jupyter)](https://www.apache.org/licenses/LICENSE-2.0)

# Kotlin kernel for IPython/Jupyter

[Kotlin](https://kotlinlang.org/) (1.3.70) kernel for [Jupyter](https://jupyter.org).

Alpha version. Tested with Jupyter 6.0.1 on OS X so far.

![Screenshot in Jupyter](./samples/Screenshot.png)

To start using Kotlin kernel for Jupyter take a look at [introductory guide](https://github.com/cheptsov/kotlin-jupyter-demo/blob/master/index.ipynb).

Example notebooks can be found in the [samples](samples) folder

Try samples online: [![Binder](https://mybinder.org/badge_logo.svg)](https://mybinder.org/v2/gh/kotlin/kotlin-jupyter/master?filepath=samples)

## Installation

There are three ways to install kernel:

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

Default installation path is `~/.ipython/kernels/kotlin/`. To install to some other location use option `-PinstallPath=`, but note that Jupyter looks for kernel specs files only in predefined places

Uninstall: `./gradlew uninstall`  

## Usage

- `jupyter console --kernel=kotlin`
- `jupyter notebook`
- `jupyter lab`

To start using `kotlin` kernel inside Jupyter Notebook or JupyterLab create a new notebook with `kotlin` kernel.

## Supported functionality

### REPL commands

The following REPL commands are supported:
 - `:help` - displays REPL commands help
 - `:classpath` - displays current classpath
 
### Dependencies resolving annotations

It is possible to add dynamic dependencies to the notebook using the following annotations:
 - `@file:DependsOn(<coordinates>)` - adds artifacts to classpath. Supports absolute and relative paths to class directories or jars, ivy and maven artifacts represented by colon separated string
 - `@file:Repository(<absolute-path>)` - adds a directory for relative path resolution or ivy/maven repository
 
### Default repositories

The following maven repositories are included by default:
 - [Bintray JCenter](https://jcenter.bintray.com)
 - [Maven Central](https://repo.maven.apache.org/maven2)
 - [JitPack](https://jitpack.io/)

### Line Magics

The following line magics are supported:
 - `%use <lib1>, <lib2> ...` - injects code for supported libraries: artifact resolution, default imports, initialization code, type renderers
 - `%trackClasspath` - logs any changes of current classpath. Useful for debugging artifact resolution failures
 - `%trackExecution` - logs pieces of code that are going to be executed. Useful for debugging of libraries support
 - `%output [--max-cell-size=N] [--max-buffer=N] [--max-buffer-newline=N] [--max-time=N] [--no-stdout] [--reset-to-defaults]` - 
 output capturing settings.
     - `max-cell-size` specifies the characters count which may be printed to stdout. Default is 100000.
     - `max-buffer` - max characters count stored in internal buffer before being sent to client. Default is 10000.
     - `max-buffer-newline` - same as above, but trigger happens only if newline character was encountered. Default is 100.
     - `max-time` - max time in milliseconds before the buffer is sent to client. Default is 100.
     - `no-stdout` - don't capture output. Default is false.
     - `reset-to-defaults` - reset all output settings that were set with magics to defaults
 
### Supported Libraries

When a library is included with `%use` keyword, the following functionality is added to the notebook:
 - repositories to search for library artifacts
 - artifact dependencies
 - default imports
 - library initialization code
 - renderers for special types, e.g. charts and data frames

This behavior is defined by `json` library descriptor. Descriptors for all supported libraries can be found in [libraries](libraries) directory.
A library descriptor may provide a set of properties with default values that can be overridden when library is included.
The major use case for library properties is to specify particular version of library. If descriptor has only one property, it can be 
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

List of supported libraries:
 - [klaxon](https://github.com/cbeust/klaxon) - JSON parser for Kotlin
 - [lets-plot](https://github.com/JetBrains/lets-plot-kotlin) - ggplot-like interactive visualization for Kotlin
 - [krangl](https://github.com/holgerbrandl/krangl) - Kotlin DSL for data wrangling
 - [kotlin-statistics](https://github.com/thomasnield/kotlin-statistics) - Idiomatic statistical operators for Kotlin
 - [kravis](https://github.com/holgerbrandl/kravis) - Kotlin grammar for data visualization
 - [spark](https://github.com/apache/spark) - Unified analytics engine for large-scale data processing
 - [gral](https://github.com/eseifert/gral) - Java library for displaying plots
 - [koma](https://koma.kyonifer.com/index.html) - Scientific computing library
 - [kmath](https://github.com/mipt-npm/kmath) - Kotlin mathematical library analogous to NumPy
 - [numpy](https://github.com/Kotlin/kotlin-numpy) - Kotlin wrapper for Python NumPy package
 - [exposed](https://github.com/JetBrains/Exposed) - Kotlin SQL framework
 - [mysql](https://github.com/mysql/mysql-connector-j) - MySql JDBC Connector

### Rich output
  
By default the return values from REPL statements are displayed in the text form. To use richer representations, e.g.
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

Press `TAB` to get the list of suggested items for completion. 

*Currently completion suggests only names for user-defined variables and functions.* 

## Debugging

1. Run `./gradlew installDebug`. Use option `-PdebugPort=` to specify port address for debugger. Default port is 1044.
2. Run `jupyter-notebook`
3. Attach remote debugger to JVM with specified port 

## Adding new libraries

To support new `JVM` library and make it available via `%use` magic command you need to create a library descriptor for it.

Check [libraries](libraries) directory to see examples of library descriptors.

Library descriptor is a `<libName>.json` file with the following fields:
- `properties`: a dictionary of properties that are used within library descriptor
- `link`: a link to library homepage. This link will be displayed in `:help` command
- `repositories`: a list of maven or ivy repositories to search for dependencies
- `dependencies`: a list of library dependencies
- `imports`: a list of default imports for library
- `init`: a list of code snippets to be executed when library is included
- `initCell`: a list of code snippets to be executed before execution of any cell
- `renderers`: a list of type converters for special rendering of particular types

*All fields are optional

Fields for type renderer:
- `class`: fully-qualified class name for the type to be rendered 
- `result`: expression that produces output value. Source object is referenced as `$it`

Name of the file is a library name that is passed to '%use' command

Library properties can be used in any parts of library descriptor as `$property`

To register new library descriptor:
1. For private usage - add it to local settings folder `<UserHome>/.jupyter_kotlin/libraries`
2. For sharing with community - commit it to [libraries](libraries) directory and create pull request.

If you are maintaining some library and want to update your library descriptor, just create pull request with your update. After your request is accepted, 
new version of your library will be available to all Kotlin Jupyter users immediately on next kernel startup (no kernel update is needed).

If a library descriptor with the same name is found in several locations, the following resolution priority is used:
1. Local settings folder (highest priority)
2. [libraries](libraries) directory at the latest master branch of `https://github.com/Kotlin/kotlin-jupyter` repository
3. Kernel installation directory

If you don't want some library to be updated automatically, put fixed version of its library descriptor into local settings folder.
