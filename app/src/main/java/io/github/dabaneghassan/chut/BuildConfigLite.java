// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut;

import android.content.Context;
import android.content.pm.PackageInfo;

/** Version info from the package manager (the build keeps BuildConfig off). */
public final class BuildConfigLite {

    private BuildConfigLite() {
    }

    private static PackageInfo info(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
        } catch (Exception e) {
            return null;
        }
    }

    public static int versionCode(Context c) {
        PackageInfo i = info(c);
        return i == null ? 0 : (int) i.getLongVersionCode();
    }

    public static String versionName(Context c) {
        PackageInfo i = info(c);
        return i == null || i.versionName == null ? "?" : i.versionName;
    }

    /** When this version was installed or updated. */
    public static long installedAt(Context c) {
        PackageInfo i = info(c);
        return i == null ? 0 : i.lastUpdateTime;
    }
}
