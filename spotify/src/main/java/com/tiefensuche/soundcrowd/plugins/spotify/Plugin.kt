/*
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.tiefensuche.soundcrowd.plugins.spotify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.support.v4.media.MediaMetadataCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import com.tiefensuche.soundcrowd.plugins.Callback
import com.tiefensuche.soundcrowd.plugins.IPlugin

class Plugin(appContext: Context, context: Context) : IPlugin {

    companion object {
        const val name = "Spotify"
        const val TRACKS = "Tracks"
        const val ARTISTS = "Artists"
        const val PLAYLISTS = "Playlists"
        const val RELEASES = "Release Radar"
    }

    private val spotifyApi: SpotifyApi = SpotifyApi(appContext, context)
    private val icon: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.plugin_icon)
    private val editTextUsername = EditTextPreference(appContext)
    private val editTextPassword = EditTextPreference(appContext)

    init {
        editTextUsername.key = context.getString(R.string.username_key)
        editTextUsername.title = context.getString(R.string.username_title)
        editTextUsername.summary = context.getString(R.string.username_summary)
        editTextUsername.dialogTitle = context.getString(R.string.username_title)
        editTextUsername.dialogMessage = context.getString(R.string.username_dialog_message)

        editTextPassword.key = context.getString(R.string.password_key)
        editTextPassword.title = context.getString(R.string.password_title)
        editTextPassword.summary = context.getString(R.string.password_summary)
        editTextPassword.dialogTitle = context.getString(R.string.password_title)
        editTextPassword.dialogMessage = context.getString(R.string.password_dialog_message)
    }

    override fun name() = name

    override fun mediaCategories(): List<String> = listOf(TRACKS, ARTISTS, PLAYLISTS, RELEASES)

    override fun preferences(): List<Preference> = listOf(editTextUsername, editTextPassword)

    override fun getMediaItems(mediaCategory: String, callback: Callback<List<MediaMetadataCompat>>, refresh: Boolean) {
        when (mediaCategory) {
            ARTISTS -> callback.onResult(spotifyApi.getArtists(refresh))
            TRACKS -> callback.onResult(spotifyApi.getUsersSavedTracks(refresh))
            PLAYLISTS -> callback.onResult(spotifyApi.getUsersPlaylists(refresh))
            RELEASES -> callback.onResult(spotifyApi.getReleaseRadar(refresh))
        }
    }

    override fun getMediaItems(
        mediaCategory: String,
        path: String,
        callback: Callback<List<MediaMetadataCompat>>,
        refresh: Boolean
    ) {
        when (mediaCategory) {
            ARTISTS -> callback.onResult(spotifyApi.getArtist(path, refresh))
            PLAYLISTS -> callback.onResult(spotifyApi.getPlaylist(path, refresh))
        }
    }

    override fun getMediaItems(mediaCategory: String, path: String, query: String, callback: Callback<List<MediaMetadataCompat>>, refresh: Boolean) {
        callback.onResult(spotifyApi.query(query, refresh))
    }

    override fun getIcon(): Bitmap = icon

    override fun getMediaUrl(metadata: MediaMetadataCompat, callback: Callback<Pair<MediaMetadataCompat, MediaDataSource?>>) {
        callback.onResult(Pair(metadata, spotifyApi.streamUri(metadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI))))
    }

    override fun favorite(id: String, callback: Callback<Boolean>) {
        callback.onResult(spotifyApi.saveTrack(id))
    }
}