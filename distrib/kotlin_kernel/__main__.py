from kotlin_kernel.install_user import install_user
from kotlin_kernel.add_jdk import add_jdk

import sys

if __name__ == "__main__":
    if len(sys.argv) == 2 and sys.argv[1] == "fix-kernelspec-location":
        install_user()
    elif len(sys.argv) >= 2 and sys.argv[1] == "add-jdk":
        if len(sys.argv) < 3:
            print("add-jdk requires one argument: the location of the JDK to add", file=sys.stderr)
            exit(1)
        if len(sys.argv) >= 4:
            add_jdk(sys.argv[2], sys.argv[3])
        else:
            add_jdk(sys.argv[2], None)
