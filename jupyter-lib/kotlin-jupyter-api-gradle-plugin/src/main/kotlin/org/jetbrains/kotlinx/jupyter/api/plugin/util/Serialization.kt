package org.jetbrains.kotlinx.jupyter.api.plugin.util

import com.google.gson.GsonBuilder

internal val gson =
    GsonBuilder()
        .registerTypeAdapter(LibrariesScanResult::class.java, LibrariesScanResultSerializer)
        .create()
