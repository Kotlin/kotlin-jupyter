from typing import Dict, List
from typing import Callable

from kotlin_kernel.add_kernel import add_kernel
from kotlin_kernel.detect_jars_location import detect_jars_location
from kotlin_kernel.install_user import install_user

import sys

commands: Dict[str, Callable[[List[str]], None]] = {
    "add-kernel": lambda x: add_kernel(x),
    "detect-jars-location": lambda x: detect_jars_location(),
    "fix-kernelspec-location": lambda x: install_user(),
}


def show_help(args):
    if len(args) < 2:
        print("Must specify a command", file=sys.stderr)
    else:
        commands_str = ", ".join(commands.keys())
        print("Unknown command " + args[1] + ", known commands: " + commands_str + ".",
              file=sys.stderr)
    exit(1)


def main(args):
    if len(args) >= 2:
        command = args[1]
        if command in commands:
            commands[command](args)
        else:
            show_help(args)
    else:
        show_help(args)


if __name__ == "__main__":
    main(sys.argv)
