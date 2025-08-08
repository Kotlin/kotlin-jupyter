[![JetBrains official project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Kotlin beta stability](https://img.shields.io/badge/project-beta-kotlin.svg?colorA=555555&colorB=AC29EC&label=&logo=kotlin&logoColor=ffffff&logoWidth=10)](https://kotlinlang.org/docs/components-stability.html)
[![PyPI](https://img.shields.io/pypi/v/kotlin-jupyter-kernel?label=PyPi)](https://pypi.org/project/kotlin-jupyter-kernel/)
[![Anaconda](https://anaconda.org/jetbrains/kotlin-jupyter-kernel/badges/version.svg)](https://anaconda.org/jetbrains/kotlin-jupyter-kernel)
[![Gradle plugin](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/org/jetbrains/kotlin/kotlin-jupyter-api-gradle-plugin/maven-metadata.xml.svg?label=Gradle+plugin)](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jupyter.api)
[![Maven Central](https://img.shields.io/maven-central/v/org.jetbrains.kotlinx/kotlin-jupyter-kernel?color=blue&label=Maven%20artifacts)](https://search.maven.org/search?q=kotlin-jupyter)
[![GitHub](https://img.shields.io/github/license/Kotlin/kotlin-jupyter)](https://www.apache.org/licenses/LICENSE-2.0)
[![Binder](https://mybinder.org/badge_logo.svg)](https://mybinder.org/v2/gh/kotlin/kotlin-jupyter/master?filepath=samples)

# Kotlin Kernel for Jupyter notebooks

The Kotlin Kernel for Jupyter notebooks is a powerful tool that allows you to write and run [Kotlin](https://kotlinlang.org/) 2.2.20-Beta2 code within the
[Jupyter Notebook](https://jupyter.org) environment. This [Kernel](https://docs.jupyter.org/en/latest/projects/kernels.html) essentially acts as a bridge between Jupyter Notebook and the Kotlin compiler.

<img src="images/kotlin_notebook.gif" width="900" height="700" alt="Alt text for the GIF">

The Kotlin Kernel for notebooks supports running code cells to immediately see outputs, basic code completion, error analysis,
and other interactive coding features, enhancing the interactive experience provided by the [Kotlin REPL](https://www.jetbrains.com/help/idea/kotlin-repl.html#kotlin-repl).

With the Kotlin Kernel for notebooks, you gain access to a range of features like:
* Accessing APIs within cells and using APIs for handling outputs.
* Retrieving information from previously executed code snippets, allowing quick project exploration.
* Importing various libraries with a single line of code or even integrating new libraries into your project.

You can leverage Kotlin Kernel's benefits in [IntelliJ IDEA](https://www.jetbrains.com/idea/) through the [Kotlin Notebook plugin](https://plugins.jetbrains.com/plugin/16340-kotlin-notebook), in your [Jupyter Notebook](https://jupyter.org/), or in [Datalore](https://www.jetbrains.com/datalore/).

## Contents
<details>
<summary>Click here to expand the table of contents.</summary>

<!-- TOC -->
* [Kotlin Kernel for Jupyter notebooks](#kotlin-kernel-for-jupyter-notebooks)
  * [Contents](#contents)
  * [Get started](#get-started)
  * [Versions and support](#versions-and-support)
    * [Kotlin version support](#kotlin-version-support)
    * [Jupyter environments](#jupyter-environments)
    * [Operating systems](#operating-systems)
  * [Install the Kotlin Kernel in various clients](#install-the-kotlin-kernel-in-various-clients)
    * [Install the Kotlin Notebook plugin](#install-the-kotlin-notebook-plugin)
    * [Install with Conda](#install-with-conda)
    * [Install with Pip](#install-with-pip)
    * [Install from sources](#install-from-sources)
    * [Troubleshoot your installation](#troubleshoot-your-installation)
  * [Update the Kotlin Kernel for notebooks](#update-the-kotlin-kernel-for-notebooks)
    * [Update the Kotlin Notebook plugin](#update-the-kotlin-notebook-plugin)
    * [Update with Conda](#update-with-conda)
    * [Update with Pip](#update-with-pip)
    * [Update in Datalore](#update-in-datalore)
  * [Use the Kotlin Kernel for notebooks](#use-the-kotlin-kernel-for-notebooks)
    * [Use the Kotlin Notebook](#use-the-kotlin-notebook)
    * [Use other Jupyter clients](#use-other-jupyter-clients)
    * [Use Datalore](#use-datalore)
    * [Create custom kernels](#create-custom-kernels)
  * [Features](#features)
    * [REPL commands](#repl-commands)
    * [Dependencies resolving](#dependencies-resolving)
      * [Annotations](#annotations)
      * [Gradle-like syntax](#gradle-like-syntax)
      * [Handling dependencies](#handling-dependencies)
    * [Default repositories](#default-repositories)
    * [Line magics](#line-magics)
    * [Supported libraries](#supported-libraries)
      * [List of supported libraries](#list-of-supported-libraries)
    * [Rich output](#rich-output)
    * [Rendering](#rendering)
      * [Common rendering semantics](#common-rendering-semantics)
    * [Autocompletion](#autocompletion)
    * [Error analysis](#error-analysis)
  * [Debug your Kotlin notebook client](#debug-your-kotlin-notebook-client)
  * [Integrate new libraries](#integrate-new-libraries)
  * [Documentation](#documentation)
  * [Contribute](#contribute)
<!-- TOC -->

</details>

## Get started

Start using the Kotlin Kernel for Jupyter notebooks:

* See the [introductory notebook guide](https://github.com/cheptsov/kotlin-jupyter-demo/blob/master/index.ipynb).
* Check [notebook samples](https://github.com/Kotlin/kotlin-jupyter/tree/master/samples).
* Try the sample notebooks online: [![Binder](https://mybinder.org/badge_logo.svg)](https://mybinder.org/v2/gh/kotlin/kotlin-jupyter/master?filepath=samples)
* Explore the Kotlin Notebook docs to learn about [features](https://www.jetbrains.com/help/idea/kotlin-notebook.html), [use cases](https://kotlinlang.org/docs/kotlin-notebook-overview.html), and [tutorials](https://kotlinlang.org/docs/get-started-with-kotlin-notebooks.html).

## Versions and support

> **Note:** The Kotlin Kernel for Jupyter notebooks is in [Beta](https://kotlinlang.org/docs/components-stability.html#stability-levels-explained).

### Kotlin version support

The latest version of the Kotlin Kernel for notebooks uses the Kotlin compiler of version 2.2.20-Beta2.

### Jupyter environments

We tested the Kotlin Kernel for notebooks with the following clients:

| Client           | Minimal supported version |
|:-----------------|:--------------------------|
| JupyterLab       | 1.2.6                     |
| Jupyter Notebook | 6.0.3                     |
| Jupyter Console  | 6.1.0                     |

### Operating systems

We tested the Kotlin Kernel for notebooks with all the mentioned clients on the following operating systems:
* Windows
* Ubuntu Linux
* macOS

## Install the Kotlin Kernel in various clients

You can create, open, and work with Kotlin notebooks on various clients:

* [Kotlin Notebook in IntelliJ IDEA](https://www.jetbrains.com/idea/)
* [Datalore](https://www.jetbrains.com/datalore/)
* [Jupyter Notebook and JupyterLab](https://jupyter.org/)

Our Kotlin Kernel is fully integrated into Kotlin Notebook, which you can use directly within IntelliJ IDEA by [installing the Kotlin Notebook plugin](#install-the-kotlin-notebook-plugin). 

In Datalore, Kotlin is supported natively, with the Kotlin Kernel already bundled for an out-of-the-box experience. 

For other Jupyter clients, you'll need to install the Kotlin Kernel separately using [conda](#install-with-conda), [pip](#install-with-pip), 
or [sources](#install-from-sources).

### Install the Kotlin Notebook plugin

If you use IntelliJ IDEA, you need to install the Kotlin Notebook plugin that contains the Kotlin Kernel for Jupyter notebooks.

Install the Kotlin Notebook plugin by downloading its latest version from the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/16340-kotlin-notebook).

Alternatively, access the Kotlin Notebook plugin from **Settings** | **Plugins** | **Marketplace** within IntelliJ IDEA.

> **Note:** For a quick introduction to Kotlin Notebook, see this [blog post](https://blog.jetbrains.com/kotlin/2023/07/introducing-kotlin-notebook/).

### Install with Conda

If you use Jupyter Notebook and Conda, run this Conda command to install the stable version [package](https://anaconda.org/jetbrains/kotlin-jupyter-kernel) of the Kotlin Kernel for Jupyter notebooks:

`conda install -c jetbrains kotlin-jupyter-kernel`

Alternatively, run this Conda command to install the [package](https://anaconda.org/jetbrains-dev/kotlin-jupyter-kernel) from the developers channel:

`conda install -c jetbrains-dev kotlin-jupyter-kernel`

To uninstall the Kotlin Kernel for Jupyter notebooks, run this Conda command:

`conda remove kotlin-jupyter-kernel`

### Install with Pip

If you use Jupyter Notebook and Pip, run this Pip command to install the stable version [package](https://pypi.org/project/kotlin-jupyter-kernel) of the Kotlin Kernel for Jupyter notebooks:

`pip install kotlin-jupyter-kernel`

Alternatively, run this Pip command to install the [package](https://test.pypi.org/project/kotlin-jupyter-kernel) from the developers channel:

`pip install -i https://test.pypi.org/simple/ kotlin-jupyter-kernel`

To uninstall the Kotlin Kernel for Jupyter notebooks, run this Pip command:

`pip uninstall kotlin-jupyter-kernel`

### Install from sources

If you use either IntelliJ IDEA or Jupyter Notebook, you can install the Kotlin Kernel for Jupyter notebooks from sources.

Clone this repository and run the following Gradle command in the root folder:

`./gradlew install`

The default installation path is `~/.ipython/kernels/kotlin/`. You can also install the package in another location using the `-PinstallPath=` option.
However, Jupyter only looks for the kernel specification files in predefined places. For more details, see [Jupyter docs](https://jupyter-client.readthedocs.io/en/stable/kernels.html#kernel-specs).

To uninstall the Kotlin Kernel for Jupyter notebooks from sources, run this Gradle command:

`./gradlew uninstall`

### Troubleshoot your installation

When installing the Kotlin Kernel for Jupyter notebooks, issues can occur while detecting the kernel specification file.
These issues occur due to different Python environments and installation modes.

If you are using Pip or Conda to install the package, run this post-install fixup script:

```bash
python -m kotlin_kernel fix-kernelspec-location
```

This script replaces the kernel specification files with the detected user path.

> **Note:** Don't forget to re-run this script when updating the Kotlin Kernel for notebooks.

## Update the Kotlin Kernel for notebooks

See how to update the Kotlin Kernel for Jupyter notebooks using the Kotlin Notebook plugin, Conda, Pip, and Datalore.

### Update the Kotlin Notebook plugin

If you use the Kotlin Notebook plugin, update it to the latest version
in **Settings** | **Plugins** | **Installed** within IntelliJ IDEA.

Alternatively, you can download and install the latest plugin version
from the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/16340-kotlin-notebook).

### Update with Conda

If you use Jupyter Notebook and Conda, run this Conda command to update the stable version package:

`conda update -c jetbrains kotlin-jupyter-kernel`

Alternatively, run this Conda command to update the package from the developers channel:

`conda update -c jetbrains-dev kotlin-jupyter-kernel`

To change to a specific Kotlin Kernel version, add the `={VERSION}` parameter to the `kotlin-jupyter-kernel` command. In this command,
replace the `{VERSION}` parameter with the desired PyPi version of the Kotlin Jupyter Kernel (for example, `0.15.0.584`).

You can specify the version in both commands the one from the stable package and the one from the developers channel package. For example, for the
stable package:

`conda install -c jetbrains kotlin-jupyter-kernel={0.15.0.584}`

### Update with Pip

If you use Jupyter Notebook and Pip, run this Pip command to update the stable version package:

`pip install kotlin-jupyter-kernel --upgrade`

Alternatively, run this Conda command to update the package from the developers channel:

`pip install -i https://test.pypi.org/simple/ kotlin-jupyter-kernel --upgrade`

To change to a specific Kotlin Kernel version, add the `={VERSION}` parameter to the `kotlin-jupyter-kernel` command. In this command,
replace the `{VERSION}` parameter with the desired PyPi version of the Kotlin Jupyter Kernel (for example, `0.15.0.584`).

You can specify the version in both commands the one from the stable package and the one from the developers channel package. For example, for the
stable package:

`pip install kotlin-jupyter-kernel=={0.15.0.584} --ignore-installed`

### Update in Datalore

To update the Kotlin Kernel for notebooks in Datalore:

1. Add an `environment.yml` file to the Notebook files containing the following
   snippet:

   ```yaml
   datalore-env-format-version: "0.2"
   datalore-package-manager: "pip"
   datalore-base-env: "default"
   dependencies:
   - pip:
     - kotlin-jupyter-kernel=={VERSION}
   ```

2. Replace the `{VERSION}` parameter for the latest PyPi version of the Kotlin Jupyter Kernel (for example, `0.15.0.584`).

3. Stop and restart the machine in Datalore.

## Use the Kotlin Kernel for notebooks

See how to use the Kotlin Kernel for Jupyter notebooks with the Kotlin Notebook plugin, Jupyter clients, and Datalore.

### Use the Kotlin Notebook

After [installing](#install-the-kotlin-notebook-plugin) the Kotlin Notebook plugin in IntelliJ IDEA, create a new notebook by selecting **File** | **New** | **Kotlin Notebook**, or right-click
on a folder and select **New** | **Kotlin Notebook**.

Now you're good to go!

### Use other Jupyter clients

You can use our Kotlin Kernel through JupyterLab, Jupyter Notebook, and Jupyter Console clients:

1. Run one of the following commands in the console:

* **In JupyterLab:**

  `jupyter lab`

* **In Jupyter Notebook:**

  `jupyter notebook`

* **In Jupyter Console:**

  `jupyter console --kernel=kotlin`

2. Create a new notebook and set `kotlin` as kernel. This step applies to Jupyter Notebook or JupyterLab, and it's not required for
   Jupyter Console.

The default kernel uses the JDK that the environment points in the `KOTLIN_JUPYTER_JAVA_HOME` variable. In case the `KOTLIN_JUPYTER_JAVA_HOME` variable is not set,
the kernel also uses the JDK from the `JAVA_HOME` variable.

The kernel uses the arguments that the environment points in the `KOTLIN_JUPYTER_JAVA_OPTS` variable.
In case the `KOTLIN_JUPYTER_JAVA_OPTS` variable is not set, the kernel also uses the JVM arguments from the `JAVA_OPTS` variable.

Additionally, the kernel uses arguments that the environment points in the `KOTLIN_JUPYTER_JAVA_OPTS_EXTRA` variable.
The arguments are parsed using the Python [`shlex.split()`](https://docs.python.org/3/library/shlex.html) function.

### Use Datalore

To create a Kotlin notebook in Datalore, click on **New notebook** and select **Kotlin** as kernel.

### Create custom kernels

You can create a custom Kotlin Kernel for Jupyter Notebook.
This allows you to tailor the kernel's environment to your specific requirements, such as using a particular JDK, setting JVM arguments, or defining environment variables.

To create a custom Kotlin Kernel for Jupyter Notebook, use the `add-kernel` command from the installed `kotlin_kernel` python package:

```bash
python -m kotlin_kernel add-kernel [--name name] [--jdk jdk_home_dir] [--set-jvm-args] [--jvm-arg arg]* [--env KEY VALUE]* [--force]
```

In the `add-kernel` script, the `name` argument is required if the `jdk` argument is not specified.  Alternatively, if the `jdk` argument is specified,
but the `name` argument is not, then the name is taken from the `JDK $vendor $version` argument, which is detected from the JDK.

Regardless of how the name is determined, the format of the kernel name is `Kotlin ($name)`,
and the format of the directory name is `kotlin_$name`. The directory name includes spaces in `name`, replaced by underscores. Ensure
this format is compatible with your file system.

JVM arguments are joined with a space (`' '`), supporting multiple arguments within the same entry.
The new arguments are added to existing ones, unless the `--set-jvm-args` flag is used. In this case, JVM
arguments are set to the `KOTLIN_JUPYTER_JAVA_OPTS` variable. Both adding and setting arguments work alongside the `KOTLIN_JUPYTER_JAVA_OPTS_EXTRA` variable.

While Jupyter Kernel environment variable substitutions are supported in the `env` argument, no replacement occurs
if the used environment variable doesn't exist.

The `add-kernel` script utilizes the `argparse` Python library, supporting the `--help`, `@argfile` (you don't need the `@` symbol in PowerShell), and `--opt=value` arguments.
The `--jvm-arg=arg` argument is required when passing JVM arguments that start with the `-` symbol.

Here's an example of an `add-kernel` script to create a custom Kotlin Kernel for Jupyter Notebook:

```bash
python -m kotlin_kernel add-kernel --name "JDK 15 Big 2 GPU" --jdk ~/.jdks/openjdk-15.0.2 --jvm-arg=-Xmx8G --env CUDA_VISIBLE_DEVICES 0,1
```

## Features

Explore the sections below to learn about the features of the Kotlin Kernel for Jupyter notebooks. You can leverage these
features using Kotlin Notebook in IntelliJ IDEA, Datalore, or other [Jupyter Notebook clients](#jupyter-environments).

The features of the Kotlin Kernel for Jupyter notebooks include:

* [REPL commands](#repl-commands)
* [Dependencies resolving](#dependencies-resolving)
* [Default repositories](#default-repositories)
* [Line magics](#line-magics)
* [Supported libraries](#supported-libraries)
* [Rich output](#rich-output)
* [Rendering](#rendering)
* [Autocompletion](#autocompletion)
* [Error analysis](#error-analysis)

<details>
<summary>Click here to expand the features.</summary>

### REPL commands

Our Kotlin Kernel for notebooks comes with a set of REPL commands that let you explore your notebook environment. The following REPL commands are supported:

| Command | Description |
|:--------|:------------|
| `:help` | Displays help information with details of the notebook version, line magics, and supported libraries. |
| `:classpath` | Displays the current classpath of your notebook environment, showing a list of locations where the notebook searches for libraries and resources. |
| `:vars` | Displays information about the declared variables and their values. |

### Dependencies resolving

You can easily add dynamic dependencies to your notebook from a remote Maven repository or local ones (local JARs).
You can add dependencies through annotations or Gradle-like syntax.

#### Annotations

You can add dynamic dependencies to the notebook using the following annotations:

* **`@file:DependsOn(<coordinates>)`:** In this annotation, you need to specify the coordinates of the dependency.
  This annotation adds artifacts (like JAR files) to the notebook's classpath. It supports absolute and relative paths to
  class directories or JARs, as well as Ivy and Maven artifacts:

   ```kotlin 
   @file:DependsOn("io.ktor:ktor-client-core-jvm:$ktorVersion")
   ```

* **`@file:Repository(<absolute-path>)`:** In this annotation, you need to specify the absolute path of the dependency.
  This annotation adds a directory or an Ivy or Maven repository to the notebook environment. To specify a Maven local
  repository, use:

   ```kotlin
   @file:Repository("*mavenLocal")
   ```

#### Gradle-like syntax

You can load any library from the Maven repository using Gradle-like syntax in any cell, specifying repositories, locations, and so on:

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

> **Note:** You can use the same Gradle-like syntax to [integrate new libraries](libraries.md).

#### Handling dependencies

When adding dependencies, consider the following:

* Dependencies in remote repositories are resolved via Maven resolver.
* Caches are stored in the `~/.m2/repository` folder by default. However, due to network issues or several artifact
  resolutions running in parallel, caches may become corrupted.
* If you have issues with artifacts resolution, remove caches, restart kernel,
  and try again.
* While you can utilize Gradle-like syntax, Gradle is not running under the hood, and Gradle metadata is not resolved.
  Therefore, advanced dependency configurations are not available. For example, top-level Multiplatform dependencies are not supported.
  In these cases, you need to use the `-jvm` variant manually.

### Default repositories

The following Maven repositories are included by default:
* [Maven Central](https://repo.maven.apache.org/maven2)
* [JitPack](https://jitpack.io/)

You can directly use libraries and dependencies from these repositories within your Kotlin code running in the Jupyter Notebook environment.

### Line magics

Line magics are special commands, starting with the % character, that interact with the notebook on a per-line basis.
Line magics allow you to import libraries, configure output settings, and perform more operations.

You can use the following line magics in your notebooks using the Kotlin Kernel:


| Magic | Description | Usage example |
|:------|:------------|:--------------|
| `%use` | Imports supported libraries and injects code from these libraries(artifact resolution, default imports, initialization code, and type renderers). | `%use klaxon(5.5), lets-plot` |
| `%trackClasspath` | Logs any changes of the current classpath. This command is useful for debugging artifact resolution failures. | `%trackClasspath [on/off]` |
| `%trackExecution` | Logs pieces of code to be executed. This command is useful for debugging libraries support. | `%trackExecution [all/generated/off]` |
| `%useLatestDescriptors` | Sets the latest versions of available library descriptors instead of bundled descriptors (used by default). Note that bundled descriptors are preferred because the current kernel version might not support the latest descriptors. For better notebook stability, use bundled descriptors. | `%useLatestDescriptors [on/off]` |
| `%output` | Configures the output capturing settings. | `%output --max-cell-size=1000 --no-stdout --max-time=100 --max-buffer=400` |
| `%logLevel` | Sets logging level. | `%logLevel [off/error/warn/info/debug]` |

> **Note:** For more information, see [Line magics](magics.md).

### Supported libraries

Kotlin Kernel for Jupyter notebooks comes with a set of integrated libraries, which you can import into your notebook by running the `%use` line magic before the library's name within a cell.

When you import a library using the `%use` line magic, the following functionality is added to the notebook:

* Repositories to search for library artifacts
* Artifact dependencies
* Default imports
* Library initialization code
* Renderers for special types. For example, charts and data frames

This behavior is defined by the [JSON library descriptor](libraries.md#library-integration-methods), which provides a set of properties with default values that can be overridden when the library is imported.

To check the descriptors of all supported libraries, see the [libraries repository](https://github.com/Kotlin/kotlin-jupyter-libraries).

The major use case for library properties is to specify a particular library version. If the descriptor has only one property, the library version can be
defined without naming:

```
%use dataframe(0.10)
```

If the descriptor has more than one property, you need to use the property name:

```
%use spark(scala=2.11.10, spark=2.4.2)

```

You can include several libraries in a single `%use` statement, separated by commas (`,`):

```
%use kandy, dataframe, mysql(8.0.15)
```

You can also specify the source of the library descriptor. By default, it's taken from the [libraries repository](https://github.com/Kotlin/kotlin-jupyter-libraries).
To try a descriptor from another revision, use the following syntax:

```
// Uses a specific version from the default repository
%use lets-plot@0.8.2.5

// Uses a specific commit of the library
%use lets-plot@ref[24a040fe22335648885b106e2f4ddd63b4d49469]

// Uses a specific version of the library from a custom repository along with library arguments
%use dataframe@dev(0.10)
```

> **Note:** Using a fixed version of a library is preferred over using the `%useLatestDescriptors` line magic.

Additionally, you can try resolving the library descriptor from a local file or a remote URL:

```
// Loads the library from a file
%use mylib@file[/home/user/lib.json]

// Loads the library from a file, and the Kernel detects it's a file
%use @/home/user/libs/lib.json

// Specifies a directory and a file name without extension (the extension file should be JSON) 
%use lib@/home/user/libs

// Loads the library descriptor from a remote URL
%use herlib@url[https://site.com/lib.json]

// Loads the library descriptor from a remote URL. The `url[]` part can be omitted if the URL responds with 200(OK)
%use @https://site.com/lib.json

// Loads the library dependencies from a specified JSON file. The library name and URL resolution can be omitted
%use @file[lib.json]
```

#### List of supported libraries

Here you can find all the supported libraries you can use in you Kotlin notebooks through the `%use` line magic.

<details>
<summary>Click to see the list of supported libraries.</summary>

 - [2p-kt](https://github.com/tuProlog/2p-kt) - Kotlin Multi-Platform ecosystem for symbolic AI
 - [adventOfCode](https://github.com/Toldoven/aoc-kotlin-notebook) - Interactive Advent of Code framework for Kotlin Notebook
 - [biokotlin](https://github.com/maize-genetics/BioKotlin) - BioKotlin aims to be a high-performance bioinformatics library that brings the power and speed of compiled programming languages to scripting and big data environments.
 - [combinatoricskt](https://github.com/shiguruikai/combinatoricskt) - A combinatorics library for Kotlin
 - [coroutines](https://github.com/Kotlin/kotlinx.coroutines) - Asynchronous programming and reactive streams support
 - [dataframe](https://github.com/Kotlin/dataframe) - Kotlin framework for structured data processing
 - [datetime](https://github.com/Kotlin/kotlinx-datetime) - Kotlin date/time library
 - [deeplearning4j](https://github.com/eclipse/deeplearning4j) - Deep learning library for the JVM
 - [deeplearning4j-cuda](https://github.com/eclipse/deeplearning4j) - Deep learning library for the JVM (CUDA support)
 - default - Default imports: dataframe and Kandy libraries
 - [develocity-api-kotlin](https://github.com/gabrielfeo/develocity-api-kotlin) - A library to use the Develocity API in Kotlin scripts or projects
 - [exposed](https://github.com/JetBrains/Exposed) - Kotlin SQL framework
 - [fuel](https://github.com/kittinunf/fuel) - HTTP networking library
 - [gral](https://github.com/eseifert/gral) - Java library for displaying plots
 - [intellij-platform](https://plugins.jetbrains.com/docs/intellij/welcome.html) - The IntelliJ Platform integration to bridge Kotlin Notebook with the IntelliJ Platform SDK and the IntelliJ IDEA runtime for seamless code execution and interactive development
 - [jdsp](https://github.com/psambit9791/jDSP) - Java library for signal processing
 - [jupyter-js](https://github.com/yidafu/kotlin-jupyter-js) - Experimental `%javascript`/`%typescript`/`%jsx` line magic support
 - [kalasim](https://www.kalasim.org) - Discrete event simulator
 - [kaliningraph](https://github.com/breandan/kaliningraph) - Graph library with a DSL for constructing graphs and visualizing the behavior of graph algorithms
 - [kandy](https://github.com/Kotlin/kandy) - Kotlin plotting DSL for Lets-Plot
 - [kandy-echarts](https://github.com/Kotlin/kandy) - Kotlin plotting DSL for Apache ECharts
 - [kandy-geo](https://github.com/Kotlin/kandy) - Geo extensions for Kandy and Kotlin Dataframe
 - [klaxon](https://github.com/cbeust/klaxon) - JSON parser for Kotlin
 - [kmath](https://github.com/mipt-npm/kmath) - Experimental Kotlin algebra-based mathematical library
 - [koog](https://github.com/JetBrains/koog) - Koog is a Kotlin-based framework designed to build and run AI agents entirely in idiomatic Kotlin.
 - [kotlin-dl](https://github.com/Kotlin/kotlindl) - KotlinDL library which provides Keras-like API for deep learning
 - [kraphviz](https://github.com/nidi3/graphviz-java) - Graphviz wrapper for JVM
 - [kravis](https://github.com/holgerbrandl/kravis) - Kotlin grammar for data visualization
 - [ksl](https://github.com/rossetti/KSL) - KSL - Kotlin Simulation Library for Monte Carlo and Discrete-Event Simulation
 - [kt-math](https://github.com/gciatto/kt-math) - Kotlin multi-platform port of java.math.*
 - [ktor-client](https://github.com/Kotlin/kotlin-jupyter-http-util) - Asynchronous HTTP client
 - [langchain4j](https://github.com/langchain4j/langchain4j) - LangChain is a framework for building applications powered by LLMs, enabling easy integration of models, data, and external tools
 - [lets-plot](https://github.com/JetBrains/lets-plot-kotlin) - Kotlin API for Lets-Plot: multiplatform plotting library based on Grammar of Graphics
 - [lets-plot-gt](https://github.com/JetBrains/lets-plot-kotlin) - Lets-Plot visualisation for GeoTools toolkit
 - [lib-ext](https://github.com/Kotlin/kotlin-jupyter) - Extended functionality for Jupyter kernel: LaTeX outputs, web-based images, graphs API
 - [londogard-nlp-toolkit](https://github.com/londogard/londogard-nlp-toolkit) - A Natural Language Processing (NLP) toolkit for Kotlin on the JVM
 - [multik](https://github.com/Kotlin/multik) - Multidimensional array library for Kotlin
 - [mysql](https://github.com/mysql/mysql-connector-j) - MySql JDBC Connector
 - [openai](https://openai.com/blog/chatgpt) - OpenAI API for Jupyter Notebooks
 - [openai-java](https://github.com/openai/openai-java) - OpenAI official Java API
 - [plotly](https://github.com/mipt-npm/plotly.kt) - [beta] Plotly.kt jupyter integration for static plots.
 - [plotly-server](https://github.com/mipt-npm/plotly.kt) - [beta] Plotly.kt jupyter integration for dynamic plots.
 - [rdkit](https://www.rdkit.org/) - Open-Source Cheminformatics Software
 - [reflection](https://kotlinlang.org/docs/reflection.html) - Imports for Kotlin Reflection
 - [roboquant](https://roboquant.org) - Algorithmic trading platform written in Kotlin
 - [serialization](https://github.com/Kotlin/kotlin-jupyter-http-util) - Deserialize JSON content using kotlinx.serialization and automatically generate classes for it
 - [smile](https://github.com/haifengl/smile) - Statistical Machine Intelligence and Learning Engine
 - [spark](https://github.com/JetBrains/kotlin-spark-api) - Kotlin API for Apache Spark: unified analytics engine for large-scale data processing
 - [spark-streaming](https://github.com/JetBrains/kotlin-spark-api) - Kotlin API for Apache Spark Streaming: scalable, high-throughput, fault-tolerant stream processing of live data streams
 - [spring-ai-anthropic](https://github.com/spring-projects/spring-ai) - Spring AI is a application framework designed specifically for AI engineering, offering seamless integration with Anthropic models.
 - [spring-ai-ollama](https://github.com/spring-projects/spring-ai) - Spring AI is a specialized application framework designed specifically for AI engineering, providing seamless integration with local models powered by Ollama.
 - [spring-ai-openai](https://github.com/spring-projects/spring-ai) - Spring AI is a application framework designed specifically for AI engineering, offering seamless integration with OpenAI models.
 - [webtau](https://github.com/testingisdocumenting/webtau) - WebTau end-to-end testing across layers

</details>

### Rich output

By default, our Kotlin Kernel for Jupyter notebooks displays return values in text form. However, you can enrich the output by rendering graphics, HTML, or other MIME-encoded data format.

One approach is to send MIME-encoded results to the client using the `MIME` helper function:

```kotlin
fun MIME(vararg mimeToData: Pair<String, String>): MimeTypedResult 
```

For example:

```kotlin
MIME("text/html" to "<p>Some <em>HTML</em></p>", "text/plain" to "No HTML for text clients")
```

Another approach is to use the `HTML` helper function, which provides a simpler way to display HTML content directly:

```kotlin
fun HTML(text: String): MimeTypedResult
```

For example:

```kotlin
HTML("<p>This is an example of <strong>HTML</strong> content rendered using the HTML helper function.</p>")
```

### Rendering

Rendering is the procedure of transforming a value to a form that is appropriate for displaying it in the Jupyter client.
The Kotlin Kernel for Jupyter notebook supports various features and mechanisms for rendering values:

* **Renderers:** Transform values into other representations. Renderers are controlled via the `RenderersProcessor` method, and you can access it with the [notebook API entry point](https://github.com/Kotlin/kotlin-jupyter/blob/master/docs/libraries.md#supported-integration-features).
  The Kotlin kernel iterates through a list of available renderers, trying to find one that can handle the given data. A library can define one or more renderers.

* **`DisplayResult` and `Renderable`:** Objects implementing `DisplayResult` and `Renderable` interfaces are rendered to output JSON.

* **Text rendering:** Render objects to strings using text renderers. Text renderers are controlled via the `TextRenderersProcessor` method, and you can access the method with the [notebook API entry point](https://github.com/Kotlin/kotlin-jupyter/blob/master/docs/libraries.md#supported-integration-features). A library can define one or more renderers.
  The Kotlin Kernel iterates until at least one renderer returns a non-null string for a passed argument.
  This kind of renderer can be easily composed with each other. For example, a text renderer for iterables can render its elements with a text renderer processor recursively.

* **Throwables rendering:** Throwable renderers behave as regular renderers but handle exceptions and errors generated during cell execution.

#### Common rendering semantics

Successful value evaluation triggers a rendering process. Initially, the `RenderersProcessor` attempts to convert the value
into a `Renderable` or `DisplayResult` object. If successful, the result is transformed into JSON output using the `toJson()` method.
For `Unit` values, no output is generated.

If the value cannot be rendered as `Renderable` or `DisplayResult`, the `TextRenderersProcessor` takes over.
It iterates to render the value to a string using the defined text renderers, seeking a non-null string representation. If no suitable renderer is found,
the value is transformed into a string using the `toString()` method. The resulting string is wrapped in a `text/plain` MIME JSON.

Upon execution failure, an exception is generated. The first applicable throwable renderer
is chosen for this exception, and the exception is passed to this renderer's `render()` method so the returned value
is displayed. If no applicable throwable renderer is found, the exception's message and stack trace are printed to standard error.

### Autocompletion

When working with the Kotlin notebooks, press `TAB` to get the list of suggested items for completion.

In Jupyter Notebook, you don't need to press `TAB`. Completion is requested automatically.

Completion works for both globally defined symbols and local symbols,
which were loaded into the notebook during cell evaluation.

### Error analysis

If you use Jupyter Notebook, you'll notice that compilation errors and warnings are underlined in
red and yellow, correspondingly. If you hover the cursor over underlined text, you'll get
an error message that can help you to fix the error.

This error analysis is achieved by the kernel-level extension of Jupyter Notebook. The extension sends
error-analysis requests to the kernel and renders their results.

</details>

## Debug your Kotlin notebook client

1. Run the `./gradlew installDebug` Gradle command. The debugger port is selected automatically.
   The default port is 1044, and if it's unavailable, the consequent ports are used. If you want an exact port, specify the `-PdebugPort=<port>` Gradle option.
2. Run the corresponding command to [open the desired notebook client](#use-other-jupyter-clients).
3. Attach a remote debugger to the JVM with the corresponding port (the debug port number is printed in the terminal when the kernel starts).

## Integrate new libraries

Read [this article](libraries.md) if you want to integrate new `JVM` libraries in the Kotlin Kernel for Jupyter notebooks.

## Documentation

To learn more, explore the available documentation:

* [Docs site](https://ileasile.github.io/kotlin-jupyter-docs) with rendered KDoc comments from the codebase.
* [Docs about integrating new libraries](libraries.md). If you are a library author, you may be interested in the `api` module in our project. There is
  also a `lib` module that contains entities available from the Notebook cells, and a `shared-compiler` module for Jupyter REPL integration
  into a standalone application or IDEA plugin.
* Explore the Kotlin Notebook docs to learn about [features](https://www.jetbrains.com/help/idea/kotlin-notebook.html), [use cases](https://kotlinlang.org/docs/kotlin-notebook-overview.html), and [tutorials](https://kotlinlang.org/docs/get-started-with-kotlin-notebooks.html).

## Contribute

We welcome contributions to further enhance our project! If you come across any issues or have feature requests, please don't hesitate to [file an issue](https://github.com/Kotlin/kotlin-jupyter/issues).

For issues specifically related to the Kotlin Notebook plugin, utilize [this tracker](https://youtrack.jetbrains.com/issues/KTNB).

Pull requests are highly appreciated! When submitting a pull request, ensure it corresponds to an existing issue. Read
[`CONTRIBUTING.MD`](../CONTRIBUTING.md) for more information on how to work with the repository.

If you are planning a substantial change, we recommend discussing it with a [project maintainer](https://github.com/ileasile).
You can reach out to me through [email](mailto:ilya.muradyan@jetbrains.com), [Kotlin Slack](https://kotlinlang.slack.com/archives/C05333T208Y), or [Telegram](https://t.me/ileasile).

We look forward to your contributions!
