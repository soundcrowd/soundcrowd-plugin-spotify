/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.plugins.spotify

import android.content.Context
import android.media.MediaDataSource
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.extensions.WebRequests
import com.tiefensuche.spotify.api.Playlist
import com.tiefensuche.spotify.api.SpotifyApi
import com.tiefensuche.spotify.api.Track
import xyz.gianlu.librespot.audio.decoders.AudioQuality
import xyz.gianlu.librespot.audio.decoders.VorbisOnlyAudioQuality
import xyz.gianlu.librespot.core.OAuth
import xyz.gianlu.librespot.core.Session
import xyz.gianlu.librespot.mercury.MercuryRequests.KEYMASTER_CLIENT_ID
import xyz.gianlu.librespot.metadata.TrackId
import xyz.gianlu.librespot.player.decoders.SeekableInputStream
import java.io.File

class SpotifyApi(private val appContext: Context, private val context: Context) {

    private val _api = SpotifyApi()
    private val api: SpotifyApi
        get() {
            _api.token = token()
            return _api
        }
    private var session: Session? = null
    internal val credentialsFile = File(appContext.filesDir.path + "/credentials.json")
    internal val oauth = OAuth(KEYMASTER_CLIENT_ID, "soundcrowd://127.0.0.1/login")

    private fun createSession() {
        val conf = Session.Configuration.Builder()
            .setStoreCredentials(true)
            .setStoredCredentialsFile(credentialsFile)
            .setCacheEnabled(true)
            .setCacheDir(appContext.cacheDir)
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

    fun getReleaseRadar(refresh: Boolean): List<MediaMetadataCompat> {
        return parseTracks(api.getReleaseRadar(refresh))
    }

    fun getUsersSavedTracks(refresh: Boolean): List<MediaMetadataCompat> {
        return parseTracks(api.getUsersSavedTracks(refresh))
    }

    fun getArtists(refresh: Boolean): List<MediaMetadataCompat> {
        return parsePlaylists(api.getArtists(refresh))
    }

    fun getArtist(id: String, refresh: Boolean): List<MediaMetadataCompat> {
        return parseTracks(api.getArtist(id, refresh))
    }

    fun getUsersPlaylists(refresh: Boolean): List<MediaMetadataCompat> {
        return parsePlaylists(api.getUsersPlaylists(refresh))
    }

    fun getPlaylist(id: String, refresh: Boolean): List<MediaMetadataCompat> {
        return parseTracks(api.getPlaylist(id, refresh))
    }

    fun query(query: String, refresh: Boolean): List<MediaMetadataCompat> {
        return parseTracks(api.query(query, refresh))
    }

    private fun parseTracks(tracks: List<Track>): List<MediaMetadataCompat> {
        val result = mutableListOf<MediaMetadataCompat>()
        if (tracks.isEmpty()) {
            return result
        }

        return tracks.map {
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, it.id)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, it.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, it.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, it.album)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, it.artwork)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, it.url)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it.duration)
                .putRating(MediaMetadataCompatExt.METADATA_KEY_FAVORITE, RatingCompat.newHeartRating(it.liked))
                .putString(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.MEDIA.name)
                .build()
        }
    }

    private fun parsePlaylists(playlists: List<Playlist>): List<MediaMetadataCompat> {
        return playlists.map {
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, it.uuid)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, it.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, it.artwork)
                .putString(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.STREAM.name)
                .build()
        }
    }

    fun saveTrack(uri: String): Boolean {
        return api.saveTrack(uri)
    }

    fun streamUri(uri: String): MediaDataSource {
        if (session == null) {
            createSession()
        }
        val stream = session!!.contentFeeder().load(
            TrackId.fromUri(uri),
            VorbisOnlyAudioQuality(AudioQuality.HIGH),
            true,
            null)

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