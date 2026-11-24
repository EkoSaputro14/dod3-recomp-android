# Drakengard 3 Recompiled — Android ARM64 (Snapdragon Vulkan 1.1–1.3)

Porting tidak resmi **Drakengard 3 (BLUS31197 / v1.01)** ke **Android `arm64-v8a`**, dibangun di atas [`soyjxck/dod3-recomp`](https://github.com/soyjxck/dod3-recomp) dan [`soyjxck/ps3recomp`](https://github.com/soyjxck/ps3recomp) dengan wrapper Android bergaya `genma-recomp-android`.

## Fitur Fase 1 MVP
- **Target GPU Utama**: Qualcomm Snapdragon (Adreno) pada Android 10–14+ (`minSdk = 29`, `arm64-v8a`, 16 KB page-aligned).
- **Negosiasi Vulkan 1.1 / 1.2 / 1.3**:
  - Otomatis memilih API tertinggi (`1.3 -> 1.2 -> 1.1`).
  - Fallback `VkRenderPass` + `VkFramebuffer` cache bila `dynamicRendering` tidak tersedia.
  - Fallback `VkFence` ring bila `timelineSemaphore` tidak tersedia.
  - Fallback static pipeline topology bila `extendedDynamicState` tidak tersedia.
  - Fallback depth `D24_UNORM_S8_UINT` bila `D32_SFLOAT_S8_UINT` tidak didukung.
  - Target SPIR-V `glslang` otomatis disesuaikan (`1.0` untuk Vulkan 1.1, `1.5` untuk 1.2, `1.3` untuk 1.3).
- **Dual Mode Build CI**:
  - **`runtime-smoke`** (`DOD3_ANDROID_RUNTIME_SMOKE=ON`): membangun APK uji bring-up Vulkan/SDL2 + diagnostik tanpa kode lifted PPU/SPU.
  - **`full-standalone`** (`DOD3_ANDROID_RUNTIME_SMOKE=OFF`): mendekripsi `EBOOT_101.ELF` & `libsre.prx` via GitHub Secrets, menjalankan lifter PPU/SPU, dan membundel `libmain.so` penuh.

## Dokumentasi
Lihat [Panduan Port Android & Setup CI](docs/android-port-guide.md) untuk langkah detail.
