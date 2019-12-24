import json
import os
import subprocess
import sys
from typing import List


def run_kernel(*args) -> None:
    try:
        run_kernel_impl(*args)
    except KeyboardInterrupt:
        print("Kernel interrupted")
        try:
            sys.exit(130)
        except SystemExit:
            # noinspection PyProtectedMember
            os._exit(130)


def run_kernel_impl(connection_file: str, jar_args_file: str = None, executables_dir: str = None) -> None:
    abspath = os.path.abspath(__file__)
    current_dir = os.path.dirname(abspath)

    if jar_args_file is None:
        jar_args_file = os.path.join(current_dir, "config", "jar_args.json")
    if executables_dir is None:
        executables_dir = current_dir

    jars_dir = os.path.join(executables_dir, "jars")

    with open(jar_args_file, "r") as fd:
        jar_args_json = json.load(fd)

        debug: str = jar_args_json["debuggerConfig"]
        cp: List[str] = jar_args_json["classPath"]
        main_jar: str = jar_args_json["mainJar"]

        debug_list = [] if debug is None or debug == "" else [debug]
        class_path_arg = os.pathsep.join([os.path.join(jars_dir, jar_name) for jar_name in cp])
        main_jar_path = os.path.join(jars_dir, main_jar)

        subprocess.call(["java", "-jar"] + debug_list +
                        [main_jar_path,
                         "-classpath=" + class_path_arg,
                         connection_file,
                         "-home=" + executables_dir])


if __name__ == "__main__":
    run_kernel(*(sys.argv[1:]))
