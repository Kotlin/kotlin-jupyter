# 0.9.0 (planned)
### Features
* Main feature of this release is an API for Kotlin libraries
  that simplifies a kernel integration for them ([#99][p99]).
  Special [Gradle plugin](https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jupyter.api)
  is provided for this purpose. It enables the dependency on `kotlin-jupyter-api` artifact
  which allows a library writer to add a kernel integration by implementing one of
  `LibraryDefinition` or `LibraryDefinitionProvider` interfaces. You may find a minimal example
  in [docs](docs/libraries.md). For more "real-world" example see the integration for
  [Kotlin dataframe library][integration-in-dataframe]
* Add handling clients with `allow_stdin=false` ([#124][p124])
* Update library descriptors for Lets-Plot, Kravis ([#118][p118]) and kaliningraph
* Add enhanced support of typed dataframes (`dataframe` library)
* Add jDSP library descriptor ([#114][p114])
* Add possibility of kernel embedding ([#102][p102])
* Provide completion and errors analysis for commands
* Add support for minKernelVersion field in library descriptors
* Formalise kernel versions format and ordering
* Add possibility to provide shutdown hooks in library descriptors ([#87][i87])
* Switch to the stable version of Kotlin (1.4.30)

### Bugs
* Fix completion bug in Notebook client ([#113][i113])
* Fix irrelevant error popups in Notebook client ([#109][i109])
* Improve and fix parsing of `%use` magic ([#110][i110])
* Add resolution of transitive dependencies with runtime scope (previously only compile dependencies were resolved)
* Fix "leaking" of kernel stdlib into script classpath ([#27][i27])
* Fix added repositories ordering ([#107][i107])

### Internal things / infrastructure
* Add parallel testing
* Upgrade Gradle
* Add ktlint check
* Add README generation from library descriptors
  and magics/commands descriptions
* Distribute `kotlin-jupyter-shared-compiler` artifact
  which may be used for building scripting compilers
  with Jupyter dialect, including compilers inside IDEA


[integration-in-dataframe]: https://github.com/nikitinas/dataframe/blob/32a21398175029d68508e2129727c135b9a126b9/src/main/kotlin/org/jetbrains/dataframe/jupyter/Integration.kt

[i27]: https://github.com/Kotlin/kotlin-jupyter/issues/27
[i87]: https://github.com/Kotlin/kotlin-jupyter/issues/87
[i107]: https://github.com/Kotlin/kotlin-jupyter/issues/107
[i109]: https://github.com/Kotlin/kotlin-jupyter/issues/109
[i110]: https://github.com/Kotlin/kotlin-jupyter/issues/110
[i113]: https://github.com/Kotlin/kotlin-jupyter/issues/113

[p99]: https://github.com/Kotlin/kotlin-jupyter/pull/99
[p102]: https://github.com/Kotlin/kotlin-jupyter/pull/102
[p114]: https://github.com/Kotlin/kotlin-jupyter/pull/114
[p118]: https://github.com/Kotlin/kotlin-jupyter/pull/118
[p124]: https://github.com/Kotlin/kotlin-jupyter/pull/124
