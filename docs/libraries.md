# Adding new libraries

Generally, there are two ways of adding new library: 
1. [Creating JSON library descriptor](#Creating-library-descriptor)
2. [Integration using Kotlin API](#Integration-using-Kotlin-API)

## Creating library descriptor

To support new `JVM` library and make it available via `%use` magic command you need to create a library descriptor for it.

Check [libraries][libs-repo] repository to see examples of library descriptors.

Library descriptor is a `<libName>.json` file with the following fields:
- `properties`: a dictionary of properties that are used within library descriptor
- `description`: a short library description which is used for generating libraries list in README
- `link`: a link to library homepage. This link will be displayed in `:help` command
- `minKernelVersion`: a minimal version of Kotlin kernel which may be used with this descriptor
- `repositories`: a list of maven or ivy repositories to search for dependencies
- `dependencies`: a list of library dependencies
- `imports`: a list of default imports for library
- `init`: a list of code snippets to be executed when library is included
- `initCell`: a list of code snippets to be executed before execution of any cell
- `shutdown`: a list of code snippets to be executed on kernel shutdown. Any cleanup code goes here
- `renderers`: a mapping from fully qualified names of types to be rendered to the Kotlin expression returning output value.
  Source object is referenced as `$it`
- `resources`: a list of JS/CSS resources. See [this descriptor](../src/test/testData/lib-with-resources.json) for example
- `integrationTypeNameRules`: a list of rules for integration classes which are about to be loaded transitively. Each rule has the form `[+|-]:<pattern>` where `+` or `-` denotes if this pattern is accepted or declined. Pattern may consist of any characters. Special combinations are allowed: `?` (any single character or no character), `*` (any character excluding dot), `**` (any character).

*All fields are optional

For the most relevant specification see `org.jetbrains.kotlinx.jupyter.libraries.LibraryDescriptor` class.

Name of the file should have the `<name>.json` format where `<name>` is an argument for '%use' command

Library properties can be used in any parts of library descriptor as `$property`

To register new library descriptor:
1. For private usage - create it anywhere on your computer and reference it using file syntax.
2. Alternative way for private usage - create descriptor in `.jupyter_kotlin/libraries` folder and reference
   it using "default" syntax
3. For sharing with community - commit it to [libraries][libs-repo] repository and create pull request.

If you are maintaining some library and want to update your library descriptor, create pull request with your update.
After your request is accepted, new version of your library will be available to all Kotlin Jupyter users
immediately on next kernel startup (no kernel update is needed) - but only if they use `%useLatestDescriptors` magic.
If not, kernel update is needed.

## Integration using Kotlin API

You may also add a Kotlin kernel integration to your library using a
[Gradle plugin](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jupyter.api).

In the following code snippets `<jupyterApiVersion>` is one of the published versions from the link above.
It is encouraged to use the latest stable version.

First, add the plugin dependency into your buildscript.

For `build.gradle`:
```groovy
plugins {
    id "org.jetbrains.kotlin.jupyter.api" version "<jupyterApiVersion>"
}
```

For `build.gradle.kts`:
```kotlin
plugins {
    kotlin("jupyter.api") version "<jupyterApiVersion>"
}
```

This plugin adds following dependencies to your project:

| Artifact                         | Gradle option to exclude/include | Enabled by default | Dependency scope     | Method for adding dependency manually    |
|:---------------------------------|:---------------------------------|:-------------------|:---------------------|:-----------------------------------------|
| `kotlin-jupyter-api`             | `kotlin.jupyter.add.api`         | yes                | `compileOnly`        | `addApiDependency(version: String?)`     |
| `kotlin-jupyter-api-annotations` | `kotlin.jupyter.add.scanner`     | no                 | `compileOnly`        | `addScannerDependency(version: String?)` |
| `kotlin-jupyter-test-kit`        | `kotlin.jupyter.add.testkit`     | yes                | `testImplementation` | `addTestKitDependency(version: String?)` |

You may turn on / turn off the dependency with its default version (version of the plugin)
by setting corresponding Gradle option to `true` or `false`.
If the corresponding option is set to `false` (by default or in your setup), you still
can add it manually using the method from the table inside `kotlinJupyter` extension like that:

```groovy
kotlinJupyter {
    addApiDependency() // Use default version
    addApiDependency("0.10.0.1") // Use custom artifact version
}
```

### Adding library integration using KSP plugin

If you are OK with using KSP, you can use annotations to mark integration classes. 

First, enable `kotlin-jupyter-api-annotations` dependency by adding following line to your `gradle.properties`:

```
kotlin.jupyter.add.scanner = true
```

Then, implement `org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer` or
`org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition` and mark implementation with
`JupyterLibrary` annotation:

```kotlin
package org.my.lib
import org.jetbrains.kotlinx.jupyter.api.annotations.JupyterLibrary
import org.jetbrains.kotlinx.jupyter.api.*
import org.jetbrains.kotlinx.jupyter.api.libraries.*

@JupyterLibrary
internal class Integration : JupyterIntegration() {
    
    override fun Builder.onLoaded() {
        render<MyClass> { HTML(it.toHTML()) }
        import("org.my.lib.*")
        import("org.my.lib.io.*")
    }
}
```

For more complicated example see [integration of dataframe library](https://github.com/Kotlin/dataframe/blob/master/core/src/main/kotlin/org/jetbrains/kotlinx/dataframe/jupyter/Integration.kt).

For a further information see docs for:
 - `org.jetbrains.kotlinx.jupyter.api.libraries.JupyterIntegration`
 - `org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinitionProducer`
 - `org.jetbrains.kotlinx.jupyter.api.libraries.LibraryDefinition`

### Adding library integration avoiding use of annotation processor
You may want not to use KSP plugin for implementations detection.
Then you may refer your implementations right in your buildscript. Note that
no checking for existence will be performed in this case.

The following example shows how to refer aforementioned `Integration` class in your buildscript.
Obviously, in this case you shouldn't mark it with `JupyterLibrary` annotation.

For `build.gradle`:
```groovy
processJupyterApiResources {
    libraryProducers = ["org.my.lib.Integration"]
}
```

For `build.gradle.kts`:
```kotlin
tasks.processJupyterApiResources {
    libraryProducers = listOf("org.my.lib.Integration")
}
```

### Integration testing for the integration logic
You may want to automatically check if your library integrates correctly into kernel. To achieve this, inherit your
test class from `org.jetbrains.kotlinx.jupyter.testkit.JupyterReplTestCase` and use its methods to execute cells.
Your library integration descriptors should be already on classpath and will be loaded automatically by the test logic,
you don't need to use `%use` magic or `DependsOn` annotation to switch on your library. But you may use magics and
annotations for other purposes, as usual.

The artifact containing test templates is included automatically into `testImplementation` configuration if you
use the Gradle plugin. You may turn this behavior off by setting `kotlin.jupyter.add.testkit` Gradle property
to `false`. If you want to include this artifact into your build manually, you'll find the instructions
[here][maven-search-testkit].

For the examples of integration testing see `org.jetbrains.kotlinx.jupyter.testkit.test.JupyterReplTestingTest` in
this repository or [related tests in DataFrame][dataframe-integration-tests].

### Integration using other build systems

If you don't use Gradle as a build system, there is an alternative way.

First, add `org.jetbrains.kotlinx:kotlin-jupyter-api:<jupyterApiVersion>` as
a compile dependency. See configuration instructions for different build systems
[here](https://search.maven.org/artifact/org.jetbrains.kotlinx/kotlin-jupyter-api/0.9.0-17/jar)

Then add one or more integration classes. They may be derived from
`LibraryDefinitionProducer` or from `LibraryDefinition` as described above.
Note that you don't need `@JupyterLibrary` annotation in this scenario.

Finally, add file `META-INF/kotlin-jupyter-libraries/libraries.json` to the JAR
resources. This file should contain FQNs of all integration classes in the JSON form:
```json
{
  "definitions":[],
  "producers": [
    { "fqn" : "org.jetbrains.kotlinx.jupyter.example.GettingStartedIntegration" }
  ]
}
```
Classes derived from `LibraryDefinition` should be added to the `definitions` array.
Classes derived from `LibraryDefinitionProducer` should be added to the `producers` array.

[libs-repo]: https://github.com/Kotlin/kotlin-jupyter-libraries
[dataframe-integration-tests]: https://github.com/Kotlin/dataframe/tree/master/src/test/kotlin/org/jetbrains/dataframe/jupyter
[maven-search-testkit]: https://search.maven.org/artifact/org.jetbrains.kotlinx/kotlin-jupyter-test-kit
