/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.plugins.spotify

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.tiefensuche.soundcrowd.plugins.IPlugin
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket

class Plugin(context: Context) : IPlugin {

    companion object {
        const val NAME = "Spotify"
        const val CATEGORIES = "Categories"
        const val TRACKS = "Tracks"
        const val ARTISTS = "Artists"
        const val ALBUMS = "Albums"
        const val PLAYLISTS = "Playlists"
        const val SHOWS = "Shows"
    }

    private val api = SpotifyApi(context)
    private val icon = BitmapFactory.decodeResource(context.resources, R.drawable.icon_plugin_spotify)
    private val connectPreference = SwitchPreference(context)

    init {
        connectPreference.key = context.getString(R.string.spotify_connect_key)
        connectPreference.title = context.getString(R.string.spotify_connect_title)
        connectPreference.summary = context.getString(R.string.spotify_connect_summary)
        connectPreference.isChecked = api.credentialsFile.exists()
        connectPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                startCallbackServer()
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(api.oauth.authUrl))
                intent.flags = FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                false
            } else {
                api.credentialsFile.delete()
                true
            }
        }
    }

    override fun name() = NAME

    override fun mediaCategories(): List<String> = listOf(CATEGORIES, TRACKS, ARTISTS, ALBUMS, PLAYLISTS, SHOWS)

    override fun preferences(): List<Preference> = listOf(connectPreference)

    override fun getMediaItems(mediaCategory: String, refresh: Boolean): List<MediaItem> {
        return when (mediaCategory) {
            CATEGORIES -> api.getCategories(refresh)
            TRACKS -> api.getUsersSavedTracks(refresh)
            ARTISTS -> api.getArtists(refresh)
            ALBUMS -> api.getAlbums(refresh)
            PLAYLISTS -> api.getUsersPlaylists(refresh)
            SHOWS -> api.getShows(refresh)
            else -> emptyList()
        }
    }

    override fun getMediaItems(
        mediaCategory: String,
        path: String,
        refresh: Boolean
    ) : List<MediaItem> {
        return when (mediaCategory) {
            CATEGORIES -> api.getCategoryPlaylist(path, refresh)
            ARTISTS -> api.getArtist(path, refresh)
            ALBUMS -> api.getAlbumTracks(path, refresh)
            PLAYLISTS -> api.getPlaylist(path, refresh)
            SHOWS -> api.getEpisodes(path, refresh)
            else -> emptyList()
        }
    }

    override fun getMediaItems(mediaCategory: String, path: String, query: String, type: String, refresh: Boolean): List<MediaItem> {
        return api.query(query, refresh)
    }

    override fun getIcon(): Bitmap = icon

    override fun getDataSource(mediaItem: MediaItem): MediaDataSource {
        return api.streamUri(mediaItem.requestMetadata.mediaUri.toString())
    }

    override fun favorite(id: String): Boolean {
        return api.saveTrack(id)
    }

    private fun callback(callback: String) {
        api.oauth.setCode(callback.substringAfterLast('='))
        connectPreference.isChecked = true
    }

    override fun callbacks() = mapOf("spotify" to ::callback)

    private fun startCallbackServer() {
        val thread = Thread(kotlinx.coroutines.Runnable {
            val server = ServerSocket(5588)
            val socket = server.accept()

            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val data = input.readLine()
            val location = data.substring(data.indexOf("/login"), data.indexOf(" HTTP/1.1"))

            val dataOutputStream = PrintWriter(socket.getOutputStream())
            dataOutputStream.write("HTTP/1.1 302 Found\r\n")
            dataOutputStream.write("Location: soundcrowd://spotify$location\r\n")
            dataOutputStream.write("\r\n\r\n")
            dataOutputStream.flush()

            input.close()
            dataOutputStream.close()
            socket.close()
            server.close()
        })
        thread.start()
    }
}