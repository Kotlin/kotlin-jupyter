# Change Log

## Unreleased (2024-04-14)

**Closed issues:**

- Using `jupyter.api` Gradle plugin breaks when I use Kotest 5.8.0 in my project [\#452](https://github.com/Kotlin/kotlin-jupyter/issues/452)

**Merged pull requests:**

- Fix links to JupyterIntegration example tests [\#457](https://github.com/Kotlin/kotlin-jupyter/pull/457) ([@gabrielfeo](https://github.com/gabrielfeo))
- Update Kotlin to 1.9.23 [\#454](https://github.com/Kotlin/kotlin-jupyter/pull/454) ([@ileasile](https://github.com/ileasile))

## [0.12.0.154](https://github.com/Kotlin/kotlin-jupyter/tree/0.12.0.154) (2024-03-05)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.12.0.145...0.12.0.154)


## [0.12.0.145](https://github.com/Kotlin/kotlin-jupyter/tree/0.12.0.145) (2024-03-04)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.12.0.139...0.12.0.145)


## [0.12.0.139](https://github.com/Kotlin/kotlin-jupyter/tree/0.12.0.139) (2024-02-18)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.12.0.128...0.12.0.139)

**Closed issues:**

- Experimental `%javascript`/`%typescript`/`%jsx` magic support [\#446](https://github.com/Kotlin/kotlin-jupyter/issues/446)

**Merged pull requests:**

- Introduce class for setting up debug integration [\#447](https://github.com/Kotlin/kotlin-jupyter/pull/447) ([@nikolay-egorov](https://github.com/nikolay-egorov))

## [0.12.0.128](https://github.com/Kotlin/kotlin-jupyter/tree/0.12.0.128) (2024-02-10)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.11.0.364...0.12.0.128)

**New features:**

- Provide API for rendering based on compile\-time types [\#421](https://github.com/Kotlin/kotlin-jupyter/issues/421)

**Closed issues:**

- HTML\("\<table\>..."\) breaks rendering \(regression\) [\#442](https://github.com/Kotlin/kotlin-jupyter/issues/442)
- dont fail on additional json payload like user\_variables [\#440](https://github.com/Kotlin/kotlin-jupyter/issues/440)
- why ctor argument type mismatch [\#433](https://github.com/Kotlin/kotlin-jupyter/issues/433)
- Using host.display inside Renderer leads to strange behavior  [\#431](https://github.com/Kotlin/kotlin-jupyter/issues/431)
- ClassNotFoundException: org.postgresql.Driver [\#426](https://github.com/Kotlin/kotlin-jupyter/issues/426)
- Add USE for `JupyterIntegration` extension [\#424](https://github.com/Kotlin/kotlin-jupyter/issues/424)
- Adding kotlin dependencies to the the jupyter\-lab instance [\#419](https://github.com/Kotlin/kotlin-jupyter/issues/419)
- Gradle plugin for kotlin jupyter doesn't work [\#418](https://github.com/Kotlin/kotlin-jupyter/issues/418)
- HTML exported with broken layout if both kandy and dataframe added [\#414](https://github.com/Kotlin/kotlin-jupyter/issues/414)

**Merged pull requests:**

- update to latest descriptors [\#439](https://github.com/Kotlin/kotlin-jupyter/pull/439) ([@koperagen](https://github.com/koperagen))
- Resolve multiple Maven dependencies at once [\#432](https://github.com/Kotlin/kotlin-jupyter/pull/432) ([@ileasile](https://github.com/ileasile))
- Add API for handling result field with a type converter API [\#425](https://github.com/Kotlin/kotlin-jupyter/pull/425) ([@ileasile](https://github.com/ileasile))
- Skip HTML files when processing this project in linguist [\#420](https://github.com/Kotlin/kotlin-jupyter/pull/420) ([@DRSchlaubi](https://github.com/DRSchlaubi))

**Fixed bugs:**

- \[JupyterLab\] unable to use symbols from unnamed packages [\#422](https://github.com/Kotlin/kotlin-jupyter/issues/422)
- Cannot access class 'jupyter.kotlin.MimeTypedResult' [\#388](https://github.com/Kotlin/kotlin-jupyter/issues/388)

## [0.11.0.364](https://github.com/Kotlin/kotlin-jupyter/tree/0.11.0.364) (2023-05-05)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.11.0.348...0.11.0.364)

**Closed issues:**

- RuntimeError: Kernel didn't respond to kernel\_info\_request FileNotFoundError: \[WinError 2\] The system cannot find the file specified [\#405](https://github.com/Kotlin/kotlin-jupyter/issues/405)

## [0.11.0.348](https://github.com/Kotlin/kotlin-jupyter/tree/0.11.0.348) (2023-03-31)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.11.0.208...0.11.0.348)

**New features:**

- Add repository authorization feature to a new gradle\-like dependency API [\#384](https://github.com/Kotlin/kotlin-jupyter/issues/384)

**Closed issues:**

- class loading behaviour [\#403](https://github.com/Kotlin/kotlin-jupyter/issues/403)
- The `nbconvert` failed to execute the `Kotlin` notebook [\#401](https://github.com/Kotlin/kotlin-jupyter/issues/401)
- Concurrency exception [\#402](https://github.com/Kotlin/kotlin-jupyter/issues/402)
- classpath resource loading uses wrong validation check [\#389](https://github.com/Kotlin/kotlin-jupyter/issues/389)

**Merged pull requests:**

- Moved cache in message\_type serializer to ConcurrentHashMap [\#404](https://github.com/Kotlin/kotlin-jupyter/pull/404) ([@jbaron](https://github.com/jbaron))
- Fix link to dataframe integration example. [\#398](https://github.com/Kotlin/kotlin-jupyter/pull/398) ([@cmelchior](https://github.com/cmelchior))
- Extract method for generation of IFrame plain text from HtmlData [\#397](https://github.com/Kotlin/kotlin-jupyter/pull/397) ([@ermolenkodev](https://github.com/ermolenkodev))
- mybinder: Update to Java 11 [\#394](https://github.com/Kotlin/kotlin-jupyter/pull/394) ([@manics](https://github.com/manics))
- Fix race condition in `DisplayContainerImpl.displays` list. [\#393](https://github.com/Kotlin/kotlin-jupyter/pull/393) ([@nikitinas](https://github.com/nikitinas))

**Fixed bugs:**

- Can't find JVM bin [\#386](https://github.com/Kotlin/kotlin-jupyter/issues/386)

## [0.11.0.208](https://github.com/Kotlin/kotlin-jupyter/tree/0.11.0.208) (2022-12-17)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.11.0.196...0.11.0.208)

**Merged pull requests:**

- added updating information to Readme [\#391](https://github.com/Kotlin/kotlin-jupyter/pull/391) ([@Jolanrensen](https://github.com/Jolanrensen))

**Fixed bugs:**

- MIME encoded json gives unexpected result [\#387](https://github.com/Kotlin/kotlin-jupyter/issues/387)

## [0.11.0.196](https://github.com/Kotlin/kotlin-jupyter/tree/0.11.0.196) (2022-12-11)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.11.0.170...0.11.0.196)

**Merged pull requests:**

- Support display id in `display\(value\)` API. [\#385](https://github.com/Kotlin/kotlin-jupyter/pull/385) ([@nikitinas](https://github.com/nikitinas))

## [0.11.0.170](https://github.com/Kotlin/kotlin-jupyter/tree/0.11.0.170) (2022-10-05)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.11.0.144...0.11.0.170)

**New features:**

- Add a function to configure dependencies and repositories in gradle\-like manner [\#367](https://github.com/Kotlin/kotlin-jupyter/issues/367)

**Merged pull requests:**

- Add comm\-message handlers and support for debugPort config retrieval [\#375](https://github.com/Kotlin/kotlin-jupyter/pull/375) ([@nikolay-egorov](https://github.com/nikolay-egorov))

**Fixed bugs:**

- Add Gradle\-like API for adding dependencies [\#382](https://github.com/Kotlin/kotlin-jupyter/pull/382) ([@ileasile](https://github.com/ileasile))
- kernel doesn't start [\#379](https://github.com/Kotlin/kotlin-jupyter/issues/379)
- Exception when reaching limit in github api [\#378](https://github.com/Kotlin/kotlin-jupyter/issues/378)

## [0.11.0.144](https://github.com/Kotlin/kotlin-jupyter/tree/0.11.0.144) (2022-08-05)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.11.0.140...0.11.0.144)


## [0.11.0.140](https://github.com/Kotlin/kotlin-jupyter/tree/0.11.0.140) (2022-07-26)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.11.0.95...0.11.0.140)

**New features:**

- Using a non\-default Maven local repository path [\#365](https://github.com/Kotlin/kotlin-jupyter/issues/365)

**Closed issues:**

- Add stability badge to README [\#370](https://github.com/Kotlin/kotlin-jupyter/issues/370)

**Merged pull requests:**

- Add presentableVarsState for debug output with caching [\#374](https://github.com/Kotlin/kotlin-jupyter/pull/374) ([@nikolay-egorov](https://github.com/nikolay-egorov))
- Add socket messages callbacks and comms API [\#376](https://github.com/Kotlin/kotlin-jupyter/pull/376) ([@ileasile](https://github.com/ileasile))

**Fixed bugs:**

- Completion doesn't work for identifiers containing Unicode letters [\#373](https://github.com/Kotlin/kotlin-jupyter/issues/373)

## [0.11.0.95](https://github.com/Kotlin/kotlin-jupyter/tree/0.11.0.95) (2022-05-19)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.11.0.74...0.11.0.95)

**New features:**

- Add onInterrupt to the API [\#369](https://github.com/Kotlin/kotlin-jupyter/issues/369)
- Add graphs visualization with GraphViz [\#219](https://github.com/Kotlin/kotlin-jupyter/issues/219)
- Add possibility to filter Integration classes FQNs in JSON descriptors [\#363](https://github.com/Kotlin/kotlin-jupyter/issues/363)
- Automatically load stdlib extensions with approprieate JDK [\#358](https://github.com/Kotlin/kotlin-jupyter/issues/358)

**Closed issues:**

- Library Loading with %use only works when `homeDir` is not null, but invalid `homeDir` works fine [\#359](https://github.com/Kotlin/kotlin-jupyter/issues/359)

**Merged pull requests:**

- integrationTypeNameRules support for LibrariesScanner [\#364](https://github.com/Kotlin/kotlin-jupyter/pull/364) ([@Jolanrensen](https://github.com/Jolanrensen))

## [0.11.0.74](https://github.com/Kotlin/kotlin-jupyter/tree/0.11.0.74) (2022-04-13)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.11.0.61...0.11.0.74)

**Closed issues:**

- Backend Internal error: Exception during IR lowering [\#360](https://github.com/Kotlin/kotlin-jupyter/issues/360)

## [0.11.0.61](https://github.com/Kotlin/kotlin-jupyter/tree/0.11.0.61) (2022-02-14)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.11.0.45...0.11.0.61)

**New features:**

- Add API to integration library to detect jupyter context [\#352](https://github.com/Kotlin/kotlin-jupyter/issues/352)

**Closed issues:**

- Unresolved reference: tensorflow when importing tensorflow\-lite  [\#350](https://github.com/Kotlin/kotlin-jupyter/issues/350)

**Merged pull requests:**

- Add detection of the current Jupyter Client to the API. Switch build to Java 11. [\#346](https://github.com/Kotlin/kotlin-jupyter/pull/346) ([@ileasile](https://github.com/ileasile))

**Fixed bugs:**

- Backend Internal error: Exception during file facade code generation [\#356](https://github.com/Kotlin/kotlin-jupyter/issues/356)
- Order of %use statements should not matter for class loading [\#354](https://github.com/Kotlin/kotlin-jupyter/issues/354)

## [0.11.0.45](https://github.com/Kotlin/kotlin-jupyter/tree/0.11.0.45) (2021-12-24)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.11.0.37...0.11.0.45)


## [0.11.0.37](https://github.com/Kotlin/kotlin-jupyter/tree/0.11.0.37) (2021-12-08)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.11.0.33...0.11.0.37)


## [0.11.0.33](https://github.com/Kotlin/kotlin-jupyter/tree/0.11.0.33) (2021-12-06)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.11.0.31...0.11.0.33)


## [0.11.0.31](https://github.com/Kotlin/kotlin-jupyter/tree/0.11.0.31) (2021-12-04)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.11.0.16...0.11.0.31)

**Closed issues:**

- An internal error when evaluating code with syntax errors [\#347](https://github.com/Kotlin/kotlin-jupyter/issues/347)

**Fixed bugs:**

- Can not use object expresssions [\#341](https://github.com/Kotlin/kotlin-jupyter/issues/341)

## [0.11.0.16](https://github.com/Kotlin/kotlin-jupyter/tree/0.11.0.16) (2021-11-27)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.11.0.15...0.11.0.16)


## [0.11.0.15](https://github.com/Kotlin/kotlin-jupyter/tree/0.11.0.15) (2021-11-27)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.3.31...0.11.0.15)

**Closed issues:**

- KSP does not work with Kotlin 1.6.0\-RC [\#342](https://github.com/Kotlin/kotlin-jupyter/issues/342)

## [0.10.3.31](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.3.31) (2021-11-02)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.3.20...0.10.3.31)


## [0.10.3.20](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.3.20) (2021-10-15)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.3.19...0.10.3.20)


## [0.10.3.19](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.3.19) (2021-10-14)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.3.18...0.10.3.19)


## [0.10.3.18](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.3.18) (2021-10-13)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.3.16...0.10.3.18)


## [0.10.3.16](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.3.16) (2021-10-13)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.1.11...0.10.3.16)

**Closed issues:**

- Replace KAPT with KSP in Gradle plugin [\#338](https://github.com/Kotlin/kotlin-jupyter/issues/338)

**Merged pull requests:**

- Replace KAPT with KSP [\#339](https://github.com/Kotlin/kotlin-jupyter/pull/339) ([@ileasile](https://github.com/ileasile))

## [0.10.1.11](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.1.11) (2021-10-12)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.1.7...0.10.1.11)


## [0.10.1.7](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.1.7) (2021-10-10)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.1.3...0.10.1.7)


## [0.10.1.3](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.1.3) (2021-10-08)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.0.260...0.10.1.3)

**New features:**

- Add exception handling/interception [\#332](https://github.com/Kotlin/kotlin-jupyter/issues/332)

**Merged pull requests:**

- Add exceptions rendering [\#337](https://github.com/Kotlin/kotlin-jupyter/pull/337) ([@ileasile](https://github.com/ileasile))

## [0.10.0.260](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.0.260) (2021-09-30)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.0.249...0.10.0.260)

**Closed issues:**

- Leaking HTTP connections [\#333](https://github.com/Kotlin/kotlin-jupyter/issues/333)

**Merged pull requests:**

- Provide info about resolved libraries in API [\#336](https://github.com/Kotlin/kotlin-jupyter/pull/336) ([@ileasile](https://github.com/ileasile))
- Fix \#333, Close HTTP clients [\#335](https://github.com/Kotlin/kotlin-jupyter/pull/335) ([@strangepleasures](https://github.com/strangepleasures))

## [0.10.0.249](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.0.249) (2021-09-27)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.0.227...0.10.0.249)

**Merged pull requests:**

- Fix \#326 \- proper handling of store\_history [\#329](https://github.com/Kotlin/kotlin-jupyter/pull/329) ([@strangepleasures](https://github.com/strangepleasures))

**Fixed bugs:**

- Every execution request increments `execution\_count`,  even if `store\_history=False` [\#326](https://github.com/Kotlin/kotlin-jupyter/issues/326)
- No syntax highlighting and completion. [\#327](https://github.com/Kotlin/kotlin-jupyter/issues/327)

## [0.10.0.227](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.0.227) (2021-09-06)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.0.200...0.10.0.227)

**Closed issues:**

- Evaluation of `Out` results in an exception [\#328](https://github.com/Kotlin/kotlin-jupyter/issues/328)
- Remove Kapt by default [\#325](https://github.com/Kotlin/kotlin-jupyter/issues/325)
- Setting `JupyterApiResourcesTask.libraryProducers` doesn't disable `kapt` that causes incompatibility with JDK 16 [\#322](https://github.com/Kotlin/kotlin-jupyter/issues/322)

**Merged pull requests:**

- Add JARs path detection for Kotlin Notebook plugin [\#324](https://github.com/Kotlin/kotlin-jupyter/pull/324) ([@ileasile](https://github.com/ileasile))
- Fix issue with recursive structures [\#320](https://github.com/Kotlin/kotlin-jupyter/pull/320) ([@nikolay-egorov](https://github.com/nikolay-egorov))

**Fixed bugs:**

- Returning a lambda as a result causes an exception [\#323](https://github.com/Kotlin/kotlin-jupyter/issues/323)
- Adding a new library to kotlin notebook using API [\#321](https://github.com/Kotlin/kotlin-jupyter/issues/321)
- The kernel hangs if a top\-level variable is assigned a recursive data structure [\#319](https://github.com/Kotlin/kotlin-jupyter/issues/319)

## [0.10.0.200](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.0.200) (2021-08-12)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.0.183...0.10.0.200)


## [0.10.0.183](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.0.183) (2021-08-06)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.0.40...0.10.0.183)

**New features:**

- Allow for multiple cell outputs [\#20](https://github.com/Kotlin/kotlin-jupyter/issues/20)
- Dynamic update of cell output [\#318](https://github.com/Kotlin/kotlin-jupyter/issues/318)
- Integration testing Jupyter notebook support [\#270](https://github.com/Kotlin/kotlin-jupyter/issues/270)
- Add resource builder for Notebook API [\#129](https://github.com/Kotlin/kotlin-jupyter/issues/129)
- Load static resources via module descriptions [\#74](https://github.com/Kotlin/kotlin-jupyter/issues/74)
- Autoremove closing bracket when opening is removed [\#245](https://github.com/Kotlin/kotlin-jupyter/issues/245)
- Allow setting JDK to use [\#284](https://github.com/Kotlin/kotlin-jupyter/issues/284)
- Pass args on JVM startup [\#72](https://github.com/Kotlin/kotlin-jupyter/issues/72)

**Closed issues:**

- Refactor build logic [\#306](https://github.com/Kotlin/kotlin-jupyter/issues/306)
- Update Kravis and Krangl library descriptors [\#309](https://github.com/Kotlin/kotlin-jupyter/issues/309)
- Create a separate repository for descriptors [\#254](https://github.com/Kotlin/kotlin-jupyter/issues/254)
- Support multiplatform projects in jupyter.api plugin [\#148](https://github.com/Kotlin/kotlin-jupyter/issues/148)
- Update changelog [\#253](https://github.com/Kotlin/kotlin-jupyter/issues/253)
- Update landing page screenshot [\#307](https://github.com/Kotlin/kotlin-jupyter/issues/307)
- Display list of data classes as table [\#294](https://github.com/Kotlin/kotlin-jupyter/issues/294)
- MIME does not allow binary data [\#293](https://github.com/Kotlin/kotlin-jupyter/issues/293)
- Images are not displayed [\#292](https://github.com/Kotlin/kotlin-jupyter/issues/292)
- Add Skija as a supported library [\#272](https://github.com/Kotlin/kotlin-jupyter/issues/272)

**Merged pull requests:**

- Refactor build [\#315](https://github.com/Kotlin/kotlin-jupyter/pull/315) ([@ileasile](https://github.com/ileasile))
- Add separate repository for library descriptors [\#303](https://github.com/Kotlin/kotlin-jupyter/pull/303) ([@ileasile](https://github.com/ileasile))
- Fix completion starting logic, avoid preventDefault [\#308](https://github.com/Kotlin/kotlin-jupyter/pull/308) ([@ileasile](https://github.com/ileasile))
- Update Changelog and fix Gradle plugin sources publication [\#311](https://github.com/Kotlin/kotlin-jupyter/pull/311) ([@ileasile](https://github.com/ileasile))
- Conditional Gradle plugin tasks initialization [\#298](https://github.com/Kotlin/kotlin-jupyter/pull/298) ([@ileasile](https://github.com/ileasile))
- Create specialized kernels w/ JDK, JVM args, and environment variables [\#287](https://github.com/Kotlin/kotlin-jupyter/pull/287) ([@rnett](https://github.com/rnett))
- Upgrade to Lets\-Plot v2.0.4, Lets\-Plot Kotlin API v3.0.1 [\#275](https://github.com/Kotlin/kotlin-jupyter/pull/275) ([@alshan](https://github.com/alshan))

**Fixed bugs:**

- Sources are not published for API Gradle plugin [\#310](https://github.com/Kotlin/kotlin-jupyter/issues/310)
- Fix Gradle plugin for Multiplatform project [\#255](https://github.com/Kotlin/kotlin-jupyter/issues/255)
- Cannot find kernel [\#268](https://github.com/Kotlin/kotlin-jupyter/issues/268)
- Completion of functions parameters works incorrectly [\#256](https://github.com/Kotlin/kotlin-jupyter/issues/256)
- Dead kernel and couln't load notebook [\#271](https://github.com/Kotlin/kotlin-jupyter/issues/271)
- Resolution errors with snapshot repo for dependency with classifier [\#285](https://github.com/Kotlin/kotlin-jupyter/issues/285)

## [0.10.0.40](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.0.40) (2021-05-22)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.0.39...0.10.0.40)


## [0.10.0.39](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.0.39) (2021-05-22)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.0.38...0.10.0.39)


## [0.10.0.38](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.0.38) (2021-05-21)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.0.33...0.10.0.38)


## [0.10.0.33](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.0.33) (2021-05-20)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.0.30...0.10.0.33)


## [0.10.0.30](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.0.30) (2021-05-20)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.10.0.17...0.10.0.30)

**New features:**

- Add more convenient ways of displaying data [\#112](https://github.com/Kotlin/kotlin-jupyter/issues/112)

**Closed issues:**

- Load library descriptor from URL with redirection [\#248](https://github.com/Kotlin/kotlin-jupyter/issues/248)

## [0.10.0.17](https://github.com/Kotlin/kotlin-jupyter/tree/0.10.0.17) (2021-05-14)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.9.1.51...0.10.0.17)

**New features:**

- Is it possible to define a custom \(cell\) magic [\#223](https://github.com/Kotlin/kotlin-jupyter/issues/223)
- Complete named parameters in function [\#187](https://github.com/Kotlin/kotlin-jupyter/issues/187)
- Add support for Kotlin @Deprecated annotation [\#185](https://github.com/Kotlin/kotlin-jupyter/issues/185)

**Merged pull requests:**

- Switch to Maven dependencies resolver [\#230](https://github.com/Kotlin/kotlin-jupyter/pull/230) ([@ileasile](https://github.com/ileasile))
- Add code preprocessors as a part of library API [\#226](https://github.com/Kotlin/kotlin-jupyter/pull/226) ([@ileasile](https://github.com/ileasile))

**Fixed bugs:**

- Add option to bypass ivy cache for dependencies [\#121](https://github.com/Kotlin/kotlin-jupyter/issues/121)
- lets\-plot example fails to load dependency \(unknown resolver null\) [\#117](https://github.com/Kotlin/kotlin-jupyter/issues/117)

## [0.9.1.51](https://github.com/Kotlin/kotlin-jupyter/tree/0.9.1.51) (2021-05-05)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.9.1.42...0.9.1.51)

**New features:**

- Add native libraries loading support [\#218](https://github.com/Kotlin/kotlin-jupyter/issues/218)

**Merged pull requests:**

- Add hierarchies visualization API to lib\-ext [\#220](https://github.com/Kotlin/kotlin-jupyter/pull/220) ([@ileasile](https://github.com/ileasile))

**Fixed bugs:**

- How to use a dynamic library? [\#214](https://github.com/Kotlin/kotlin-jupyter/issues/214)

## [0.9.1.42](https://github.com/Kotlin/kotlin-jupyter/tree/0.9.1.42) (2021-05-01)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.9.1.39...0.9.1.42)


## [0.9.1.39](https://github.com/Kotlin/kotlin-jupyter/tree/0.9.1.39) (2021-04-30)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.9.1.9...0.9.1.39)

**New features:**

- Allow defining notebook values in the Notebook API [\#206](https://github.com/Kotlin/kotlin-jupyter/issues/206)

**Merged pull requests:**

- Add support for variables declarations in library API [\#215](https://github.com/Kotlin/kotlin-jupyter/pull/215) ([@ileasile](https://github.com/ileasile))
- Update plotly.kt descriptors [\#213](https://github.com/Kotlin/kotlin-jupyter/pull/213) ([@altavir](https://github.com/altavir))

## [0.9.1.9](https://github.com/Kotlin/kotlin-jupyter/tree/0.9.1.9) (2021-04-24)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.9.1.5...0.9.1.9)

**Merged pull requests:**

- Add Multik library descriptor [\#198](https://github.com/Kotlin/kotlin-jupyter/pull/198) ([@devcrocod](https://github.com/devcrocod))

## [0.9.1.5](https://github.com/Kotlin/kotlin-jupyter/tree/0.9.1.5) (2021-04-19)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.9.1.3...0.9.1.5)


## [0.9.1.3](https://github.com/Kotlin/kotlin-jupyter/tree/0.9.1.3) (2021-04-19)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.9.1.1...0.9.1.3)


## [0.9.1.1](https://github.com/Kotlin/kotlin-jupyter/tree/0.9.1.1) (2021-04-17)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.9.0.14...0.9.1.1)

**New features:**

- Implement execution interruption [\#58](https://github.com/Kotlin/kotlin-jupyter/issues/58)
- Logging options [\#54](https://github.com/Kotlin/kotlin-jupyter/issues/54)

**Closed issues:**

- Support rendering of an object dynamically inside CodeCell context [\#182](https://github.com/Kotlin/kotlin-jupyter/issues/182)

**Merged pull requests:**

- Update Lets\-Plot dependencies [\#196](https://github.com/Kotlin/kotlin-jupyter/pull/196) ([@alshan](https://github.com/alshan))
- Create londogard\-nlp\-toolkit.json [\#186](https://github.com/Kotlin/kotlin-jupyter/pull/186) ([@Lundez](https://github.com/Lundez))
- Allow value rendering using renderers processor from API [\#183](https://github.com/Kotlin/kotlin-jupyter/pull/183) ([@ileasile](https://github.com/ileasile))

## [0.9.0.14](https://github.com/Kotlin/kotlin-jupyter/tree/0.9.0.14) (2021-04-01)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.9.0.6...0.9.0.14)


## [0.9.0.7](https://github.com/Kotlin/kotlin-jupyter/tree/0.9.0.7) (2021-03-30)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.9.0.5...0.9.0.7)


## [0.9.0.6](https://github.com/Kotlin/kotlin-jupyter/tree/0.9.0.6) (2021-03-30)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.9.0.7...0.9.0.6)


## [0.9.0.5](https://github.com/Kotlin/kotlin-jupyter/tree/0.9.0.5) (2021-03-29)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.9.0.3...0.9.0.5)


## [0.9.0.3](https://github.com/Kotlin/kotlin-jupyter/tree/0.9.0.3) (2021-03-29)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.8.3.122...0.9.0.3)

**New features:**

- Support for custom type handlers [\#12](https://github.com/Kotlin/kotlin-jupyter/issues/12)
- Publish the cell and display layout API as a separate artifact. [\#78](https://github.com/Kotlin/kotlin-jupyter/issues/78)

**Closed issues:**

- Gradle plugin [\#105](https://github.com/Kotlin/kotlin-jupyter/issues/105)
- Kernel uses serialization runtime from itself, not the library [\#161](https://github.com/Kotlin/kotlin-jupyter/issues/161)
- Use JVM\-IR compiler in scripts [\#131](https://github.com/Kotlin/kotlin-jupyter/issues/131)
- Error when integrating with the Kotlin API [\#147](https://github.com/Kotlin/kotlin-jupyter/issues/147)
- Problem starting kernel: org.zeromq.ZMQException: Errno 48 : Address already in use [\#136](https://github.com/Kotlin/kotlin-jupyter/issues/136)
- Add Cell information to API render method [\#128](https://github.com/Kotlin/kotlin-jupyter/issues/128)
- Why I cannot use xchart in notebook? [\#123](https://github.com/Kotlin/kotlin-jupyter/issues/123)

**Merged pull requests:**

- Update Lets\-Plot dependencies: api \-\> 1.3.0, lib \-\> 2.0.1 [\#176](https://github.com/Kotlin/kotlin-jupyter/pull/176) ([@alshan](https://github.com/alshan))
- Update kscience versions [\#167](https://github.com/Kotlin/kotlin-jupyter/pull/167) ([@altavir](https://github.com/altavir))
- Feature/plugin mpp [\#159](https://github.com/Kotlin/kotlin-jupyter/pull/159) ([@altavir](https://github.com/altavir))
- Feature/resource builders [\#158](https://github.com/Kotlin/kotlin-jupyter/pull/158) ([@altavir](https://github.com/altavir))
- Make embedKernel arguments nullable [\#132](https://github.com/Kotlin/kotlin-jupyter/pull/132) ([@fmagin](https://github.com/fmagin))
- Don't kill process when embedded [\#135](https://github.com/Kotlin/kotlin-jupyter/pull/135) ([@fmagin](https://github.com/fmagin))
- Fill HistoryRequest fields [\#134](https://github.com/Kotlin/kotlin-jupyter/pull/134) ([@fmagin](https://github.com/fmagin))
- Move notebook from onLoaded to Builder argument [\#130](https://github.com/Kotlin/kotlin-jupyter/pull/130) ([@ileasile](https://github.com/ileasile))
- Fix publishing [\#127](https://github.com/Kotlin/kotlin-jupyter/pull/127) ([@ileasile](https://github.com/ileasile))
- Notebook API [\#99](https://github.com/Kotlin/kotlin-jupyter/pull/99) ([@ileasile](https://github.com/ileasile))
- Support clients without stdin support \(allow\_stdin=false\) [\#124](https://github.com/Kotlin/kotlin-jupyter/pull/124) ([@strangepleasures](https://github.com/strangepleasures))

**Fixed bugs:**

- %use kmath fails with "module not found" [\#166](https://github.com/Kotlin/kotlin-jupyter/issues/166)
- Attaching with console or qtconsole crashes kernel [\#133](https://github.com/Kotlin/kotlin-jupyter/issues/133)

## [0.8.3.122](https://github.com/Kotlin/kotlin-jupyter/tree/0.8.3.122) (2021-01-16)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.8.3.64...0.8.3.122)

**Merged pull requests:**

- Updated kravis.json [\#118](https://github.com/Kotlin/kotlin-jupyter/pull/118) ([@holgerbrandl](https://github.com/holgerbrandl))
- Add jDSP library to supported libraries. [\#114](https://github.com/Kotlin/kotlin-jupyter/pull/114) ([@biranyucel](https://github.com/biranyucel))

## [0.8.3.64](https://github.com/Kotlin/kotlin-jupyter/tree/0.8.3.64) (2020-12-16)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.8.3.1...0.8.3.64)

**Fixed bugs:**

- Completion bug [\#113](https://github.com/Kotlin/kotlin-jupyter/issues/113)

## [0.8.3.1](https://github.com/Kotlin/kotlin-jupyter/tree/0.8.3.1) (2020-11-18)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.8.2.61...0.8.3.1)

**New features:**

- Add optional version tag to use directive [\#70](https://github.com/Kotlin/kotlin-jupyter/issues/70)

**Closed issues:**

- Run kotlin\-jupyter locally [\#104](https://github.com/Kotlin/kotlin-jupyter/issues/104)
- Add \*mavenLocal to default repositories? [\#100](https://github.com/Kotlin/kotlin-jupyter/issues/100)
- Add tested operating systems [\#98](https://github.com/Kotlin/kotlin-jupyter/issues/98)
- Unable to import krangl [\#48](https://github.com/Kotlin/kotlin-jupyter/issues/48)
- Exception in starting Kernel [\#52](https://github.com/Kotlin/kotlin-jupyter/issues/52)

**Merged pull requests:**

- Change added repositories order [\#108](https://github.com/Kotlin/kotlin-jupyter/pull/108) ([@ileasile](https://github.com/ileasile))
- Allow REPL/Kernel to be embedded and share classes/variables with its caller [\#102](https://github.com/Kotlin/kotlin-jupyter/pull/102) ([@fmagin](https://github.com/fmagin))
- Add Kaliningraph library descriptor [\#106](https://github.com/Kotlin/kotlin-jupyter/pull/106) ([@breandan](https://github.com/breandan))
- Add plotly descriptors [\#103](https://github.com/Kotlin/kotlin-jupyter/pull/103) ([@altavir](https://github.com/altavir))
- Perhaps, the year number should be changed. [\#97](https://github.com/Kotlin/kotlin-jupyter/pull/97) ([@zhelenskiy](https://github.com/zhelenskiy))
- Add automatic libraries list generation [\#96](https://github.com/Kotlin/kotlin-jupyter/pull/96) ([@ileasile](https://github.com/ileasile))
- Refactor libraries handling, add handling of descriptors from different sources [\#89](https://github.com/Kotlin/kotlin-jupyter/pull/89) ([@ileasile](https://github.com/ileasile))
- Update to Lets\-Plot Kotlin API v1.0.0 [\#95](https://github.com/Kotlin/kotlin-jupyter/pull/95) ([@alshan](https://github.com/alshan))
- Update to Lets\-Plot to v1.5.2, Kotlin API v1.0.0\-rc1 [\#94](https://github.com/Kotlin/kotlin-jupyter/pull/94) ([@alshan](https://github.com/alshan))
- Upgrade to Lets\-Plot 1.5.1\-SNAPSHOT [\#93](https://github.com/Kotlin/kotlin-jupyter/pull/93) ([@alshan](https://github.com/alshan))

**Fixed bugs:**

- Irrelevant 'Unresolved reference' popup. [\#109](https://github.com/Kotlin/kotlin-jupyter/issues/109)
- The %use magic doesn't allow to specify version range. [\#110](https://github.com/Kotlin/kotlin-jupyter/issues/110)
- Dependency not resolving in case pom existing but jar missing in repository [\#107](https://github.com/Kotlin/kotlin-jupyter/issues/107)

## [0.8.2.61](https://github.com/Kotlin/kotlin-jupyter/tree/0.8.2.61) (2020-08-05)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.8.2.53...0.8.2.61)

**Merged pull requests:**

- Add long description to published packages [\#91](https://github.com/Kotlin/kotlin-jupyter/pull/91) ([@ileasile](https://github.com/ileasile))

## [0.8.2.53](https://github.com/Kotlin/kotlin-jupyter/tree/0.8.2.53) (2020-08-03)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0.8.2.5...0.8.2.53)

**Fixed bugs:**

- Provide correct completion and error analysis for commands \(:help and :classpath\) [\#90](https://github.com/Kotlin/kotlin-jupyter/issues/90)

## [0.8.2.5](https://github.com/Kotlin/kotlin-jupyter/tree/0.8.2.5) (2020-07-17)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/build-0.8.2.4...0.8.2.5)


## [build\-0.8.2.4](https://github.com/Kotlin/kotlin-jupyter/tree/build-0.8.2.4) (2020-07-17)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/build-0.8.2.2...build-0.8.2.4)

**New features:**

- Add shutdown hook to the module descriptor [\#87](https://github.com/Kotlin/kotlin-jupyter/issues/87)

**Fixed bugs:**

- Exclude kernel magics from error analysis [\#57](https://github.com/Kotlin/kotlin-jupyter/issues/57)

## [build\-0.8.2.2](https://github.com/Kotlin/kotlin-jupyter/tree/build-0.8.2.2) (2020-07-16)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/build-0.8.2.1...build-0.8.2.2)


## [build\-0.8.2.1](https://github.com/Kotlin/kotlin-jupyter/tree/build-0.8.2.1) (2020-07-15)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/build-0.8.1.121...build-0.8.2.1)

**Fixed bugs:**

- Kernel references leaked into script classpath [\#27](https://github.com/Kotlin/kotlin-jupyter/issues/27)

## [build\-0.8.1.121](https://github.com/Kotlin/kotlin-jupyter/tree/build-0.8.1.121) (2020-07-15)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/build-0.8.1.120...build-0.8.1.121)

**New features:**

- Add import kotlin.math.\* to default imports [\#30](https://github.com/Kotlin/kotlin-jupyter/issues/30)

## [build\-0.8.1.120](https://github.com/Kotlin/kotlin-jupyter/tree/build-0.8.1.120) (2020-07-14)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/demo-jan-nikitin...build-0.8.1.120)

**New features:**

- Add mavenLocal resolver [\#86](https://github.com/Kotlin/kotlin-jupyter/issues/86)
- Java 10 Support [\#39](https://github.com/Kotlin/kotlin-jupyter/issues/39)
- Add full\-featured autocomplete [\#17](https://github.com/Kotlin/kotlin-jupyter/issues/17)

**Closed issues:**

- Internal error for if expression [\#47](https://github.com/Kotlin/kotlin-jupyter/issues/47)
- Syntax error but no clue [\#50](https://github.com/Kotlin/kotlin-jupyter/issues/50)
- Installation issue on MacOs Mojave [\#21](https://github.com/Kotlin/kotlin-jupyter/issues/21)
- How do I add a JAR file into the classpath? [\#49](https://github.com/Kotlin/kotlin-jupyter/issues/49)
- Error when importing a dependency [\#53](https://github.com/Kotlin/kotlin-jupyter/issues/53)
- Installing without Conda [\#51](https://github.com/Kotlin/kotlin-jupyter/issues/51)

**Merged pull requests:**

- Upgrade Lets\-Plot Kotlin API to v. 0.0.22\-SNAPSHOT [\#85](https://github.com/Kotlin/kotlin-jupyter/pull/85) ([@alshan](https://github.com/alshan))
- Optimize kernel performance [\#77](https://github.com/Kotlin/kotlin-jupyter/pull/77) ([@ileasile](https://github.com/ileasile))
- Switch to Lets\-Plot Kotlin API 0.0.18\-SNAPSHOT [\#82](https://github.com/Kotlin/kotlin-jupyter/pull/82) ([@alshan](https://github.com/alshan))
- Switch to api 0.0.17\-SNAPSHOT [\#80](https://github.com/Kotlin/kotlin-jupyter/pull/80) ([@alshan](https://github.com/alshan))
- Upgrade lets\-plot version to 1.4.2 [\#79](https://github.com/Kotlin/kotlin-jupyter/pull/79) ([@alshan](https://github.com/alshan))
- Move branch and formatVersion to runtime properties [\#67](https://github.com/Kotlin/kotlin-jupyter/pull/67) ([@ileasile](https://github.com/ileasile))
- Fix syntax error in samples [\#65](https://github.com/Kotlin/kotlin-jupyter/pull/65) ([@breandan](https://github.com/breandan))
- Adding deeplearning4j to the supported libraries [\#63](https://github.com/Kotlin/kotlin-jupyter/pull/63) ([@fbrunacci](https://github.com/fbrunacci))
- add link to smile in supported libraries [\#64](https://github.com/Kotlin/kotlin-jupyter/pull/64) ([@haifengl](https://github.com/haifengl))
- Add library of smile\-kotlin for machine learning [\#62](https://github.com/Kotlin/kotlin-jupyter/pull/62) ([@haifengl](https://github.com/haifengl))
- PSI completion with code generation [\#46](https://github.com/Kotlin/kotlin-jupyter/pull/46) ([@ileasile](https://github.com/ileasile))

**Fixed bugs:**

- Failed to load maven\-central dependency with @DependsOn [\#71](https://github.com/Kotlin/kotlin-jupyter/issues/71)
- Code inlining and jvm\-target [\#81](https://github.com/Kotlin/kotlin-jupyter/issues/81)
- readLine causing java.lang.ArrayIndexOutOfBoundsException [\#84](https://github.com/Kotlin/kotlin-jupyter/issues/84)
- Kernel keeps crashing in Win10's Linux subsystem [\#16](https://github.com/Kotlin/kotlin-jupyter/issues/16)
- Unable to resolve static functions from dependent libraries [\#24](https://github.com/Kotlin/kotlin-jupyter/issues/24)
- Kotlin stdlib has greater resolution priority than jars added via @file:DependsOn annotation [\#25](https://github.com/Kotlin/kotlin-jupyter/issues/25)
- Detect transitive dependencies for supported libraries [\#75](https://github.com/Kotlin/kotlin-jupyter/issues/75)
- Unable to use kernel because of error in parserLibraryDescriptors [\#66](https://github.com/Kotlin/kotlin-jupyter/issues/66)

## [demo\-jan\-nikitin](https://github.com/Kotlin/kotlin-jupyter/tree/demo-jan-nikitin) (2020-02-11)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/v0.7.3...demo-jan-nikitin)

**Closed issues:**

- When reading an image, the process doesn't end [\#45](https://github.com/Kotlin/kotlin-jupyter/issues/45)
- println prints to console, but should print to output [\#32](https://github.com/Kotlin/kotlin-jupyter/issues/32)
- Config editor / network based configuration [\#31](https://github.com/Kotlin/kotlin-jupyter/issues/31)

**Merged pull requests:**

- WIP: PSI\-based completion [\#41](https://github.com/Kotlin/kotlin-jupyter/pull/41) ([@ileasile](https://github.com/ileasile))
- Improved output capturing and added tests [\#35](https://github.com/Kotlin/kotlin-jupyter/pull/35) ([@ileasile](https://github.com/ileasile))
- add library fuel [\#38](https://github.com/Kotlin/kotlin-jupyter/pull/38) ([@EVGENIYGUBAREV](https://github.com/EVGENIYGUBAREV))
- Use gradle variables in kernel source [\#37](https://github.com/Kotlin/kotlin-jupyter/pull/37) ([@ileasile](https://github.com/ileasile))
- Fix file extension in language\_info [\#36](https://github.com/Kotlin/kotlin-jupyter/pull/36) ([@mpcjanssen](https://github.com/mpcjanssen))
- Add TeamCity integration [\#33](https://github.com/Kotlin/kotlin-jupyter/pull/33) ([@ileasile](https://github.com/ileasile))
- Library configuration improvements [\#34](https://github.com/Kotlin/kotlin-jupyter/pull/34) ([@nikitinas](https://github.com/nikitinas))
- Fixed windows path separator issues [\#29](https://github.com/Kotlin/kotlin-jupyter/pull/29) ([@ileasile](https://github.com/ileasile))
- Improve completion in Jupyter Lab [\#28](https://github.com/Kotlin/kotlin-jupyter/pull/28) ([@ileasile](https://github.com/ileasile))

**Fixed bugs:**

- Kernel spec produced for Windows is invalid [\#19](https://github.com/Kotlin/kotlin-jupyter/issues/19)

## [v0.7.3](https://github.com/Kotlin/kotlin-jupyter/tree/v0.7.3) (2019-12-05)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/v0.7.2...v0.7.3)


## [v0.7.2](https://github.com/Kotlin/kotlin-jupyter/tree/v0.7.2) (2019-12-04)
[View commits](https://github.com/Kotlin/kotlin-jupyter/compare/0a1a4c6f4bc545fac48724178b536320aa80c375...v0.7.2)

**Closed issues:**

- protocol issue with 5.x protocol of Jupyter [\#11](https://github.com/Kotlin/kotlin-jupyter/issues/11)
- Cannot start jupyther notebook  [\#15](https://github.com/Kotlin/kotlin-jupyter/issues/15)
- Package level functions can not be used [\#13](https://github.com/Kotlin/kotlin-jupyter/issues/13)
- resultOf is missing [\#10](https://github.com/Kotlin/kotlin-jupyter/issues/10)
- After a a println command "Ok" is printed instead of the actual string to be printed. [\#3](https://github.com/Kotlin/kotlin-jupyter/issues/3)
- The kernel crashes after running println twice [\#4](https://github.com/Kotlin/kotlin-jupyter/issues/4)
- Fix stdout / stderr so they come back correctly as side effects and not as data values [\#6](https://github.com/Kotlin/kotlin-jupyter/issues/6)

**Merged pull requests:**

- Incremented lets\-plot version [\#26](https://github.com/Kotlin/kotlin-jupyter/pull/26) ([@ileasile](https://github.com/ileasile))
- Fix jupyter\-lab issue [\#23](https://github.com/Kotlin/kotlin-jupyter/pull/23) ([@ileasile](https://github.com/ileasile))
- Completion base support [\#22](https://github.com/Kotlin/kotlin-jupyter/pull/22) ([@ileasile](https://github.com/ileasile))
- IPython is upper case I [\#8](https://github.com/Kotlin/kotlin-jupyter/pull/8) ([@Carreau](https://github.com/Carreau))
- Create installation directory if it does not exist [\#9](https://github.com/Kotlin/kotlin-jupyter/pull/9) ([@nilsga](https://github.com/nilsga))
- Fix the output for stdout, stderr, errors in general, and Unit return types [\#7](https://github.com/Kotlin/kotlin-jupyter/pull/7) ([@apatrida](https://github.com/apatrida))
- Move to latest 1.1\-M04 and kotlin\-script\-util to lower code in this kernel [\#5](https://github.com/Kotlin/kotlin-jupyter/pull/5) ([@apatrida](https://github.com/apatrida))
- add language\_info to kernel\_info\_reply [\#1](https://github.com/Kotlin/kotlin-jupyter/pull/1) ([@AndreyG](https://github.com/AndreyG))
