// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.engine;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** All the statistics, computed on the phone from the call history. Pure Java. */
public final class Stats {

    /** One screened call, as stored in the history. */
    public static final class Event {
        public final long time;
        public final String number;
        public final Engine.Action action;
        public final Engine.Category category;
        public final String reasons;
        public final int score;

        public Event(long time, String number, Engine.Action action, Engine.Category category, String reasons, int score) {
            this.time = time;
            this.number = number == null ? "" : number;
            this.action = action;
            this.category = category;
            this.reasons = reasons == null ? "" : reasons;
            this.score = score;
        }

        public boolean stopped() {
            return action != Engine.Action.ALLOW;
        }
    }

    public static final class Caller {
        public final String number;
        public final int calls;
        public final Engine.Category category;

        Caller(String number, int calls, Engine.Category category) {
            this.number = number;
            this.calls = calls;
            this.category = category;
        }
    }

    public int screened, blocked, silenced, allowed;
    public int stoppedToday, stoppedThisWeek, stoppedLastWeek, stoppedThisMonth;
    /** Oldest first; [i][0] blocked, [i][1] silenced. */
    public int[][] perDay;
    public int[][] perWeek;
    public final int[] byHour = new int[24];
    /** Monday = 0. */
    public final int[] byWeekday = new int[7];
    public final Map<Engine.Category, Integer> byCategory = new EnumMap<>(Engine.Category.class);
    public final Map<PhoneNumbers.Region, Integer> byRegion = new EnumMap<>(PhoneNumbers.Region.class);
    public final Map<Engine.Signal, Integer> bySignal = new EnumMap<>(Engine.Signal.class);
    public final List<Caller> topCallers = new ArrayList<>();
    public int distinctSpammers;
    /** Days since chut last stopped a call; -1 when it never has. */
    public int quietDays = -1;

    public int stopped() {
        return blocked + silenced;
    }

    /** Share of screened calls that chut stopped, 0–100. */
    public int spamShare() {
        return screened == 0 ? 0 : Math.round(100f * stopped() / screened);
    }

    /** Minutes not spent on spam, counting about 1.5 minutes per call answered and hung up. */
    public int minutesSaved() {
        return Math.round(stopped() * 1.5f);
    }

    public int busiestHour() {
        int best = -1, max = 0;
        for (int h = 0; h < 24; h++) if (byHour[h] > max) { max = byHour[h]; best = h; }
        return best;
    }

    public static Stats compute(List<Event> events, long now, ZoneId zone, int days, int weeks) {
        Stats s = new Stats();
        s.perDay = new int[days][2];
        s.perWeek = new int[weeks][2];
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = today.withDayOfMonth(1);
        Map<String, Integer> perCaller = new HashMap<>();
        Map<String, Engine.Category> callerCategory = new HashMap<>();
        long lastStop = -1;

        for (Event e : events) {
            s.screened++;
            switch (e.action) {
                case BLOCK: s.blocked++; break;
                case SILENCE: s.silenced++; break;
                default: s.allowed++; break;
            }
            if (!e.stopped()) continue;
            int col = e.action == Engine.Action.BLOCK ? 0 : 1;
            LocalDateTime t = Instant.ofEpochMilli(e.time).atZone(zone).toLocalDateTime();
            LocalDate d = t.toLocalDate();
            long ago = ChronoUnit.DAYS.between(d, today);
            if (ago >= 0 && ago < days) s.perDay[days - 1 - (int) ago][col]++;
            long weeksAgo = ChronoUnit.WEEKS.between(d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), weekStart);
            if (weeksAgo >= 0 && weeksAgo < weeks) s.perWeek[weeks - 1 - (int) weeksAgo][col]++;
            if (ago == 0) s.stoppedToday++;
            if (weeksAgo == 0) s.stoppedThisWeek++;
            if (weeksAgo == 1) s.stoppedLastWeek++;
            if (!d.isBefore(monthStart)) s.stoppedThisMonth++;

            s.byHour[t.getHour()]++;
            s.byWeekday[t.getDayOfWeek().getValue() - 1]++;
            inc(s.byCategory, e.category);
            inc(s.byRegion, PhoneNumbers.region(e.number));
            for (String code : e.reasons.split(";")) {
                if (code.isEmpty()) continue;
                int colon = code.indexOf(':');
                try {
                    inc(s.bySignal, Engine.Signal.valueOf(colon < 0 ? code : code.substring(0, colon)));
                } catch (IllegalArgumentException ignored) {
                    // a reason from a newer or older version
                }
            }
            if (!e.number.isEmpty()) {
                perCaller.merge(e.number, 1, Integer::sum);
                callerCategory.put(e.number, e.category);
            }
            lastStop = Math.max(lastStop, e.time);
        }

        s.distinctSpammers = perCaller.size();
        List<Map.Entry<String, Integer>> callers = new ArrayList<>(perCaller.entrySet());
        Collections.sort(callers, (a, b) -> b.getValue() != a.getValue().intValue()
                ? b.getValue() - a.getValue() : a.getKey().compareTo(b.getKey()));
        for (int i = 0; i < callers.size() && i < 5; i++) {
            Map.Entry<String, Integer> c = callers.get(i);
            s.topCallers.add(new Caller(c.getKey(), c.getValue(), callerCategory.get(c.getKey())));
        }
        if (lastStop >= 0) {
            s.quietDays = (int) ChronoUnit.DAYS.between(Instant.ofEpochMilli(lastStop).atZone(zone).toLocalDate(), today);
        }
        return s;
    }

    private static <K> void inc(Map<K, Integer> m, K k) {
        m.merge(k, 1, Integer::sum);
    }
}
