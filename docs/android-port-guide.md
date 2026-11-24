# Panduan Port Android ARM64 — Drakengard 3 Recompiled

## 1. Prasyarat File Game (Milik Sendiri)
Repo ini **tidak menyertakan** aset game atau firmware PS3.
- Untuk **build Full Standalone APK** di GitHub Actions CI, Anda membutuhkan:
  1. `EBOOT_101.ELF` — executable update 1.01 yang sudah didekripsi (26.872.424 bytes, SHA-256 diawali `ed89e80f...`).
  2. `libsre.prx` — modul firmware PS3 4.91 (`dev_flash/sys/external/libsre.sprx` yang sudah didekripsi via RPCS3).
- Untuk **bermain di HP Android** (Android 10+, Snapdragon Adreno direkomendasikan, RAM ≥ 8 GB, penyimpanan kosong ~16 GB):
  - Salin folder `game/disc/` (BLUS31197 + update 1.01) dan `elf/EBOOT_101.ELF` ke `/storage/emulated/0/Dod3Recomp/`, atau gunakan dialog **Allow Storage / Import File** di layar awal aplikasi.

## 2. Mengaktifkan Build Full Standalone di GitHub Actions
1. Buat repositori GitHub **private** baru, lalu hubungkan ke repo lokal ini.
2. Enkripsi `EBOOT_101.ELF` dan `libsre.prx`:
   ```bash
   PS3_CI_KEY="kata-sandi-kuat-anda" bash tools/encrypt_ps3_for_ci.sh /path/to/EBOOT_101.ELF /path/to/libsre.prx
   git add ci/EBOOT_101.ELF.enc ci/libsre.prx.enc
   git commit -m "ci: add encrypted EBOOT_101.ELF and libsre.prx"
   git push
   ```
3. Tambahkan **GitHub Actions Secret** di *Settings → Secrets and variables → Actions*:
   - `PS3_CI_KEY` (wajib untuk `full-standalone`)
   - Opsional untuk release signing: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASS`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASS`.
4. Jalankan workflow **Build & Release Android APK (ARM64)** dari tab Actions:
   - Pilih `build_mode: runtime-smoke` untuk menguji SDL2 + Vulkan 1.1–1.3 bring-up di HP Snapdragon terlebih dahulu.
   - Pilih `build_mode: full-standalone` (atau `auto` setelah file `.enc` + secret terpasang) untuk menghasilkan APK penuh.

## 3. Diagnostik & Debugging via ADB
Pantau seluruh log runtime, probe Vulkan, dan status lifter/game dari komputer:
```bash
adb logcat -s Dod3Android SDL
```
Atau buka tombol **Diagnostics** pada dialog awal aplikasi untuk melihat:
- Mode build (`RUNTIME SMOKE` vs `FULL STANDALONE`)
- Host page size (`4096` atau `16384` bytes)
- Versi Vulkan Loader, nama GPU Adreno, dukungan `BCn`, `D32S8`/`D24S8`, `KHR_dynamic_rendering`, dan `KHR_timeline_semaphore`.
