// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The call filter. It runs on the phone, in a fraction of a millisecond, with no network.
 *
 * <p>Hard rules come first: your allow list and your contacts always ring, your block list
 * (with the numbers chut ships with) never does. Then two signals give a spam score: the
 * ranges ARCEP reserves for telemarketing, and the {@link Learner model} trained on what you
 * blocked. They combine as a "noisy OR", 1 − Π(1 − wᵢ), so the score never passes 100.
 * Depending on your sensitivity, the score lets the call ring, silences it (it rings without
 * sound and you get a notification) or blocks it. Every verdict keeps its reasons.
 */
public final class Engine {

    public enum Action { ALLOW, SILENCE, BLOCK }

    public enum Category { CLEAN, PERSONAL, TELEMARKETING, LEARNED, FOREIGN, HIDDEN, UNKNOWN }

    /** Why a call got its verdict. */
    public enum Signal {
        ALLOWLIST(Category.CLEAN),
        CONTACT(Category.CLEAN),
        USER_LIST(Category.PERSONAL),
        HIDDEN(Category.HIDDEN),
        ARCEP_RANGE(Category.TELEMARKETING),
        LEARNED(Category.LEARNED),
        FOREIGN_BLOCKED(Category.FOREIGN),
        SENIOR_UNKNOWN(Category.UNKNOWN),
        CALLED_BACK(Category.CLEAN);

        public final Category category;

        Signal(Category category) {
            this.category = category;
        }
    }

    public static final int ARCEP_POINTS = 70;
    /** The model's score is capped: a learned guess alone is never "certain". */
    public static final int LEARNED_MAX = 95;

    /** The user's choices. Defaults match a fresh install. */
    public static final class Config {
        /** 0 relaxed, 1 balanced, 2 strict. */
        public int sensitivity = 1;
        public boolean arcepRanges = true;
        public boolean learn = true;
        public boolean blockHidden = false;
        public boolean blockForeign = false;
        public boolean contactsAlwaysRing = true;
        /** A silenced caller who calls back within a few minutes rings through. */
        public boolean letCallbacksThrough = true;
        /** Senior protection: strict sensitivity, and every unknown number is at least silenced. */
        public boolean seniorMode = false;

        public int blockThreshold() {
            switch (effectiveSensitivity()) {
                case 0: return 90;
                case 2: return 50;
                default: return 70;
            }
        }

        public int silenceThreshold() {
            switch (effectiveSensitivity()) {
                case 0: return 60;
                case 2: return 25;
                default: return 40;
            }
        }

        int effectiveSensitivity() {
            return seniorMode ? 2 : sensitivity;
        }
    }

    /** What the engine may ask about the user's data. */
    public interface Lists {
        boolean isAllowed(String number);

        /** The block-list entry (exact number or prefix rule) matching this number, or null. */
        String blockedBy(String number);

        /** Calls already received from this number since the given time (ms). */
        int callsSince(String number, long sinceMillis);
    }

    /** One incoming call. */
    public static final class Call {
        public final String number;   // E.164, "" when hidden
        public final boolean isContact;
        public final int hourOfDay;
        public final long now;

        public Call(String rawNumber, boolean isContact, int hourOfDay, long now) {
            this.number = PhoneNumbers.normalize(rawNumber);
            this.isContact = isContact;
            this.hourOfDay = hourOfDay;
            this.now = now;
        }
    }

    public static final class Reason {
        public final Signal signal;
        public final String detail;
        public final int points;

        Reason(Signal signal, String detail, int points) {
            this.signal = signal;
            this.detail = detail;
            this.points = points;
        }

        @Override
        public String toString() {
            return detail == null ? signal.name() : signal.name() + ":" + detail;
        }
    }

    public static final class Verdict {
        public final Action action;
        public final int score;
        public final Category category;
        public final List<Reason> reasons;

        Verdict(Action action, int score, Category category, List<Reason> reasons) {
            this.action = action;
            this.score = score;
            this.category = category;
            this.reasons = Collections.unmodifiableList(reasons);
        }

        /** Compact form stored in the history: "ARCEP_RANGE:0162;LEARNED:87:037725". */
        public String reasonCodes() {
            StringBuilder sb = new StringBuilder();
            for (Reason r : reasons) {
                if (sb.length() > 0) sb.append(';');
                sb.append(r);
            }
            return sb.toString();
        }

        public boolean has(Signal s) {
            for (Reason r : reasons) if (r.signal == s) return true;
            return false;
        }
    }

    static final long CALLBACK_WINDOW_MS = 5 * 60 * 1000L;

    private Engine() {
    }

    public static Verdict evaluate(Call call, Config cfg, Lists lists, Learner.Model model) {
        List<Reason> reasons = new ArrayList<>();
        String n = call.number;

        // 1. Hard rules.
        if (n.isEmpty()) {
            if (cfg.blockHidden) return hard(Action.BLOCK, Signal.HIDDEN, null);
            if (cfg.seniorMode) return new Verdict(Action.SILENCE, 0, Category.HIDDEN, one(Signal.SENIOR_UNKNOWN, null));
            return new Verdict(Action.ALLOW, 0, Category.CLEAN, reasons);
        }
        if (lists.isAllowed(n)) return hard(Action.ALLOW, Signal.ALLOWLIST, null);
        if (call.isContact && cfg.contactsAlwaysRing) return hard(Action.ALLOW, Signal.CONTACT, null);
        String rule = lists.blockedBy(n);
        if (rule != null) return hard(Action.BLOCK, Signal.USER_LIST, rule);
        PhoneNumbers.Kind kind = PhoneNumbers.kind(n);
        if (cfg.blockForeign && kind == PhoneNumbers.Kind.FOREIGN) {
            return hard(Action.BLOCK, Signal.FOREIGN_BLOCKED, PhoneNumbers.countryCode(n));
        }

        // 2. Signals.
        if (cfg.arcepRanges && kind == PhoneNumbers.Kind.TELEMARKETING) {
            reasons.add(new Reason(Signal.ARCEP_RANGE, PhoneNumbers.national(n).substring(0, 4), ARCEP_POINTS));
        }
        if (cfg.learn && model != null && model.ready()) {
            double p = model.predict(n, call.hourOfDay);
            if (p >= Learner.THRESHOLD) {
                int points = (int) Math.min(LEARNED_MAX, Math.round(p * 100));
                String prefix = model.strongestPrefix(n);
                reasons.add(new Reason(Signal.LEARNED, points + (prefix == null ? "" : ":" + prefix), points));
            }
        }

        // 3. Combine.
        int score = combine(reasons);
        Action action = score >= cfg.blockThreshold() ? Action.BLOCK
                : score >= cfg.silenceThreshold() ? Action.SILENCE : Action.ALLOW;

        if (cfg.seniorMode && action == Action.ALLOW) {
            action = Action.SILENCE;
            reasons.add(new Reason(Signal.SENIOR_UNKNOWN, null, 0));
        }
        // A person who really needs you calls back; most robots move on to the next number.
        if (cfg.letCallbacksThrough && action == Action.SILENCE && !cfg.seniorMode
                && lists.callsSince(n, call.now - CALLBACK_WINDOW_MS) > 0) {
            action = Action.ALLOW;
            reasons.add(new Reason(Signal.CALLED_BACK, null, 0));
        }
        return new Verdict(action, score, mainCategory(reasons), reasons);
    }

    /** Noisy OR of the reasons' points, 0–100. */
    static int combine(List<Reason> reasons) {
        double keep = 1.0;
        for (Reason r : reasons) keep *= 1.0 - Math.min(100, r.points) / 100.0;
        return (int) Math.round((1.0 - keep) * 100);
    }

    private static Category mainCategory(List<Reason> reasons) {
        Reason best = null;
        for (Reason r : reasons) if (best == null || r.points > best.points) best = r;
        if (best == null) return Category.CLEAN;
        return best.points == 0 && best.signal != Signal.SENIOR_UNKNOWN ? Category.CLEAN : best.signal.category;
    }

    private static Verdict hard(Action action, Signal s, String detail) {
        return new Verdict(action, action == Action.BLOCK ? 100 : 0, s.category, one(s, detail));
    }

    private static List<Reason> one(Signal s, String detail) {
        List<Reason> l = new ArrayList<>();
        l.add(new Reason(s, detail, s == Signal.USER_LIST || s == Signal.HIDDEN || s == Signal.FOREIGN_BLOCKED ? 100 : 0));
        return l;
    }
}
