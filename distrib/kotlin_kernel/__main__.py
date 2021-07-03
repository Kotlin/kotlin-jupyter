from kotlin_kernel.install_user import install_user
from kotlin_kernel.add_kernel import add_kernel

import sys

if __name__ == "__main__":
    if len(sys.argv) == 2 and sys.argv[1] == "fix-kernelspec-location":
        install_user()
    elif len(sys.argv) >= 2 and sys.argv[1] == "add-kernel":
        add_kernel()
    else:
        if len(sys.argv) < 2:
            print("Must specify a command", file=sys.stderr)
        else:
            print("Unknown command " + sys.argv[1] + ", known commands are fix-kernelspec-location and add-kernel.",
                  file=sys.stderr)
        exit(1)
