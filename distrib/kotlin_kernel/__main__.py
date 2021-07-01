from kotlin_kernel.install_user import install_user
from kotlin_kernel.add_kernel import add_kernel

import sys

if __name__ == "__main__":
    if len(sys.argv) == 2 and sys.argv[1] == "fix-kernelspec-location":
        install_user()
    elif len(sys.argv) >= 2 and sys.argv[1] == "add-kernel":
        add_kernel()
