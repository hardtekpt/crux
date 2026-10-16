package com.hardtekpt.crux.ui.session

import com.hardtekpt.crux.data.Session
import com.hardtekpt.crux.data.SessionItem

/**
 * A session as blocks: each exercise is one, and so is a stretch of climbing (with or without a
 * playlist). The rail, the list and the dock all work on blocks, so a training session, a climbing
 * day, a mix of both and a session with no plan read the same way.
 */
internal sealed interface SessionBlock {
    val key: Long

    data class Exercise(val item: SessionItem) : SessionBlock {
        override val key: Long get() = item.id
    }

    data object Climbing : SessionBlock {
        override val key: Long get() = CLIMBING_KEY
    }
}

/** A key no session item has, for the climbing block. */
internal const val CLIMBING_KEY = -1L

/**
 * The blocks in the order they happened: the exercises in the plan's order, with climbing where
 * it started, before the first exercise that had no set in by then. A playlist with no climbs yet
 * goes before the first exercise not started; no climbing at all, no climbing block.
 */
internal fun Session.blocks(hasPlaylist: Boolean): List<SessionBlock> {
    val exercises = items.map { SessionBlock.Exercise(it) }
    if (climbs.isEmpty() && !hasPlaylist) return exercises
    val startedClimbing = climbs.minOfOrNull { it.createdAtMillis }?.takeIf { it > 0 } ?: Long.MAX_VALUE
    val at = items.indexOfFirst { item -> item.sets.none { it.completedAtMillis in 1 until startedClimbing } }
    return exercises.toMutableList<SessionBlock>().apply { add(if (at < 0) size else at, SessionBlock.Climbing) }
}

/**
 * Climbing is what's happening now: a playlist is up, there are only climbs, or the newest thing
 * logged is a climb rather than a set.
 */
internal fun Session.climbingNow(hasPlaylist: Boolean): Boolean {
    if (hasPlaylist) return true
    val lastClimb = climbs.maxOfOrNull { it.createdAtMillis } ?: return false
    if (items.isEmpty()) return true
    val lastSet = items.flatMap { it.sets }.maxOfOrNull { it.completedAtMillis } ?: 0L
    return lastClimb >= lastSet
}

/** Nothing in the session yet: no exercise, no climb, no playlist. It opens on what to do first. */
internal fun Session.isBlank(hasPlaylist: Boolean): Boolean = items.isEmpty() && climbs.isEmpty() && !hasPlaylist
