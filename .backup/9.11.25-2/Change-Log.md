======v1.2.8 Update ====================
===============================================
daftar masalah
1. cmd `/apartmentcore rent info <apartment_id>` tab-completion bagian `<apartment_id>` tidak muncul.
2. cmd `/apartmentcore guestbook <leave|read|clear> <apartment_id>` tab-completion bagian `<leave|read|clear>` tidak muncul.
3. saat plugin berhasil diload, tidak ada messages.yml yang muncul di folder plugin
4. file `apartments-stats.yml` kosong.
5. sesuaikan `/apartmentcore admin create <region> <id> <price> <tax> <tax_days>` agar menjadi `/apartmentcore admin create <region> <id> <price>` dan muncul tab-completion agar mempermudah admin.
6. cmd `/apartmentcore rent claim <apartment_id>` tab-completion bagian `<apartment_id>` tidak muncul.
7. cmd `/apartmentcore rent info <apartment_id>` tab-completion bagian `<apartment_id>` tidak muncul.
===============================================
Saya telah memperbaiki kedua poin yang diminta:

1. UUID Parsing Tanpa Validasi (ApartmentManager.java)
Di loadApartments(): Menambahkan validasi untuk memastikan string owner tidak kosong sebelum parsing UUID.
Sebelum: aptSection.getString("owner") != null ? UUID.fromString(aptSection.getString("owner")) : null
Sesudah: Mengekstrak string owner ke variabel ownerStr, lalu UUID owner = (ownerStr != null && !ownerStr.isEmpty()) ? UUID.fromString(ownerStr) : null;
Di loadRatings(): Menambahkan try-catch di sekitar UUID.fromString(uuid) untuk menangani UUID tidak valid, dengan logging peringatan jika gagal.
2. Null Pointer Exception di onDisable (ApartmentCorei3.java)
Kode sudah memiliki pengecekan null untuk apartmentManager di onDisable():
if (apartmentManager != null) {
    apartmentManager.saveApartments();
    apartmentManager.saveRatings();
    apartmentManager.saveGuestBooks();
    apartmentManager.saveStats();```
}
java

Ini mencegah NPE jika inisialisasi gagal. Tidak ada perubahan tambahan yang diperlukan karena pengecekan sudah ada.
Perbaikan ini meningkatkan ketahanan plugin terhadap data konfigurasi yang tidak valid dan mencegah crash saat shutdown. Jika ada poin lain yang perlu diperbaiki, beri tahu saya.

======================================

Perbaikan diterapkan sesuai feedback.
Perubahan yang dibuat

1. Implementasi penuh Admin backup commands
Mengisi subcommand backup: create, list, restore.
Lokasi:
handleBackupCommand()
Detail:
create: memanggil DataManager.createBackup("manual") dan mengembalikan pesan sukses.
list: membaca folder backup dari DataManager.getBackupFolder(), menampilkan daftar file .yml terbaru lengkap dengan size dan tanggal (maks 100 baris untuk mencegah spam).
restore:
Validasi nama file (hindari traversal, harus .yml).
Memakai DataManager.restoreBackup(String).
Setelah restore berhasil, melakukan reload in-memory:
clear semua struktur data ApartmentManager, panggil ulang ApartmentManager.loadApartments(), ApartmentManager.loadRatings(), ApartmentManager.loadGuestBooks(), ApartmentManager.loadStats().
Memberi pesan sukses dengan nama file backup.
Catatan kecil: Pesan bantuan di cabang “list” menggunakan “/apartmentcore admin backup restore <filename.yml>” sementara default usage masih “/apt ...”; ini tidak mempengaruhi fungsi dan bisa diseragamkan nanti jika diinginkan.

2. Implementasi Fix isu KRITIS — Thread-safety: meniadakan save async di auto-save
Mengubah auto-save dari async ke main thread:
Dari runTaskTimerAsynchronously → runTaskTimer agar semua operasi Bukkit/YAML berlangsung di main thread yang aman.
Lokasi:
TaskManager.startAutoSaveTask()
Dampak:
Mencegah race condition saat YamlConfiguration.save() dan akses struktur data Bukkit.
Menghilangkan risiko corrupt file apartments.yml/guestbook/stats akibat penulisan dari thread non-main.
Rationale teknis

3. Rationale Teknsi
Semua task yang menyentuh Bukkit API (YamlConfiguration, Plugin logger, World/Player akses) kini berjalan di main thread. Task lain (income, daily update, confirmation cleanup) sudah berjalan synchronous sebelumnya.
Perubahan ini menyelesaikan masalah inti thread-safety tanpa memaksa refactor besar terhadap seluruh struktur data menjadi fully concurrent.
Saran opsional (tidak diubah saat ini)

4. Saran
Untuk menghindari potensi lag saat save, pertimbangkan pola “snapshot on main-thread + serialize di async dengan library non-Bukkit”. Namun karena ini memerlukan refactor lebih luas, perubahan saat ini fokus pada keamanan data terlebih dahulu.
Hasil akhir

5. Kesimpulan
Admin backup commands sekarang berfungsi: create/list/restore.
Auto-save berjalan aman di main thread sehingga mencegah race condition/korupsi file konfigurasi.

==================

Ringkasan Refactoring: Pemisahan Layanan Perintah Apartemen
Perubahan terbaru ini melibatkan refactoring utama "Extract Class" untuk meningkatkan struktur dan pemeliharaan kelas CommandHandler. Tujuan utamanya adalah untuk memisahkan logika penanganan perintah inti dari perutean dan pengaturan perintah.

Sebuah kelas baru, ApartmentCommandService, telah dibuat untuk memusatkan semua operasi perintah spesifik terkait apartemen.

Perubahan Kunci
Kelas Layanan Baru: Sebuah kelas baru, ApartmentCommandService, diperkenalkan. Kelas ini sekarang berisi semua logika bisnis untuk berbagai perintah, seperti handleInfoCommand(), handleBuyCommand(), handleRentCommand(), dan handleUpgradeCommand().

Delegasi: Kelas CommandHandler yang asli tidak lagi berisi logika langsung untuk mengeksekusi perintah. Sebaliknya, sekarang ia bertindak sebagai router, mendelegasikan semua perintah masuk ke metode yang sesuai di dalam ApartmentCommandService yang baru.

Alur Perintah Utama Tetap Sama: Titik masuk utama, logika cooldown, dan penyelesaian tab (tab completion) di CommandHandler tetap tidak berubah. Pengguna tidak akan melihat perbedaan dalam perilaku.

Pembersihan Kode: Metode helper pribadi asli di CommandHandler sekarang sudah usang dan dapat dihapus dengan aman, yang akan mengurangi duplikasi kode.

Refactoring ini berhasil memisahkan tanggung jawab, membuat kode lebih mudah dibaca, diuji, dan dipelihara di masa depan.

============================
File konfigurasi pesan bernama messages.yml telah dibuat dan diselesaikan. Ini adalah file siap pakai yang memungkinkan pemilik server untuk sepenuhnya menyesuaikan semua pesan yang dihasilkan oleh plugin, mulai dari notifikasi hingga pesan kesalahan.

Sorotan Utama
Format YAML Standar: File ini menggunakan format YAML yang valid dengan indentasi yang jelas dan komentar inline yang ekstensif, memudahkan pengeditan.

Dukungan Kode Warna: Mendukung kode warna Minecraft (&a, &c, &l) sehingga pesan dapat diformat dengan warna dan gaya yang berbeda.

Placeholder Dinamis: Pesan-pesan di dalamnya menggunakan placeholder seperti %player%, %amount%, %apartment%, dan lainnya, yang akan diganti secara otomatis dengan data yang relevan saat runtime.

Struktur Terorganisir
File ini diatur ke dalam beberapa kategori untuk navigasi yang mudah:

format: Mengatur prefiks pesan dan format tanggal.

general: Berisi pesan umum seperti cooldown, izin yang kurang, dan penggunaan perintah.

apartment: Mengatur semua pesan yang terkait dengan apartemen (membeli, menjual, mengatur nama, dll.).

rent, rating, list: Kategori khusus untuk pesan terkait sewa, sistem rating, dan daftar apartemen.

guestbook: Pesan untuk buku tamu, termasuk penggunaan dan format.

tax: Mengatur pesan untuk sistem pajak, termasuk faktur terperinci dan total.

notifications: Kumpulan pesan untuk siklus hidup tagihan pajak (baru, pengingat, terlambat, dll.).

admin: Pesan untuk perintah admin (membuat, menghapus, mencadangkan).

economy & errors: Pesan umum untuk masalah ekonomi dan kesalahan spesifik.

debug: Pesan terstruktur untuk konsistensi log.

File ini dirancang agar dapat langsung digunakan, memberikan fleksibilitas penuh kepada administrator server untuk menyesuaikan pengalaman pengguna tanpa perlu mengubah kode plugin.

=====================================
File konfigurasi plugin plugin.yml telah diperbaiki untuk mengatasi kesalahan skema dan memastikan validasinya sesuai dengan spesifikasi Spigot/Bukkit.

Perubahan Kunci
Penyelarasan Skema: File plugin.yml sekarang secara eksplisit mengarah ke skema JSON lokal yang baru dibuat (spigot-plugin.json). Ini dilakukan untuk mencegah konflik validasi dengan skema PocketMine yang secara otomatis terdeteksi oleh editor.

Normalisasi Izin: Nilai default untuk izin (permissions) telah dinormalisasi. Nilai-nilai ini sekarang menggunakan nilai Spigot yang valid seperti true, false, dan op tanpa tanda kutip. Misalnya, default: 'true' telah diperbaiki menjadi default: true.

Penyesuaian Struktur: Konfigurasi utama seperti name, version, main, dan api-version tetap konsisten dan valid untuk Spigot/Bukkit.

Masalah yang Diperbaiki
Kesalahan "Missing property 'api'": Kesalahan ini muncul karena validasi otomatis menggunakan skema PocketMine. Dengan mengarahkan plugin.yml ke skema Spigot yang benar, masalah ini telah teratasi.

Kesalahan "Value is not accepted": Kesalahan ini terjadi karena nilai default izin dikutip atau tidak sesuai dengan skema Spigot. Perbaikan ini memastikan semua nilai default sekarang sesuai.

Masalah Koneksi Skema: Menggunakan skema lokal juga menyelesaikan masalah saat editor tidak dapat memuat skema dari URL eksternal, sehingga validasi menjadi lebih andal.

Secara keseluruhan, plugin.yml sekarang tervalidasi dengan benar di editor, menggunakan skema dan nilai default yang sesuai dengan standar Spigot, menyelesaikan semua kesalahan skema yang ada.
=====================================

Perubahan-perubahan ini berfokus pada perbaikan error "ApartmentRating cannot be resolved to a type" dan meningkatkan kualitas kode secara keseluruhan berdasarkan saran dari code analyzer.

Penyebab dan Solusi Utama
Penyebab: Kelas ApartmentRating dan GuestBookEntry dideklarasikan sebagai tipe package-private di dalam file Apartment.java. Namun, keduanya dirujuk oleh API publik di ApartmentManager.java, yang menyebabkan error kompilasi dan peringatan "exporting non-public type".

Solusi:

Memindahkan ApartmentRating ke file publiknya sendiri: ApartmentRating.java.

Memindahkan GuestBookEntry ke file publiknya sendiri: GuestBookEntry.java.

Menghapus deklarasi lama untuk kedua kelas tersebut dari Apartment.java.

Perbaikan Kualitas Tambahan
Selain mengatasi masalah inti, beberapa perbaikan lain juga dilakukan untuk meningkatkan kebersihan dan keandalan kode:

Finalisasi Variabel: Variabel map internal di ApartmentManager.java sekarang dideklarasikan sebagai final untuk memenuhi saran analyzer "Field ... can be final".

Perbaikan Logging: Penggunaan String.format() sekarang digunakan untuk pesan logger yang kompleks, menggantikan string concatenation yang tidak efisien.

Pencegahan Null Pointer Exception (NPE): Ditambahkan null check pada logika pemuatan lokasi dan di pencari lokasi aman untuk mencegah error saat variabel world atau teleport-location tidak ada atau null.

Penggunaan Variabel yang Benar: Variabel economy yang sudah di-inject sekarang digunakan untuk menghitung pajak, menghilangkan peringatan "Variable economy is never read".

Hasil yang Diharapkan
Dengan perubahan ini, error kompilasi dan peringatan yang disebutkan seharusnya sudah teratasi. Kode sekarang lebih bersih, efisien, dan lebih aman dari potensi Null Pointer Exception.

=====================================
Secara keseluruhan, pembaruan ini berfokus pada perbaikan error kompilasi dan peningkatan kualitas kode. Masalah utama yaitu ApartmentRating cannot be resolved to a type berhasil diatasi dengan restrukturisasi yang lebih bersih dan sesuai standar.

Perubahan Kunci
Pemisahan Kelas: Kelas ApartmentRating dan GuestBookEntry yang sebelumnya dideklarasikan di dalam file Apartment.java telah dipindahkan menjadi file publiknya sendiri: ApartmentRating.java dan GuestBookEntry.java.

Pembersihan Apartment.java: Definisi kelas lama untuk ApartmentRating dan GuestBookEntry yang berada di Apartment.java telah dihapus. Ini menyelesaikan masalah Exporting non-public type through public API.

Peningkatan Kualitas di ApartmentManager.java:

Finalisasi Variabel: Variabel map internal sekarang bersifat final.

Penggunaan Variabel yang Benar: Logika pajak sekarang menggunakan field economy yang sudah di-inject dan tidak lagi memanggil plugin.getEconomy().

Pencegahan Error: Penambahan null-check pada logika teleport dan pencarian lokasi aman untuk mencegah Null Pointer Exception.

Logging yang Efisien: Mengganti string concatenation yang tidak efisien dengan String.format(...) untuk pesan log.

Kesimpulan
Perubahan ini tidak hanya menyelesaikan error cannot be resolved to a type dan duplicate class, tetapi juga meningkatkan stabilitas, efisiensi, dan kebersihan kode secara keseluruhan, membuat proyek lebih mudah dipelihara di masa depan.
=========================================
##Summary of Changes:

1. **Quality of Life**:
    - All relevant files (`plugin.yml`, `ApartmentCorei3.java`, etc.) have been updated to version 1.2.5.
    - changes to the code logic in several features that were already present in previous versions.
    - fixing several commands where tab completion does not work correctly

2. **Manual Teleport Location**:
    - A new command, `/apartmentcore setteleport`, allows owners to set the exact teleport point for their apartment.
    - The Apartment data model now stores this custom location.
    - The teleport logic prioritizes this custom location, falling back to the region's center if not set.

3. **Guest Book System**:
    - Players can now leave messages for apartment owners using `/apartmentcore guestbook leave <id> <message>`.
    - Owners can read and clear their guestbook with `/apartmentcore guestbook read <id>` and `/apartmentcore guestbook clear <id>`.
    - Guestbook data is stored in a new `guestbook.yml` file.
    - New configuration options for message limits and cooldowns have been added to `config.yml`.

4. **Real-Time Countdowns**:
    - The output of `/ac info`, `/ac rent info`, and `/ac tax info` now includes real-time countdowns for the next income deposit and next tax due date.
    - New PlaceholderAPI placeholders have been added:
        - `%apartmentcore_<id>_tax_due_in%`
        - `%apartmentcore_<id>_income_in%`

5. NewCode & Configuration Enhancements:
    - New permissions for the added features have been included in `plugin.yml`.
    - `config.yml` has a new guestbook section for customization.
    - Command handlers and tab-completion have been updated for the new commands.



