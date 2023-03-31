import json
import os
import shlex
import subprocess
import sys
from typing import List, AnyStr

from kotlin_kernel import env_names
from kotlin_kernel import port_generator


def run_kernel(*args) -> None:
    try:
        run_kernel_impl(*args)
    except KeyboardInterrupt:
        print('Kernel interrupted')
        try:
            sys.exit(130)
        except SystemExit:
            # noinspection PyProtectedMember,PyUnresolvedReferences
            os._exit(130)


def module_install_path() -> str:
    abspath: AnyStr = os.path.abspath(__file__)
    current_dir: AnyStr = os.path.dirname(abspath)
    return str(current_dir)


def run_kernel_impl(connection_file: str, jar_args_file: str = None, executables_dir: str = None) -> None:
    abspath = os.path.abspath(__file__)
    current_dir = os.path.dirname(abspath)

    if jar_args_file is None:
        jar_args_file = os.path.join(current_dir, 'config', 'jar_args.json')
    if executables_dir is None:
        executables_dir = current_dir

    jars_dir = os.path.join(executables_dir, 'jars')

    with open(jar_args_file, 'r') as fd:
        jar_args_json = json.load(fd)

        debug_port = jar_args_json['debuggerPort']
        cp: List[str] = jar_args_json['classPath']
        main_jar: str = jar_args_json['mainJar']

        debug_list = []
        if debug_port is not None and debug_port != '':
            if debug_port == 'generate':
                debug_port = port_generator.get_port_not_in_use(port_generator.DEFAULT_DEBUG_PORT)
            debug_list.append('-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address={}'.format(debug_port))
        else:
            debug_port = None
        class_path_arg = os.pathsep.join([os.path.join(jars_dir, jar_name) for jar_name in cp])
        main_jar_path = os.path.join(jars_dir, main_jar)

        java_exec = os.getenv(env_names.KERNEL_JAVA_EXECUTABLE)
        java_home = os.getenv(env_names.KERNEL_JAVA_HOME) or os.getenv(env_names.JAVA_HOME)

        if java_exec is not None:
            java = java_exec
        elif java_home is not None:
            java = os.path.join(java_home, "bin", "java")
        else:
            java = "java"

        jvm_arg_str = os.getenv(env_names.KERNEL_JAVA_OPTS) or os.getenv(env_names.JAVA_OPTS) or ""
        extra_args = os.getenv(env_names.KERNEL_EXTRA_JAVA_OPTS)
        if extra_args is not None:
            jvm_arg_str += " " + extra_args

        kernel_args = os.getenv(env_names.KERNEL_INTERNAL_ADDED_JAVA_OPTS)
        if kernel_args is not None:
            jvm_arg_str += " " + kernel_args

        jvm_args = shlex.split(jvm_arg_str)

        jar_args = [
            main_jar_path,
            '-classpath=' + class_path_arg,
            connection_file,
            '-home=' + executables_dir
        ]
        if debug_port is not None:
            jar_args.append('-debugPort=' + str(debug_port))
        subprocess.call([java] + jvm_args + ['-jar'] + debug_list + jar_args)


if __name__ == '__main__':
    run_kernel(*(sys.argv[1:]))
