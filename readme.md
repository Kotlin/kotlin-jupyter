# Kotlin kernel for iPython/Jupyter

Basic kotlin REPL kernel for jupyter (http://jupyter.org).

Plotting, autocompletion and other advanced features are not yet supported.

Alpha version. Tested only with jupyter 4.1.1 on OS X so far.

## Installation

Run `./gradlew install`

Use option `-PinstallPath=` to specify installation path. *(Note that jupyter looks for kernel specs files only in predefined places.)*

Default installation path is `~/.ipython/kernels/kotlin/`.

## Usage

`jupyter-console --kernel=kotlin`

or

`jupyter-notebook`

and the create a new notebook with `kotlin` kernel.

## Additional libraries

Additional jars could be added to the REPL using `-cp=` parameter in `argv` list in the installed `kernel.json` file.
Standard classpath format is used. *(Please make sure to use only absolute paths in the `kernel.json` file.)*

## Debugging

- run kernel jar passing some connection config file as a parameter, e.g. `testData/config.json`
    - additional jars for the REPL could be passed using `-cp=` parameter
- run `jupyter-console` passing the full path to the same config file as an argument to the `--existing` command line parameter
