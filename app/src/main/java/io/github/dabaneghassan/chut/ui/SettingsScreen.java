// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.ui;

import android.app.AlertDialog;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import io.github.dabaneghassan.chut.BackgroundJobs;
import io.github.dabaneghassan.chut.BuildConfigLite;
import io.github.dabaneghassan.chut.MainActivity;
import io.github.dabaneghassan.chut.R;
import io.github.dabaneghassan.chut.data.Learning;
import io.github.dabaneghassan.chut.data.Phone;
import io.github.dabaneghassan.chut.data.Prefs;
import io.github.dabaneghassan.chut.data.Store;
import io.github.dabaneghassan.chut.engine.Learner;

/** The few choices that matter, and everything else folded under "More settings". */
public final class SettingsScreen {

    public static final String SITE = "https://dabane-ghassan.github.io/chut/";
    public static final String REPO = "https://github.com/dabane-ghassan/chut";

    private SettingsScreen() {
    }

    public static View build(MainActivity a) {
        Prefs p = new Prefs(a);
        ScrollView scroll = new ScrollView(a);
        LinearLayout col = Ui.column(a);
        int pad = Ui.dp(a, 24);
        col.setPadding(pad, Ui.dp(a, 4), pad, Ui.dp(a, 32));
        scroll.addView(col);
        col.addView(a.backLink(MainActivity.PAGE_HOME, R.string.tab_home));
        col.addView(StatsScreen.header(a, a.getString(R.string.tab_settings), null));

        // Language, written in both so anyone finds it.
        col.addView(StatsScreen.section(a, a.getString(R.string.language)));
        int current = "en".equals(p.language()) ? 1 : 0;
        col.addView(Ui.segmented(a, new String[]{"Français", "English"}, current, i -> {
            if (i != current) a.setLanguage(i == 1 ? "en" : "fr");
        }));
        col.addView(Ui.space(a, 28));

        col.addView(StatsScreen.section(a, a.getString(R.string.sensitivity)));
        int level = p.number(Prefs.SENSITIVITY, 1);
        col.addView(Ui.segmented(a, a.getResources().getStringArray(R.array.sensitivity), level, i -> {
            p.setNumber(Prefs.SENSITIVITY, i);
            a.refresh();
        }));
        String[] explain = a.getResources().getStringArray(R.array.sensitivity_explain);
        TextView ex = Ui.muted(a, p.flag(Prefs.SENIOR) ? a.getString(R.string.senior_overrides) : explain[level]);
        ex.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 16));
        col.addView(ex);

        col.addView(flag(a, p, Prefs.ARCEP, R.string.set_arcep, R.string.set_arcep_sub));
        col.addView(flag(a, p, Prefs.LEARN, R.string.set_learn, R.string.set_learn_sub));
        col.addView(Ui.switchRow(a, R.string.set_contacts, R.string.set_contacts_sub, p.flag(Prefs.CONTACTS), (b, on) -> {
            p.setFlag(Prefs.CONTACTS, on);
            if (on && !Phone.canReadContacts(a)) a.requestContacts();
        }));
        col.addView(flag(a, p, Prefs.NOTIFY_SILENCED, R.string.set_notify_silenced, R.string.set_notify_silenced_sub));

        col.addView(Ui.space(a, 12));
        col.addView(Ui.divider(a));
        col.addView(Ui.linkRow(a, a.getString(R.string.more_settings), a.showAdvanced ? "–" : "+", v -> {
            a.showAdvanced = !a.showAdvanced;
            a.refresh();
        }));
        if (a.showAdvanced) advanced(a, p, col);

        // About
        col.addView(Ui.divider(a));
        col.addView(Ui.space(a, 8));
        LinearLayout brand = Ui.row(a);
        brand.addView(a.logo(24));
        TextView v = Ui.muted(a, "chut " + BuildConfigLite.versionName(a) + " · " + a.getString(R.string.about_line));
        v.setPadding(Ui.dp(a, 10), 0, 0, 0);
        brand.addView(v, Ui.weight(1));
        col.addView(brand);
        col.addView(Ui.space(a, 6));
        col.addView(Ui.linkRow(a, a.getString(R.string.how_it_works), null, x -> howItWorks(a)));
        col.addView(Ui.linkRow(a, a.getString(R.string.website), null, x -> a.openUrl(SITE)));
        col.addView(Ui.linkRow(a, a.getString(R.string.source_code), "GPL-3.0", x -> a.openUrl(REPO)));
        return scroll;
    }

    private static void advanced(MainActivity a, Prefs p, LinearLayout col) {
        col.addView(flag(a, p, Prefs.CALLBACKS, R.string.set_callbacks, R.string.set_callbacks_sub));
        col.addView(flag(a, p, Prefs.HIDDEN, R.string.set_hidden, R.string.set_hidden_sub));
        col.addView(flag(a, p, Prefs.FOREIGN, R.string.set_foreign, R.string.set_foreign_sub));
        col.addView(Ui.switchRow(a, R.string.set_senior, R.string.set_senior_sub, p.flag(Prefs.SENIOR), (b, on) -> {
            p.setFlag(Prefs.SENIOR, on);
            if (on) {
                p.setFlag(Prefs.CONTACTS, true);
                p.setFlag(Prefs.NOTIFY_SILENCED, true);
                if (!Phone.canReadContacts(a)) a.requestContacts();
            }
            a.refresh();
        }));
        col.addView(flag(a, p, Prefs.NOTIFY_BLOCKED, R.string.set_notify_blocked, R.string.set_notify_blocked_sub));
        col.addView(Ui.switchRow(a, R.string.set_weekly, R.string.set_weekly_sub, p.flag(Prefs.WEEKLY), (b, on) -> {
            p.setFlag(Prefs.WEEKLY, on);
            BackgroundJobs.schedule(a);
        }));
        col.addView(Ui.switchRow(a, R.string.set_show_numbers, R.string.set_show_numbers_sub, p.flag(Prefs.SHOW_NUMBERS), (b, on) -> {
            p.setFlag(Prefs.SHOW_NUMBERS, on);
            a.refresh();
        }));
        if (a.needsNotificationPermission()) {
            col.addView(Ui.linkRow(a, a.getString(R.string.allow_notifications), "›", v -> a.requestNotifications()));
        }

        // The learned model
        col.addView(Ui.space(a, 16));
        col.addView(StatsScreen.section(a, a.getString(R.string.sec_model)));
        Learner.Model m = Learning.model(a);
        if (!m.ready()) {
            col.addView(Ui.muted(a, a.getString(R.string.model_not_ready, Learner.MIN_POSITIVES)));
        } else {
            String recall = m.recall < 0 ? "–" : Math.round(m.recall * 100) + " %";
            String fa = m.falseAlarms < 0 ? "–" : String.format(java.util.Locale.ROOT, "%.1f %%", m.falseAlarms * 100);
            col.addView(Ui.body(a, a.getString(R.string.model_summary, recall, fa)));
            TextView t = Ui.muted(a, a.getString(R.string.model_line, m.positives, m.negatives,
                    DateUtils.getRelativeTimeSpanString(m.trainedAt)));
            t.setPadding(0, Ui.dp(a, 6), 0, 0);
            col.addView(t);
            col.addView(Ui.linkRow(a, a.getString(R.string.model_retrain), null, v -> new Thread(() -> {
                Learning.retrainNow(a);
                a.runOnUiThread(() -> {
                    a.toast(a.getString(R.string.model_retrained));
                    a.refresh();
                });
            }).start()));
        }

        // Data
        col.addView(Ui.space(a, 16));
        col.addView(StatsScreen.section(a, a.getString(R.string.sec_data)));
        col.addView(Ui.linkRow(a, a.getString(R.string.import_calllog), null, v -> a.importFromCallLog()));
        col.addView(Ui.linkRow(a, a.getString(R.string.import_file), null, v -> a.importFile(Store.BLOCKED)));
        col.addView(Ui.linkRow(a, a.getString(R.string.export_csv), null, v -> a.exportFile()));
        col.addView(Ui.linkRow(a, a.getString(R.string.clear_history), null, v ->
                new AlertDialog.Builder(a)
                        .setTitle(R.string.clear_history)
                        .setMessage(R.string.clear_history_confirm)
                        .setPositiveButton(R.string.clear, (d, w) -> {
                            Store.get(a).clearHistory();
                            a.refresh();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()));
        TextView privacy = Ui.muted(a, a.getString(R.string.privacy_note));
        privacy.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 16));
        col.addView(privacy);
    }

    private static View flag(MainActivity a, Prefs p, String key, int title, int sub) {
        return Ui.switchRow(a, title, sub, p.flag(key), (b, on) -> p.setFlag(key, on));
    }

    static void howItWorks(MainActivity a) {
        TextView t = Ui.body(a, a.getString(R.string.how_it_works_text));
        int p = Ui.dp(a, 22);
        t.setPadding(p, Ui.dp(a, 12), p, 0);
        ScrollView s = new ScrollView(a);
        s.addView(t);
        new AlertDialog.Builder(a).setTitle(R.string.how_it_works).setView(s).setPositiveButton(android.R.string.ok, null).show();
    }
}
