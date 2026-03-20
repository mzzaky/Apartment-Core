# Panduan Manajemen Lisensi ApartmentCore Pro

Berikut adalah panduan untuk mengelola lisensi Anda menggunakan infrastruktur Supabase yang telah Anda setup.

## Jawaban Pertanyaan Anda
> **"Apakah setiap kali saya build versi pro plugin automatis aktif tanpa key?"**

**TIDAK.** Meskipun Anda membuild versi **Pro**, fitur Pro tidak akan aktif secara otomatis. Plugin akan tetap berjalan dalam **mode Free** jika:
1. Tidak ada `license.key` di `config.yml`.
2. Lisensi tidak valid atau sudah expired.
3. Batas server (max_servers) tercapai.

Hal ini bertujuan agar file JAR Pro yang Anda distribusikan tetap aman; hanya pembeli yang memiliki key valid yang bisa menggunakan fitur Pro.

---

## Panduan Manajemen via Supabase (SQL Editor)

Gunakan **SQL Editor** di Supabase untuk menjalankan perintah-perintah berikut:

### 1. Membuat Lisensi Baru
Gunakan fungsi `create_license()` yang sudah kita buat sebelumnya.
```sql
-- Format: SELECT * FROM create_license('email_pembeli', 'Nama Pembeli', jumlah_server_maksimal);
SELECT * FROM create_license('customer@email.com', 'Budi Santoso', 1);
```
*Hasil: Anda akan mendapatkan `license_key` (contoh: `ACPRO-ABCD-1234...`).*

### 2. Melihat Semua Lisensi Aktif
```sql
SELECT license_key, owner_email, status, max_servers, created_at 
FROM licenses 
ORDER BY created_at DESC;
```

### 3. Monitoring Aktivasi Server
Untuk melihat server mana saja yang sedang menggunakan lisensi tertentu:
```sql
SELECT l.license_key, a.server_ip, a.plugin_version, a.last_seen_at
FROM license_activations a
JOIN licenses l ON a.license_id = l.id
WHERE a.is_active = true;
```

### 4. Cabut (Revoke) Lisensi
Jika Anda ingin menonaktifkan lisensi (misalnya karena refund atau penyalahgunaan):
```sql
UPDATE licenses 
SET status = 'revoked' 
WHERE license_key = 'ACPRO-KEY-YANG-MAU-DICABUT';
```
*Efek: Server tersebut akan otomatis turun ke mode Free pada pengecekan berikutnya.*

### 5. Lihat Log Validasi (Audit Log)
Untuk melihat siapa saja yang mencoba validasi, termasuk yang gagal:
```sql
SELECT created_at, license_key, server_ip, result, message 
FROM validation_log 
ORDER BY created_at DESC 
LIMIT 50;
```

---

## Troubleshooting Cepat
- **License: INVALID (No key):** Pastikan `license.key` sudah ada di `config.yml` (bukan `config.yml` default, tapi yang di folder `plugins/ApartmentCore/`).
- **License: ERROR (Connection):** Pastikan Cloudflare Worker Anda sudah benar (`SUPABASE_URL` dan `SERVICE_KEY` sudah di-set via `wrangler secret`).
- **Verifikasi Online:** Anda bisa selalu cek status Worker Anda di `https://apartmentcore-license.apartmentcore-license.workers.dev/api/health`.

---

## Skenario Lisensi & Konsekuensinya

Sistem lisensi ApartmentCore dirancang dengan prinsip **"Fail-to-Free"**. Jika terjadi masalah pada lisensi, plugin TIDAK AKAN mati atau crash, melainkan hanya menonaktifkan fitur Pro dan berjalan sebagai versi Free.

| Skenario | Apa yang Terjadi pada Plugin? | Apa yang Dilihat di Console? |
| :--- | :--- | :--- |
| **Lisensi Kosong** | Plugin berjalan dalam mode Free. | `No license key configured. Add 'license.key' to config.yml.` |
| **Batas Server Tercapai** | Server baru yang mencoba aktif akan turun ke mode Free. | `Maximum server limit reached. Deactivate another server first.` |
| **Lisensi Expired** | Fitur Pro dinonaktifkan secara otomatis. | `License has expired. Please renew your license.` |
| **Gagal Koneksi API** | Plugin menggunakan cache (Grace Period 7 hari) jika sebelumnya sudah pernah valid. | `Offline mode – cached validation (X days ago).` |
| **Key Salah Ketik** | Plugin berjalan dalam mode Free. | `License key is not valid.` |

---

## Reset Lisensi (Pindah Server)

Jika pembeli memindahkan plugin ke server baru (IP/ID berbeda) dan mendapatkan error `Maximum server limit reached`, Anda perlu melakukan reset aktivasi.

### Cara Manual (SQL Editor)
Jalankan perintah ini di Supabase untuk menghapus semua aktivasi lama pada lisensi tersebut:
```sql
-- Ganti 'ACPRO-XXXX' dengan key pembeli
UPDATE license_activations 
SET is_active = false 
WHERE license_id = (SELECT id FROM licenses WHERE license_key = 'ACPRO-XXXX');
```

### Tips Automasi
Anda dapat mengotomatiskan proses ini dengan:
1.  **Discord Bot:** Membuat command `/license reset` yang menjalankan query di atas.
2.  **Dashboard:** Membuat tombol "Reset" di web portal pembeli.
3.  **Auto-Expiry:** (Opsional) Mengaktifkan sistem di Worker untuk otomatis deaktif jika server tidak terlihat selama > 3 hari.

---

## Mekanisme Proteksi & Keamanan Fitur

Bagaimana plugin mencegah penggunaan fitur Pro jika lisensi tidak valid, meskipun file konfigurasi Pro masih ada di folder?

1.  **Pengecekan Logic (Hard-Coded):** Setiap fitur Pro di dalam kode Java selalu dibungkus dengan pengecekan `isLicenseActive()`. Jika lisensi tidak valid, kode fitur tersebut tidak akan pernah dieksekusi.
2.  **Ignored Config:** Meskipun pengguna menambahkan `level-10` secara manual di `config.yml`, plugin akan secara otomatis membatasi (limit) pembacaan data hanya sampai batas versi Free (`level-5`) jika lisensi mati.
3.  **Data Isolation:** File log atau data Pro lainnya akan berhenti diupdate (freeze) seketika saat lisensi terdeteksi Expired/Invalid.
4.  **Startup Validation:** Pengecekan dilakukan setiap kali server startup. Modul-modul Pro tidak akan dimuat ke dalam memori server (RAM) jika validasi gagal, sehingga data di folder disk hanyalah "data diam" yang tidak berfungsi.

---

## Automasi Pengiriman Lisensi Marketplace (Auto-Delivery)

Untuk memberikan pelayanan instan kepada pembeli di marketplace (seperti BuiltByBit/MCMarket, Polymart, SpigotMC, atau Tebex), Anda dapat mengotomatiskan pembuatan dan pengiriman kode lisensi. Berikut adalah metode yang bisa Anda gunakan dengan infrastruktur Supabase & Cloudflare Worker yang sudah Anda miliki:

### 1. Integrasi Webhook (Cepat & Sepenuhnya Otomatis)
Marketplace modern (seperti **BuiltByBit/MCMarket**, **Polymart**, atau platform seperti **Tebex**) mendukung fitur Webhook.
* **Alur:** Saat ada pembelian baru -> Marketplace merespons dengan mengirim request Webhook ke URL Cloudflare Worker Anda -> Worker membuat lisensi baru di Supabase -> Worker membalas dengan lisensi key untuk ditampilkan di akun pembeli (atau mengirimkannya ke email/Discord pembeli).
* **Eksekusi:** Anda perlu membuat penanganan route baru (contoh: `/api/webhook/purchase`) di Cloudflare Worker yang memverifikasi signature marketplace, mengkonsumsi data pembeli, dan menjalankan `create_license()`.

### 2. Bot Verifikasi Discord (Standar untuk SpigotMC)
Marketplace seperti **SpigotMC** tidak memiliki webhook modern. Solusi paling populer adalah menggunakan Discord.
* **Alur:** Pembeli masuk ke server Discord Anda -> Memverifikasi kepemilikan plugin lewat bot (misalnya Spigot Verifier bot) untuk mendapatkan role "Buyer".
* **Eksekusi:** Anda dapat menambahkan bot Discord Anda sendiri yang mendengarkan command (mis. `/claim-license`). Ketika member dengan role "Buyer" menjalankan command ini, bot menembak endpoint API Cloudflare Worker (yang diamankan dengan API key internal) untuk membuat sebuah lisensi, lalu mengirimkannya via DM (Direct Message) kepada pengguna Discord tersebut secara otomatis.

### 3. Otomasi No-Code (Zapier / Make.com)
Jika Anda menerima penjualan di platform yang didukung atau pembayaran langsung (Stripe/PayPal).
* **Alur:** Zapier mendeteksi pembelian yang berhasil -> Memanggil webhook rahasia di Cloudflare Worker Anda -> Mengambil lisensi yang digenerate -> Secara otomatis mengirim email terima kasih yang berisi lisensi kepada pembeli.

### 4. Membuat "Customer Portal" (Web Dashboard Berbasis Discord)
Membangun web dashboard sederhana yang dapat di-hosting gratis.
* **Alur:** Pembeli masuk via Discord (OAuth) -> Web mengecek server Discord Anda apakah user tersebut punya role "Buyer". Jika punya, halaman web menyediakan tombol "Generate License Key" yang mengeksekusi request ke Supabase.

**Rekomendasi:** Jika Anda berjualan utamanya di **Polymart** atau **BuiltByBit**, fokus pada **Metode 1 (Webhook)**. Jika basis pembeli Anda di **SpigotMC**, jalankan **Metode 2 (Discord Bot)**.
