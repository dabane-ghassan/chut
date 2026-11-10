// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.ui;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.dabaneghassan.chut.MainActivity;
import io.github.dabaneghassan.chut.R;
import io.github.dabaneghassan.chut.data.Store;
import io.github.dabaneghassan.chut.engine.Engine;
import io.github.dabaneghassan.chut.engine.PhoneNumbers;
import io.github.dabaneghassan.chut.engine.Stats;

/** Every statistic, free: totals, trends, types, reasons, regions, hours and the most insistent callers. */
public final class StatsScreen {

    private StatsScreen() {
    }

    public static View build(MainActivity a) {
        long now = System.currentTimeMillis();
        List<Stats.Event> events = Store.get(a).events(now - 400L * 86_400_000L);
        Stats s = Stats.compute(events, now, ZoneId.systemDefault(), 30, 12);

        ScrollView scroll = new ScrollView(a);
        LinearLayout col = Ui.column(a);
        int pad = Ui.dp(a, 16);
        col.setPadding(pad, Ui.dp(a, 8), pad, Ui.dp(a, 24));
        scroll.addView(col);
        col.addView(header(a, a.getString(R.string.tab_stats), a.getString(R.string.stats_subtitle)));

        if (s.screened == 0) {
            LinearLayout empty = Ui.card(a);
            empty.addView(Ui.body(a, a.getString(R.string.stats_empty)));
            col.addView(empty);
            return scroll;
        }

        col.addView(Ui.grid(a, 2,
                Ui.tile(a, String.valueOf(s.stopped()), a.getString(R.string.stat_stopped), R.color.brand_ink),
                Ui.tile(a, s.spamShare() + " %", a.getString(R.string.stat_share), R.color.ink),
                Ui.tile(a, String.valueOf(s.blocked), a.getString(R.string.stat_blocked), R.color.block),
                Ui.tile(a, String.valueOf(s.silenced), a.getString(R.string.stat_silenced), R.color.silence),
                Ui.tile(a, "≈ " + duration(a, s.minutesSaved()), a.getString(R.string.stat_saved), R.color.allow),
                Ui.tile(a, String.valueOf(s.distinctSpammers), a.getString(R.string.stat_distinct), R.color.ink)));

        // Trend
        LinearLayout week = Ui.card(a);
        week.addView(Ui.title(a, a.getString(R.string.this_week_vs_last)));
        int diff = s.stoppedThisWeek - s.stoppedLastWeek;
        String trend = diff == 0 ? a.getString(R.string.trend_same)
                : a.getString(diff > 0 ? R.string.trend_more : R.string.trend_less, Math.abs(diff));
        TextView t = Ui.muted(a, a.getString(R.string.week_line, s.stoppedThisWeek, s.stoppedLastWeek, trend));
        t.setPadding(0, Ui.dp(a, 4), 0, 0);
        week.addView(t);
        col.addView(week);

        LinearLayout days = Ui.card(a);
        days.addView(Ui.title(a, a.getString(R.string.last_30_days)));
        days.addView(Ui.space(a, 12));
        String[] dayLabels = new String[30];
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 30; i++) if ((29 - i) % 7 == 0) dayLabels[i] = String.valueOf(today.minusDays(29 - i).getDayOfMonth());
        days.addView(new Charts.Bars(a, s.perDay, dayLabels, 130));
        days.addView(Charts.legend(a));
        col.addView(days);

        LinearLayout weeks = Ui.card(a);
        weeks.addView(Ui.title(a, a.getString(R.string.last_12_weeks)));
        weeks.addView(Ui.space(a, 12));
        String[] weekLabels = new String[12];
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int i = 0; i < 12; i++) {
            if (i % 3 == 2) {
                LocalDate d = monday.minusWeeks(11 - i);
                weekLabels[i] = d.getDayOfMonth() + "/" + d.getMonthValue();
            }
        }
        weeks.addView(new Charts.Bars(a, s.perWeek, weekLabels, 120));
        col.addView(weeks);

        // Breakdowns
        col.addView(breakdown(a, a.getString(R.string.by_type), sortedCategories(a, s.byCategory), R.color.brand));
        col.addView(breakdown(a, a.getString(R.string.by_reason), sortedSignals(a, s.bySignal), R.color.block));
        col.addView(breakdown(a, a.getString(R.string.by_region), sortedRegions(a, s.byRegion), R.color.silence));

        // When
        LinearLayout hours = Ui.card(a);
        hours.addView(Ui.title(a, a.getString(R.string.by_hour)));
        int busiest = s.busiestHour();
        if (busiest >= 0) {
            TextView b = Ui.muted(a, a.getString(R.string.busiest_hour, busiest, (busiest + 1) % 24));
            b.setPadding(0, Ui.dp(a, 4), 0, 0);
            hours.addView(b);
        }
        hours.addView(Ui.space(a, 12));
        int[][] perHour = new int[24][2];
        String[] hourLabels = new String[24];
        for (int h = 0; h < 24; h++) {
            perHour[h][0] = s.byHour[h];
            if (h % 6 == 0) hourLabels[h] = h + "h";
        }
        hours.addView(new Charts.Bars(a, perHour, hourLabels, 100));
        hours.addView(Ui.space(a, 16));
        int[][] perDay = new int[7][2];
        String[] dayNames = new String[7];
        for (int d = 0; d < 7; d++) {
            perDay[d][0] = s.byWeekday[d];
            dayNames[d] = DayOfWeek.of(d + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault());
        }
        hours.addView(new Charts.Bars(a, perDay, dayNames, 90));
        col.addView(hours);

        // Who
        if (!s.topCallers.isEmpty()) {
            LinearLayout top = Ui.card(a);
            top.addView(Ui.title(a, a.getString(R.string.top_callers)));
            top.addView(Ui.space(a, 6));
            for (Stats.Caller c : s.topCallers) {
                LinearLayout row = Ui.linkRow(a, PhoneNumbers.pretty(c.number),
                        a.getResources().getQuantityString(R.plurals.calls_short, c.calls, c.calls), v -> {
                            for (int i = events.size() - 1; i >= 0; i--) {
                                if (events.get(i).number.equals(c.number)) {
                                    a.showCall(events.get(i));
                                    break;
                                }
                            }
                        });
                top.addView(row);
            }
            col.addView(top);
        }

        LinearLayout foot = Ui.card(a);
        foot.addView(Ui.muted(a, a.getString(R.string.stats_privacy)));
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(a, 12);
        foot.addView(Ui.button(a, a.getString(R.string.export_csv), false, v -> a.exportFile()), lp);
        col.addView(foot);
        return scroll;
    }

    static LinearLayout header(MainActivity a, String title, String subtitle) {
        LinearLayout h = Ui.column(a);
        h.setPadding(Ui.dp(a, 4), Ui.dp(a, 14), 0, Ui.dp(a, 14));
        h.addView(Ui.display(a, title, 34, R.color.ink));
        if (subtitle != null) {
            TextView s = Ui.muted(a, subtitle);
            s.setPadding(0, Ui.dp(a, 4), 0, 0);
            h.addView(s);
        }
        return h;
    }

    static String duration(MainActivity a, int minutes) {
        if (minutes < 60) return a.getString(R.string.minutes_short, minutes);
        return a.getString(R.string.hours_short, minutes / 60, minutes % 60);
    }

    private static final class Item {
        final String name;
        final int value;

        Item(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    private static LinearLayout breakdown(MainActivity a, String title, List<Item> items, int colorRes) {
        LinearLayout card = Ui.card(a);
        card.addView(Ui.title(a, title));
        card.addView(Ui.space(a, 6));
        int max = 0;
        for (Item i : items) max = Math.max(max, i.value);
        for (Item i : items) card.addView(Charts.breakdownRow(a, i.name, i.value, max, colorRes));
        return card;
    }

    private static List<Item> sortedCategories(MainActivity a, Map<Engine.Category, Integer> m) {
        List<Item> out = new ArrayList<>();
        for (Map.Entry<Engine.Category, Integer> e : m.entrySet()) out.add(new Item(Labels.category(a, e.getKey()), e.getValue()));
        return sort(out);
    }

    private static List<Item> sortedRegions(MainActivity a, Map<PhoneNumbers.Region, Integer> m) {
        List<Item> out = new ArrayList<>();
        for (Map.Entry<PhoneNumbers.Region, Integer> e : m.entrySet()) out.add(new Item(Labels.region(a, e.getKey()), e.getValue()));
        return sort(out);
    }

    private static List<Item> sortedSignals(MainActivity a, Map<Engine.Signal, Integer> m) {
        List<Item> out = new ArrayList<>();
        for (Map.Entry<Engine.Signal, Integer> e : m.entrySet()) {
            Engine.Signal s = e.getKey();
            if (s == Engine.Signal.CALLED_BACK || s == Engine.Signal.CONTACT || s == Engine.Signal.ALLOWLIST) continue;
            out.add(new Item(signalName(a, s), e.getValue()));
        }
        // merge the three learning levels under one name
        List<Item> merged = new ArrayList<>();
        for (Item i : out) {
            Item same = null;
            for (Item m2 : merged) if (m2.name.equals(i.name)) same = m2;
            if (same == null) merged.add(i);
            else {
                merged.remove(same);
                merged.add(new Item(i.name, i.value + same.value));
            }
        }
        return sort(merged);
    }

    /** Short signal names for the breakdown (without the per-call details). */
    private static String signalName(MainActivity a, Engine.Signal s) {
        switch (s) {
            case USER_LIST: return a.getString(R.string.sig_user_list);
            case ARCEP_RANGE: return a.getString(R.string.sig_arcep_short);
            case LEARNED_STRONG:
            case LEARNED:
            case LEARNED_WEAK: return a.getString(R.string.sig_learned_short);
            case HIGH_RISK_COUNTRY: return a.getString(R.string.sig_high_risk_short);
            case FOREIGN_BLOCKED:
            case FOREIGN: return a.getString(R.string.sig_foreign_short);
            default: return Labels.signal(a, s, null);
        }
    }

    private static List<Item> sort(List<Item> l) {
        Collections.sort(l, (x, y) -> y.value - x.value);
        return l;
    }
}
