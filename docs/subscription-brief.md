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
- **channels**: `id`, `name`, `logo_url`, `group/kategori`, `stream_url`, `stream_type`, `is_free` (bool), `is_enabled`, `sort_index`, `drm…`, `headers`.
- **admins**: `id`, `email`, `password_hash`, `role`.
- **activation_logs**: `device_id`, `admin_id`, `action`, `old/new_expiry`, `at` (audit).

## 6. API backend
| Endpoint | Fungsi |
|----------|--------|
| `POST /v1/sync` (body: deviceId, token?) | Daftar device kalau baru (mulai trial), balikan **entitlement + daftar channel terfilter** + `expires_at` (untuk countdown) + config (URL web, no WA, promo video) |
| `GET /v1/config` | URL website, video promo, dll (remote, bisa ubah tanpa update app) |
| **Admin (auth):** `…/devices` (list/edit/extend/ban), `…/channels` (CRUD), `…/login` | Dashboard |

## 7. Aturan akses channel + enforcement (anti-bajak)
- **Premium/trial:** dapat semua channel **lengkap dengan stream_url**.
- **Free:** channel free dapat stream_url; channel paid **dikirim TANPA stream_url** (hanya `name`, `logo`, `locked=true`).
- Jadi user gratis **secara fisik tidak punya URL** channel paid → tidak bisa dibajak walau utak-atik app. Penguncian di server, bukan cuma UI.
- Klik channel terkunci → app putar **video promo berlangganan** + overlay tombol "Berlangganan".

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

## 11. Keamanan & anti-abuse
1. **URL channel paid tidak pernah dikirim ke device free** (Β§7) — paling krusial.
2. **Token device** terbitan server saat registrasi (dikirim tiap request) → kurangi spoofing Device ID.
3. **Cache entitlement + grace** kalau backend mati (pakai status terakhir s/d X jam, lalu turun ke free).
4. **(Lanjutan)** signed/expiring stream URL agar link tak bisa dibagikan; deteksi pemakaian bersamaan.
5. **Reminder legalitas konten**: jual akses konten harus berlisensi.

## 12. Tambahan yang disarankan
- **Notifikasi "trial hampir habis"** (mis. sisa 10 menit) → pendorong konversi.
- **Remote config** (URL web, nomor WA, video promo, durasi trial) — ubah tanpa update app.
- **Analitik** sederhana: channel populer, konversi trial→bayar.
- **Tampilkan Device ID** di app (Settings) supaya user bisa kirim ke admin saat aktivasi.
- **Pembayaran manual = MVP yang tepat** (chat admin + QRIS statis). Integrasi PSP/QRIS otomatis menyusul.

## 13. Rekomendasi tech stack
- **Backend + DB:** **Supabase** (Postgres + REST otomatis + auth + RLS). Alternatif: Node (NestJS) + Postgres di VPS.
- **Dashboard admin:** Next.js (atau awalnya Supabase Studio).
- **Website langganan:** statis (Vercel/Netlify/GitHub Pages).
- **App:** integrasi via Retrofit/OkHttp (OkHttp sudah ada).

## 14. Fase pengerjaan
1. **Fase 1 — Backend + entitlement:** DB, `/v1/sync`, registrasi device + trial 1 jam, katalog channel free/paid, enforcement URL.
2. **Fase 2 — Integrasi app:** chip status, tombol berlangganan, channel terkunci + promo video, WebView, Device ID di Settings.
3. **Fase 3 — Dashboard admin:** kelola user/channel + aktivasi manual + import M3U.
4. **Fase 4 — Website + hardening:** halaman langganan + WA, token device, cache/grace, notifikasi, analitik.

## 15. Keputusan yang perlu dikonfirmasi
1. **Unit langganan: per-Device ID** (1 bayar = 1 device)? (multi-device butuh sistem akun).
2. **Katalog channel sepenuhnya dari backend** — masih izinkan user tambah playlist sendiri, atau hapus untuk produk berbayar?
3. **Tech stack backend:** Supabase (rekomendasi) atau Node+VPS?
4. **Video promo:** satu video umum, atau beda per channel?
5. **Konten paid sudah berlisensi?** (prasyarat jualan).
6. URL website + nomor WhatsApp admin (untuk di-config).
