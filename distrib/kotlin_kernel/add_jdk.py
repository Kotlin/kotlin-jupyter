import json
import os.path
import platform
import subprocess
import sys
from typing import Optional

from kotlin_kernel.install_user import get_user_jupyter_path

from kotlin_kernel.install_user import install_base_kernel


def add_jdk(jdk: str, name: Optional[str]):
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
        name = version + " " + dist

    kernel_name = "kotlin_" + name.replace(" ", "_")
    kernel_location = os.path.join(get_user_jupyter_path(), "kernels", kernel_name)
    if os.path.exists(kernel_location):
        print("There is already a kernel with name " + kernel_name + ", use a different name", file=sys.stderr)
        exit(1)

    install_base_kernel(kernel_name)

    with open(os.path.join(kernel_location, "kernel.json")) as kernel_file:
        kernelspec = json.load(kernel_file)

    kernelspec["display_name"] = "Kotlin (JDK " + name + ")"
    if "env" in kernelspec:
        kernelspec["env"]["KOTLIN_JUPYTER_JAVA_HOME"] = jdk
    else:
        kernelspec["env"] = {"KOTLIN_JUPYTER_JAVA_HOME": jdk}

    with open(os.path.join(kernel_location, "kernel.json"), "w") as kernel_file:
        json.dump(kernelspec, kernel_file, indent=4)
