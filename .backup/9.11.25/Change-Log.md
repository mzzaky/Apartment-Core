v1.2.8
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



Perbaikan diterapkan sesuai feedback.

Perubahan yang dibuat

Implementasi penuh Admin backup commands
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

Fix isu KRITIS — Thread-safety: meniadakan save async di auto-save
Mengubah auto-save dari async ke main thread:
Dari runTaskTimerAsynchronously → runTaskTimer agar semua operasi Bukkit/YAML berlangsung di main thread yang aman.
Lokasi:
TaskManager.startAutoSaveTask()
Dampak:
Mencegah race condition saat YamlConfiguration.save() dan akses struktur data Bukkit.
Menghilangkan risiko corrupt file apartments.yml/guestbook/stats akibat penulisan dari thread non-main.
Rationale teknis

Semua task yang menyentuh Bukkit API (YamlConfiguration, Plugin logger, World/Player akses) kini berjalan di main thread. Task lain (income, daily update, confirmation cleanup) sudah berjalan synchronous sebelumnya.
Perubahan ini menyelesaikan masalah inti thread-safety tanpa memaksa refactor besar terhadap seluruh struktur data menjadi fully concurrent.
Saran opsional (tidak diubah saat ini)

Untuk menghindari potensi lag saat save, pertimbangkan pola “snapshot on main-thread + serialize di async dengan library non-Bukkit”. Namun karena ini memerlukan refactor lebih luas, perubahan saat ini fokus pada keamanan data terlebih dahulu.
Hasil akhir

Admin backup commands sekarang berfungsi: create/list/restore.
Auto-save berjalan aman di main thread sehingga mencegah race condition/korupsi file konfigurasi.


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



