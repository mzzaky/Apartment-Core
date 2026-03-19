# Panduan Instalasi Kode Lisensi – ApartmentCore Pro

Dokumen ini menjelaskan langkah-langkah untuk menginstal dan mengonfigurasi lisensi ApartmentCore Pro di server Minecraft Anda.

---

## Prasyarat

- Server Minecraft dengan Spigot/Paper 1.21+
- Plugin **Vault** dan **WorldGuard** sudah terinstal
- File `ApartmentCore-Pro-x.x.x.jar` (edisi Pro)
- Kode lisensi yang sudah dibeli (format: `ACPRO-XXXX-XXXX-XXXX-XXXX`)

---

## Langkah 1: Instal Plugin

1. Hentikan server Minecraft Anda
2. Salin file `ApartmentCore-Pro-x.x.x.jar` ke folder `plugins/`
3. Hapus file `ApartmentCore-Free-x.x.x.jar` jika sebelumnya menggunakan edisi Free
4. Jalankan server untuk pertama kali — plugin akan membuat folder `plugins/ApartmentCore/`

---

## Langkah 2: Masukkan Kode Lisensi

1. Buka file `plugins/ApartmentCore/config.yml`
2. Cari bagian `license`:

```yaml
# -----------------------------------------------------------------
# License (Pro Edition Only)
# -----------------------------------------------------------------
license:
  key: ""
```

3. Masukkan kode lisensi Anda di antara tanda kutip:

```yaml
license:
  key: "ACPRO-ABCD-EFGH-IJKL-MNOP"
```

4. Simpan file

---

## Langkah 3: Restart Server

1. Restart server Minecraft Anda (atau gunakan `/apartmentcore admin reload`)
2. Periksa console server — Anda akan melihat salah satu pesan berikut:

### Lisensi Valid ✓
```
[ApartmentCore] License: VALID - License validated successfully.
```

### Lisensi Tidak Valid ✗
```
[ApartmentCore] License: INVALID - License key is not valid.
[ApartmentCore] Pro features are DISABLED. The plugin will run in Free mode.
```

### Mode Offline (Grace Period)
```
[ApartmentCore] License: GRACE PERIOD - Offline mode – cached validation (2d ago).
```
Plugin masih berfungsi dalam mode Pro selama 7 hari sejak validasi terakhir.

---

## Langkah 4: Verifikasi Edisi

Jalankan perintah `/apartmentcore version` di server. Anda akan melihat:
```
ApartmentCore 1.3.8
Edition: Pro
License: VALID
```

Atau periksa splash banner di console saat startup — akan menampilkan `» Edition  Pro`.

---

## Troubleshooting

### Masalah: "No license key configured"
**Solusi:** Pastikan Anda sudah mengisi `license.key` di `config.yml` dan me-restart server.

### Masalah: "Cannot reach license server"
**Solusi:**
- Pastikan server memiliki akses internet keluar (outbound)
- Periksa firewall — worker URL harus bisa diakses
- Plugin akan tetap berjalan dalam mode grace period selama 7 hari

### Masalah: "Maximum server limit reached"
**Solusi:**
- Lisensi Anda terbatas pada jumlah server tertentu
- Hubungi developer untuk meningkatkan batas atau menonaktifkan server lama

### Masalah: "License has expired"
**Solusi:**
- Hubungi developer untuk memperbarui lisensi
- Plugin akan berjalan dalam mode Free sampai lisensi diperbarui

### Masalah: Plugin berjalan dalam mode Free meski sudah memasukkan lisensi
**Solusi:**
1. Pastikan Anda menggunakan file JAR edisi **Pro** (`ApartmentCore-Pro-x.x.x.jar`), bukan Free
2. Periksa console untuk pesan error lisensi
3. Pastikan format kode lisensi benar: `ACPRO-XXXX-XXXX-XXXX-XXXX`

---

## Informasi Teknis

### Bagaimana Validasi Bekerja

```
Plugin Startup
    │
    ├─► Baca license.key dari config.yml
    │
    ├─► Kirim request POST ke Cloudflare Worker API
    │   ├─ license_key
    │   ├─ server_ip & port
    │   ├─ server_id (hash unik)
    │   └─ plugin_version
    │
    ├─► Worker memvalidasi key di database Supabase
    │   ├─ Cek status (active/expired/revoked)
    │   ├─ Cek tanggal kedaluwarsa
    │   └─ Cek jumlah server yang aktif
    │
    └─► Hasil dikembalikan ke plugin
        ├─ VALID → Semua fitur Pro aktif
        ├─ INVALID → Berjalan dalam mode Free
        ├─ EXPIRED → Berjalan dalam mode Free
        └─ ERROR → Grace period (7 hari dari cache terakhir)
```

### Keamanan
- Validasi dilakukan secara **asinkron** (tidak memblokir startup server)
- Hasil validasi di-**cache** lokal untuk offline fallback
- Cache memiliki **grace period 7 hari**
- Komunikasi menggunakan **HTTPS**
- Kode lisensi di-hash menggunakan **SHA-256** sebelum disimpan di cache lokal

### Infrastruktur (untuk Developer)
- **Database:** Supabase (PostgreSQL) — gratis
- **API:** Cloudflare Workers — gratis hingga 100.000 request/hari
- Schema database: `infrastructure/supabase/schema.sql`
- Worker code: `infrastructure/cloudflare-worker/worker.js`
