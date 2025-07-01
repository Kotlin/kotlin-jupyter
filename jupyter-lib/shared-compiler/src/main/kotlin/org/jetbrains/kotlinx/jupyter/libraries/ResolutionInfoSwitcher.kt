package org.jetbrains.kotlinx.jupyter.libraries

import org.jetbrains.kotlinx.jupyter.api.libraries.LibraryResolutionInfo

class ResolutionInfoSwitcher<T>(
    private val infoProvider: ResolutionInfoProvider,
    initialSwitchVal: T,
    private val switcher: (T) -> LibraryResolutionInfo,
) {
    private val defaultInfoCache = hashMapOf<T, LibraryResolutionInfo>()

    var switch: T = initialSwitchVal
        set(value) {
            infoProvider.fallback = defaultInfoCache.getOrPut(value) { switcher(value) }
            field = value
        }

    companion object {
        // Used in Kotlin Jupyter plugin for IDEA
        @Suppress("unused")
        fun noop(provider: ResolutionInfoProvider): ResolutionInfoSwitcher<DefaultInfoSwitch> =
            ResolutionInfoSwitcher(provider, DefaultInfoSwitch.GIT_REFERENCE) {
                provider.fallback
            }
    }
}
