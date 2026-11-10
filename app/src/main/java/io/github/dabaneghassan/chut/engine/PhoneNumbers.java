// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.engine;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** French-aware phone number helpers. Pure Java, no Android, so it is unit-tested on the JVM. */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    /** What kind of line a number belongs to, from its format alone. */
    public enum Kind {
        MOBILE, LANDLINE, VOIP, TELEMARKETING, TOLL_FREE, SPECIAL, PREMIUM,
        OVERSEAS, FOREIGN, SHORT, INVALID, HIDDEN
    }

    /** Where a call comes from, for the statistics. */
    public enum Region {
        IDF, NORTH_WEST, NORTH_EAST, SOUTH_EAST, SOUTH_WEST, MOBILE, VOIP, SPECIAL, OVERSEAS, FOREIGN, HIDDEN, OTHER
    }

    /**
     * Ranges ARCEP reserves, since 1 January 2023, for telemarketing and automated callers
     * in mainland France (decision 2022-1583): 01 62, 01 63, 02 70, 02 71, 03 77, 03 78,
     * 04 24, 04 25, 05 68, 05 69, 09 48, 09 49.
     */
    public static final String[] ARCEP_TELEMARKETING = {
            "0162", "0163", "0270", "0271", "0377", "0378",
            "0424", "0425", "0568", "0569", "0948", "0949"
    };

    /** French overseas departments and territories: not "foreign". */
    private static final Set<String> OVERSEAS = new HashSet<>(Arrays.asList(
            "262", "269", "508", "590", "594", "596", "681", "687", "689"));

    private static final Set<String> CC_1 = new HashSet<>(Arrays.asList("1", "7"));
    private static final Set<String> CC_2 = new HashSet<>(Arrays.asList(
            "20", "27", "30", "31", "32", "33", "34", "36", "39", "40", "41", "43", "44", "45", "46",
            "47", "48", "49", "51", "52", "53", "54", "55", "56", "57", "58", "60", "61", "62", "63",
            "64", "65", "66", "81", "82", "84", "86", "90", "91", "92", "93", "94", "95", "98"));

    /**
     * Normalises any written form to E.164: "04 24 03 60 62", "0033424036062" and
     * "33424036062" all become "+33424036062". A trailing '*' (prefix rule) is kept.
     * Numbers without a country code are taken as French. Short codes (≤ 6 digits) stay as they are.
     */
    public static String normalize(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        boolean prefix = trimmed.endsWith("*");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
            else if (c == '+' && sb.length() == 0) sb.append('+');
        }
        String s = sb.toString();
        if (s.equals("+")) s = "";
        if (s.startsWith("00")) {
            s = "+" + s.substring(2);
        } else if (s.startsWith("0") && s.length() > 1) {
            s = "+33" + s.substring(1);
        } else if (!s.startsWith("+") && s.startsWith("33") && s.length() == 11) {
            s = "+" + s;
        }
        if (s.startsWith("+330")) s = "+33" + s.substring(4);    // "+33 (0)4 …"
        if (prefix && !s.isEmpty()) s += "*";
        return s;
    }

    public static boolean isPrefixRule(String n) {
        return n != null && n.endsWith("*");
    }

    /** "+33424036062" → "0424036062"; other numbers are returned as they are. */
    public static String national(String e164) {
        if (e164 != null && e164.startsWith("+33")) return "0" + e164.substring(3);
        return e164 == null ? "" : e164;
    }

    /** Country calling code without '+', or "" for numbers without one. */
    public static String countryCode(String e164) {
        if (e164 == null || !e164.startsWith("+") || e164.length() < 3) return "";
        String d = e164.substring(1);
        if (CC_1.contains(d.substring(0, 1))) return d.substring(0, 1);
        if (d.length() >= 2 && CC_2.contains(d.substring(0, 2))) return d.substring(0, 2);
        return d.length() >= 3 ? d.substring(0, 3) : d;
    }

    public static boolean isArcepTelemarketing(String e164) {
        String n = national(e164);
        if (!e164.startsWith("+33")) return false;
        for (String p : ARCEP_TELEMARKETING) if (n.startsWith(p)) return true;
        return false;
    }

    public static Kind kind(String e164) {
        if (e164 == null || e164.isEmpty()) return Kind.HIDDEN;
        if (!e164.startsWith("+")) return e164.length() <= 6 ? Kind.SHORT : Kind.INVALID;
        String cc = countryCode(e164);
        if (!cc.equals("33")) return OVERSEAS.contains(cc) ? Kind.OVERSEAS : Kind.FOREIGN;
        String n = national(e164);
        if (n.length() != 10) return Kind.INVALID;
        if (isArcepTelemarketing(e164)) return Kind.TELEMARKETING;
        switch (n.charAt(1)) {
            case '6':
            case '7':
                return Kind.MOBILE;
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
                return Kind.LANDLINE;
            case '9':
                return Kind.VOIP;
            case '8':
                if (n.startsWith("080")) return Kind.TOLL_FREE;
                if (n.startsWith("089")) return Kind.PREMIUM;
                return Kind.SPECIAL;
            default:
                return Kind.INVALID;
        }
    }

    public static Region region(String e164) {
        Kind k = kind(e164);
        switch (k) {
            case HIDDEN:
                return Region.HIDDEN;
            case FOREIGN:
                return Region.FOREIGN;
            case OVERSEAS:
                return Region.OVERSEAS;
            case MOBILE:
                return Region.MOBILE;
            case VOIP:
                return Region.VOIP;
            case TOLL_FREE:
            case SPECIAL:
            case PREMIUM:
                return Region.SPECIAL;
            case LANDLINE:
            case TELEMARKETING:
                switch (national(e164).charAt(1)) {
                    case '1':
                        return Region.IDF;
                    case '2':
                        return Region.NORTH_WEST;
                    case '3':
                        return Region.NORTH_EAST;
                    case '4':
                        return Region.SOUTH_EAST;
                    case '5':
                        return Region.SOUTH_WEST;
                    case '9':
                        return Region.VOIP;
                    default:
                        return Region.OTHER;
                }
            default:
                return Region.OTHER;
        }
    }

    /**
     * The block of 1 000 numbers a number belongs to ("+33377259" for +33377259405).
     * Spam platforms rent numbers in such blocks and rotate through them.
     */
    public static String block1000(String e164) {
        if (e164 == null || !e164.startsWith("+33") || e164.length() != 12) return null;
        return e164.substring(0, 9);
    }

    /** Readable form: "+33 4 24 03 60 62", "+225 07 12 34 56", prefix rules end in " …". */
    public static String pretty(String n) {
        if (n == null || n.isEmpty()) return "";
        boolean prefix = isPrefixRule(n);
        String core = prefix ? n.substring(0, n.length() - 1) : n;
        String out;
        if (core.startsWith("+33") && core.length() > 3) {
            String rest = core.substring(3);
            StringBuilder sb = new StringBuilder("0").append(rest.charAt(0));
            for (int i = 1; i < rest.length(); i += 2) {
                if (i % 2 == 1) sb.append(' ');
                sb.append(rest, i, Math.min(i + 2, rest.length()));
            }
            out = sb.toString();
        } else if (core.startsWith("+")) {
            String cc = countryCode(core);
            String rest = core.substring(1 + cc.length());
            StringBuilder sb = new StringBuilder("+").append(cc);
            for (int i = 0; i < rest.length(); i += 2) {
                sb.append(' ').append(rest, i, Math.min(i + 2, rest.length()));
            }
            out = sb.toString();
        } else {
            out = core;
        }
        return prefix ? out + " …" : out;
    }
}
