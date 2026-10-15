package com.hardtekpt.crux.live

import com.hardtekpt.crux.data.BoardLink
import com.hardtekpt.crux.data.PlaceRepository
import com.hardtekpt.crux.data.PlaylistItem
import com.hardtekpt.crux.data.PlaylistRepository
import com.hardtekpt.crux.data.Session
import com.hardtekpt.crux.data.SessionRepository
import com.hardtekpt.crux.data.model.Section
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The playlist side of the live notification: what's up, Prev and Next, and Light, which sends
 * the climb that's up to a paired board (the session's facility's first). Nothing lights by itself.
 */
@Singleton
class LivePlaylists @Inject constructor(
    private val sessions: SessionRepository,
    private val playlists: PlaylistRepository,
    private val places: PlaceRepository,
    private val link: BoardLink,
) {
    /** The card's playlist line for [session], or null when it isn't climbing one. */
    suspend fun card(session: Session): LivePlaylist? {
        val (item, size) = up(session) ?: return null
        return LivePlaylist(item.name, session.playlistIndex + 1, size, board(session, item) != null)
    }

    suspend fun move(by: Int) {
        val session = sessions.observeRunning().first() ?: return
        val (_, size) = up(session) ?: return
        sessions.setPlaylist(session.id, session.playlistId, (session.playlistIndex + by).coerceIn(0, size - 1))
    }

    suspend fun light() {
        val session = sessions.observeRunning().first() ?: return
        val (item, _) = up(session) ?: return
        val section = board(session, item) ?: return
        link.light(section, item.dataset?.holdsBoard ?: return, item.climb?.holds ?: return, item.name)
    }

    private suspend fun up(session: Session): Pair<PlaylistItem, Int>? {
        val id = session.playlistId ?: return null
        val list = playlists.observePlaylists().first().firstOrNull { it.id == id } ?: return null
        return list.items.getOrNull(session.playlistIndex)?.let { it to list.items.size }
    }

    /** The paired board that can light [item]: the session's facility when it can, else the first that can. */
    private suspend fun board(session: Session, item: PlaylistItem): Section? {
        val board = item.dataset?.holdsBoard ?: return null
        val holds = item.climb?.holds ?: return null
        val paired = places.observePlaces().first().flatMap { it.place.sections }.filter { it.lights != null && link.messages(it, board, holds) != null }
        return paired.firstOrNull { it.id == session.sectionId } ?: paired.firstOrNull()
    }
}
