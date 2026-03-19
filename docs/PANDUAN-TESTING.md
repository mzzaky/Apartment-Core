# Panduan Testing – ApartmentCore Free & Pro

Dokumen ini menjelaskan langkah-langkah untuk melakukan testing edisi Free dan Pro dari ApartmentCore.

---

## Persiapan Environment Testing

### 1. Siapkan Server Testing
1. Instal server Spigot/Paper 1.21+
2. Instal plugin **Vault** + Economy provider (contoh: EssentialsX)
3. Instal plugin **WorldGuard** + **WorldEdit**
4. Buat beberapa region WorldGuard untuk testing apartment

### 2. Build Kedua Edisi

```bash
# Build Free Edition
mvn clean package
# Output: target/ApartmentCore-Free-1.3.8.jar

# Build Pro Edition
mvn clean package -Pedition-pro
# Output: target/ApartmentCore-Pro-1.3.8.jar
```

---

## Testing Free Edition

### Test 1: Verifikasi Edisi
1. Pasang `ApartmentCore-Free-1.3.8.jar` di folder `plugins/`
2. Start server
3. **Verifikasi:**
   - Console menampilkan `» Edition  Free`
   - Console menampilkan "Free Edition Limits" di splash banner
   - Tidak ada pesan error lisensi

### Test 2: Batas Apartment (Maks 20)
1. Buat 20 apartment:
   ```
   /ac admin create region1 apt1 1000
   /ac admin create region2 apt2 1000
   ... (sampai 20)
   ```
2. Coba buat apartment ke-21:
   ```
   /ac admin create region21 apt21 1000
   ```
3. **Verifikasi:** Pesan error "Maximum apartment limit reached (20)"

### Test 3: Batas Level (Maks 5)
1. Buat apartment dan set level ke 5:
   ```
   /ac admin set level apt1 5
   ```
2. Coba upgrade ke level 6:
   ```
   /ac upgrade apt1 confirm
   ```
3. **Verifikasi:** Pesan error tentang batas level Free edition

### Test 4: Research System Disabled
1. Buka GUI:
   ```
   /ac gui
   ```
2. Klik menu "Research"
3. **Verifikasi:** Pesan "Research System feature is only available in ApartmentCore Pro"

### Test 5: File Konfigurasi Tidak Di-generate
1. Periksa folder `plugins/ApartmentCore/`
2. **Verifikasi:**
   - `shop.yml` **TIDAK ADA** di folder plugin
   - `achievements.yml` **TIDAK ADA** di folder plugin
   - `research.yml` **TIDAK ADA** di folder plugin
   - `custom_gui/main_menu.yml` **TIDAK ADA** di folder plugin
   - `config.yml` **ADA** (selalu tersedia)
   - `messages.yml` **ADA** (selalu tersedia)

### Test 6: Backup Command Disabled
1. Jalankan:
   ```
   /ac admin backup create
   ```
2. **Verifikasi:** Pesan "Backup feature is only available in ApartmentCore Pro"

### Test 7: Fitur Dasar Berfungsi
1. **Beli apartment:** `/ac buy apt1` → harus berhasil
2. **Jual apartment:** `/ac sell apt1` → harus berhasil
3. **Teleport:** `/ac teleport apt1` → harus berhasil
4. **Upgrade (sampai level 5):** `/ac upgrade apt1 confirm` → harus berhasil
5. **GUI:** `/ac gui` → harus terbuka dengan semua menu dasar
6. **Auction:** `/ac auction list` → harus berfungsi
7. **Guestbook:** `/ac guestbook leave apt1 Hello!` → harus berfungsi
8. **Achievement:** Verifikasi achievement tracking masih berfungsi

---

## Testing Pro Edition

### Test 1: Verifikasi Edisi
1. Ganti JAR ke `ApartmentCore-Pro-1.3.8.jar`
2. Start server
3. **Verifikasi:**
   - Console menampilkan `» Edition  Pro`
   - Console menampilkan "License Manager" di module list

### Test 2: Lisensi Tanpa Key
1. Biarkan `license.key` kosong di `config.yml`
2. Start server
3. **Verifikasi:**
   - Console: "License: INVALID - No license key configured"
   - Console: "Pro features are DISABLED. The plugin will run in Free mode."
   - Plugin berjalan dengan batasan Free

### Test 3: Lisensi Dengan Key Valid
1. Siapkan infrastruktur:
   - Deploy Supabase schema (`infrastructure/supabase/schema.sql`)
   - Deploy Cloudflare Worker (`infrastructure/cloudflare-worker/`)
   - Generate license key via Supabase: `SELECT * FROM create_license('test@email.com', 'Test User');`
2. Update `LicenseManager.java` → ganti `VALIDATE_URL` dengan URL worker Anda
3. Rebuild Pro edition
4. Masukkan license key di `config.yml`
5. Start server
6. **Verifikasi:**
   - Console: "License: VALID - License validated successfully."
   - Semua fitur Pro aktif

### Test 4: Unlimited Apartments (Pro)
1. Buat lebih dari 20 apartment
2. **Verifikasi:** Tidak ada batas — semua berhasil dibuat

### Test 5: Unlimited Levels (Pro)
1. Tambahkan level 6-10 di `config.yml`:
   ```yaml
   apartment-levels:
     level-6:
       min-income: 600
       max-income: 700
       upgrade-cost: 7000
       upgrade-duration: 10080000
       income-capacity: 60000
       tax-percentage: 15.0
   ```
2. Upgrade apartment ke level 6+
3. **Verifikasi:** Upgrade berhasil tanpa batas

### Test 6: Research System (Pro)
1. Buka GUI → Research
2. **Verifikasi:** Research GUI terbuka dan berfungsi
3. Mulai research → verifikasi timer berjalan
4. Edit `research.yml` → reload → verifikasi perubahan diterapkan

### Test 7: File Konfigurasi Di-generate (Pro)
1. Periksa folder `plugins/ApartmentCore/`
2. **Verifikasi:**
   - `shop.yml` **ADA** dan bisa diedit
   - `achievements.yml` **ADA** dan bisa diedit
   - `research.yml` **ADA** dan bisa diedit
   - `custom_gui/main_menu.yml` **ADA** dan bisa diedit
3. Edit setiap file → `/ac admin reload` → verifikasi perubahan diterapkan

### Test 8: Logging & Backup (Pro)
1. Aktifkan logging di `config.yml`
2. Lakukan transaksi (beli/jual apartment)
3. **Verifikasi:** File log tertulis di `logs/apartmentcore.log`
4. Jalankan backup:
   ```
   /ac admin backup create
   ```
5. **Verifikasi:** Backup berhasil dibuat

---

## Testing Infrastruktur Lisensi

### Setup Supabase
1. Buat project baru di [supabase.com](https://supabase.com)
2. Buka **SQL Editor**
3. Jalankan script `infrastructure/supabase/schema.sql`
4. Verifikasi tabel terbuat:
   - `licenses`
   - `license_activations`
   - `validation_log`
5. Generate test license:
   ```sql
   SELECT * FROM create_license('test@example.com', 'Test User', 2);
   ```
6. Catat license key yang dihasilkan

### Setup Cloudflare Worker
1. Instal Wrangler CLI:
   ```bash
   npm install -g wrangler
   ```
2. Login ke Cloudflare:
   ```bash
   wrangler login
   ```
3. Masuk ke folder worker:
   ```bash
   cd infrastructure/cloudflare-worker
   ```
4. Set secrets:
   ```bash
   npx wrangler secret put SUPABASE_URL
   # Masukkan: https://your-project.supabase.co

   npx wrangler secret put SUPABASE_SERVICE_KEY
   # Masukkan: service_role key dari Supabase dashboard
   ```
5. Deploy:
   ```bash
   npx wrangler deploy
   ```
6. Test endpoint:
   ```bash
   # Health check
   curl https://apartmentcore-license.your-subdomain.workers.dev/api/health

   # Validasi
   curl -X POST https://apartmentcore-license.your-subdomain.workers.dev/api/validate \
     -H "Content-Type: application/json" \
     -d '{"license_key":"ACPRO-XXXX-XXXX-XXXX-XXXX","server_ip":"127.0.0.1","server_port":25565,"server_id":"test-server","plugin_version":"1.3.8"}'
   ```
7. **Verifikasi:**
   - Health check mengembalikan `{"status":"ok"}`
   - Validasi dengan key valid → `{"valid":true,"status":"active"}`
   - Validasi dengan key salah → `{"valid":false,"status":"invalid"}`

### Test Offline Mode
1. Start server dengan lisensi valid (pastikan tervalidasi)
2. Matikan server
3. Blokir akses internet keluar (atau ubah `VALIDATE_URL` ke URL yang salah)
4. Start server lagi
5. **Verifikasi:**
   - Console: "License: GRACE PERIOD"
   - Fitur Pro masih aktif
6. Ubah tanggal cache > 7 hari (edit `.license_cache`)
7. Start server lagi
8. **Verifikasi:**
   - Console: "License: ERROR - Cached validation expired"
   - Plugin berjalan dalam mode Free

---

## Checklist Testing Lengkap

### Free Edition
- [ ] Splash banner menampilkan "Free" dan batasan
- [ ] Maks 20 apartment — pesan error saat melebihi
- [ ] Maks level 5 — pesan error saat upgrade lebih tinggi
- [ ] Research menu menampilkan "Pro only"
- [ ] Backup command menampilkan "Pro only"
- [ ] File config Pro tidak di-generate
- [ ] Semua fitur dasar berfungsi normal
- [ ] Tidak ada pesan error di console

### Pro Edition
- [ ] Splash banner menampilkan "Pro" dan License Manager
- [ ] Unlimited apartments
- [ ] Unlimited levels
- [ ] Research system aktif
- [ ] Semua file config di-generate dan bisa diedit
- [ ] Logging berfungsi
- [ ] Backup berfungsi
- [ ] Lisensi valid → semua fitur aktif
- [ ] Lisensi invalid → fallback ke Free
- [ ] Mode offline → grace period 7 hari
- [ ] Grace period habis → fallback ke Free

### Infrastruktur
- [ ] Supabase schema terdeploy tanpa error
- [ ] Cloudflare Worker merespons health check
- [ ] Validasi key valid berhasil
- [ ] Validasi key invalid ditolak
- [ ] Validasi key expired ditolak
- [ ] Batas server terdeteksi
- [ ] Log validasi tercatat di `validation_log`
