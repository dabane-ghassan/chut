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
    public static final String LEARN = "learn";
    public static final String HIDDEN = "hidden";
    public static final String FOREIGN = "foreign";
    public static final String CONTACTS = "contacts";
    public static final String CALLBACKS = "callbacks";
    public static final String SENIOR = "senior";
    public static final String NOTIFY_SILENCED = "notify_silenced";
    public static final String NOTIFY_BLOCKED = "notify_blocked";
    public static final String WEEKLY = "weekly";
    public static final String LANGUAGE = "language";
    /** Off by default: chut never displays phone numbers unless asked to. */
    public static final String SHOW_NUMBERS = "show_numbers";

    private final SharedPreferences p;

    public Prefs(Context context) {
        Context app = context.getApplicationContext();
        p = (app != null ? app : context).getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** "fr" (the default) or "en". */
    public String language() {
        return "en".equals(p.getString(LANGUAGE, "fr")) ? "en" : "fr";
    }

    public void setLanguage(String lang) {
        p.edit().putString(LANGUAGE, "en".equals(lang) ? "en" : "fr").commit();
    }

    public boolean flag(String key) {
        return p.getBoolean(key, defaultFlag(key));
    }

    public void setFlag(String key, boolean on) {
        p.edit().putBoolean(key, on).commit();   // on disk at once: survives the app being killed
    }

    public int number(String key, int def) {
        return p.getInt(key, def);
    }

    public void setNumber(String key, int value) {
        p.edit().putInt(key, value).commit();
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
            case LEARN:
            case CONTACTS:
            case CALLBACKS:
            case NOTIFY_SILENCED:
            case WEEKLY:
                return true;
            default:
                return false;   // hidden, foreign, senior, notify_blocked, show_numbers
        }
    }

    public Engine.Config engineConfig() {
        Engine.Config c = new Engine.Config();
        c.sensitivity = number(SENSITIVITY, 1);
        c.arcepRanges = flag(ARCEP);
        c.learn = flag(LEARN);
        c.blockHidden = flag(HIDDEN);
        c.blockForeign = flag(FOREIGN);
        c.contactsAlwaysRing = flag(CONTACTS);
        c.letCallbacksThrough = flag(CALLBACKS);
        c.seniorMode = flag(SENIOR);
        return c;
    }
}
