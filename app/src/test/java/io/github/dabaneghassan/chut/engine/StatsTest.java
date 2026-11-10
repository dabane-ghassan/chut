// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dabaneghassan.chut.engine;

import static org.junit.Assert.assertEquals;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class StatsTest {

    static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    static long at(int month, int day, int hour) {
        return ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, PARIS).toInstant().toEpochMilli();
    }

    @Test
    public void countsAndBuckets() {
        List<Stats.Event> ev = new ArrayList<>();
        // Tuesday 10 November 2026 is "today".
        ev.add(new Stats.Event(at(11, 10, 9), "+33162434166", Engine.Action.BLOCK, Engine.Category.TELEMARKETING, "ARCEP_RANGE:0162", 70));
        ev.add(new Stats.Event(at(11, 10, 11), "+33162434166", Engine.Action.BLOCK, Engine.Category.TELEMARKETING, "ARCEP_RANGE:0162", 70));
        ev.add(new Stats.Event(at(11, 9, 18), "+33612345678", Engine.Action.SILENCE, Engine.Category.SPOOFED, "SPOOFED", 60));
        ev.add(new Stats.Event(at(11, 9, 18), "+33698765432", Engine.Action.ALLOW, Engine.Category.CLEAN, "", 0));
        ev.add(new Stats.Event(at(11, 3, 10), "+33424036062", Engine.Action.BLOCK, Engine.Category.PERSONAL, "USER_LIST:+33424036062", 100));
        ev.add(new Stats.Event(at(10, 1, 10), "", Engine.Action.BLOCK, Engine.Category.HIDDEN, "HIDDEN", 100));

        Stats s = Stats.compute(ev, at(11, 10, 12), PARIS, 30, 12);

        assertEquals(6, s.screened);
        assertEquals(4, s.blocked);
        assertEquals(1, s.silenced);
        assertEquals(1, s.allowed);
        assertEquals(83, s.spamShare());
        assertEquals(2, s.stoppedToday);
        assertEquals(3, s.stoppedThisWeek);          // Mon 9 + Tue 10
        assertEquals(1, s.stoppedLastWeek);          // Tue 3
        assertEquals(4, s.stoppedThisMonth);
        assertEquals(2, s.perDay[29][0]);            // today, blocked
        assertEquals(1, s.perDay[28][1]);            // yesterday, silenced
        assertEquals(3, s.perWeek[11][0] + s.perWeek[11][1]);
        assertEquals(2, (int) s.byCategory.get(Engine.Category.TELEMARKETING));
        assertEquals(2, (int) s.bySignal.get(Engine.Signal.ARCEP_RANGE));
        assertEquals(2, (int) s.byRegion.get(PhoneNumbers.Region.IDF));
        assertEquals("+33162434166", s.topCallers.get(0).number);
        assertEquals(2, s.topCallers.get(0).calls);
        assertEquals(3, s.distinctSpammers);
        assertEquals(0, s.quietDays);
        assertEquals(3, s.byWeekday[1]);             // Tuesdays 3 and 10 November
        assertEquals(1, s.byWeekday[0]);             // Monday 9 November
        assertEquals(2, s.byHour[10]);               // 3 November and 1 October
        assertEquals(10, s.busiestHour());
    }

    @Test
    public void emptyHistory() {
        Stats s = Stats.compute(new ArrayList<>(), at(11, 10, 12), PARIS, 14, 4);
        assertEquals(0, s.spamShare());
        assertEquals(-1, s.busiestHour());
        assertEquals(-1, s.quietDays);
    }
}
