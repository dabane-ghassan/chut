// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.ui;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.github.dabaneghassan.chut.MainActivity;
import io.github.dabaneghassan.chut.R;
import io.github.dabaneghassan.chut.data.Store;
import io.github.dabaneghassan.chut.engine.Engine;
import io.github.dabaneghassan.chut.engine.Stats;

/** The statistics that matter, computed on the phone. No phone numbers here. */
public final class StatsScreen {

    private StatsScreen() {
    }

    public static View build(MainActivity a) {
        long now = System.currentTimeMillis();
        Stats s = Stats.compute(Store.get(a).events(now - 400L * 86_400_000L), now, ZoneId.systemDefault(), 30, 1);

        ScrollView scroll = new ScrollView(a);
        LinearLayout col = Ui.column(a);
        int pad = Ui.dp(a, 24);
        col.setPadding(pad, Ui.dp(a, 4), pad, Ui.dp(a, 32));
        scroll.addView(col);
        col.addView(a.backLink(MainActivity.PAGE_HOME, R.string.tab_home));
        col.addView(header(a, a.getString(R.string.page_stats), null));

        if (s.screened == 0) {
            col.addView(Ui.muted(a, a.getString(R.string.stats_empty)));
            return scroll;
        }

        LinearLayout figures = Ui.row(a);
        figures.setGravity(android.view.Gravity.TOP);
        figures.addView(figure(a, String.valueOf(s.stopped()), a.getString(R.string.stat_stopped)), Ui.weight(1));
        figures.addView(figure(a, s.spamShare() + " %", a.getString(R.string.stat_share_short)), Ui.weight(1));
        figures.addView(figure(a, duration(a, s.minutesSaved()), a.getString(R.string.stat_saved_short)), Ui.weight(1));
        col.addView(figures);
        col.addView(Ui.space(a, 36));

        col.addView(section(a, a.getString(R.string.last_30_days)));
        String[] dayLabels = new String[30];
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 30; i++) if ((29 - i) % 7 == 0) dayLabels[i] = String.valueOf(today.minusDays(29 - i).getDayOfMonth());
        col.addView(new Charts.Bars(a, s.perDay, dayLabels, 110));
        col.addView(Charts.legend(a));
        col.addView(Ui.space(a, 32));

        col.addView(section(a, a.getString(R.string.by_reason)));
        List<String[]> reasons = reasons(a, s.bySignal);
        int max = 0;
        for (String[] r : reasons) max = Math.max(max, Integer.parseInt(r[1]));
        for (String[] r : reasons) col.addView(Charts.breakdownRow(a, r[0], Integer.parseInt(r[1]), max, R.color.brand));
        col.addView(Ui.space(a, 32));

        col.addView(section(a, a.getString(R.string.by_hour)));
        int busiest = s.busiestHour();
        if (busiest >= 0) {
            TextView b = Ui.muted(a, a.getString(R.string.busiest_hour, busiest, (busiest + 1) % 24));
            b.setPadding(0, 0, 0, Ui.dp(a, 12));
            col.addView(b);
        }
        int[][] perHour = new int[24][2];
        String[] hourLabels = new String[24];
        for (int h = 0; h < 24; h++) {
            perHour[h][0] = s.byHour[h];
            if (h % 6 == 0) hourLabels[h] = h + "h";
        }
        col.addView(new Charts.Bars(a, perHour, hourLabels, 90));
        return scroll;
    }

    static LinearLayout header(MainActivity a, String title, String subtitle) {
        LinearLayout h = Ui.column(a);
        h.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 28));
        h.addView(Ui.display(a, title, 34, R.color.ink));
        if (subtitle != null) {
            TextView s = Ui.muted(a, subtitle);
            s.setPadding(0, Ui.dp(a, 6), 0, 0);
            h.addView(s);
        }
        return h;
    }

    /** A section heading: small and grey, so it never looks like something to tap. */
    static TextView section(MainActivity a, String title) {
        TextView t = Ui.text(a, title, 13, R.color.muted, Ui.semibold());
        t.setLetterSpacing(0.04f);
        t.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 10));
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        t.setAccessibilityHeading(true);
        return t;
    }

    private static LinearLayout figure(MainActivity a, String value, String caption) {
        LinearLayout f = Ui.column(a);
        f.addView(Ui.display(a, value, 34, R.color.ink));
        TextView c = Ui.muted(a, caption);
        c.setPadding(0, Ui.dp(a, 4), Ui.dp(a, 8), 0);
        f.addView(c);
        return f;
    }

    static String duration(MainActivity a, int minutes) {
        if (minutes < 60) return a.getString(R.string.minutes_short, minutes);
        return a.getString(R.string.hours_short, minutes / 60, minutes % 60);
    }

    private static List<String[]> reasons(MainActivity a, Map<Engine.Signal, Integer> m) {
        List<String[]> out = new ArrayList<>();
        for (Map.Entry<Engine.Signal, Integer> e : m.entrySet()) {
            String name;
            switch (e.getKey()) {
                case USER_LIST: name = a.getString(R.string.sig_user_list); break;
                case ARCEP_RANGE: name = a.getString(R.string.sig_arcep_short); break;
                case LEARNED: name = a.getString(R.string.sig_learned_short); break;
                case FOREIGN_BLOCKED: name = a.getString(R.string.sig_foreign_short); break;
                case HIDDEN: name = a.getString(R.string.cat_hidden); break;
                case SENIOR_UNKNOWN: name = a.getString(R.string.sig_senior); break;
                default: continue;
            }
            out.add(new String[]{name, String.valueOf(e.getValue())});
        }
        Collections.sort(out, (x, y) -> Integer.parseInt(y[1]) - Integer.parseInt(x[1]));
        return out;
    }
}
