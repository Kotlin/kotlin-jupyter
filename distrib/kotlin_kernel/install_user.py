import os.path
import platform
import shutil
import site
import sys
from os import path, environ


def get_user_jupyter_path() -> str:
    platform_name = platform.system()

    if platform_name == 'Linux':
        jupyter_path = '~/.local/share/jupyter'
    elif platform_name == 'Darwin':
        jupyter_path = '~/Library/Jupyter'
    elif platform_name == 'Windows':
        jupyter_path = path.join(environ['APPDATA'], 'jupyter')
    else:
        raise OSError("Unknown platform: " + platform_name)

    return os.path.abspath(os.path.expanduser(jupyter_path))


def install_base_kernel(kernel_name: str):
    data_relative_path = 'share/jupyter/kernels/kotlin'
    user_location = path.join(site.getuserbase(), data_relative_path)
    sys_location = path.join(sys.prefix, data_relative_path)
    src_paths = [user_location, sys_location]

    user_jupyter_path = get_user_jupyter_path()

    dst = path.join(user_jupyter_path, 'kernels/' + kernel_name)
    for src in src_paths:
        if not path.exists(src):
            continue
        shutil.copytree(src, dst, dirs_exist_ok=True)


def install_user():
    install_base_kernel('kotlin')
