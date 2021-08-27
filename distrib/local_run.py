import sys

import kotlin_kernel.__main__
import run_kotlin_kernel.__main__


def main():
    module_name = sys.argv[1]
    args = sys.argv[1:]
    if module_name == "kotlin_kernel":
        kotlin_kernel.__main__.main(args)
    elif module_name == "run_kotlin_kernel":
        run_kotlin_kernel.__main__.main(args)


if __name__ == "__main__":
    main()
