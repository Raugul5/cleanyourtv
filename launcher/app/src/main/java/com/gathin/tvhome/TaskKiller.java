package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * "Clean memory" button: asks Android to kill the background processes of every app that has a Home icon,
 * except this launcher, the keyboard and the packages in {@code keep} (the VPN app, top-bar apps, live TV).
 * Uses KILL_BACKGROUND_PROCESSES (a normal permission): apps that run a foreground service, such as an active
 * VPN or a playing music app, are left alone by Android itself. Then shows how much RAM is free.
 */
final class TaskKiller {
    private TaskKiller() { }

    static void cleanup(Activity act, Collection<String> keep) {
        ActivityManager am = act.getSystemService(ActivityManager.class);
        if (am == null) return;
        PackageManager pm = act.getPackageManager();
        Set<String> pkgs = new HashSet<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) pkgs.add(ri.activityInfo.packageName);
        }
        pkgs.remove(act.getPackageName());
        for (String k : keep) if (k != null) pkgs.remove(k.trim());
        String ime = Settings.Secure.getString(act.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        if (ime != null) pkgs.remove(ime.split("/")[0]);

        long before = availMb(am);
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            int n = 0;
            for (String p : pkgs) {
                try { am.killBackgroundProcesses(p); n++; } catch (RuntimeException ignored) { }
            }
            final int closed = n;
            main.postDelayed(() -> {
                if (act.isFinishing()) return;
                long after = availMb(am);
                long freed = Math.max(0, after - before);
                Toast.makeText(act, act.getString(R.string.cleanup_done, freed, after, closed),
                        Toast.LENGTH_LONG).show();
            }, 800);
        }, "cleanup").start();
    }

    private static long availMb(ActivityManager am) {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem / (1024 * 1024);
    }
}
