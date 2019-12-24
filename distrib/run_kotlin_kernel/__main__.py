from sys import argv
from run_kotlin_kernel.run_kernel import run_kernel

if __name__ == "__main__":
    run_kernel(*(argv[1:]))
