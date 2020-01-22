import glob
from os import path
from setuptools import setup, find_packages

abspath = path.abspath(__file__)
current_dir = path.dirname(abspath)
version_file = path.join(current_dir, 'VERSION')

with open(version_file, 'r') as f:
    version = f.read().strip()

DATA_FILES = [
    ('share/jupyter/kernels/kotlin', glob.glob('kernel/*.json'))
]


PACKAGE_DATA = {
    'run_kotlin_kernel': ['jars/*.jar', 'config/*.json', 'libraries/*']
}

if __name__ == "__main__":
    setup(name="kotlin-jupyter-kernel",
          author="JetBrains",
          version=version,
          url="https://github.com/Kotlin/kotlin-jupyter",
          license="Apache 2.0",
          description="Kotlin kernel for Jupyter notebooks",
          packages=find_packages(),
          package_data=PACKAGE_DATA,
          data_files=DATA_FILES
          )
