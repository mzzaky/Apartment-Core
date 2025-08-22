# Apartment Core Plugin — Update v1.2.5

## Ringkasan
Update **Apartment Core v1.2.5** menghadirkan fitur-fitur besar yang memperluas fungsionalitas plugin, khususnya dalam sistem apartemen, interaksi pemain, statistik, dan integrasi placeholder. Dokumentasi ini menjelaskan detail fitur baru, perubahan kode, serta tambahan konfigurasi.

---

## Fitur Baru v1.2.5

1. **Manual Teleport Location**
   - Pemilik apartemen dapat mengatur lokasi teleportasi secara manual.
   - Lokasi ini digunakan sebagai titik masuk saat pemain teleport ke apartemen.

2. **Sistem Guest Book**
   - Pengunjung dapat meninggalkan pesan kepada pemilik apartemen.
   - Pesan dapat disertai item melalui sistem **gift and claim**.
   - Setiap pesan menyimpan:
     - Nama pengirim
     - Waktu pengiriman
     - Pesan teks
   - Dapat diakses melalui perintah berbasis command.
   - Didukung placeholder untuk ditampilkan ke HUD/scoreboard/chat.

3. **Statistik Apartemen Lengkap**
   - Statistik apartemen dapat diambil melalui placeholder baru.
   - Statistik mencakup informasi kepemilikan, jumlah kunjungan, penggunaan guestbook, income, pajak, dsb.

4. **Virtual Apartment Tours**
   - Pemilik apartemen dapat mengatur titik **preview tour**.
   - Mode ini menggunakan spectator mode **non-interaktif** (pemain tidak dapat bergerak).
   - Berguna untuk melihat apartemen sebelum membeli/menyewa.

5. **Informasi Rent dan Pajak dengan Placeholder**
   - Menampilkan informasi:
     - Waktu kapan **income** berikutnya tersedia.
     - Waktu kapan **pajak** berikutnya akan ditagih.
   - Format waktu mendukung jam, menit, detik.

---

## Perubahan pada Kode Java

### Method Baru (v1.2.5)
Berikut daftar method baru yang terdeteksi, beserta tujuan fungsionalnya:

- `loadGuestBookData()` — load data guestbook dari penyimpanan.
- `saveGuestBookData()` — simpan data guestbook ke penyimpanan.
- `handleGiftCommand()` / `handleClaimCommand()` — proses pengiriman & klaim hadiah.
- `setTeleportLocation()` / `teleportToApartment()` — mengatur & mengeksekusi teleportasi manual.
- `startPreviewTour()` / `stopPreviewTour()` — mengatur virtual tour (spectator mode).
- `getApartmentStats()` — mengambil statistik apartemen untuk placeholder.
- `formatTimeRemaining()` — utilitas format waktu (jam/menit/detik).

### Placeholder Baru
Implementasi baru di dalam `ApartmentPlaceholder` meliputi:
- `%apartment_guestbook_count%`
- `%apartment_guestbook_last_message%`
- `%apartment_guestbook_last_sender%`
- `%apartment_stats_*%` (dinamis sesuai statistik yang tersedia)
- `%apartment_income_next%` (waktu income berikutnya)
- `%apartment_tax_next%` (waktu pajak berikutnya)

### Config.yml
Perubahan pada file konfigurasi:
- **Key baru ditambahkan:**
  - `guestbook.max-messages` → Membatasi jumlah pesan yang dapat tersimpan di guestbook.

- **Key yang dihapus:**
  - Tidak ada.

---

## Update Log (CHANGELOG)

### v1.2.5
- [Added] Sistem guest book + gift & claim items dengan pencatatan nama dan waktu pengirim.
- [Added] Placeholder baru untuk menampilkan pesan guestbook, statistik, income, dan pajak.
- [Added] Teleportasi manual untuk pemilik apartemen.
- [Added] Virtual apartment tour (spectator mode preview).
- [Added] Sistem statistik apartemen yang terhubung dengan placeholder.
- [Changed] Struktur internal `ApartmentPlaceholder` untuk mendukung kategori baru.
- [Config] Penambahan key `guestbook.max-messages`.

### v1.2.0 → v1.2.5
- Fitur lama tetap berjalan tanpa perubahan besar.
- Kompatibilitas backward dengan config.yml tetap terjaga.

---

## Catatan Pengembangan
- Semua fitur baru menggunakan command handler dan placeholder yang sudah terintegrasi.
- Data guestbook diserialisasi untuk mendukung penyimpanan persisten.
- Placeholder menggunakan sistem *dynamic switch* untuk memetakan statistik dan informasi baru.
- Configurable limit (`guestbook.max-messages`) untuk menghindari overload data.

---

## Kesimpulan
Update v1.2.5 menambahkan sistem interaksi sosial, statistik, dan fitur tur apartemen yang meningkatkan kedalaman gameplay. Perubahan bersifat **kompatibel ke belakang** kecuali pengguna ingin mengatur batasan guestbook baru di config.yml.
