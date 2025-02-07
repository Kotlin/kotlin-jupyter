# Contributing to Kotlin Jupyter Kernel

This file contains information about how to work with this repository.

## How to build locally

### Prerequisites

- Java 17 or 21.
- Git

### Getting the source code 

Checkout repo:
```sh
git clone https://github.com/Kotlin/kotlin-jupyter.git 
```

### Building 

To build the kernel for use in Jupyter Notebook Web Client and IntelliJ, run:

```sh
./gradlew install
```

This will publish binaries in `~/.ipython/kernels/kotlin/`. See [Install from Sources](docs/README.md#install-from-sources) for more information.

The version number used is defined by the `baseVersion` property in [gradle.properties](gradle.properties)

During development, you can set the environment variable `KOTLIN_JUPYTER_USE_MAVEN_LOCAL=true` to use artifacts 
published to Maven Local. If this has not been set, Maven Local artifacts are not available.

### Tests

Run all linters and unit tests across the entire project by using

```sh
./gradlew check
```

Note, this requires that all kernel library descriptors have been downloaded into the [libraries](./libraries)
folder. This can be done by running the command:

```sh
./gradlew updateLibraryDescriptors
```

Running `./gradlew install` will also download these.

### Debugging Execution Tests

When running tests in `org.jetbrains.kotlinx.jupyter.test.protocol.ExecuteTests`, the default behavior 
is that code is compiled inside a kernel running in a different process than the test process as this emulates
the real-world behavior. This makes debugging difficult. If you want to debug these tests, change the class declaration 
to `runServerInSeparateProcess = false`, as this will cause the code to be compiled inside the test process and 
thus be available to the debugger.

## Repository Layout

The project consists of a top-level Gradle project with a number of submodules.

* The main kernel code is located in the top-level [`src`](./src) folder.
* All submodules can be found in the [`./jupyter-lib`](./jupyter-lib) folder.
* A custom build plugin is being used to share build logic across project modules, this plugin can
  be found in [`/build-plugin`](./build-plugin).

Other relevant folders

* [`samples`](./samples): Contains sample notebooks that explore different aspects of how to use the notebook APIs.
* [`docs`](./docs): Contains the components making up the main README. 

## Code Style

We use the offical [style guide](https://kotlinlang.org/docs/reference/coding-conventions.html) from Kotlin which is enforced using [ktlint](https://github.com/pinterest/ktlint).

```sh
# Call from root folder to check if code is compliant.
./gradlew ktlintCheck

# Call from root folder to automatically format all Kotlin code according to the code style rules.
./gradlew ktlintFormat
```

**Note:** ktlint does not allow group imports using `.*`. You can configure IntelliJ to disallow this by going to 
preferences `Editor > Code Style > Kotlin > Imports` and select "Use single name imports".

## Updating the README

Updating the README should be done by modifying [`README_STUB.md`](./docs/README-STUB.md) and the call:

```sh
./gradlew generateReadme
```

## Making Pull Requests

If fixing an issue, the PR title should include a reference to the issue being fixed as well as a descriptive title, 
e.g. `KTNB-794: Restart Spring sever on shutdown request`. The title should be kept short, with additional information 
in the body of the commit message.

Before submitting a PR, make sure to run the following commands:

```sh
./gradlew generateReadme
./gradlew check
```
