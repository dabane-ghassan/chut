// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The call filter. It runs on the phone, in a few microseconds, with no network.
 *
 * <p>Hard rules come first: allowed numbers and contacts always ring, numbers on your
 * block list never do. Everything else gets a spam score from independent signals,
 * each with a probability-like weight (ARCEP telemarketing range, community list,
 * failed caller-ID authentication, numbers next to ones you blocked, …). The weights
 * combine as a "noisy OR": 1 − Π(1 − wᵢ). Two weak signals make a stronger one, and the
 * score never passes 100. Depending on your sensitivity, the score lets the call ring,
 * silences it (it rings silently and you get a notification), or blocks it.
 *
 * <p>Every verdict keeps its reasons, so the app can always say why a call was stopped.
 */
public final class Engine {

    public enum Action { ALLOW, SILENCE, BLOCK }

    public enum Category { CLEAN, PERSONAL, TELEMARKETING, SCAM, SPOOFED, SUSPICIOUS, FOREIGN, HIDDEN }

    /** Why a call got its verdict. Weight is in points out of 100. */
    public enum Signal {
        ALLOWLIST(0, Category.CLEAN),
        CONTACT(0, Category.CLEAN),
        USER_LIST(100, Category.PERSONAL),
        HIDDEN(100, Category.HIDDEN),
        ARCEP_RANGE(70, Category.TELEMARKETING),
        COMMUNITY_SCAM(85, Category.SCAM),
        COMMUNITY_TELEMARKETING(75, Category.TELEMARKETING),
        SPOOFED(60, Category.SPOOFED),
        LEARNED_STRONG(60, Category.SUSPICIOUS),
        LEARNED(45, Category.SUSPICIOUS),
        LEARNED_WEAK(20, Category.SUSPICIOUS),
        PREMIUM(50, Category.SCAM),
        HIGH_RISK_COUNTRY(45, Category.SCAM),
        FOREIGN_BLOCKED(100, Category.FOREIGN),
        FOREIGN(10, Category.FOREIGN),
        INVALID_FORMAT(40, Category.SPOOFED),
        QUIET_HOURS(30, Category.SUSPICIOUS),
        SENIOR_UNKNOWN(0, Category.SUSPICIOUS),
        CALLED_BACK(0, Category.CLEAN);

        public final int weight;
        public final Category category;

        Signal(int weight, Category category) {
            this.weight = weight;
            this.category = category;
        }
    }

    public static final int VERIFICATION_UNKNOWN = 0;
    public static final int VERIFICATION_PASSED = 1;
    public static final int VERIFICATION_FAILED = 2;

    /** The user's choices. Defaults match a fresh install. */
    public static final class Config {
        /** 0 relaxed, 1 balanced, 2 strict. */
        public int sensitivity = 1;
        public boolean arcepRanges = true;
        public boolean community = true;
        public boolean spoofCheck = true;
        public boolean learn = true;
        public boolean blockHidden = false;
        public boolean blockForeign = false;
        public boolean contactsAlwaysRing = true;
        /** A silenced caller who calls back within a few minutes rings through. */
        public boolean letCallbacksThrough = true;
        public boolean quietHours = false;
        public int quietStart = 22, quietEnd = 8;
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

        /** "scam", "telemarketing" or null when the community list doesn't know the number. */
        String communityCategory(String number);

        /** How many numbers you or the community blocked in the same block of 1 000 (not counting this one). */
        int blockedNeighbours(String block1000, String number);

        /** Calls already received from this number since the given time (ms). */
        int callsSince(String number, long sinceMillis);
    }

    /** One incoming call. */
    public static final class Call {
        public final String number;   // E.164, "" when hidden
        public final int verification;
        public final boolean isContact;
        public final int hourOfDay;
        public final long now;

        public Call(String rawNumber, int verification, boolean isContact, int hourOfDay, long now) {
            this.number = PhoneNumbers.normalize(rawNumber);
            this.verification = verification;
            this.isContact = isContact;
            this.hourOfDay = hourOfDay;
            this.now = now;
        }
    }

    public static final class Reason {
        public final Signal signal;
        public final String detail;

        Reason(Signal signal, String detail) {
            this.signal = signal;
            this.detail = detail;
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

        /** Compact form stored in the history: "ARCEP_RANGE;LEARNED:3". */
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

    public static Verdict evaluate(Call call, Config cfg, Lists lists) {
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

        // 2. Signals.
        PhoneNumbers.Kind kind = PhoneNumbers.kind(n);
        if (cfg.arcepRanges && kind == PhoneNumbers.Kind.TELEMARKETING) {
            reasons.add(new Reason(Signal.ARCEP_RANGE, PhoneNumbers.national(n).substring(0, 4)));
        }
        if (cfg.community) {
            String c = lists.communityCategory(n);
            if ("scam".equals(c)) reasons.add(new Reason(Signal.COMMUNITY_SCAM, null));
            else if (c != null) reasons.add(new Reason(Signal.COMMUNITY_TELEMARKETING, null));
        }
        if (cfg.spoofCheck && call.verification == VERIFICATION_FAILED) {
            reasons.add(new Reason(Signal.SPOOFED, null));
        }
        if (cfg.learn) {
            String block = PhoneNumbers.block1000(n);
            int k = block == null ? 0 : lists.blockedNeighbours(block, n);
            if (k >= 3) reasons.add(new Reason(Signal.LEARNED_STRONG, String.valueOf(k)));
            else if (k == 2) reasons.add(new Reason(Signal.LEARNED, "2"));
            else if (k == 1) reasons.add(new Reason(Signal.LEARNED_WEAK, "1"));
        }
        switch (kind) {
            case PREMIUM:
                reasons.add(new Reason(Signal.PREMIUM, null));
                break;
            case INVALID:
                reasons.add(new Reason(Signal.INVALID_FORMAT, null));
                break;
            case FOREIGN:
                String cc = PhoneNumbers.countryCode(n);
                if (cfg.blockForeign) reasons.add(new Reason(Signal.FOREIGN_BLOCKED, cc));
                else if (PhoneNumbers.isHighRiskCountry(n)) reasons.add(new Reason(Signal.HIGH_RISK_COUNTRY, cc));
                else reasons.add(new Reason(Signal.FOREIGN, cc));
                break;
            default:
                break;
        }
        if (cfg.quietHours && inQuietHours(call.hourOfDay, cfg.quietStart, cfg.quietEnd)) {
            reasons.add(new Reason(Signal.QUIET_HOURS, null));
        }

        // 3. Combine.
        int score = combine(reasons);
        Action action = score >= cfg.blockThreshold() ? Action.BLOCK
                : score >= cfg.silenceThreshold() ? Action.SILENCE : Action.ALLOW;

        if (cfg.seniorMode && action == Action.ALLOW) {
            action = Action.SILENCE;
            reasons.add(new Reason(Signal.SENIOR_UNKNOWN, null));
        }
        // A person who really needs you calls back; most robots move on to the next number.
        if (cfg.letCallbacksThrough && action == Action.SILENCE && !cfg.seniorMode
                && lists.callsSince(n, call.now - CALLBACK_WINDOW_MS) > 0) {
            action = Action.ALLOW;
            reasons.add(new Reason(Signal.CALLED_BACK, null));
        }
        return new Verdict(action, score, mainCategory(reasons), reasons);
    }

    /** Noisy OR of the signal weights, 0–100. */
    static int combine(List<Reason> reasons) {
        double keep = 1.0;
        for (Reason r : reasons) keep *= 1.0 - Math.min(100, r.signal.weight) / 100.0;
        return (int) Math.round((1.0 - keep) * 100);
    }

    static boolean inQuietHours(int hour, int start, int end) {
        if (start == end) return false;
        return start < end ? hour >= start && hour < end : hour >= start || hour < end;
    }

    private static Category mainCategory(List<Reason> reasons) {
        Reason best = null;
        for (Reason r : reasons) if (best == null || r.signal.weight > best.signal.weight) best = r;
        return best == null || best.signal.weight == 0 ? Category.CLEAN : best.signal.category;
    }

    private static Verdict hard(Action action, Signal s, String detail) {
        return new Verdict(action, action == Action.BLOCK ? 100 : 0, s.category, one(s, detail));
    }

    private static List<Reason> one(Signal s, String detail) {
        List<Reason> l = new ArrayList<>();
        l.add(new Reason(s, detail));
        return l;
    }
}
