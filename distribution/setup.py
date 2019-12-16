import glob
from setuptools import setup, find_packages

version = '%%VERSION%%'

DATA_FILES = [
    ('share/jupyter/kernels/kotlin', glob.glob('kernel/*.json'))
]


PACKAGE_DATA = {
    'run_kotlin_kernel': ['jars/*.jar', 'config/*.json']
}

if __name__ == "__main__":
    setup(name="kotlin_jupyter_kernel",
          author="JetBrains",
          version=version,
          url="https://github.com/ligee/kotlin-jupyter",
          license="Apache 2.0",
          packages=find_packages(),
          package_data=PACKAGE_DATA,
          data_files=DATA_FILES
          )
