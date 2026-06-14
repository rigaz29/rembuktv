# Brief: Sistem Berlangganan Rembuk TV

> Status: rancangan (belum implementasi). Disusun 2026-06-15.

## 1. Prinsip & alur singkat
Autentikasi berbasis **Device ID** (tanpa login/akun). Setiap app dibuka → backend cek device → tentukan hak akses (**entitlement**) → app menampilkan channel sesuai hak akses.

```
App buka → kirim Device ID ke backend
   ├─ device baru?  → daftarkan + beri TRIAL 1 jam → status = premium(trial)
   ├─ trial/langganan aktif? → PREMIUM → semua channel terbuka
   └─ kadaluarsa/gratis     → FREE → hanya channel free, channel paid terkunci
```

## 2. Device ID — realita teknis
Android tidak punya ID hardware permanen yang legal dipakai. Rekomendasi: **`Settings.Secure.ANDROID_ID`**.
- Stabil per (device + signing key + user) sejak Android 8.
- **Bertahan saat app di-uninstall/install ulang** → mencegah orang "farming" trial dengan reinstall.
- Reset hanya saat factory reset (abuse minor, ditoleransi).
- Berlaku sama untuk Android TV.

> Catatan abuse: factory reset = trial baru. Untuk MVP diterima; mitigasi lanjutan di Β§11.

## 3. Model entitlement
| Status | Arti | Akses |
|--------|------|-------|
| `trial` | device baru, ≤1 jam | Semua channel (seperti premium) |
| `premium` | langganan aktif (admin set tanggal) | Semua channel |
| `free` | trial habis / belum langganan | Hanya channel `is_free = true` |
| `banned` | diblokir admin | Tidak ada |

Entitlement dihitung **di server** tiap request: `premium jika (subscription_expires_at > now) ATAU (trial_expires_at > now)`.

## 4. Arsitektur
```
┌────────────┐     HTTPS      ┌─────────────┐     ┌──────────────┐
│ App Android│ ◄────────────► │   Backend   │ ──► │  Database    │
│ (mobile/TV)│  device-id +   │  (API)      │     │ devices,     │
└────────────┘  token         └─────────────┘     │ channels,    │
      │                              ▲             │ subs, admins │
      │ WebView                      │ admin auth  └──────────────┘
      ▼                       ┌─────────────┐
┌────────────┐               │ Dashboard   │  kelola user & channel
│ Web langganan│             │ Admin (web) │  + aktivasi manual
│ + Chat Admin │             └─────────────┘
└────────────┘
```
**Perubahan besar:** katalog channel kini **dilayani backend** (bukan lagi tarik langsung iptv-org), supaya admin bisa atur flag free/paid & sembunyikan URL channel paid.

## 5. Data model (tabel inti)
- **devices**: `id`, `device_id` (ANDROID_ID, unik), `created_at`, `trial_expires_at`, `subscription_expires_at` (nullable), `status` (free/premium/banned), `last_seen`, `note_admin`.
- **channels**: `id`, `name`, `logo_url`, `group/kategori`, `stream_url` (**URL asli/upstream — rahasia, hanya di server**), `stream_type`, `is_free` (bool), `is_enabled`, `sort_index`, `drm…`, `headers`.
- **admins**: `id`, `email`, `password_hash`, `role`.
- **activation_logs**: `device_id`, `admin_id`, `action`, `old/new_expiry`, `at` (audit).

## 6. API backend
| Endpoint | Fungsi |
|----------|--------|
| `POST /v1/sync` (body: deviceId, token?) | Daftar device kalau baru (mulai trial), balikan **entitlement + daftar channel terfilter** + `expires_at` (untuk countdown) + config (URL web, no WA, promo video) |
| `GET /v1/config` | URL website, video promo, dll (remote, bisa ubah tanpa update app) |
| `GET /s/{channelId}?token=…` | **Proxy stream**: validasi token → **HTTP 302** redirect ke URL asli (lihat §7.1) |
| **Admin (auth):** `…/devices` (list/edit/extend/ban), `…/channels` (CRUD), `…/login` | Dashboard |

## 7. Aturan akses channel + enforcement (anti-bajak)
- **Premium/trial:** dapat semua channel **lengkap dengan stream_url**.
- **Free:** channel free dapat stream_url; channel paid **dikirim TANPA stream_url** (hanya `name`, `logo`, `locked=true`).
- Jadi user gratis **secara fisik tidak punya URL** channel paid → tidak bisa dibajak walau utak-atik app. Penguncian di server, bukan cuma UI.
- Klik channel terkunci → app putar **video promo berlangganan** + overlay tombol "Berlangganan".

### 7.1 Proxy URL stream (sembunyikan URL asli + token)
Semua URL stream (`.m3u8` / `.mpd`) **tidak pernah dikirim apa adanya** ke app. Backend membungkusnya jadi URL domain sendiri ber-token:

```
DB (asli, rahasia):  https://stream.nasatv.com.mk/hls/nasatv_live.m3u8
Dikirim ke app:      https://websaya.com/s/nasatv?token=<token>
```

**Mekanisme — 302 redirect (rekomendasi, murah):**
1. `/v1/sync` mengembalikan URL proxy ber-token untuk tiap channel yang berhak.
2. Player membuka `https://websaya.com/s/{channel}?token=…`.
3. Endpoint **validasi token** (tanda tangan + kedaluwarsa + device tidak banned + berhak atas channel) → balas **HTTP 302** ke URL asli.
4. Player lanjut ke URL asli; segmen HLS/DASH mengalir **langsung dari origin** → hemat bandwidth server.

**Token:** ditandatangani (HMAC/JWT) berisi `channelId + deviceId + exp`, diterbitkan saat `/v1/sync`. Stateless → endpoint redirect tak perlu query DB tiap request (skalabel).
- **TTL** cukup panjang untuk sesi live (mis. 12–24 jam): setelah redirect pertama, reload manifest live diarahkan player langsung ke origin, jadi endpoint proxy praktis hanya kena sekali. Tiap buka app, token di-refresh oleh `/v1/sync`.
- Channel paid untuk device free: token tidak diterbitkan → URL proxy pun tidak ada (selaras §7).
- Banned/expired bisa langsung memutus karena token cepat kedaluwarsa & tidak di-refresh (atau tambah cek DB ringan saat redirect bila perlu revoke instan).

**Trade-off & opsi:**
- **302 redirect (default):** murah, URL asli hilang dari playlist/app & butuh token valid. Kekurangan: URL asli tetap terlihat di inspeksi jaringan **setelah** redirect.
- **Reverse proxy penuh (opsional, per-channel):** server mem-fetch + me-relay konten dan **menulis ulang URL segmen** → URL asli benar-benar tersembunyi, tapi **seluruh bandwidth video lewat server** (mahal + latensi). Pakai hanya untuk channel proteksi tinggi.
- **Header khusus** (Referer/User-Agent) tetap dikirim player ke origin saat 302; untuk reverse proxy, header disuntik server.

## 8. Perubahan di App Android (UX)

**a. Status langganan di sebelah "Rembuk TV"** (chip, bisa diklik → buka web langganan):
```
Rembuk TV  [● Premium]     |   Rembuk TV  [Trial 57m]   |   Rembuk TV  [Gratis]
```
Premium = hijau, Trial = kuning + hitung mundur, Gratis = abu.

**b. Tombol "Berlangganan" — penempatan berlapis (bukan satu tempat):**
1. **Chip status** (selalu tampil, samping judul) — bisa diklik.
2. **Tombol "Berlangganan" warna aksen** di top bar — hanya muncul saat **bukan premium**. Mobile: action di TopAppBar; TV: tombol di baris atas (kiri "Cari").
3. **Paywall CTA** saat klik channel terkunci (titik niat tertinggi → konversi terbaik).
4. Entri di **Settings** sebagai cadangan.

**c. Channel paid untuk user free:** kartu tampil **logo + ikon gembok + label "Premium"**; diklik → layar paywall yang memutar video promo + tombol "Berlangganan sekarang".

**d. WebView:** tombol berlangganan → buka **WebView** in-app memuat URL website (URL dari `/v1/config`, bisa diganti tanpa update app).

## 9. Website langganan (dibuka di WebView)
Halaman sederhana: **paket & harga**, **cara berlangganan**, tombol **"Chat Admin (WhatsApp)"** untuk bayar (mis. QRIS statis / transfer) → admin aktivasi manual. Tampilkan **Device ID** user (via parameter URL) supaya admin gampang aktivasi.

## 10. Dashboard Admin (web)
- **Login admin**.
- **Kelola user/device:** list + cari by Device ID, lihat status/sisa waktu, **aktifkan/perpanjang langganan** (set tanggal kadaluarsa), ban/unban, catatan. → inti aktivasi setelah user bayar.
- **Kelola channel:** CRUD, set **free/paid**, kategori, logo, stream URL, urutan, enable/disable, **import M3U/JSON** (manfaatkan parser yang sudah ada).
- **Audit log** aktivasi.

### 10.1 Alur admin menambah channel
Admin **hanya menempel URL asli** + atur metadata; URL server & token dikerjakan backend otomatis.
- **Input admin:** URL asli (`.m3u8`/`.mpd`), nama, logo, kategori, **free/paid**, (opsional) header khusus/DRM.
- **Otomatis backend:** simpan URL asli sebagai rahasia (`stream_url`); saat `/v1/sync`, buat **URL proxy + token** per-device (§7.1); tipe stream dideteksi dari URL.
- **Tidak diatur per channel:** token (dinamis per-device/waktu) & domain proxy (config global).

Contoh: admin ketik `https://stream.nasatv.com.mk/hls/nasatv_live.m3u8` → app menerima `https://websaya.com/s/nasatv?token=<unik per device>`.

## 11. Keamanan & anti-abuse
1. **Semua URL stream di-proxy ber-token (§7.1)** — URL asli tak pernah dikirim ke app; channel paid untuk device free bahkan tanpa token sama sekali. Paling krusial.
2. **Token device** terbitan server saat registrasi (dikirim tiap request) → kurangi spoofing Device ID.
3. **Cache entitlement + grace** kalau backend mati (pakai status terakhir s/d X jam, lalu turun ke free).
4. Token stream **bertanda tangan & cepat kedaluwarsa** agar link tak bisa dibagikan (§7.1); (lanjutan) deteksi pemakaian bersamaan.
5. **Reminder legalitas konten**: jual akses konten harus berlisensi.

## 12. Tambahan yang disarankan
- **Notifikasi "trial hampir habis"** (mis. sisa 10 menit) → pendorong konversi.
- **Remote config** (URL web, nomor WA, video promo, durasi trial) — ubah tanpa update app.
- **Analitik** sederhana: channel populer, konversi trial→bayar.
- **Tampilkan Device ID** di app (Settings) supaya user bisa kirim ke admin saat aktivasi.
- **Pembayaran manual = MVP yang tepat** (chat admin + QRIS statis). Integrasi PSP/QRIS otomatis menyusul.

## 13. Tech stack (backend: **shared hosting**)
Dipilih: **shared web hosting** — termurah & cukup untuk semua kebutuhan brief ini (model proxy 302).
- **Backend + DB:** **PHP (Laravel) + MySQL** di shared hosting.
- **Dashboard admin:** web PHP (Laravel/Blade) di hosting yang sama (atau frontend statis + API).
- **Website langganan:** halaman statis, bisa di hosting yang sama.
- **Endpoint proxy `/s` (§7.1):** PHP `header("Location: …")` (302) di hosting yang sama. Karena video **tidak lewat server** (cuma redirect), bandwidth shared hosting sanggup.
- **App:** integrasi via Retrofit/OkHttp (OkHttp sudah ada).

**Batasan & jalur skala:**
- Shared hosting cukup untuk MVP. Hindari **reverse proxy penuh** (relay video) di shared hosting — ada batas `max_execution_time`/CPU/fair-use.
- Jika nanti butuh sembunyikan URL total atau trafik besar → pindahkan **hanya bagian itu** ke **VPS / Cloudflare Worker**; sisanya tetap di shared hosting. Bila pemakaian membesar, pindah penuh ke VPS.

## 14. Fase pengerjaan
1. **Fase 1 — Backend + entitlement:** DB, `/v1/sync`, registrasi device + trial 1 jam, katalog channel free/paid, **endpoint proxy `/s` + token bertanda tangan (§7.1)**, enforcement URL.
2. **Fase 2 — Integrasi app:** chip status, tombol berlangganan, channel terkunci + promo video, WebView, Device ID di Settings.
3. **Fase 3 — Dashboard admin:** kelola user/channel + aktivasi manual + import M3U.
4. **Fase 4 — Website + hardening:** halaman langganan + WA, token device, cache/grace, notifikasi, analitik.

## 15. Keputusan yang perlu dikonfirmasi
1. **Unit langganan: per-Device ID** (1 bayar = 1 device)? (multi-device butuh sistem akun).
2. **Katalog channel sepenuhnya dari backend** — masih izinkan user tambah playlist sendiri, atau hapus untuk produk berbayar?
3. ~~Tech stack backend~~ → **DIPUTUSKAN: shared hosting (PHP/Laravel + MySQL)** (lihat §13).
4. **Video promo:** satu video umum, atau beda per channel?
5. **Konten paid sudah berlisensi?** (prasyarat jualan).
6. URL website + nomor WhatsApp admin (untuk di-config).
7. **Mode proxy stream (§7.1):** 302 redirect (murah, rekomendasi) atau reverse proxy penuh (sembunyikan total, mahal bandwidth)? + domain proxy yang dipakai (mis. `websaya.com`).
