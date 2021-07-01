import argparse
import json
import os.path
import platform
import subprocess
import sys
import warnings

from kotlin_kernel.install_user import get_user_jupyter_path
from kotlin_kernel.install_user import install_base_kernel

java_opts = "JAVA_OPTS"
kernel_java_opts = "KOTLIN_JUPYTER_JAVA_OPTS"
kernel_extra_java_opts = "KOTLIN_JUPYTER_JAVA_OPTS_EXTRA"
kernel_added_java_opts = "KOTLIN_JUPYTER_KERNEL_EXTRA_JVM_OPTS"

java_home = "JAVA_OPTS"
kernel_java_home = "KOTLIN_JUPYTER_JAVA_HOME"


def add_kernel():
    parser = argparse.ArgumentParser(description="Add a kernel with specified JDK, JVM args, and environment")
    parser.add_argument("name", nargs='?',
                        help="The kernel's sub-name.  The kernel will be named \"Kotlin ($name)\".  "
                             "Will be autodetected if JDK is specified, otherwise required.  "
                             "Must be file system compatible.")
    parser.add_argument("--jdk",
                        help="The home directory of the JDK to use")
    parser.add_argument("--jvm_arg", action='append', default=[],
                        help="Add a JVM argument")
    parser.add_argument("--env", action='append', nargs=2, default=[],
                        help="Add an environment variable")
    parser.add_argument("--add_jvm_args", action="store_true", default=False,
                        help="If present, adds JVM args instead of setting them.")

    args = parser.parse_args(sys.argv[2:])

    jdk = args.jdk
    name = args.name
    env = {e[0]: e[1] for e in args.env}

    for arg in [java_home, kernel_java_home, java_opts, kernel_extra_java_opts, kernel_added_java_opts]:
        if arg in env:
            warnings.warn(
                "Specified environment variable " + arg + ", will be ignored.  "
                                                          "Use the corresponding arguments instead.")
            del env[arg]

    if args.add_jvm_args:
        env[kernel_added_java_opts] = " ".join(args.jvm_arg)
    else:
        env[kernel_java_opts] = " ".join(args.jvm_arg)

    if jdk is not None:
        env[kernel_java_home] = jdk
        if platform.system() == 'Windows':
            java = os.path.join(jdk, "bin/java.exe")
        else:
            java = os.path.join(jdk, "bin/java")

        if not os.path.exists(java):
            print("JDK " + jdk + " has no bin/" + os.path.basename(java), file=sys.stderr)
            exit(1)

        if name is None:
            version_spec = subprocess.check_output([java, "--version"], text=True).splitlines()[0].split(" ")
            dist = version_spec[0]
            version = version_spec[1]
            name = "JDK " + dist + " " + version

    if name is None:
        print("name is required when JDK not specified.", file=sys.stderr)
        exit(1)

    kernel_name = "kotlin_" + name.replace(" ", "_")
    kernel_location = os.path.join(get_user_jupyter_path(), "kernels", kernel_name)
    if os.path.exists(kernel_location):
        print("There is already a kernel with name " + kernel_name + ", specify a different name", file=sys.stderr)
        exit(1)

    install_base_kernel(kernel_name)

    with open(os.path.join(kernel_location, "kernel.json")) as kernel_file:
        kernelspec = json.load(kernel_file)

    kernelspec["display_name"] = "Kotlin (" + name + ")"

    if "env" in kernelspec:
        kernelspec["env"].update(env)
    else:
        kernelspec["env"] = env

    with open(os.path.join(kernel_location, "kernel.json"), "w") as kernel_file:
        json.dump(kernelspec, kernel_file, indent=4)