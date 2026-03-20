# ApartmentCore – Perbandingan Edisi Free vs Pro

## Ringkasan

ApartmentCore tersedia dalam dua edisi yang dikompilasi dari **satu codebase yang sama** menggunakan Maven profiles. Tidak perlu mengelola dua repository terpisah.

| Fitur | Free | Pro |
|-------|------|-----|
| **JAR Output** | `ApartmentCore-Free-x.x.x.jar` | `ApartmentCore-Pro-x.x.x.jar` |
| **Distribusi** | SpigotMC / Modrinth (gratis) | Pasman / BuiltByBit (berbayar) |
| **Lisensi** | Tidak diperlukan | License key diperlukan |
| **Maksimal Apartment** | 20 apartment | Unlimited |
| **Maksimal Level** | Level 5 | Unlimited (sesuai config) |
| **Research System** | Tersedia (nilai hardcode, tidak bisa diedit) | Tersedia & bisa dikustomisasi via `research.yml` |
| **Shop System** | Bawaan (tidak bisa diedit) | Bisa dikustomisasi via `shop.yml` |
| **Achievement System** | Bawaan (tidak bisa diedit) | Bisa dikustomisasi via `achievements.yml` |
| **Custom GUI** | Bawaan (tidak bisa diedit) | Bisa dikustomisasi via `custom_gui/main_menu.yml` |
| **Logging** | Tidak tersedia | Tersedia via `config.yml` |
| **Backup** | Tidak tersedia | Tersedia via `config.yml` |
| **Discord Role** | Tidak tersedia | Tersedia |
| **Priority Support** | Tidak tersedia | Tersedia |

---

## Detail Fitur Free Edition

### Batasan
- **Maksimal 20 apartment** dapat didaftarkan di server
- **Maksimal level apartment: 5** — upgrade di atas level 5 tidak tersedia
- **Research system** tetap aktif, namun menggunakan **nilai hardcode** (cost, duration, dan effect tidak bisa diubah) — file `research.yml` **tidak** di-generate ke folder plugin
- **File konfigurasi** (`shop.yml`, `achievements.yml`, `custom_gui/main_menu.yml`) **tidak** di-generate ke folder plugin — konfigurasi bawaan dari JAR yang digunakan
- **Logging dan backup** fitur dinonaktifkan

### Yang Tetap Tersedia
- Semua fitur dasar apartment (beli, jual, teleport, upgrade sampai level 5)
- Sistem income & tax
- Auction house
- Shop system (dengan konfigurasi bawaan)
- Achievement system (dengan konfigurasi bawaan)
- Research system (dengan konfigurasi bawaan/hardcode)
- GUI lengkap
- PlaceholderAPI support
- Guestbook system

---

## Detail Fitur Pro Edition

### Keunggulan
- **Unlimited apartment** — tidak ada batasan jumlah apartment
- **Unlimited level** — tambahkan level sebanyak yang diinginkan di `config.yml`
- **Research system** aktif dan bisa dikustomisasi melalui `research.yml` — cost, duration, dan effect tiap research dapat disesuaikan
- **Shop system** bisa dikustomisasi melalui `shop.yml`
- **Achievement system** bisa dikustomisasi melalui `achievements.yml`
- **Custom GUI** bisa dikustomisasi melalui `custom_gui/main_menu.yml`
- **Logging** — log transaksi dan aksi admin ke file
- **Backup** — backup otomatis dan manual via command
- **Discord role** — integrasi untuk role Discord
- **Priority support** — dukungan prioritas dari developer

---

## Cara Build

### Build Free Edition (Default)
```bash
mvn clean package
```
Output: `target/ApartmentCore-Free-1.3.8.jar`

### Build Pro Edition
```bash
mvn clean package -Pedition-pro
```
Output: `target/ApartmentCore-Pro-1.3.8.jar`

---

## Arsitektur Edisi

```
pom.xml
├── Profile: edition-free (default)
│   └── apartmentcore.edition = FREE
└── Profile: edition-pro
    └── apartmentcore.edition = PRO

plugin.yml
└── edition: '${apartmentcore.edition}'  ← di-inject saat build

ApartmentCore.java (onEnable)
├── Baca edition dari plugin.yml
├── Inisialisasi EditionManager
├── [Pro] Inisialisasi LicenseManager → validasi ke Cloudflare Worker
├── [Pro] Inisialisasi LoggerManager
├── Inisialisasi ResearchManager (Free: hardcode, Pro: baca research.yml)
└── Semua fitur lain: cek EditionManager sebelum aksi

EditionManager.java
├── getMaxApartments()        → Free: 20, Pro: unlimited
├── getMaxLevel()             → Free: 5, Pro: unlimited
├── isResearchCustomisable()  → Free: false, Pro: true
├── isLoggingEnabled()        → Free: false, Pro: true
├── isBackupEnabled()         → Free: false, Pro: true
└── sendProOnlyMessage()      → Pesan ke player

ResearchManager.java (loadResearchConfig)
├── [Free] Hardcode semua nilai (cost-base, duration, effect) → tidak baca/buat research.yml
└── [Pro]  Baca/buat research.yml dari data folder seperti biasa
```
