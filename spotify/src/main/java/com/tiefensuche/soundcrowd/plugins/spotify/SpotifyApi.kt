/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.plugins.spotify

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaDataSource
import android.support.v4.media.MediaMetadataCompat
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
import java.io.ByteArrayOutputStream
import java.util.*

class SpotifyApi(private val appContext: Context, private val context: Context) {

    private var session: Session? = null
    private val nextQueryUrls: HashMap<String, String> = HashMap()
    private var sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext)

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
            return "Bearer " + it.tokens().get("user-library-read")
        } ?: throw Exception("No session!")
    }

    fun getReleaseRadar(refresh: Boolean): List<MediaMetadataCompat> {
        val response = JSONObject(request(RELEASE_RADAR_URL, "GET").value)
        val id = response.getJSONObject("playlists").getJSONArray("items").getJSONObject(0).getString("id")
        val items = request(String.format(PLAYLIST_URL, id), refresh, "tracks")
        return parseTracks(items)
    }

    fun getUsersSavedTracks(refresh: Boolean): List<MediaMetadataCompat> {
        val items = request(USERS_SAVED_TRACKS_URL, refresh)
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
        if (type != null) {
            response = response.getJSONObject(type)
        }
        nextQueryUrls[url] = response.getString("next")
        return response.getJSONArray("items")
    }

    private fun parseTracks(items: JSONArray): List<MediaMetadataCompat> {
        val result = mutableListOf<MediaMetadataCompat>()
        if (items.length() == 0) {
            return result
        }

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
                .build())
        }
        return result
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
        val bos = ByteArrayOutputStream()
        var cur: Int
        while (stream.`in`.stream().read().also { cur = it } != -1) {
            bos.write(cur)
        }
        stream.`in`.stream().close()
        return BufferedMediaDataSource(bos.toByteArray())
    }

    private fun request(url: String, method: String): WebRequests.Response {
        if (session == null) {
            createSession()
        }
        return WebRequests.request(url, method, mapOf("Authorization" to token()))
    }

    class BufferedMediaDataSource(private val buffer: ByteArray) : MediaDataSource() {

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            val position = position.toInt()
            if (position >= this.buffer.size) {
                return -1
            }
            var size = size
            if (position + size > this.buffer.size) {
                size -= (position + size) - this.buffer.size
            }
            System.arraycopy(this.buffer, position, buffer, offset, size)
            return size
        }

        override fun getSize(): Long {
            return buffer.size.toLong()
        }

        override fun close() {}
    }

    companion object {
        private const val BASE_URL = "https://api.spotify.com/v1"
        private const val QUERY_URL = "$BASE_URL/search?q=%s&type=track"
        private const val USERS_SAVED_TRACKS_URL = "$BASE_URL/me/tracks"
        private const val NEW_RELEASES_URL = "$BASE_URL/browse/new-releases"
        private const val RELEASE_RADAR_URL = "$BASE_URL/search?q=Release-Radar&type=playlist&limit=1"
        private const val PLAYLIST_URL = "$BASE_URL/playlists/%s"
    }
}