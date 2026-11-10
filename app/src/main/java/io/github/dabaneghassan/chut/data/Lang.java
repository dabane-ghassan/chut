// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.data;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;

import java.util.Locale;

/**
 * chut speaks French unless you pick English in its settings, whatever the phone's language.
 * Every screen, notification and job builds its texts from a context wrapped here.
 */
public final class Lang {

    private Lang() {
    }

    public static Locale locale(Context c) {
        return "en".equals(new Prefs(c).language()) ? Locale.ENGLISH : Locale.FRANCE;
    }

    public static Context wrap(Context base) {
        Locale l = locale(base);
        Locale.setDefault(l);
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocales(new LocaleList(l));
        return base.createConfigurationContext(cfg);
    }
}
