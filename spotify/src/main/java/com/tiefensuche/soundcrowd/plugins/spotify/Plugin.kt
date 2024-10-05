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
import android.support.v4.media.MediaMetadataCompat
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.tiefensuche.soundcrowd.plugins.Callback
import com.tiefensuche.soundcrowd.plugins.IPlugin

class Plugin(appContext: Context, context: Context) : IPlugin {

    companion object {
        const val name = "Spotify"
        const val CATEGORIES = "Categories"
        const val TRACKS = "Tracks"
        const val ARTISTS = "Artists"
        const val ALBUMS = "Albums"
        const val PLAYLISTS = "Playlists"
        const val SHOWS = "Shows"
    }

    private val api = SpotifyApi(appContext, context)
    private val icon = BitmapFactory.decodeResource(context.resources, R.drawable.plugin_icon)
    private val connectPreference = SwitchPreference(appContext)

    init {
        connectPreference.key = context.getString(R.string.connect_key)
        connectPreference.title = context.getString(R.string.connect_title)
        connectPreference.summary = context.getString(R.string.connect_summary)
        connectPreference.isChecked = api.credentialsFile.exists()
        connectPreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            if (newValue == true) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(api.oauth.authUrl))
                intent.flags = FLAG_ACTIVITY_NEW_TASK
                appContext.startActivity(intent)
                false
            } else {
                api.credentialsFile.delete()
                true
            }
        }
    }

    override fun name() = name

    override fun mediaCategories(): List<String> = listOf(CATEGORIES, TRACKS, ARTISTS, ALBUMS, PLAYLISTS, SHOWS)

    override fun preferences(): List<Preference> = listOf(connectPreference)

    override fun getMediaItems(mediaCategory: String, callback: Callback<List<MediaMetadataCompat>>, refresh: Boolean) {
        when (mediaCategory) {
            CATEGORIES -> callback.onResult(api.getCategories(refresh))
            TRACKS -> callback.onResult(api.getUsersSavedTracks(refresh))
            ARTISTS -> callback.onResult(api.getArtists(refresh))
            ALBUMS -> callback.onResult(api.getAlbums(refresh))
            PLAYLISTS -> callback.onResult(api.getUsersPlaylists(refresh))
            SHOWS -> callback.onResult(api.getShows(refresh))
        }
    }

    override fun getMediaItems(
        mediaCategory: String,
        path: String,
        callback: Callback<List<MediaMetadataCompat>>,
        refresh: Boolean
    ) {
        when (mediaCategory) {
            CATEGORIES -> callback.onResult(api.getCategoryPlaylist(path, refresh))
            ARTISTS -> callback.onResult(api.getArtist(path, refresh))
            ALBUMS -> callback.onResult(api.getAlbumTracks(path, refresh))
            PLAYLISTS -> callback.onResult(api.getPlaylist(path, refresh))
            SHOWS -> callback.onResult(api.getEpisodes(path, refresh))
        }
    }

    override fun getMediaItems(mediaCategory: String, path: String, query: String, callback: Callback<List<MediaMetadataCompat>>, refresh: Boolean) {
        callback.onResult(api.query(query, refresh))
    }

    override fun getIcon(): Bitmap = icon

    override fun getMediaUrl(metadata: MediaMetadataCompat, callback: Callback<Pair<MediaMetadataCompat, MediaDataSource?>>) {
        callback.onResult(Pair(metadata, api.streamUri(metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI))))
    }

    override fun favorite(id: String, callback: Callback<Boolean>) {
        callback.onResult(api.saveTrack(id))
    }

    private fun callback(callback: String) {
        api.oauth.setCode(callback.substringAfterLast('='))
        connectPreference.isChecked = true
    }

    override fun callbacks() = mapOf("127.0.0.1" to ::callback)
}