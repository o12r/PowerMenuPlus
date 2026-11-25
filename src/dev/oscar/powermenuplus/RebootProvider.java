package dev.oscar.powermenuplus;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/** Runs the root soft reboot on behalf of SystemUI (SystemUI itself has no root). */
public final class RebootProvider extends ContentProvider {
    static final String CALL_SOFT_REBOOT = "soft_reboot";
    private static final String COMMAND = "/data/adb/ksud soft-reboot";
    private static boolean busy;

    @Override public boolean onCreate() { return true; }

    private boolean callerIsSystemUi() {
        int uid = Binder.getCallingUid();
        if (uid / 100000 != android.os.Process.myUid() / 100000) return false;
        String[] pkgs = getContext().getPackageManager().getPackagesForUid(uid);
        return pkgs != null && Arrays.asList(pkgs).contains("com.android.systemui");
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle out = new Bundle();
        if (!CALL_SOFT_REBOOT.equals(method)) { out.putString("message", "unknown method"); return out; }
        if (!callerIsSystemUi()) { out.putString("message", "caller not allowed"); return out; }
        synchronized (RebootProvider.class) {
            if (busy) { out.putString("message", "busy"); return out; }
            busy = true;
        }
        try {
            Process p = new ProcessBuilder(suPath(), "-c", COMMAND).redirectErrorStream(true).start();
            // A successful soft reboot kills this very process, so "still running" counts as accepted.
            if (p.waitFor(4, TimeUnit.SECONDS)) {
                int code = p.exitValue();
                out.putBoolean("ok", code == 0);
                if (code != 0) out.putString("message", "su/ksud exit " + code + " (grant root to Power Menu Plus in KernelSU)");
            } else {
                out.putBoolean("ok", true);
            }
        } catch (Throwable t) {
            out.putString("message", String.valueOf(t.getMessage()));
        } finally {
            synchronized (RebootProvider.class) { busy = false; }
        }
        return out;
    }

    @Override public Cursor query(Uri u, String[] a, String b, String[] c, String d) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }

    private static String suPath() {
        String[] c = {"/system/bin/su", "/system/xbin/su", "/debug_ramdisk/su", "/data/adb/ksu/bin/su", "/data/adb/ap/bin/su", "/sbin/su", "/product/bin/su", "/vendor/bin/su"};
        for (String f : c) if (new java.io.File(f).canExecute()) return f;
        return "su";
    }
}
