import argparse
import json
import os.path
import platform
import shutil
import subprocess
import sys

from kotlin_kernel import env_names
from kotlin_kernel.install_user import get_user_jupyter_path
from kotlin_kernel.install_user import install_base_kernel


def add_kernel(sys_args):
    parser = argparse.ArgumentParser(
        prog="add-kernel",
        description="Add a kernel with specified JDK, JVM args, and environment",
        fromfile_prefix_chars='@')
    parser.add_argument("--name",
                        help="The kernel's sub-name.  The kernel will be named \"Kotlin ($name)\".  "
                             "Will be autodetected if JDK is specified, otherwise required.  "
                             "Must be file system compatible.")
    parser.add_argument("--jdk",
                        help="The home directory of the JDK to use")
    parser.add_argument("--jvm-arg", action='append', default=[],
                        help="Add a JVM argument")
    parser.add_argument("--env", action='append', nargs=2, default=[],
                        help="Add an environment variable")
    parser.add_argument("--set-jvm-args", action="store_true", default=False,
                        help="Set JVM args instead of adding them.")
    parser.add_argument("--force", action="store_true", default=False,
                        help="Overwrite an existing kernel with the same name.")

    if len(sys_args) == 2:
        parser.print_usage()
        exit(0)

    args = parser.parse_args(sys_args[2:])

    jdk = args.jdk
    if jdk is not None:
        jdk = os.path.abspath(os.path.expanduser(jdk))

    name = args.name
    env = {e[0]: e[1] for e in args.env}

    for arg in [env_names.JAVA_HOME, env_names.KERNEL_JAVA_HOME, env_names.JAVA_OPTS,
                env_names.KERNEL_EXTRA_JAVA_OPTS, env_names.KERNEL_INTERNAL_ADDED_JAVA_OPTS]:
        if arg in env:
            print(
                "Specified environment variable " + arg + ", will be ignored.  "
                                                          "Use the corresponding arguments instead.", file=sys.stderr)
            del env[arg]

    if args.set_jvm_args:
        env[env_names.KERNEL_JAVA_OPTS] = " ".join(args.jvm_arg)
    else:
        env[env_names.KERNEL_INTERNAL_ADDED_JAVA_OPTS] = " ".join(args.jvm_arg)

    if jdk is not None:
        env[env_names.KERNEL_JAVA_HOME] = jdk
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

    print("Installing kernel to", kernel_location)

    if os.path.exists(kernel_location):
        if args.force:
            print("Overwriting existing kernel at " + kernel_location, file=sys.stderr)
            shutil.rmtree(kernel_location)
        else:
            print("There is already a kernel with name " + kernel_name + ", specify a different name "
                                                                         "or use --force to overwrite it",
                  file=sys.stderr)
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
