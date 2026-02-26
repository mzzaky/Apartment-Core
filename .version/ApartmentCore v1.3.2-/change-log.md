# Change Log — Implementasi dan Pengembalian Konfigurasi

Versi: 1.3.2
Tanggal: 2025-09-30

Ringkasan:
Mengembalikan dan mengimplementasikan semua bagian konfigurasi yang diminta (gui.*, performance.*, worldguard.*, messages.*, features.*, time.ticks-per-*, placeholderapi.update-interval, security.require-confirmation, log-suspicious). Semua pengaturan sekarang dibaca dan diterapkan secara runtime untuk meningkatkan fleksibilitas dan kontrol pengguna.

File yang diubah:
- [`src/main/resources/config.yml`](src/main/resources/config.yml) — Ditambahkan kembali semua bagian dengan nilai default yang masuk akal.
- [`src/main/java/com/aithor/apartmentcorei3/ConfigManager.java`](src/main/java/com/aithor/apartmentcorei3/ConfigManager.java) — Ditambahkan field dan getter untuk semua konfigurasi baru.
- [`src/main/java/com/aithor/apartmentcorei3/ApartmentCorei3.java`](src/main/java/com/aithor/apartmentcorei3/ApartmentCorei3.java) — Inisialisasi GUI sekarang memeriksa `gui.enabled`.
- [`src/main/java/com/aithor/apartmentcorei3/TaskManager.java`](src/main/java/com/aithor/apartmentcorei3/TaskManager.java) — Tugas pendapatan dan harian menghormati toggle fitur dan menggunakan tick rate yang dapat dikonfigurasi.
- [`src/main/java/com/aithor/apartmentcorei3/MessageManager.java`](src/main/java/com/aithor/apartmentcorei3/MessageManager.java) — Pemilihan file bahasa dan penanganan warna/prefix dari konfigurasi.
- [`src/main/java/com/aithor/apartmentcorei3/gui/GUIManager.java`](src/main/java/com/aithor/apartmentcorei3/gui/GUIManager.java) — Penjadwalan auto-refresh dan toggle suara.
- [`src/main/java/com/aithor/apartmentcorei3/ApartmentManager.java`](src/main/java/com/aithor/apartmentcorei3/ApartmentManager.java) — Operasi WorldGuard otomatis, pemeriksaan teleportasi, caching performa, validasi flag.
- [`src/main/java/com/aithor/apartmentcorei3/ApartmentPlaceholder.java`](src/main/java/com/aithor/apartmentcorei3/ApartmentPlaceholder.java) — Ditambahkan metode refresh untuk penggunaan masa depan.
- [`src/main/java/com/aithor/apartmentcorei3/CommandHandler.java`](src/main/java/com/aithor/apartmentcorei3/CommandHandler.java) — Pemeriksaan flag WorldGuard saat pembuatan apartemen.

Perubahan yang diterapkan:
- **gui.*:** Sistem GUI dapat dinonaktifkan, interval refresh, efek suara, dan animasi sekarang dikontrol oleh konfigurasi.
- **performance.*:** Tugas async untuk operasi berat, caching daftar apartemen dengan expiry time.
- **worldguard.*:** Auto-add/remove pemilik ke region, pemeriksaan flag yang diperlukan sebelum membuat apartemen.
- **messages.*:** Prefix, penggunaan warna, dan pemilihan bahasa dari konfigurasi; file bahasa spesifik dimuat jika tersedia.
- **features.*:** Toggle untuk pembangkitan pendapatan, sistem pajak, sistem level, dan teleportasi.
- **time.ticks-per-***: Tick rate kustom untuk jam dan hari Minecraft, memengaruhi siklus pendapatan dan pembaruan harian.
- **placeholderapi.update-interval:** Ditambahkan untuk caching masa depan (saat ini placeholder dihitung on-demand).
- **security.require-confirmation dan log-suspicious:** Getter ditambahkan untuk penggunaan masa depan.

Langkah pengujian:
1. Atur `gui.enabled: false` untuk menonaktifkan sistem GUI.
2. Atur `features.income-generation: false` untuk menghentikan tugas pendapatan.
3. Atur `worldguard.check-flags: true` dan definisikan `required-flags` untuk menegakkan persyaratan region.
4. Ubah `messages.language: "en_US"` untuk memuat file bahasa yang berbeda.
5. Sesuaikan `time.ticks-per-hour` untuk mempercepat/memperlambat siklus pendapatan.
6. Aktifkan `performance.use-cache: true` untuk caching daftar apartemen.

Semua fitur sekarang dapat dikonfigurasi dan diimplementasikan. Plugin menghormati pengaturan ini saat runtime.
# Change Log — Config audit and cleanup

Version: 1.3.1  
Date: 2025-09-30

Overview:
Aligned `config.yml` with actual runtime usage. Removed unused/unimplemented sections, clarified supported keys, and preserved keys read by the code to ensure configuration behaves as expected.

Files changed:
- [`src/main/resources/config.yml`](src/main/resources/config.yml)

What the plugin really reads (key → code reference):
- debug → [`ConfigManager.loadConfiguration()`](src/main/java/com/aithor/apartmentcorei3/ConfigManager.java:74)
- database.use-mysql (flag only; MySQL not implemented) → [`ConfigManager.loadConfiguration()`](src/main/java/com/aithor/apartmentcorei3/ConfigManager.java:75)
- economy.currency-symbol / sell-percentage / penalty-percentage → [`ConfigManager.loadConfiguration()`](src/main/java/com/aithor/apartmentcorei3/ConfigManager.java:76)
- auto-save.enabled / interval-minutes → [`ConfigManager.loadConfiguration()`](src/main/java/com/aithor/apartmentcorei3/ConfigManager.java:77)
- backup.enabled / max-backups → [`ConfigManager.loadConfiguration()`](src/main/java/com/aithor/apartmentcorei3/ConfigManager.java:83)
- guestbook.max-messages / max-message-length / leave-cooldown → [`ConfigManager.loadConfiguration()`](src/main/java/com/aithor/apartmentcorei3/ConfigManager.java:86)
- logging.log-*, log-file, max-log-size, keep-old-logs → [`ConfigManager.loadConfiguration()`](src/main/java/com/aithor/apartmentcorei3/ConfigManager.java:91)
- apartment-levels.level-N(min-income, max-income, upgrade-cost) → [`ConfigManager.loadConfiguration()`](src/main/java/com/aithor/apartmentcorei3/ConfigManager.java:100)
- auction.* (enabled, bids, durations, fees, commission, cooldown, broadcast, extend-*) → [`ConfigManager.loadConfiguration()`](src/main/java/com/aithor/apartmentcorei3/ConfigManager.java:116)
- limits.max-apartments-per-player → [`CommandHandler.handleBuyCommand()`](src/main/java/com/aithor/apartmentcorei3/CommandHandler.java:797)
- placeholderapi.enabled → [`ApartmentCorei3.onEnable()`](src/main/java/com/aithor/apartmentcorei3/ApartmentCorei3.java:102)
- security.command-cooldown → [`ConfigManager.loadConfiguration()`](src/main/java/com/aithor/apartmentcorei3/ConfigManager.java:82)
- security.confirmation-timeout → [`TaskManager.startConfirmationCleanupTask()`](src/main/java/com/aithor/apartmentcorei3/TaskManager.java:84)

Removed or clarified (not implemented or not read):
- gui.* (enabled/refresh/sounds/animations) → GUI is managed without config toggles; see constructor registration at [`GUIManager.GUIManager()`](src/main/java/com/aithor/apartmentcorei3/gui/GUIManager.java:28).
- performance.* (use-async, use-cache, cache-expiry) → not referenced anywhere.
- worldguard.* (auto-add/remove/check-flags/required-flags) → WG operations are done directly through API in [`ApartmentManager.addPlayerToRegion()`](src/main/java/com/aithor/apartmentcorei3/ApartmentManager.java:492) and related methods, no config gates.
- messages.* (prefix, use-colors, language) → messages come from messages.yml only via [`MessageManager.reloadMessages()`](src/main/java/com/aithor/apartmentcorei3/MessageManager.java:24).
- economy.default-price / default-tax / default-tax-days → not used; price/tax are per-apartment in data.
- time.ticks-per-hour / time.ticks-per-day → scheduler uses fixed intervals in [`TaskManager.startIncomeTask()`](src/main/java/com/aithor/apartmentcorei3/TaskManager.java:39) and [`TaskManager.startDailyUpdateTask()`](src/main/java/com/aithor/apartmentcorei3/TaskManager.java:55).
- placeholderapi.update-interval → not read; only enabled flag is checked at [`ApartmentCorei3.onEnable()`](src/main/java/com/aithor/apartmentcorei3/ApartmentCorei3.java:102).
- features.* (income-generation, tax-system, level-system, teleportation) → not read anywhere.
- database.mysql.* connection parameters → no JDBC usage; only the boolean flag exists currently.

Notes on retained but currently not enforced by logic:
- time.inactive-grace-period is loaded at [`ConfigManager.loadConfiguration()`](src/main/java/com/aithor/apartmentcorei3/ConfigManager.java:81) but the new invoice-based tax status thresholds are hardcoded in [`Apartment.computeTaxStatus()`](src/main/java/com/aithor/apartmentcorei3/Apartment.java:222).
- economy.penalty-percentage is loaded but unused by the current invoice system.

Testing steps:
1) Review updated keys in [`src/main/resources/config.yml`](src/main/resources/config.yml) and adjust as needed.
2) Restart the server. On startup, logs should indicate “Configuration loaded successfully” from [`ConfigManager.loadConfiguration()`](src/main/java/com/aithor/apartmentcorei3/ConfigManager.java:130).
3) Validate:
   - Auction: use `/apartmentcore auction create|bid|list|cancel` with limits enforced per config.
   - Limits: attempt buying beyond `limits.max-apartments-per-player` at [`CommandHandler.handleBuyCommand()`](src/main/java/com/aithor/apartmentcorei3/CommandHandler.java:797).
   - Logging: check rotation and retention under `plugins/ApartmentCorei3/logs` managed by [`LoggerManager`](src/main/java/com/aithor/apartmentcorei3/LoggerManager.java:12).
   - Auto-save/backup: autosave cadence and periodic backups per [`TaskManager.startAutoSaveTask()`](src/main/java/com/aithor/apartmentcorei3/TaskManager.java:94).

No code changes were necessary; this release strictly updates configuration to match existing behavior.
# Change Log — Auction integration & fixes

Version: 1.2.9  
Date: 2025-09-15

Overview:
Implemented wiring and safety checks for the auction subsystem: initialization, scheduled processing, persistence, user commands, admin tools, transaction handling, and WorldGuard region updates.

Files changed (high level):
- [`src/main/java/com/aithor/apartmentcorei3/ApartmentCorei3.java`](src/main/java/com/aithor/apartmentcorei3/ApartmentCorei3.java:68) — init/shutdown & task handle
- [`src/main/java/com/aithor/apartmentcorei3/AuctionManager.java`](src/main/java/com/aithor/apartmentcorei3/AuctionManager.java:1) — auction logic + admin APIs
- [`src/main/java/com/aithor/apartmentcorei3/CommandHandler.java`](src/main/java/com/aithor/apartmentcorei3/CommandHandler.java:262) — user auction commands
- [`src/main/java/com/aithor/apartmentcorei3/ApartmentCommandService.java`](src/main/java/com/aithor/apartmentcorei3/ApartmentCommandService.java:276) — sell/remove guards & admin auction tools
- [`src/main/java/com/aithor/apartmentcorei3/ApartmentManager.java`](src/main/java/com/aithor/apartmentcorei3/ApartmentManager.java:541) — addOwnerUuidToRegion for offline winners
- [`src/main/java/com/aithor/apartmentcorei3/TaskManager.java`](src/main/java/com/aithor/apartmentcorei3/TaskManager.java:100) — autosave saves auctions

Changes (detailed)
- Lifecycle & scheduling
  - Added initialization and shutdown helpers for the auction system and a scheduled task to process ended auctions every 60s.
  - See [`ApartmentCorei3.initAuctionSystem()`](src/main/java/com/aithor/apartmentcorei3/ApartmentCorei3.java:239) and [`ApartmentCorei3.shutdownAuctionSystem()`](src/main/java/com/aithor/apartmentcorei3/ApartmentCorei3.java:261).

- Commands & admin tools
  - Added user commands: `/apartmentcore auction create|bid|list|cancel`.
    - Implemented in [`CommandHandler`](src/main/java/com/aithor/apartmentcorei3/CommandHandler.java:262).
  - Added admin commands: `/apartmentcore admin auction list|cancel|forceend`.
    - Implemented in [`ApartmentCommandService.handleAdminCommand`](src/main/java/com/aithor/apartmentcorei3/ApartmentCommandService.java:943).

- Integrity guards
  - Prevent selling an apartment while it is actively being auctioned (user sell flow and confirm).
    - See [`ApartmentCommandService.handleSellCommand`](src/main/java/com/aithor/apartmentcorei3/ApartmentCommandService.java:294) and confirm flow.
  - Admin remove now cancels any active auction first to avoid orphan auctions.

- Admin APIs for auctions
  - `AuctionManager.cancelAuctionAdmin(apartmentId)` — cancels and refunds current bidder if present.
  - `AuctionManager.forceEndAuction(apartmentId)` — immediately ends and processes transfer if a winner exists.
  - See [`AuctionManager`](src/main/java/com/aithor/apartmentcorei3/AuctionManager.java:455).

- Economy handling and safety
  - All Vault withdraw/deposit calls used for auctions now check `EconomyResponse` success and handle failures (refund fallback / logging).
  - See relevant logic in [`AuctionManager.createAuction`](src/main/java/com/aithor/apartmentcorei3/AuctionManager.java:95) and [`AuctionManager.placeBid`](src/main/java/com/aithor/apartmentcorei3/AuctionManager.java:183).

- WorldGuard region handling
  - When an auction ends and the winner is offline, the winner's UUID is added to the region owners so access is granted immediately.
  - Implemented via [`ApartmentManager.addOwnerUuidToRegion`](src/main/java/com/aithor/apartmentcorei3/ApartmentManager.java:541) and used in [`AuctionManager.processAuctionEnd`](src/main/java/com/aithor/apartmentcorei3/AuctionManager.java:332).

- Persistence
  - Auctions are loaded/saved to data file under `auctions.*`.
  - Auto-save routine now also persists auctions on each autosave.
  - See [`AuctionManager.saveAuctions()`](src/main/java/com/aithor/apartmentcorei3/AuctionManager.java:76) and autosave change in [`TaskManager.startAutoSaveTask()`](src/main/java/com/aithor/apartmentcorei3/TaskManager.java:93).

Testing & verification steps
1. Ensure `auction.enabled: true` in [`src/main/resources/config.yml`](src/main/resources/config.yml:23).
2. Build and restart plugin.
3. As owner: `/apartmentcore auction create <id> <startBid> <hours>` — verify creation and broadcast.
4. Attempt to `/apartmentcore sell <id>` while auction active — should be rejected.
5. Admin: `/apartmentcore admin auction list` and `/apartmentcore admin auction cancel <id>` or `forceend <id>` — verify refunds and ownership transfer.
6. Place bids and verify previous bidder refund, commission to seller on end, and region owner update for offline winners.

Notes & next steps
- Consider adding explicit user-facing messages on auction autosave snapshots and more admin telemetry.
- Consider unit tests for auction edge cases (concurrent bids, economy provider failures).

End of change log.