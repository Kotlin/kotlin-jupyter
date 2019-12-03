[![Build Status](https://travis-ci.com/ileasile/kotlin-jupyter.svg?branch=master)](https://travis-ci.com/ileasile/kotlin-jupyter) <br/>

# Kotlin kernel for IPython/Jupyter

Kotlin (1.3.70) REPL kernel for jupyter (http://jupyter.org).

Alpha version. Tested with jupyter 5.2.0 on OS X so far.

## Screenshot

![Screenshot in Jupyter](./samples/ScreenShotInJupyter.png)

## Example 

Example notebook output is [here](samples/KotlinSample01.ipynb). *(It is ported from [Gral](https://github.com/eseifert/gral)
project's `ConvolutionExample.java`).* 

The notebook itself is located in the `samples` folder.

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
 - [JitPack](https://jitpack.io/)*

### Line Magics

The following line magics are supported:
 - `%use <lib1>, <lib2> ...` - injects code for supported libraries: artifact resolution, default imports, initialization code, type renderers
 - `%trackClasspath` - logs any changes of current classpath. Useful for debugging artifact resolution failures
 - `%trackExecution` - logs pieces of code that are going to be executed. Useful for debugging of libraries support
 
### Supported Libraries

When a library is included with `%use` keyword, the following functionality is added to the notebook:
 - repositories to search for library artifacts
 - artifact dependencies
 - default imports
 - library initialization code
 - renderers for special types, e.g. charts and data frames 

Current set of supported libraries:
 - [klaxon](https://github.com/cbeust/klaxon) - JSON parser for Kotlin
 - lets-plot - ggplot-like interactive visualization for Kotlin
 - [krangl](https://github.com/holgerbrandl/krangl) - Kotlin DSL for data wrangling
 - [kotlin-statistics](https://github.com/thomasnield/kotlin-statistics) - Idiomatic statistical operators for Kotlin
 - [kravis](https://github.com/holgerbrandl/kravis) - Kotlin grammar for data visualization
 - [spark](https://github.com/apache/spark) - Unified analytics engine for large-scale data processing
 - [gral](https://github.com/eseifert/gral) - Java library for displaying plots

*Note: The list of all supported libraries can be found in [config file](config.json)*

A definition of supported library may have a list of optional arguments that can be overriden when library is included.
The major use case for library arguments is to specify particular version of library. Most library definitions default to `-SNAPSHOT` version that may be overriden in `%use` magic.     

Usage example:
```
%use krangl(0.10), lets-plot
```

### MIME output
  
By default the return values from REPL statements are displayed in the text form. To use richer representations, e.g.
 to display graphics or html, it is possible to send MIME-encoded result to the client using the `Result` type 
 and `MIME` helper function. The latter has a signature: 
```kotlin
fun MIME(vararg mimeToData: Pair<String, Any>): Result 
```
E.g.:
```kotlin
MIME("text/html" to "<p>Some <em>HTML</em></p>", "text/plain" to "No HTML for text clients")

```
HTML outputs can be rendered with `HTML` helper function:
```kotlin
fun HTML(text: String): Result
```

### Autocompletion

Press `TAB` to get the list of suggested items for completion. Currently completion suggests only names for user-defined variables and functions. 

## Installation

Run `./gradlew install`

Use option `-PinstallPath=` to specify installation path. *(Note that jupyter looks for kernel specs files only in predefined places.)*

Default installation path is `~/.ipython/kernels/kotlin/`.

## Usage

`jupyter-console --kernel=kotlin`

or

`jupyter-notebook`

and then create a new notebook with `kotlin` kernel.

## Debugging

- run kernel jar passing some connection config file as a parameter, e.g. `testData/config.json`
    - additional jars for the REPL could be passed using `-cp=` parameter
- run `jupyter-console` passing the full path to the same config file as an argument to the `--existing` command line parameter
