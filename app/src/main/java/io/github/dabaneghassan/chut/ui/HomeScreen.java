// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.ui;

import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.ZoneId;
import java.util.List;

import io.github.dabaneghassan.chut.MainActivity;
import io.github.dabaneghassan.chut.R;
import io.github.dabaneghassan.chut.data.Prefs;
import io.github.dabaneghassan.chut.data.Store;
import io.github.dabaneghassan.chut.engine.Engine;
import io.github.dabaneghassan.chut.engine.PhoneNumbers;
import io.github.dabaneghassan.chut.engine.Stats;

/** Protection status, this week at a glance and the latest filtered calls. */
public final class HomeScreen {

    private HomeScreen() {
    }

    public static View build(MainActivity a) {
        Store store = Store.get(a);
        Prefs prefs = new Prefs(a);
        long now = System.currentTimeMillis();
        Stats s = Stats.compute(store.events(now - 400L * 86_400_000L), now, ZoneId.systemDefault(), 14, 1);

        ScrollView scroll = new ScrollView(a);
        LinearLayout col = Ui.column(a);
        int pad = Ui.dp(a, 16);
        col.setPadding(pad, Ui.dp(a, 8), pad, Ui.dp(a, 24));
        scroll.addView(col);

        // Header
        LinearLayout head = Ui.row(a);
        head.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 14));
        head.addView(a.logo(40));
        LinearLayout name = Ui.column(a);
        name.setPadding(Ui.dp(a, 12), 0, 0, 0);
        name.addView(Ui.display(a, "chut", 30, R.color.ink));
        name.addView(Ui.muted(a, a.getString(R.string.tagline)));
        head.addView(name, Ui.weight(1));
        col.addView(head);

        // Status
        boolean on = a.isProtectionOn();
        LinearLayout status = Ui.card(a, on ? R.color.allow_soft : R.color.brand);
        TextView st = Ui.title(a, a.getString(on ? R.string.status_on : R.string.status_off));
        st.setTextColor(a.getColor(on ? R.color.allow_ink : R.color.on_brand));
        status.addView(st);
        TextView sub = Ui.body(a, on ? statusLine(a, prefs, store) : a.getString(R.string.status_off_explain));
        sub.setTextColor(a.getColor(on ? R.color.allow_ink : R.color.on_brand));
        sub.setPadding(0, Ui.dp(a, 4), 0, 0);
        status.addView(sub);
        if (!on) {
            TextView b = Ui.button(a, a.getString(R.string.activate), false, v -> a.requestProtection());
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(a, 14);
            status.addView(b, lp);
        }
        col.addView(status);

        if (on && a.needsNotificationPermission() && prefs.flag(Prefs.NOTIFY_SILENCED)) {
            LinearLayout n = Ui.card(a, R.color.silence_soft);
            n.addView(Ui.body(a, a.getString(R.string.notif_permission_explain)));
            TextView b = Ui.button(a, a.getString(R.string.allow_notifications), false, v -> a.requestNotifications());
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(a, 10);
            n.addView(b, lp);
            col.addView(n);
        }

        // Numbers
        col.addView(Ui.eyebrow(a, a.getString(R.string.at_a_glance)));
        col.addView(Ui.grid(a, 3,
                Ui.tile(a, String.valueOf(s.stoppedToday), a.getString(R.string.today), R.color.ink),
                Ui.tile(a, String.valueOf(s.stoppedThisWeek), a.getString(R.string.this_week), R.color.ink),
                Ui.tile(a, String.valueOf(s.stopped()), a.getString(R.string.since_install), R.color.brand_ink)));

        LinearLayout chart = Ui.card(a);
        chart.addView(Ui.text(a, a.getString(R.string.last_14_days), 15, R.color.ink, Ui.semibold()));
        chart.addView(Ui.space(a, 10));
        String[] labels = new String[14];
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 0; i < 14; i++) {
            java.time.LocalDate d = today.minusDays(13 - i);
            labels[i] = (i % 2 == 1) ? String.valueOf(d.getDayOfMonth()) : null;
        }
        chart.addView(new Charts.Bars(a, s.perDay, labels, 110));
        chart.addView(Charts.legend(a));
        col.addView(chart);

        // Latest calls
        col.addView(Ui.eyebrow(a, a.getString(R.string.latest_calls)));
        List<Stats.Event> recent = store.recent(8);
        LinearLayout list = Ui.card(a);
        if (recent.isEmpty()) {
            list.addView(Ui.muted(a, a.getString(on ? R.string.no_calls_yet : R.string.no_calls_off)));
        }
        for (int i = 0; i < recent.size(); i++) {
            if (i > 0) list.addView(Ui.divider(a));
            list.addView(callRow(a, recent.get(i)));
        }
        col.addView(list);
        return scroll;
    }

    static String statusLine(MainActivity a, Prefs prefs, Store store) {
        Engine.Config cfg = prefs.engineConfig();
        String[] modes = a.getResources().getStringArray(R.array.sensitivity);
        StringBuilder sb = new StringBuilder(cfg.seniorMode ? a.getString(R.string.senior_short) : modes[cfg.sensitivity]);
        if (cfg.arcepRanges) sb.append(" · ARCEP");
        if (cfg.community) sb.append(" · ").append(a.getString(R.string.community_short, store.communitySize()));
        if (cfg.learn) sb.append(" · ").append(a.getString(R.string.learning_short));
        return a.getString(R.string.status_on_explain, sb.toString());
    }

    public static View callRow(MainActivity a, Stats.Event e) {
        LinearLayout row = Ui.row(a);
        row.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 2));
        row.addView(Ui.dot(a, Labels.actionColor(e.action), 10));
        LinearLayout texts = Ui.column(a);
        String who = e.number.isEmpty() ? a.getString(R.string.hidden_number) : PhoneNumbers.pretty(e.number);
        texts.addView(Ui.text(a, who, 15.5f, R.color.ink, Ui.semibold()));
        List<String> reasons = Labels.reasons(a, e.reasons);
        String why = reasons.isEmpty() ? Labels.category(a, e.category) : reasons.get(0);
        TextView w = Ui.muted(a, Labels.action(a, e.action) + " · " + why);
        w.setMaxLines(1);
        w.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(w);
        row.addView(texts, Ui.weight(1));
        TextView when = Ui.muted(a, DateUtils.getRelativeTimeSpanString(e.time, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE));
        when.setGravity(Gravity.END);
        when.setPadding(Ui.dp(a, 8), 0, 0, 0);
        row.addView(when);
        row.setOnClickListener(v -> a.showCall(e));
        row.setBackground(Ui.pressable(a, 0x00000000, Ui.dp(a, 10)));
        return row;
    }
}
