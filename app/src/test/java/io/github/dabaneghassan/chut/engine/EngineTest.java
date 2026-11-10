// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dabaneghassan.chut.engine;

import static org.junit.Assert.assertEquals;
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
        public int callsSince(String n, long since) {
            Long t = lastCall.get(n);
            return t != null && t >= since ? 1 : 0;
        }
    }

    static final long NOON = ZonedDateTime.of(2026, 11, 10, 12, 0, 0, 0, ZoneId.of("Europe/Paris")).toInstant().toEpochMilli();

    static Engine.Verdict eval(String number, Engine.Config cfg, FakeLists lists, Learner.Model model) {
        return Engine.evaluate(new Engine.Call(number, false, 12, NOON), cfg, lists, model);
    }

    static Engine.Verdict eval(String number, Engine.Config cfg, FakeLists lists) {
        return eval(number, cfg, lists, null);
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
        assertEquals("ARCEP_RANGE:0162", v.reasonCodes());

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
        assertNull(l.blockedBy("+33100000000"));
    }

    @Test
    public void contactsAlwaysRing() {
        Engine.Verdict v = Engine.evaluate(new Engine.Call("0162434166", true, 12, NOON), new Engine.Config(), new FakeLists(), null);
        assertEquals(Engine.Action.ALLOW, v.action);
        assertTrue(v.has(Engine.Signal.CONTACT));
    }

    @Test
    public void learnedModelFlagsNeighboursOfBlockedNumbers() {
        // Three numbers you blocked in 03 77 25 9x xx, plus a few others.
        FakeLists l = new FakeLists();
        List<Learner.Sample> history = new ArrayList<>();
        for (String n : new String[]{"+33377259405", "+33377259522", "+33377259230", "+33568305160", "+33568305236", "+33424288384"}) {
            l.blocked.add(n);
            history.add(new Learner.Sample(n, -1, true, 1));
        }
        Learner.Model model = Learner.train(history, 42, NOON);
        Engine.Config cfg = new Engine.Config();
        cfg.arcepRanges = false;   // the model alone

        Engine.Verdict v = eval("03 77 25 91 11", cfg, l, model);
        assertTrue(v.reasonCodes(), v.has(Engine.Signal.LEARNED));
        assertTrue(v.action != Engine.Action.ALLOW);
        assertEquals(Engine.Category.LEARNED, v.category);
        assertTrue(v.reasonCodes().contains(":0377259"));

        // A number unlike anything you blocked is left alone.
        assertEquals(Engine.Action.ALLOW, eval("06 98 76 54 32", cfg, l, model).action);
        // Learning off: nothing.
        cfg.learn = false;
        assertEquals(Engine.Action.ALLOW, eval("03 77 25 91 11", cfg, l, model).action);
    }

    @Test
    public void signalsCombineAsNoisyOr() {
        List<Engine.Reason> r = new ArrayList<>();
        r.add(new Engine.Reason(Engine.Signal.ARCEP_RANGE, null, 70));
        r.add(new Engine.Reason(Engine.Signal.LEARNED, "60", 60));
        assertEquals(88, Engine.combine(r));
        r.add(new Engine.Reason(Engine.Signal.USER_LIST, null, 100));
        assertEquals(100, Engine.combine(r));
    }

    @Test
    public void foreignNumbers() {
        Engine.Config cfg = new Engine.Config();
        assertEquals(Engine.Action.ALLOW, eval("+442079460958", cfg, new FakeLists()).action);
        cfg.blockForeign = true;
        Engine.Verdict v = eval("+442079460958", cfg, new FakeLists());
        assertEquals(Engine.Action.BLOCK, v.action);
        assertEquals("FOREIGN_BLOCKED:44", v.reasonCodes());
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
        assertEquals(Engine.Category.UNKNOWN, v.category);
        // contacts still ring
        v = Engine.evaluate(new Engine.Call("0612345678", true, 12, NOON), cfg, new FakeLists(), null);
        assertEquals(Engine.Action.ALLOW, v.action);
        // hidden numbers are silenced too
        assertEquals(Engine.Action.SILENCE, eval("", cfg, new FakeLists()).action);
    }
}
