/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.plugins.spotify

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaDataSource
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.RatingCompat
import androidx.preference.PreferenceManager
import com.spotify.connectstate.Connect
import com.tiefensuche.soundcrowd.extensions.MediaMetadataCompatExt
import com.tiefensuche.soundcrowd.extensions.WebRequests
import org.json.JSONArray
import org.json.JSONObject
import xyz.gianlu.librespot.audio.HaltListener
import xyz.gianlu.librespot.audio.decoders.AudioQuality
import xyz.gianlu.librespot.audio.decoders.VorbisOnlyAudioQuality
import xyz.gianlu.librespot.core.Session
import xyz.gianlu.librespot.metadata.TrackId
import xyz.gianlu.librespot.player.decoders.SeekableInputStream
import java.util.*

class SpotifyApi(private val appContext: Context, private val context: Context) {

    private var session: Session? = null
    private val nextQueryUrls: HashMap<String, String> = HashMap()
    private var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)
    private var savedTrackIds: MutableSet<String> = HashSet()

    private fun createSession() {
        val username = sharedPref.getString(context.getString(R.string.username_key), null)
        val password = sharedPref.getString(context.getString(R.string.password_key), null)
        if (username == null || password == null) {
            throw Exception("Username and/or password missing!")
        }
        val conf = Session.Configuration.Builder()
            .setStoreCredentials(false)
            .setCacheEnabled(true)
            .setCacheDir(appContext.cacheDir)
            .setConnectionTimeout(30 * 1000)
            .build()
        val builder = Session.Builder(conf)
            .setPreferredLocale(Locale.getDefault().language)
            .setDeviceType(Connect.DeviceType.SMARTPHONE)
            .setDeviceId(sharedPref.getString(context.getString(R.string.device_id_key), null))
            .userPass(username, password)
        session = builder.create()
        sharedPref.edit().putString(context.getString(R.string.device_id_key), session?.deviceId()).apply()
    }

    private fun token(): String {
        session?.let {
            return "Bearer " + it.tokens().getToken("user-library-read", "user-library-modify", "user-follow-read").accessToken
        } ?: throw Exception("No session!")
    }

    fun getReleaseRadar(refresh: Boolean): List<MediaMetadataCompat> {
        val response = JSONObject(request(RELEASE_RADAR_URL, "GET").value)
        val id = response.getJSONObject("playlists").getJSONArray("items").getJSONObject(0).getString("id")
        val items = request(String.format(PLAYLIST_URL, id), refresh)
        return parseTracks(items)
    }

    fun getUsersSavedTracks(refresh: Boolean): List<MediaMetadataCompat> {
        val items = request(USERS_SAVED_TRACKS_URL, refresh)
        return parseTracks(items)
    }

    fun getArtists(refresh: Boolean): List<MediaMetadataCompat> {
        val items = request(USERS_FOLLOWING, refresh, "artists")
        return parsePlaylists(items)
    }

    fun getArtist(id: String, refresh: Boolean): List<MediaMetadataCompat> {
        val items = request(ARTIST_TRACKS.format(id), refresh, "tracks")
        return parseTracks(items)
    }

    fun getUsersPlaylists(refresh: Boolean): List<MediaMetadataCompat> {
        val items = request(USERS_PLAYLISTS, refresh)
        return parsePlaylists(items)
    }

    fun getPlaylist(id: String, refresh: Boolean): List<MediaMetadataCompat> {
        val items = request(String.format(PLAYLIST_URL, id), refresh)
        return parseTracks(items)
    }

    fun query(query: String, refresh: Boolean): List<MediaMetadataCompat> {
        val items = request(String.format(QUERY_URL, query), refresh, "tracks")
        return parseTracks(items)
    }

    private fun request(url: String, refresh: Boolean, type: String? = null): JSONArray {
        val request = if (!refresh && url in nextQueryUrls) {
            if (nextQueryUrls[url] == "null") {
                return JSONArray()
            }
            nextQueryUrls[url].toString()
        } else {
            url
        }
        var response = JSONObject(request(request, "GET").value)
        if (type != null && response.get(type) is JSONObject) {
            response = response.getJSONObject(type)
        }
        nextQueryUrls[url] = if (response.has("next")) response.getString("next") else "null"
        if (type != null && response.has(type))
            return response.getJSONArray(type)
        return response.getJSONArray("items")
    }

    private fun parseTracks(items: JSONArray): List<MediaMetadataCompat> {
        val result = mutableListOf<MediaMetadataCompat>()
        if (items.length() == 0) {
            return result
        }

        val savedStatus = getTracksSavedStatus(items)
        for (i in 0 until items.length()) {
            var item = items.getJSONObject(i)
            if (item.has("track")) {
                item = item.getJSONObject("track")
            }

            result.add(MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, item.getString("uri"))
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.getString("name"))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, item.getJSONArray("artists").getJSONObject(0).getString("name"))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, item.getJSONObject("album").getString("name"))
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, item.getJSONObject("album").getJSONArray("images").getJSONObject(0).getString("url"))
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, item.getString("uri"))
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, item.getLong("duration_ms"))
                .putString(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.MEDIA.name)
                .putRating(MediaMetadataCompatExt.METADATA_KEY_FAVORITE, RatingCompat.newHeartRating(savedStatus.getBoolean(i)))
                .build())

            if (savedStatus.getBoolean(i)) {
                savedTrackIds.add(item.getString("uri"))
            }
        }
        return result
    }

    private fun parsePlaylists(items: JSONArray): List<MediaMetadataCompat> {
        val result = mutableListOf<MediaMetadataCompat>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            result.add(MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, item.getString("id"))
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.getString("name"))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "")
                .putString(
                    MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI,
                    if (item.isNull("images") || item.getJSONArray("images").length() == 0) ""
                    else item.getJSONArray("images").getJSONObject(0).getString("url")
                )
                .putString(MediaMetadataCompatExt.METADATA_KEY_TYPE, MediaMetadataCompatExt.MediaType.STREAM.name)
                .build()
            )
        }
        return result
    }

    private fun getTracksSavedStatus(items: JSONArray): JSONArray {
        val ids = mutableListOf<String>()
        for (i in 0 until items.length()) {
            var item = items.getJSONObject(i)
            if (item.has("track")) {
                item = item.getJSONObject("track")
            }
            ids.add(item.getString("id"))
        }
        return JSONArray(request("$USERS_SAVED_TRACKS_URL/contains?ids=${ids.joinToString(",")}", "GET").value)
    }

    fun saveTrack(uri: String): Boolean {
        return try {
            val exists = savedTrackIds.contains(uri)
            request("$USERS_SAVED_TRACKS_URL?ids=${uri.substringAfterLast(':')}",
                if (exists) "DELETE" else "PUT")
            if (exists) {
                savedTrackIds.remove(uri)
            } else {
                savedTrackIds.add(uri)
            }
            true
        } catch (e: WebRequests.HttpException) {
            false
        }
    }

    fun streamUri(uri: String): MediaDataSource {
        if (session == null) {
            createSession()
        }
        val stream = session!!.contentFeeder().load(
            TrackId.fromUri(uri),
            VorbisOnlyAudioQuality(AudioQuality.HIGH),
            true,
            object : HaltListener {
                override fun streamReadHalted(chunk: Int, time: Long) {}
                override fun streamReadResumed(chunk: Int, time: Long) {}
            })

        val audioIn = stream.`in`.stream()
        return SpotifyMediaDataSource(audioIn)
    }

    private fun request(url: String, method: String): WebRequests.Response {
        if (session == null) {
            createSession()
        }
        return WebRequests.request(url, method, mapOf("Authorization" to token()))
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

    companion object {
        private const val BASE_URL = "https://api.spotify.com/v1"
        private const val QUERY_URL = "$BASE_URL/search?q=%s&type=track"
        private const val USERS_SAVED_TRACKS_URL = "$BASE_URL/me/tracks"
        private const val USERS_FOLLOWING = "$BASE_URL/me/following?type=artist"
        private const val ARTIST_TRACKS = "$BASE_URL/artists/%s/top-tracks?market=US"
        private const val USERS_PLAYLISTS = "$BASE_URL/me/playlists"
        private const val RELEASE_RADAR_URL = "$BASE_URL/search?q=Release-Radar&type=playlist&limit=1"
        private const val PLAYLIST_URL = "$BASE_URL/playlists/%s/tracks?limit=50"
    }
}