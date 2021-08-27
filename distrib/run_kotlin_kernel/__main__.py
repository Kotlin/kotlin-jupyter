import sys

from run_kotlin_kernel.run_kernel import run_kernel


def main(args):
    run_kernel(*(args[1:]))


if __name__ == "__main__":
    main(sys.argv)
