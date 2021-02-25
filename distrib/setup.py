import glob
import shutil
from os import path
from pathlib import Path

from setuptools import setup, find_packages

DATA_FILES = [
    ('share/jupyter/kernels/kotlin', glob.glob('kernel/*'))
]

PACKAGE_DATA = {
    'run_kotlin_kernel': ['jars/*.jar', 'config/*.json', 'libraries/*']
}

LIBRARIES_FOLDER_NAME = '.jupyter_kotlin'
LIBRARIES_CACHE_FOLDER_NAME = 'cache'
VERSION_FILE_NAME = 'VERSION'
README_FILE_NAME = 'README.md'
REPO_URL_FILE_NAME = 'REPO_URL'


def main():
    home_dir = str(Path.home())
    libraries_cache_path = path.join(home_dir, LIBRARIES_FOLDER_NAME, LIBRARIES_CACHE_FOLDER_NAME)
    shutil.rmtree(libraries_cache_path, ignore_errors=True)

    abspath = path.abspath(__file__)
    current_dir = path.dirname(abspath)
    version_file = path.join(current_dir, VERSION_FILE_NAME)
    readme_file = path.join(current_dir, README_FILE_NAME)
    repo_url_file = path.join(current_dir, REPO_URL_FILE_NAME)

    with open(version_file, 'r') as f:
        version = f.read().strip()

    with open(readme_file, 'r') as f:
        long_description = f.read()

    with open(repo_url_file, 'r') as f:
        repo_url = f.read()

    classifiers = [
        "Framework :: Jupyter",
        "Development Status :: 4 - Beta",
        "Programming Language :: Kotlin",
        "License :: OSI Approved :: Apache Software License",
        "Intended Audience :: Science/Research",
        "Operating System :: OS Independent",
        "Topic :: Software Development :: Interpreters",
    ]

    setup(name="kotlin-jupyter-kernel",
          author="JetBrains",
          version=version,
          url=repo_url,
          license="Apache 2.0",
          description="Kotlin kernel for Jupyter notebooks",
          long_description=long_description,
          long_description_content_type="text/markdown",
          classifiers=classifiers,
          packages=find_packages(),
          package_data=PACKAGE_DATA,
          data_files=DATA_FILES
          )


if __name__ == "__main__":
    main()
