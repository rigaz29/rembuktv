# Live TV — Aplikasi Streaming TV (Mobile + Android TV)

Aplikasi streaming TV online untuk Android dengan **satu codebase** yang berjalan di
**handphone/tablet** dan **Android TV**. Ditulis dengan **Kotlin + Jetpack Compose**,
arsitektur **MVVM + repository**, dan pemutar berbasis **Media3/ExoPlayer** di dalam
`MediaSessionService`.

---

## Fitur

**Inti (wajib):**
- Satu app, dua mode UI: mobile (Material 3) & TV (`androidx.tv.material3`), dipilih
  otomatis via `UiModeManager` saat runtime.
- Multi-playlist: sumber **JSON** (GitHub raw milik user) dan **M3U/M3U8** (iptv-org atau
  URL sendiri). Bisa ditambah/dihapus/diaktifkan, di-cache di Room.
- Pemutar DASH (`.mpd`) & HLS (`.m3u8`) dengan deteksi tipe otomatis + MIME eksplisit.
- **Bypass HTTP Headers**: Mendukung kustomisasi `Referer` dan `User-Agent` (via `#EXTVLCOPT` di M3U atau field `headers` di JSON) untuk channel yang diproteksi.
- **Auto-retry** saat stream error: exponential backoff + batas maksimal, indikator
  "menyambungkan ulang…", tombol retry manual, dan reaksi terhadap perubahan jaringan.
- **Pemilihan track**: kualitas video, audio, dan subtitle — hanya menampilkan yang
  tersedia di stream (dibaca dari `Tracks` ExoPlayer). Subtitle bisa on/off & ganti bahasa.
- **Screen mode**: Fit / Zoom (crop) / Stretch.
- **Picture-in-Picture** (API 26+) otomatis saat `onUserLeaveHint`, dengan kontrol
  play/pause di jendela PiP.
- **Auto-resume** ke channel terakhir saat app dibuka (bisa dimatikan di Settings).

**Tambahan:**
- Favorit & riwayat tontonan (baris khusus di Home).
- Search by nama + filter by group/kategori/negara.
- Grid channel dengan logo (Coil 3) + placeholder huruf saat logo gagal dimuat.
- Settings: tema (Sistem/Terang/Gelap/AMOLED), auto-resume, ukuran buffer, cap bitrate
  saat seluler, hapus cache & riwayat.
- Sleep timer.
- Channel zapping: swipe atas/bawah (mobile) + tombol prev/next dan D-pad (TV).
- Penanganan error geo-block/DRM dengan pesan jelas (bukan layar hitam diam).
- ABR di-cap otomatis pada jaringan seluler (hemat kuota).

---

## Arsitektur & struktur

```
app/src/main/java/com/bagas/livetv/
├── core/            # deteksi device (TV/mobile), NetworkMonitor, Constants
├── di/              # modul Hilt (App, Database, Repository)
├── domain/          # model & interface repository (layer murni)
│   ├── model/       # Channel, PlaylistSource, AppSettings, ...
│   └── repository/  # PlaylistRepository, SettingsRepository
├── data/            # implementasi data
│   ├── remote/      # fetch playlist mentah (OkHttp) + DTO JSON
│   ├── parser/      # JsonPlaylistParser, M3uPlaylistParser, StreamTypeResolver
│   ├── local/       # Room (entity, DAO, database, mappers) + DataStore settings
│   └── repository/  # PlaylistRepositoryImpl, SettingsRepositoryImpl
├── player/          # PlaybackService (MediaSessionService), PlaybackConnection,
│                    #   MediaItemFactory (MIME, DRM, live config)
├── ui/
│   ├── theme/       # warna, tipografi, LiveTvTheme (light/dark/amoled)
│   ├── navigation/  # rute bersama
│   ├── common/      # komponen UI bersama (state, ChannelLogo)
│   ├── player/      # PlayerScreen adaptif (mobile+TV), PlayerViewModel, dialog
│   ├── mobile/      # navigasi + Home/Settings/Playlist mobile
│   ├── tv/          # navigasi + Home TV (10-foot, D-pad)
│   ├── RootViewModel, ChannelsViewModel, SettingsViewModel, PlaylistViewModel
│   └── AppRoot.kt   # cabang mobile vs TV + auto-resume
├── LiveTvApp.kt     # Application (Hilt + seed playlist + ImageLoader Coil)
└── MainActivity.kt  # entry point + PiP
```

Logika data/pemutar dipakai bersama; hanya layer UI yang bercabang mobile/TV.

---

## Cara build & jalankan

1. **Android Studio** (versi yang mendukung AGP 8.7+, mis. Koala/terbaru).
2. Biarkan Gradle sync berjalan.
3. Build debug: `./gradlew assembleDebug`
4. Unit test parser & logic: `./gradlew testDebugUnitTest`
5. Lint: `./gradlew lintDebug`
6. Smoke test (perangkat/emulator): `./gradlew connectedDebugAndroidTest`

minSdk 23, target/compileSdk 36. Fitur API baru (PiP, foreground service media,
`POST_NOTIFICATIONS`) di-guard dengan `Build.VERSION.SDK_INT` dan punya fallback aman.

---

## Mengganti / menambah playlist

Tidak ada URL yang di-hardcode mati. Atur dari **Home → ikon Playlist** (atau Settings →
Kelola playlist):

- **Tambah cepat**: chip iptv-org per negara/kategori.
- **Tambah manual**: isi Nama + URL, pilih tipe **M3U** atau **JSON**.
- Aktif/nonaktif, segarkan, atau hapus tiap playlist.

Playlist pertama (**BBC UK - Custom JSON**) di-seed otomatis saat pertama kali app dibuka.

### Skema JSON yang didukung

```json
[
  {
    "id": "string unik",
    "name": "Nama channel",
    "logo": "https://.../logo.png",
    "group": "Kategori/Negara",
    "url": "https://.../stream.mpd",
    "type": "dash | hls | other",
    "drm": { "scheme": "widevine", "licenseUrl": "https://..." },
    "headers": { "User-Agent": "...", "Referer": "..." }
  }
]
```

---

## Versi & kompatibilitas

| Komponen | Versi |
| :--- | :--- |
| Compose BOM | `2026.05.00` |
| androidx.tv:tv-material | `1.1.0` |
| Media3 | `1.10.0` |
| Coil | `3.0.4` · Hilt `2.54` · Room `2.6.1` |
| Kotlin | `2.1.0` · AGP `8.7.3` · KSP `2.1.0-1.0.29` |

---

## Keterbatasan yang diketahui

- **Geo-block / DRM**: sebagian stream (broadcaster tertentu) di-geo-restrict atau
  dilindungi DRM, sehingga **tidak bisa diputar tanpa IP region yang sesuai atau license
  server yang valid**. Ini batasan sumber stream, **bukan bug aplikasi**.
- **EPG (XMLTV)** belum diimplementasikan (opsional di brief).
- **Settings & Playlist di TV** memakai ulang layar mobile (Material 3) yang tetap bisa
  dinavigasi D-pad.

## Catatan Perbaikan Terbaru
- **HTTP Header Bypass**: Mendukung `Referer` dan `User-Agent` kustom untuk channel seperti RCTI Maxstream.
- **Auto-Reload**: Playlist kini otomatis di-refresh setiap kali aplikasi dibuka.
- **Background Audio**: Memperbaiki masalah suara tetap aktif saat kembali ke menu utama dari pemutar.
- **Dependency**: Memperbarui Hilt ke 2.54 untuk kompatibilitas penuh dengan Kotlin 2.1.0 dan AGP 8.7.3.
- **Seed Playlist**: Mengganti playlist default awal ke BBC UK (JSON) dengan dukungan logo lengkap.
