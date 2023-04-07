# soundcrowd-plugin-spotify

[![android](https://github.com/soundcrowd/soundcrowd-plugin-spotify/actions/workflows/android.yml/badge.svg)](https://github.com/soundcrowd/soundcrowd-plugin-spotify/actions/workflows/android.yml)
[![GitHub release](https://img.shields.io/github/release/soundcrowd/soundcrowd-plugin-spotify.svg)](https://github.com/soundcrowd/soundcrowd-plugin-spotify/releases)
[![GitHub](https://img.shields.io/github/license/soundcrowd/soundcrowd-plugin-spotify.svg)](LICENSE)

This soundcrowd plugin adds basic Spotify support. It allows you to listen and browse music from your saved tracks, release radar and additionally supports searching for music. This plugin requires a Spotify account. The plugin greatly depends on the `librespot-java` library, thanks!

## Building

    $ git clone --recursive https://github.com/soundcrowd/soundcrowd-plugin-spotify
    $ cd soundcrowd-plugin-spotify
    $ ./gradlew assembleDebug

Install via ADB:

    $ adb install spotify/build/outputs/apk/debug/spotify-debug.apk

## License

Licensed under GPLv3.

## Dependencies

- [librespot-java](https://github.com/librespot-org/librespot-java) - Apache 2.0