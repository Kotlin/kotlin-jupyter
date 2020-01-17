package org.jetbrains.kotlin.jupyter.repl.reflect

import org.jetbrains.kotlin.cli.common.repl.AggregatedReplStageState

val AggregatedReplStageState<*, *>.lines
    get() = history
            .mapNotNull { (it.item.second as Pair<*, *>).second }
            .asReversed()