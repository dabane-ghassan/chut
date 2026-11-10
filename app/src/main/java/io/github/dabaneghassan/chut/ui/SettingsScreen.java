// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.ui;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import io.github.dabaneghassan.chut.BackgroundJobs;
import io.github.dabaneghassan.chut.BuildConfigLite;
import io.github.dabaneghassan.chut.MainActivity;
import io.github.dabaneghassan.chut.R;
import io.github.dabaneghassan.chut.data.Community;
import io.github.dabaneghassan.chut.data.Phone;
import io.github.dabaneghassan.chut.data.Prefs;
import io.github.dabaneghassan.chut.data.Store;

/** How strict chut is, what it looks at, notifications, data and about. */
public final class SettingsScreen {

    public static final String SITE = "https://dabane-ghassan.github.io/chut/";

    private SettingsScreen() {
    }

    public static View build(MainActivity a) {
        Prefs p = new Prefs(a);
        ScrollView scroll = new ScrollView(a);
        LinearLayout col = Ui.column(a);
        int pad = Ui.dp(a, 16);
        col.setPadding(pad, Ui.dp(a, 8), pad, Ui.dp(a, 24));
        scroll.addView(col);
        col.addView(StatsScreen.header(a, a.getString(R.string.tab_settings), null));

        // Sensitivity
        col.addView(Ui.eyebrow(a, a.getString(R.string.sec_filter)));
        LinearLayout sens = Ui.card(a);
        sens.addView(Ui.title(a, a.getString(R.string.sensitivity)));
        sens.addView(Ui.space(a, 10));
        int current = p.number(Prefs.SENSITIVITY, 1);
        sens.addView(Ui.segmented(a, a.getResources().getStringArray(R.array.sensitivity), current, i -> {
            p.setNumber(Prefs.SENSITIVITY, i);
            a.refresh();
        }));
        String[] explain = a.getResources().getStringArray(R.array.sensitivity_explain);
        TextView ex = Ui.muted(a, p.flag(Prefs.SENIOR) ? a.getString(R.string.senior_overrides) : explain[current]);
        ex.setPadding(0, Ui.dp(a, 10), 0, 0);
        sens.addView(ex);
        col.addView(sens);

        LinearLayout signals = Ui.card(a);
        signals.addView(flag(a, p, Prefs.ARCEP, R.string.set_arcep, R.string.set_arcep_sub));
        signals.addView(flag(a, p, Prefs.COMMUNITY, R.string.set_community, R.string.set_community_sub));
        signals.addView(flag(a, p, Prefs.LEARN, R.string.set_learn, R.string.set_learn_sub));
        signals.addView(flag(a, p, Prefs.SPOOF, R.string.set_spoof, R.string.set_spoof_sub));
        signals.addView(flag(a, p, Prefs.CALLBACKS, R.string.set_callbacks, R.string.set_callbacks_sub));
        signals.addView(flag(a, p, Prefs.HIDDEN, R.string.set_hidden, R.string.set_hidden_sub));
        signals.addView(flag(a, p, Prefs.FOREIGN, R.string.set_foreign, R.string.set_foreign_sub));
        col.addView(signals);

        // Contacts
        LinearLayout contacts = Ui.card(a);
        contacts.addView(Ui.switchRow(a, R.string.set_contacts, R.string.set_contacts_sub, p.flag(Prefs.CONTACTS), (b, on) -> {
            p.setFlag(Prefs.CONTACTS, on);
            if (on && !Phone.canReadContacts(a)) a.requestContacts();
        }));
        if (p.flag(Prefs.CONTACTS) && !Phone.canReadContacts(a)) {
            contacts.addView(Ui.button(a, a.getString(R.string.allow_contacts), false, v -> a.requestContacts()));
        }
        col.addView(contacts);

        // Quiet hours
        LinearLayout quiet = Ui.card(a);
        quiet.addView(Ui.switchRow(a, R.string.set_quiet, R.string.set_quiet_sub, p.flag(Prefs.QUIET), (b, on) -> {
            p.setFlag(Prefs.QUIET, on);
            a.refresh();
        }));
        if (p.flag(Prefs.QUIET)) {
            int start = p.number(Prefs.QUIET_START, 22), end = p.number(Prefs.QUIET_END, 8);
            quiet.addView(Ui.linkRow(a, a.getString(R.string.quiet_from), hour(start), v ->
                    new TimePickerDialog(a, (tp, h, m) -> {
                        p.setNumber(Prefs.QUIET_START, h);
                        a.refresh();
                    }, start, 0, true).show()));
            quiet.addView(Ui.linkRow(a, a.getString(R.string.quiet_to), hour(end), v ->
                    new TimePickerDialog(a, (tp, h, m) -> {
                        p.setNumber(Prefs.QUIET_END, h);
                        a.refresh();
                    }, end, 0, true).show()));
        }
        col.addView(quiet);

        // Senior protection
        col.addView(Ui.eyebrow(a, a.getString(R.string.sec_senior)));
        LinearLayout senior = Ui.card(a, R.color.brand_soft);
        senior.addView(Ui.switchRow(a, R.string.set_senior, R.string.set_senior_sub, p.flag(Prefs.SENIOR), (b, on) -> {
            p.setFlag(Prefs.SENIOR, on);
            if (on) {
                p.setFlag(Prefs.CONTACTS, true);
                p.setFlag(Prefs.NOTIFY_SILENCED, true);
                if (!Phone.canReadContacts(a)) a.requestContacts();
            }
            a.refresh();
        }));
        col.addView(senior);

        // Notifications
        col.addView(Ui.eyebrow(a, a.getString(R.string.sec_notifications)));
        LinearLayout notif = Ui.card(a);
        notif.addView(flag(a, p, Prefs.NOTIFY_SILENCED, R.string.set_notify_silenced, R.string.set_notify_silenced_sub));
        notif.addView(flag(a, p, Prefs.NOTIFY_BLOCKED, R.string.set_notify_blocked, R.string.set_notify_blocked_sub));
        notif.addView(Ui.switchRow(a, R.string.set_weekly, R.string.set_weekly_sub, p.flag(Prefs.WEEKLY), (b, on) -> {
            p.setFlag(Prefs.WEEKLY, on);
            BackgroundJobs.schedule(a);
        }));
        if (a.needsNotificationPermission()) {
            notif.addView(Ui.button(a, a.getString(R.string.allow_notifications), false, v -> a.requestNotifications()));
        }
        col.addView(notif);

        // Data
        col.addView(Ui.eyebrow(a, a.getString(R.string.sec_data)));
        LinearLayout data = Ui.card(a);
        data.addView(Ui.linkRow(a, a.getString(R.string.export_csv), null, v -> a.exportFile()));
        data.addView(Ui.linkRow(a, a.getString(R.string.import_file), null, v -> a.importFile(Store.BLOCKED)));
        data.addView(Ui.linkRow(a, a.getString(R.string.clear_history), null, v ->
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
        privacy.setPadding(0, Ui.dp(a, 8), 0, 0);
        data.addView(privacy);
        col.addView(data);

        // About
        col.addView(Ui.eyebrow(a, a.getString(R.string.sec_about)));
        LinearLayout about = Ui.card(a);
        LinearLayout brand = Ui.row(a);
        brand.addView(a.logo(36));
        LinearLayout names = Ui.column(a);
        names.setPadding(Ui.dp(a, 12), 0, 0, 0);
        names.addView(Ui.title(a, "chut " + BuildConfigLite.versionName(a)));
        names.addView(Ui.muted(a, a.getString(R.string.about_line)));
        brand.addView(names, Ui.weight(1));
        about.addView(brand);
        about.addView(Ui.space(a, 8));
        about.addView(Ui.linkRow(a, a.getString(R.string.how_it_works), null, v -> howItWorks(a)));
        about.addView(Ui.linkRow(a, a.getString(R.string.website), "dabane-ghassan.github.io/chut", v -> a.openUrl(SITE)));
        about.addView(Ui.linkRow(a, a.getString(R.string.source_code), "GitHub", v -> a.openUrl(Community.REPO)));
        about.addView(Ui.linkRow(a, a.getString(R.string.report_bug), null, v -> a.openUrl(Community.REPO + "/issues")));
        about.addView(Ui.linkRow(a, a.getString(R.string.license), "GPL-3.0+", v -> a.openUrl(Community.REPO + "/blob/main/LICENSE")));
        col.addView(about);
        return scroll;
    }

    private static View flag(MainActivity a, Prefs p, String key, int title, int sub) {
        return Ui.switchRow(a, title, sub, p.flag(key), (b, on) -> p.setFlag(key, on));
    }

    private static String hour(int h) {
        return String.format(java.util.Locale.ROOT, "%02d:00", h);
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
