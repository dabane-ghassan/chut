// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dabaneghassan.chut.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class EngineTest {

    /** In-memory lists for tests. */
    static class FakeLists implements Engine.Lists {
        final Set<String> allowed = new HashSet<>();
        final Set<String> blocked = new HashSet<>();
        final Map<String, String> community = new HashMap<>();
        final Map<String, Long> lastCall = new HashMap<>();

        @Override
        public boolean isAllowed(String n) {
            return allowed.contains(n);
        }

        @Override
        public String blockedBy(String n) {
            if (blocked.contains(n)) return n;
            for (String b : blocked) if (b.endsWith("*") && n.startsWith(b.substring(0, b.length() - 1))) return b;
            return null;
        }

        @Override
        public String communityCategory(String n) {
            return community.get(n);
        }

        @Override
        public int blockedNeighbours(String block, String n) {
            Set<String> all = new HashSet<>(blocked);
            all.addAll(community.keySet());
            int k = 0;
            for (String b : all) if (!b.endsWith("*") && b.startsWith(block) && !b.equals(n)) k++;
            return k;
        }

        @Override
        public int callsSince(String n, long since) {
            Long t = lastCall.get(n);
            return t != null && t >= since ? 1 : 0;
        }
    }

    static final long NOON = ZonedDateTime.of(2026, 11, 10, 12, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant().toEpochMilli();

    static Engine.Verdict eval(String number, Engine.Config cfg, FakeLists lists) {
        return Engine.evaluate(new Engine.Call(number, Engine.VERIFICATION_UNKNOWN, false, 12, NOON), cfg, lists);
    }

    @Test
    public void normalizesEveryFrenchForm() {
        assertEquals("+33424036062", PhoneNumbers.normalize("04 24 03 60 62"));
        assertEquals("+33424036062", PhoneNumbers.normalize("0033424036062"));
        assertEquals("+33424036062", PhoneNumbers.normalize("33424036062"));
        assertEquals("+33424036062", PhoneNumbers.normalize("+33 (0)4 24 03 60 62"));
        assertEquals("+33424036062", PhoneNumbers.normalize("tel:+33424036062"));
        assertEquals("+33162*", PhoneNumbers.normalize("0162*"));
        assertEquals("3179", PhoneNumbers.normalize("3179"));
        assertEquals("", PhoneNumbers.normalize(null));
        assertEquals("", PhoneNumbers.normalize("+"));
    }

    @Test
    public void prettyPrints() {
        assertEquals("04 24 03 60 62", PhoneNumbers.pretty("+33424036062"));
        assertEquals("01 62 …", PhoneNumbers.pretty("+33162*"));
        assertEquals("+225 07 12 34 56 78", PhoneNumbers.pretty("+2250712345678"));
        assertEquals("+44 20 79 46 09 58", PhoneNumbers.pretty("+442079460958"));
    }

    @Test
    public void classifiesNumbers() {
        assertEquals(PhoneNumbers.Kind.TELEMARKETING, PhoneNumbers.kind("+33162434166"));
        assertEquals(PhoneNumbers.Kind.TELEMARKETING, PhoneNumbers.kind("+33949000000"));
        assertEquals(PhoneNumbers.Kind.LANDLINE, PhoneNumbers.kind("+33187686895"));
        assertEquals(PhoneNumbers.Kind.VOIP, PhoneNumbers.kind("+33978808220"));
        assertEquals(PhoneNumbers.Kind.MOBILE, PhoneNumbers.kind("+33612345678"));
        assertEquals(PhoneNumbers.Kind.PREMIUM, PhoneNumbers.kind("+33899123456"));
        assertEquals(PhoneNumbers.Kind.TOLL_FREE, PhoneNumbers.kind("+33800123456"));
        assertEquals(PhoneNumbers.Kind.INVALID, PhoneNumbers.kind("+3361234567"));
        assertEquals(PhoneNumbers.Kind.FOREIGN, PhoneNumbers.kind("+442079460958"));
        assertEquals(PhoneNumbers.Kind.OVERSEAS, PhoneNumbers.kind("+262692123456"));
        assertEquals(PhoneNumbers.Kind.HIDDEN, PhoneNumbers.kind(""));
        assertEquals("44", PhoneNumbers.countryCode("+442079460958"));
        assertEquals("1", PhoneNumbers.countryCode("+12025550123"));
        assertEquals("225", PhoneNumbers.countryCode("+2250712345678"));
        assertEquals(PhoneNumbers.Region.SOUTH_EAST, PhoneNumbers.region("+33424036062"));
        assertEquals(PhoneNumbers.Region.IDF, PhoneNumbers.region("+33162434166"));
    }

    @Test
    public void ordinaryCallsRing() {
        Engine.Verdict v = eval("0612345678", new Engine.Config(), new FakeLists());
        assertEquals(Engine.Action.ALLOW, v.action);
        assertEquals(0, v.score);
        assertEquals(Engine.Category.CLEAN, v.category);
    }

    @Test
    public void arcepRangeIsBlockedWhenBalancedAndSilencedWhenRelaxed() {
        Engine.Config cfg = new Engine.Config();
        Engine.Verdict v = eval("01 62 43 41 66", cfg, new FakeLists());
        assertEquals(Engine.Action.BLOCK, v.action);
        assertEquals(Engine.Category.TELEMARKETING, v.category);
        assertTrue(v.has(Engine.Signal.ARCEP_RANGE));

        cfg.sensitivity = 0;
        assertEquals(Engine.Action.SILENCE, eval("0162434166", cfg, new FakeLists()).action);

        cfg.arcepRanges = false;
        assertEquals(Engine.Action.ALLOW, eval("0162434166", cfg, new FakeLists()).action);
    }

    @Test
    public void hardRulesWin() {
        FakeLists l = new FakeLists();
        l.allowed.add("+33162434166");
        assertEquals(Engine.Action.ALLOW, eval("0162434166", new Engine.Config(), l).action);

        l = new FakeLists();
        l.blocked.add("+33612345678");
        Engine.Verdict v = eval("06 12 34 56 78", new Engine.Config(), l);
        assertEquals(Engine.Action.BLOCK, v.action);
        assertEquals(Engine.Category.PERSONAL, v.category);

        l.blocked.add("+33187*");
        assertEquals(Engine.Action.BLOCK, eval("0187686895", new Engine.Config(), l).action);
    }

    @Test
    public void contactsAlwaysRing() {
        Engine.Verdict v = Engine.evaluate(new Engine.Call("0162434166", 0, true, 12, NOON), new Engine.Config(), new FakeLists());
        assertEquals(Engine.Action.ALLOW, v.action);
        assertTrue(v.has(Engine.Signal.CONTACT));
    }

    @Test
    public void learnsFromNeighbouringSpamNumbers() {
        // The user's real history: three numbers in the 03 77 25 9x xx block.
        FakeLists l = new FakeLists();
        l.blocked.add("+33377259405");
        l.blocked.add("+33377259522");
        l.blocked.add("+33377259230");
        Engine.Config cfg = new Engine.Config();
        cfg.arcepRanges = false;   // learning alone
        Engine.Verdict v = eval("03 77 25 91 11", cfg, l);
        assertTrue(v.has(Engine.Signal.LEARNED_STRONG));
        assertEquals(Engine.Action.SILENCE, v.action);

        // A number outside any spam block is left alone.
        assertEquals(Engine.Action.ALLOW, eval("03 88 12 34 56", cfg, l).action);
    }

    @Test
    public void signalsCombineAsNoisyOr() {
        List<Engine.Reason> r = new ArrayList<>();
        r.add(new Engine.Reason(Engine.Signal.ARCEP_RANGE, null));       // 70
        r.add(new Engine.Reason(Engine.Signal.LEARNED_STRONG, "3"));     // 60
        assertEquals(88, Engine.combine(r));
        r.add(new Engine.Reason(Engine.Signal.USER_LIST, null));         // 100
        assertEquals(100, Engine.combine(r));
    }

    @Test
    public void spoofedCallerIdIsSilenced() {
        Engine.Verdict v = Engine.evaluate(new Engine.Call("0612345678", Engine.VERIFICATION_FAILED, false, 12, NOON),
                new Engine.Config(), new FakeLists());
        assertEquals(Engine.Action.SILENCE, v.action);
        assertEquals(Engine.Category.SPOOFED, v.category);
    }

    @Test
    public void communityScamIsBlocked() {
        FakeLists l = new FakeLists();
        l.community.put("+33612345678", "scam");
        Engine.Verdict v = eval("0612345678", new Engine.Config(), l);
        assertEquals(Engine.Action.BLOCK, v.action);
        assertEquals(Engine.Category.SCAM, v.category);

        Engine.Config off = new Engine.Config();
        off.community = false;
        assertEquals(Engine.Action.ALLOW, eval("0612345678", off, l).action);
    }

    @Test
    public void foreignNumbers() {
        Engine.Config cfg = new Engine.Config();
        assertEquals(Engine.Action.ALLOW, eval("+442079460958", cfg, new FakeLists()).action);
        assertEquals(Engine.Action.SILENCE, eval("+2250712345678", cfg, new FakeLists()).action);
        cfg.blockForeign = true;
        assertEquals(Engine.Action.BLOCK, eval("+442079460958", cfg, new FakeLists()).action);
        // Overseas France is not foreign.
        assertEquals(Engine.Action.ALLOW, eval("+262692123456", cfg, new FakeLists()).action);
    }

    @Test
    public void hiddenNumbers() {
        Engine.Config cfg = new Engine.Config();
        assertEquals(Engine.Action.ALLOW, eval("", cfg, new FakeLists()).action);
        cfg.blockHidden = true;
        Engine.Verdict v = eval(null, cfg, new FakeLists());
        assertEquals(Engine.Action.BLOCK, v.action);
        assertEquals(Engine.Category.HIDDEN, v.category);
    }

    @Test
    public void callbackWithinFiveMinutesRingsThrough() {
        FakeLists l = new FakeLists();
        Engine.Config cfg = new Engine.Config();
        cfg.sensitivity = 0;
        assertEquals(Engine.Action.SILENCE, eval("0162434166", cfg, l).action);
        l.lastCall.put("+33162434166", NOON - 2 * 60 * 1000L);
        Engine.Verdict v = eval("0162434166", cfg, l);
        assertEquals(Engine.Action.ALLOW, v.action);
        assertTrue(v.has(Engine.Signal.CALLED_BACK));
        // but never a blocked one
        cfg.sensitivity = 1;
        assertEquals(Engine.Action.BLOCK, eval("0162434166", cfg, l).action);
    }

    @Test
    public void seniorModeSilencesUnknownCallers() {
        Engine.Config cfg = new Engine.Config();
        cfg.seniorMode = true;
        Engine.Verdict v = eval("0612345678", cfg, new FakeLists());
        assertEquals(Engine.Action.SILENCE, v.action);
        assertTrue(v.has(Engine.Signal.SENIOR_UNKNOWN));
        // strict: a spoofed number is blocked outright
        v = Engine.evaluate(new Engine.Call("0612345678", Engine.VERIFICATION_FAILED, false, 12, NOON), cfg, new FakeLists());
        assertEquals(Engine.Action.BLOCK, v.action);
        // contacts still ring
        v = Engine.evaluate(new Engine.Call("0612345678", 0, true, 12, NOON), cfg, new FakeLists());
        assertEquals(Engine.Action.ALLOW, v.action);
    }

    @Test
    public void quietHoursWrapAroundMidnight() {
        assertTrue(Engine.inQuietHours(23, 22, 8));
        assertTrue(Engine.inQuietHours(3, 22, 8));
        assertFalse(Engine.inQuietHours(12, 22, 8));
        assertTrue(Engine.inQuietHours(13, 12, 14));
        assertFalse(Engine.inQuietHours(5, 5, 5));
    }

    @Test
    public void reasonCodesRoundTrip() {
        Engine.Verdict v = eval("0162434166", new Engine.Config(), new FakeLists());
        assertEquals("ARCEP_RANGE:0162", v.reasonCodes());
        assertNull(new FakeLists().blockedBy("+33100000000"));
    }
}
