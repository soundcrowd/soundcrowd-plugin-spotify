/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.plugins.spotify

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import com.tiefensuche.soundcrowd.plugins.MediaItemUtils
import com.tiefensuche.soundcrowd.plugins.MediaMetadataCompatExt
import io.github.tiefensuche.spotify.api.Album
import io.github.tiefensuche.spotify.api.Category
import io.github.tiefensuche.spotify.api.Episode
import io.github.tiefensuche.spotify.api.Playlist
import io.github.tiefensuche.spotify.api.Show
import io.github.tiefensuche.spotify.api.SpotifyApi
import io.github.tiefensuche.spotify.api.Track
import xyz.gianlu.librespot.audio.decoders.AudioQuality
import xyz.gianlu.librespot.audio.decoders.VorbisOnlyAudioQuality
import xyz.gianlu.librespot.core.OAuth
import xyz.gianlu.librespot.core.Session
import xyz.gianlu.librespot.mercury.MercuryRequests.KEYMASTER_CLIENT_ID
import xyz.gianlu.librespot.metadata.EpisodeId
import xyz.gianlu.librespot.metadata.TrackId
import xyz.gianlu.librespot.player.decoders.SeekableInputStream
import java.io.File

class SpotifyApi(private val context: Context) {

    private val _api = SpotifyApi()
    private val api: SpotifyApi
        get() {
            _api.token = token()
            return _api
        }
    private var session: Session? = null
    internal val credentialsFile = File(context.filesDir.path + "/credentials.json")
    internal val oauth = OAuth(KEYMASTER_CLIENT_ID, "http://127.0.0.1:5588/login")
    private val albumTracks = HashMap<String, List<Track>>()

    private fun createSession() {
        val conf = Session.Configuration.Builder()
            .setStoreCredentials(true)
            .setStoredCredentialsFile(credentialsFile)
            .setCacheEnabled(true)
            .setCacheDir(context.cacheDir)
            .setConnectionTimeout(30 * 1000)
            .build()

        session = Session.Builder(conf).let {
            if (credentialsFile.exists()) {
                it.stored()
            } else {
                oauth.requestToken()
                it.credentials(oauth.credentials)
            }
        }.create()
    }

    private fun token(): String {
        if (session == null) {
            createSession()
        }
        session?.let {
            return "Bearer " + it.tokens().getToken("user-library-read", "user-library-modify", "user-follow-read").accessToken
        } ?: throw IllegalStateException("No session!")
    }

    fun getCategories(refresh: Boolean): List<MediaItem> {
        return parseCategories(api.getBrowseCategories(refresh))
    }

    fun getUsersSavedTracks(refresh: Boolean): List<MediaItem> {
        return parseTracks(api.getUsersSavedTracks(refresh))
    }

    fun getArtists(refresh: Boolean): List<MediaItem> {
        return parsePlaylists(api.getArtists(refresh))
    }

    fun getArtist(id: String, refresh: Boolean): List<MediaItem> {
        return parseTracks(api.getArtist(id, refresh))
    }

    fun getAlbums(refresh: Boolean): List<MediaItem> {
        return parseAlbums(api.getUsersSavedAlbums(refresh))
    }

    fun getAlbumTracks(id: String, refresh: Boolean): List<MediaItem> {
        return albumTracks[id]?.let { parseTracks(it) } ?: emptyList()
    }

    fun getUsersPlaylists(refresh: Boolean): List<MediaItem> {
        return parsePlaylists(api.getUsersPlaylists(refresh))
    }

    fun getCategoryPlaylist(path: String, refresh: Boolean): List<MediaItem> {
        if (path.contains('/'))
            return parseTracks(api.getPlaylist(path.substringAfter('/'), false))
        return parsePlaylists(api.getCategoryPlaylists(path, refresh))
    }

    fun getPlaylist(id: String, refresh: Boolean): List<MediaItem> {
        return parseTracks(api.getPlaylist(id, refresh))
    }

    fun getShows(refresh: Boolean): List<MediaItem> {
        return parseShows(api.getUsersSavedShows(refresh))
    }

    fun getEpisodes(id: String, refresh: Boolean): List<MediaItem> {
        return parseEpisodes(api.getEpisodes(id, refresh))
    }

    fun query(query: String, refresh: Boolean): List<MediaItem> {
        return parseTracks(api.query(query, refresh))
    }

    private fun parseTracks(tracks: List<Track>): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        if (tracks.isEmpty()) {
            return result
        }

        return tracks.map {
            MediaItemUtils.createMediaItem(
                it.id,
                Uri.parse(it.url),
                it.title,
                it.duration,
                it.artist,
                it.album,
                Uri.parse(it.artwork),
                rating = HeartRating(it.liked),
                isDataSource = true
            )
        }
    }

    private fun parseAlbums(albums: List<Album>): List<MediaItem> {
        return albums.map {
            albumTracks[it.id] = it.tracks
            MediaItemUtils.createBrowsableItem(
                it.id,
                it.title,
                MediaMetadataCompatExt.MediaType.STREAM,
                it.artist,
                artworkUri = Uri.parse(it.artwork),
            )
        }
    }

    private fun parsePlaylists(playlists: List<Playlist>): List<MediaItem> {
        return playlists.map {
            MediaItemUtils.createBrowsableItem(
                it.id,
                it.title,
                MediaMetadataCompatExt.MediaType.STREAM,
                artworkUri = Uri.parse(it.artwork),
            )
        }
    }

    private fun parseCategories(categories: List<Category>): List<MediaItem> {
        return categories.map {
            MediaItemUtils.createBrowsableItem(
                it.id,
                it.name,
                MediaMetadataCompatExt.MediaType.COLLECTION,
                artworkUri = Uri.parse(it.artwork),
            )
        }
    }

    private fun parseShows(shows: List<Show>): List<MediaItem> {
        return shows.map {
            MediaItemUtils.createBrowsableItem(
                it.id,
                it.title,
                MediaMetadataCompatExt.MediaType.STREAM,
                artworkUri = Uri.parse(it.artwork),
                description = it.description
            )
        }
    }

    private fun parseEpisodes(episodes: List<Episode>): List<MediaItem> {
        return episodes.map {
            MediaItemUtils.createMediaItem(
                it.id,
                Uri.parse(it.uri),
                it.title,
                it.duration,
                artworkUri = Uri.parse(it.artwork),
                isDataSource = true
            )
        }
    }

    fun saveTrack(uri: String): Boolean {
        return api.saveTrack(uri)
    }

    fun streamUri(uri: String): MediaDataSource {
        if (session == null) {
            createSession()
        }
        val playableUri = when {
            uri.startsWith("spotify:track:") -> TrackId.fromUri(uri)
            uri.startsWith("spotify:episode:") -> EpisodeId.fromUri(uri)
            else -> throw IllegalArgumentException("Unsupported uri")
        }
        val stream = session?.contentFeeder()?.load(
            playableUri,
            VorbisOnlyAudioQuality(AudioQuality.HIGH),
            true,
            null) ?: throw IllegalStateException("No session!")

        val audioIn = stream.`in`.stream()
        return SpotifyMediaDataSource(audioIn)
    }

    class SpotifyMediaDataSource(private val stream: SeekableInputStream) : MediaDataSource() {

        private val pos = stream.position()

        override fun close() {
            stream.close()
        }

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            stream.seek(position.toInt() + pos)
            return stream.read(buffer, offset, size)
        }

        override fun getSize(): Long {
            return stream.size().toLong() - pos
        }
    }
}