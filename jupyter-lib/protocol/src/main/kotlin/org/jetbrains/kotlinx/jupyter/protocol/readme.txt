This module contains core protocol logic and data structures for Jupyter kernel communication.

The entire `org.jetbrains.kotlinx.jupyter.protocol` package and parts of `org.jetbrains.kotlinx.jupyter.messaging`
were moved from `kotlin-jupyter-kernel.shared-compiler` to this protocol module.

The goal is to create a lightweight module with minimal dependencies,
so it can be imported without dragging in the Kotlin compiler or other kernel-specific logic.
