import platform
import shutil
import site
import sys
from os import path, environ


def install_user():
    data_relative_path = 'share/jupyter/kernels/kotlin'
    user_location = path.join(site.getuserbase(), data_relative_path)
    sys_location = path.join(sys.prefix, data_relative_path)
    src_paths = [user_location, sys_location]

    platform_name = platform.system()

    if platform_name == 'Linux':
        user_jupyter_path = '~/.local/share/jupyter'
    elif platform_name == 'Darwin':
        user_jupyter_path = '~/Library/Jupyter'
    elif platform_name == 'Windows':
        user_jupyter_path = path.join(environ['APPDATA'], 'jupyter')
    else:
        raise OSError("Unknown platform: " + platform_name)

    dst = path.join(user_jupyter_path, 'kernels/kotlin')
    for src in src_paths:
        if not path.exists(src):
            continue
        shutil.copytree(src, dst, dirs_exist_ok=True)
