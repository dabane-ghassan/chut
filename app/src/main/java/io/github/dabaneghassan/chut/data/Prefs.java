// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.data;

import android.content.Context;
import android.content.SharedPreferences;

import io.github.dabaneghassan.chut.engine.Engine;

/** The user's settings. */
public final class Prefs {

    public static final String SENSITIVITY = "sensitivity";
    public static final String ARCEP = "arcep";
    public static final String COMMUNITY = "community";
    public static final String SPOOF = "spoof";
    public static final String LEARN = "learn";
    public static final String HIDDEN = "hidden";
    public static final String FOREIGN = "foreign";
    public static final String CONTACTS = "contacts";
    public static final String CALLBACKS = "callbacks";
    public static final String QUIET = "quiet";
    public static final String QUIET_START = "quiet_start";
    public static final String QUIET_END = "quiet_end";
    public static final String SENIOR = "senior";
    public static final String NOTIFY_SILENCED = "notify_silenced";
    public static final String NOTIFY_BLOCKED = "notify_blocked";
    public static final String WEEKLY = "weekly";
    public static final String SYNC = "sync";
    public static final String LAST_SYNC = "last_sync";
    public static final String BUNDLED_VERSION = "bundled_version";

    private final SharedPreferences p;

    public Prefs(Context context) {
        p = context.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public boolean flag(String key) {
        return p.getBoolean(key, defaultFlag(key));
    }

    public void setFlag(String key, boolean on) {
        p.edit().putBoolean(key, on).apply();
    }

    public int number(String key, int def) {
        return p.getInt(key, def);
    }

    public void setNumber(String key, int value) {
        p.edit().putInt(key, value).apply();
    }

    public long time(String key) {
        return p.getLong(key, 0);
    }

    public void setTime(String key, long value) {
        p.edit().putLong(key, value).apply();
    }

    private static boolean defaultFlag(String key) {
        switch (key) {
            case ARCEP:
            case COMMUNITY:
            case SPOOF:
            case LEARN:
            case CONTACTS:
            case CALLBACKS:
            case NOTIFY_SILENCED:
            case WEEKLY:
                return true;
            default:
                return false;   // hidden, foreign, quiet, senior, notify_blocked, sync
        }
    }

    public Engine.Config engineConfig() {
        Engine.Config c = new Engine.Config();
        c.sensitivity = number(SENSITIVITY, 1);
        c.arcepRanges = flag(ARCEP);
        c.community = flag(COMMUNITY);
        c.spoofCheck = flag(SPOOF);
        c.learn = flag(LEARN);
        c.blockHidden = flag(HIDDEN);
        c.blockForeign = flag(FOREIGN);
        c.contactsAlwaysRing = flag(CONTACTS);
        c.letCallbacksThrough = flag(CALLBACKS);
        c.quietHours = flag(QUIET);
        c.quietStart = number(QUIET_START, 22);
        c.quietEnd = number(QUIET_END, 8);
        c.seniorMode = flag(SENIOR);
        return c;
    }
}
