package com.example.privatebrowser

/** Candidate selection only. Not wired to destructive tab closure. */
internal data class RetainedTab(
    val id: Long,
    val lastUsed: Long,
    val active: Boolean = false,
    val pinned: Boolean = false,
    val playing: Boolean = false,
    val dirtyForm: Boolean = false,
)

internal fun tabsToRetire(tabs: List<RetainedTab>, maximum: Int): List<Long> = tabs
    .filterNot { it.active || it.pinned || it.playing || it.dirtyForm }
    .sortedBy { it.lastUsed }
    .take((tabs.size - maximum.coerceAtLeast(1)).coerceAtLeast(0))
    .map { it.id }
