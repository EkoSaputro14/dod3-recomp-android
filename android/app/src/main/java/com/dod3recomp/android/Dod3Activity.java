package com.dod3recomp.android;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;

import org.libsdl.app.SDLActivity;
import org.libsdl.app.SDLControllerManager;

/**
 * SDL2 activity hosting libmain.so for Drakengard 3 Recompiled (Android ARM64, Vulkan 1.1-1.3).
 */
public class Dod3Activity extends SDLActivity {
    private static final String FOLDER_NAME = "Dod3Recomp";
    private static final int REQ_STORAGE = 0x4433;
    private static final int REQ_IMPORT_FILE = 0x4434;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private InputManager inputManager;
    private InputManager.InputDeviceListener inputDeviceListener;
    private Method sdlPollInputMethod;
    private AlertDialog setupDialog;
    private boolean gateCleared = false;

    @Override
    protected String[] getLibraries() {
        return new String[] {"SDL2", "main"};
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureImmersiveMode();
        syncBaseDirToNative();
        setupControllerHotplug();
        if (!checkAndReleaseGate()) {
            showSetupDialog();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        configureImmersiveMode();
        if (!checkAndReleaseGate()) {
            showSetupDialog();
        }
    }

    @Override
    protected void resumeNativeThread() {
        if (!gateCleared) return;
        super.resumeNativeThread();
    }

    @Override
    protected void onDestroy() {
        dismissSetupDialog();
        if (inputManager != null && inputDeviceListener != null) {
            inputManager.unregisterInputDeviceListener(inputDeviceListener);
            inputDeviceListener = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private boolean isRuntimeSmokeMode() {
        if (mBrokenLibraries) return false;
        try {
            String diag = nativeRunDiagnostics();
            return diag != null && diag.contains("RUNTIME SMOKE");
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    private File resolveBaseDir() {
        if (hasStoragePermission()) {
            File ext = new File(Environment.getExternalStorageDirectory(), FOLDER_NAME);
            if (ext.exists() || ext.mkdirs()) return ext;
        }
        File appExt = getExternalFilesDir(null);
        if (appExt != null) {
            appExt.mkdirs();
            return appExt;
        }
        return getFilesDir();
    }

    private void syncBaseDirToNative() {
        File base = resolveBaseDir();
        if (!mBrokenLibraries && base != null) {
            try {
                nativeSetBaseDir(base.getAbsolutePath());
            } catch (UnsatisfiedLinkError ignored) {
            }
        }
    }

    private boolean hasGameFiles() {
        File base = resolveBaseDir();
        if (base == null) return false;
        File sfo = new File(base, "game/disc/PS3_GAME/PARAM.SFO");
        File elf = new File(base, "elf/EBOOT_101.ELF");
        return sfo.isFile() && elf.isFile();
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, REQ_STORAGE);
            } catch (ActivityNotFoundException e) {
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQ_STORAGE);
            }
        } else {
            requestPermissions(new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQ_STORAGE);
        }
    }

    private void launchSafImporter() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT_FILE);
    }

    private void dismissSetupDialog() {
        if (setupDialog != null) {
            if (setupDialog.isShowing()) setupDialog.dismiss();
            setupDialog = null;
        }
    }

    private boolean checkAndReleaseGate() {
        syncBaseDirToNative();
        if (gateCleared) return true;
        if (hasGameFiles() || isRuntimeSmokeMode()) {
            gateCleared = true;
            dismissSetupDialog();
            resumeNativeThread();
            return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_STORAGE) {
            if (!checkAndReleaseGate()) showSetupDialog();
            return;
        }
        if (requestCode == REQ_IMPORT_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            importUriAtomic(data.getData());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE && !checkAndReleaseGate()) {
            showSetupDialog();
        }
    }

    private void importUriAtomic(Uri uri) {
        File base = resolveBaseDir();
        String name = queryDisplayName(uri);
        if (name == null || name.isEmpty()) name = "imported_file.bin";
        name = name.replace("/", "_").replace("\\", "_").replace("..", "_");
        final boolean isBootElf = "EBOOT_101.ELF".equalsIgnoreCase(name);
        final File destDir = new File(base, isBootElf ? "elf" : "import");
        destDir.mkdirs();
        final String targetName = isBootElf ? "EBOOT_101.ELF" : name;
        final File part = new File(destDir, targetName + ".part");
        final File target = new File(destDir, targetName);

        Toast.makeText(this, "Mengimpor " + targetName + "...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean ok = false;
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(part)) {
                if (in != null) {
                    byte[] buf = new byte[1024 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    out.getFD().sync();
                    ok = part.renameTo(target);
                }
            } catch (Exception e) {
                part.delete();
            }
            final boolean finalOk = ok;
            final String destLabel = (isBootElf ? "elf/" : "import/") + targetName;
            mainHandler.post(() -> {
                Toast.makeText(Dod3Activity.this,
                        finalOk ? "Tersimpan di " + destLabel : "Gagal mengimpor file",
                        Toast.LENGTH_LONG).show();
                if (!checkAndReleaseGate()) showSetupDialog();
            });
        }).start();
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        return null;
    }

    private void showSetupDialog() {
        if (gateCleared || isFinishing() || isDestroyed()) return;
        if (setupDialog != null && setupDialog.isShowing()) return;
        File base = resolveBaseDir();
        String msg = "Folder data game:\n" + base.getAbsolutePath() + "\n\n"
                + "Letakkan game/disc/ (BLUS31197) + elf/EBOOT_101.ELF, atau impor berkas ISO/PKG.";
        setupDialog = new AlertDialog.Builder(this)
                .setTitle("Drakengard 3 Recompiled")
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("Allow Storage", (d, w) -> requestStorageAccess())
                .setNeutralButton("Diagnostics", (d, w) -> showDiagnosticsDialog())
                .setNegativeButton("Import File", (d, w) -> launchSafImporter())
                .create();
        setupDialog.setOnDismissListener(d -> {
            if (setupDialog == d) setupDialog = null;
        });
        setupDialog.show();
    }

    private void showDiagnosticsDialog() {
        String diag = "Native library belum dimuat.";
        if (!mBrokenLibraries) {
            try {
                diag = nativeRunDiagnostics();
            } catch (UnsatisfiedLinkError e) {
                diag = "JNI error: " + e.getMessage();
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("Diagnostik Android / Vulkan")
                .setMessage(diag)
                .setPositiveButton("OK", (d, w) -> {
                    if (!checkAndReleaseGate()) showSetupDialog();
                })
                .show();
    }

    private void configureImmersiveMode() {
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void setupControllerHotplug() {
        try {
            sdlPollInputMethod = SDLControllerManager.class.getDeclaredMethod("pollInputDevices");
            sdlPollInputMethod.setAccessible(true);
        } catch (Exception ignored) {
        }
        inputManager = getSystemService(InputManager.class);
        if (inputManager == null) return;
        inputDeviceListener = new InputManager.InputDeviceListener() {
            @Override
            public void onInputDeviceAdded(int deviceId) {
                InputDevice dev = InputDevice.getDevice(deviceId);
                pollControllers();
                if (dev != null && ((dev.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                        || (dev.getSources() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)) {
                    Toast.makeText(Dod3Activity.this, "Controller connected: " + dev.getName(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onInputDeviceChanged(int deviceId) {
                pollControllers();
            }

            @Override
            public void onInputDeviceRemoved(int deviceId) {
                pollControllers();
            }
        };
        inputManager.registerInputDeviceListener(inputDeviceListener, mainHandler);
    }

    private void pollControllers() {
        if (sdlPollInputMethod == null) return;
        try {
            sdlPollInputMethod.invoke(null);
        } catch (Exception ignored) {
        }
    }

    private static native void nativeSetBaseDir(String path);
    private static native String nativeRunDiagnostics();
}
