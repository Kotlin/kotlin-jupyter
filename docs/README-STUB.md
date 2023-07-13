[![JetBrains official project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Kotlin beta stability](https://img.shields.io/badge/project-beta-kotlin.svg?colorA=555555&colorB=AC29EC&label=&logo=kotlin&logoColor=ffffff&logoWidth=10)](https://kotlinlang.org/docs/components-stability.html)
[![PyPI](https://img.shields.io/pypi/v/kotlin-jupyter-kernel?label=PyPi)](https://pypi.org/project/kotlin-jupyter-kernel/)
[![Anaconda](https://anaconda.org/jetbrains/kotlin-jupyter-kernel/badges/version.svg)](https://anaconda.org/jetbrains/kotlin-jupyter-kernel)
[![Gradle plugin](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/org/jetbrains/kotlin/kotlin-jupyter-api-gradle-plugin/maven-metadata.xml.svg?label=Gradle+plugin)](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jupyter.api)
[![Maven Central](https://img.shields.io/maven-central/v/org.jetbrains.kotlinx/kotlin-jupyter-kernel?color=blue&label=Maven%20artifacts)](https://search.maven.org/search?q=kotlin-jupyter)
[![GitHub](https://img.shields.io/github/license/Kotlin/kotlin-jupyter)](https://www.apache.org/licenses/LICENSE-2.0)
[![Binder](https://mybinder.org/badge_logo.svg)](https://mybinder.org/v2/gh/kotlin/kotlin-jupyter/master?filepath=samples)

# Kotlin Kernel for IPython/Jupyter

[Kotlin](https://kotlinlang.org/) ([[kotlin_version]]) [kernel](https://docs.jupyter.org/en/latest/projects/kernels.html) for [Jupyter](https://jupyter.org).

The kernel is a powerful engine designed to enhance your Kotlin REPL experience. It offers support for executing code cells,
providing basic code completion, and analyzing errors. With the Kotlin kernel, you gain access to a range of features,
including an API for handling outputs, retrieving information from previously executed code snippets,
executing generic Kotlin code effortlessly, seamless integration with libraries, and more.

![Screenshot in Jupyter](images/kotlin_notebook_screenshot.png)

Beta version. Tested with Jupyter Notebook, Jupyter Lab, and Jupyter Console
on Windows, Ubuntu Linux, and macOS. The minimal supported versions of clients are given in the table below:

| Client           | Version |
|:-----------------|:--------|
| Jupyter Lab      | 1.2.6   |
| Jupyter Notebook | 6.0.3   |
| Jupyter Console  | 6.1.0   |

To start using the Kotlin kernel for Jupyter, take a look at the [introductory guide](https://github.com/cheptsov/kotlin-jupyter-demo/blob/master/index.ipynb).

Example notebooks can be found in the [samples](../samples) folder.

Try samples online: [![Binder](https://mybinder.org/badge_logo.svg)](https://mybinder.org/v2/gh/kotlin/kotlin-jupyter/master?filepath=samples)

## Contents

<!-- Start Document Outline -->

* [Installation](#installation)
	* [Kotlin Notebook plugin](#kotlin-notebook-plugin)
	* [Conda](#conda)
	* [Pip](#pip)
	* [From sources](#from-sources)
	* [Troubleshooting](#troubleshooting)
* [Updating](#updating)
	* [Kotlin Notebook](#kotlin-notebook)
	* [Datalore](#datalore)
	* [Conda](#conda-1)
	* [Pip](#pip-1)
* [Usage](#usage)
	* [Kotlin Notebook](#kotlin-notebook-1)
	* [Other clients](#other-clients)
	* [Creating Kernels](#creating-kernels)
* [Supported functionality](#supported-functionality)
	* [REPL commands](#repl-commands)
	* [Dependencies resolving](#dependencies-resolving)
	* [Default repositories](#default-repositories)
	* [Line Magics](#line-magics)
	* [Supported Libraries](#supported-libraries)
		* [List of supported libraries:](#list-of-supported-libraries)
	* [Rich output](#rich-output)
	* [Rendering](#rendering)
		* [Renderers](#renderers)
		* [DisplayResult and Renderable](#displayresult-and-renderable)
		* [Text rendering](#text-rendering)
		* [Throwables rendering](#throwables-rendering)
		* [Common rendering semantics](#common-rendering-semantics)
	* [Autocompletion](#autocompletion)
	* [Error analysis](#error-analysis)
* [Debugging](#debugging)
* [Adding new libraries](#adding-new-libraries)
* [Documentation](#documentation)
* [Contributing](#contributing)

<!-- End Document Outline -->

## Installation

There are several ways to use the kernel:

### Kotlin Notebook plugin

Simply download and use the latest version of the [Kotlin Notebook plugin](https://plugins.jetbrains.com/plugin/16340-kotlin-notebook) from the Marketplace.
The Kotlin kernel is embedded in it.

Check out the [blog post](https://blog.jetbrains.com/kotlin/2023/07/introducing-kotlin-notebook/) for a quick introduction to Kotlin Notebook.

### Conda

If you have `conda` installed, run the following command to install the stable package version:

`conda install -c jetbrains kotlin-jupyter-kernel` ([package home](https://anaconda.org/jetbrains/kotlin-jupyter-kernel))

To install the conda package from the dev channel:

`conda install -c jetbrains-dev kotlin-jupyter-kernel` ([package home](https://anaconda.org/jetbrains-dev/kotlin-jupyter-kernel))

Uninstall: `conda remove kotlin-jupyter-kernel`

### Pip

You can also install this package using `pip`:

Stable:
`pip install kotlin-jupyter-kernel` ([package home](https://pypi.org/project/kotlin-jupyter-kernel/))

Dev:
`pip install -i https://test.pypi.org/simple/ kotlin-jupyter-kernel` ([package home](https://test.pypi.org/project/kotlin-jupyter-kernel/))

Uninstall: `pip uninstall kotlin-jupyter-kernel`

### From sources

To install the kernel from sources, clone the repository and run the following command in the root folder:

`./gradlew install`

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

To update the Kotlin kernel, follow the instructions below based on your installation method:

### Kotlin Notebook

If you are using the Kotlin Notebook plugin, update it to the latest version
within the IDE or manually download and install the latest plugin version
from the [Marketplace](https://plugins.jetbrains.com/plugin/16340-kotlin-notebook).

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

## Usage

### Kotlin Notebook

Within IDEA with installed Kotlin Notebook plugin, just open a notebook, and you're good to go.

### Other clients

Run one of the following commands in console:

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
[[supported_commands]]
 
### Dependencies resolving

It is possible to add dynamic dependencies to the notebook using the following annotations:
- `@file:DependsOn(<coordinates>)` - adds artifacts to classpath. Supports absolute and relative paths to class
  directories or jars, ivy and maven artifacts represented by the colon separated string
- `@file:Repository(<absolute-path>)` - adds a directory for relative path resolution or ivy/maven repository.
  To specify Maven local, use `@file:Repository("*mavenLocal")`.

Alternative way to do the same is using Gradle-like syntax:

```kotlin
USE {
    repositories {
        maven {
            url = "https://my.secret.repo/maven/"
            credentials {
                username = USER
                password = TOKEN
            }
        }
    }

    dependencies {
        val ktorVersion = "2.0.3"

        implementation("my.secret:artifact:1.0-beta")
        implementation("io.ktor:ktor-client-core:$ktorVersion")
        implementation("io.ktor:ktor-client-apache:$ktorVersion")
    }
}
```

The same syntax can be used in [integrations creating](libraries.md).

Note that dependencies in remote repositories are resolved via Maven resolver.
Caches are stored in `~/.m2/repository` folder by default. Sometimes, due to network
issues or running several artifacts resolutions in parallel, caches may get corrupted.
If you have some troubles with artifacts resolution, please remove caches, restart kernel
and try again.

### Default repositories

The following maven repositories are included by default:
- [Maven Central](https://repo.maven.apache.org/maven2)
- [JitPack](https://jitpack.io/)

### Line Magics

The following line magics are supported:
[[magics]]
 
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
Note that using descriptor from specific revision is better than using `%useLatestDescriptors`.

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
[[supported_libraries]]

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

#### Throwables rendering

Throwable renderers do the same thing as renderers do, but only for results of the cells that were not
successfully executed, and some exception was generated.

#### Common rendering semantics

Successfully evaluated value is firstly transformed with RenderersProcessor. Resulting value is checked. If it's Renderable or DisplayResult, it is transformed into output JSON using `toJson()` method. If it's Unit, the cell won't have result at all. Otherwise, value is passed to `TextRenderersProcessor`. It tries to render the value to string using defined text renderers having in mind their priority. If all the renderers returned null, value is transformed to string using `toString()`. Resulting string is wrapped to `text/plain` MIME JSON.

If the cell execution finished unsuccessfully and exception was generated, then the first applicable throwable renderer
will be chosen for this exception, and exception will be passed to this renderer's `render()` method. Returned value
will be displayed. If no applicable throwable renderer was found, exception message and stacktrace will be printed
to stderr.

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

## Contributing

We welcome contributions to further enhance our project! If you come across any issues or have feature requests, please don't hesitate to [file an issue](https://github.com/Kotlin/kotlin-jupyter/issues).

For issues specifically related to the Kotlin Notebook plugin, kindly utilize [another tracker](https://youtrack.jetbrains.com/issues/KTNB).

Pull requests are highly appreciated! When submitting a pull request, please ensure that it corresponds to an existing issue. If you are planning a substantial change, we recommend discussing it with a [project maintainer](https://github.com/ileasile). You can reach out to me through [email](mailto:ilya.muradyan@jetbrains.com), [Kotlin Slack](https://kotlinlang.slack.com/archives/C05333T208Y), or [Telegram](https://t.me/ileasile). We look forward to your contributions!
