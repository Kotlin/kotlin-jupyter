from kotlin_kernel.install_user import install_user

import sys

if __name__ == "__main__":
    if len(sys.argv) == 2 and sys.argv[1] == "fix-kernelspec-location":
        install_user()
