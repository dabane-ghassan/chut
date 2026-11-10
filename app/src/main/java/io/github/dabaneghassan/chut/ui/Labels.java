// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.ui;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import io.github.dabaneghassan.chut.R;
import io.github.dabaneghassan.chut.engine.Engine;
import io.github.dabaneghassan.chut.engine.PhoneNumbers;

/** Human words for the engine's codes. */
public final class Labels {

    private Labels() {
    }

    public static String category(Context c, Engine.Category cat) {
        switch (cat) {
            case PERSONAL: return c.getString(R.string.cat_personal);
            case TELEMARKETING: return c.getString(R.string.cat_telemarketing);
            case SCAM: return c.getString(R.string.cat_scam);
            case SPOOFED: return c.getString(R.string.cat_spoofed);
            case SUSPICIOUS: return c.getString(R.string.cat_suspicious);
            case FOREIGN: return c.getString(R.string.cat_foreign);
            case HIDDEN: return c.getString(R.string.cat_hidden);
            default: return c.getString(R.string.cat_unknown);
        }
    }

    public static String region(Context c, PhoneNumbers.Region r) {
        switch (r) {
            case IDF: return c.getString(R.string.region_idf);
            case NORTH_WEST: return c.getString(R.string.region_nw);
            case NORTH_EAST: return c.getString(R.string.region_ne);
            case SOUTH_EAST: return c.getString(R.string.region_se);
            case SOUTH_WEST: return c.getString(R.string.region_sw);
            case MOBILE: return c.getString(R.string.region_mobile);
            case VOIP: return c.getString(R.string.region_voip);
            case SPECIAL: return c.getString(R.string.region_special);
            case OVERSEAS: return c.getString(R.string.region_overseas);
            case FOREIGN: return c.getString(R.string.region_foreign);
            case HIDDEN: return c.getString(R.string.region_hidden);
            default: return c.getString(R.string.region_other);
        }
    }

    public static String action(Context c, Engine.Action a) {
        switch (a) {
            case BLOCK: return c.getString(R.string.verdict_blocked);
            case SILENCE: return c.getString(R.string.verdict_silenced);
            default: return c.getString(R.string.verdict_allowed);
        }
    }

    public static int actionColor(Engine.Action a) {
        switch (a) {
            case BLOCK: return R.color.block;
            case SILENCE: return R.color.silence;
            default: return R.color.allow;
        }
    }

    public static String signal(Context c, Engine.Signal s, String detail) {
        switch (s) {
            case ALLOWLIST: return c.getString(R.string.sig_allowlist);
            case CONTACT: return c.getString(R.string.sig_contact);
            case USER_LIST:
                return detail != null && detail.endsWith("*")
                        ? c.getString(R.string.sig_user_prefix, PhoneNumbers.pretty(detail))
                        : c.getString(R.string.sig_user_list);
            case HIDDEN: return c.getString(R.string.sig_hidden);
            case ARCEP_RANGE:
                return c.getString(R.string.sig_arcep, detail == null ? "" : detail.substring(0, 2) + " " + detail.substring(2));
            case COMMUNITY_SCAM: return c.getString(R.string.sig_community_scam);
            case COMMUNITY_TELEMARKETING: return c.getString(R.string.sig_community_tm);
            case SPOOFED: return c.getString(R.string.sig_spoofed);
            case LEARNED_STRONG:
            case LEARNED:
            case LEARNED_WEAK:
                int k = 1;
                try {
                    k = Integer.parseInt(detail);
                } catch (Exception ignored) {
                }
                return c.getResources().getQuantityString(R.plurals.sig_learned, k, k);
            case PREMIUM: return c.getString(R.string.sig_premium);
            case HIGH_RISK_COUNTRY: return c.getString(R.string.sig_high_risk, detail == null ? "" : detail);
            case FOREIGN_BLOCKED: return c.getString(R.string.sig_foreign_blocked, detail == null ? "" : detail);
            case FOREIGN: return c.getString(R.string.sig_foreign, detail == null ? "" : detail);
            case INVALID_FORMAT: return c.getString(R.string.sig_invalid);
            case QUIET_HOURS: return c.getString(R.string.sig_quiet);
            case SENIOR_UNKNOWN: return c.getString(R.string.sig_senior);
            case CALLED_BACK: return c.getString(R.string.sig_called_back);
            default: return s.name();
        }
    }

    /** All reasons of a verdict, one per line. */
    public static List<String> reasons(Context c, Engine.Verdict v) {
        List<String> out = new ArrayList<>();
        for (Engine.Reason r : v.reasons) out.add(signal(c, r.signal, r.detail));
        return out;
    }

    /** Reasons from the stored codes ("ARCEP_RANGE:0162;LEARNED:2"). */
    public static List<String> reasons(Context c, String codes) {
        List<String> out = new ArrayList<>();
        if (codes == null) return out;
        for (String code : codes.split(";")) {
            if (code.isEmpty()) continue;
            int colon = code.indexOf(':');
            try {
                Engine.Signal s = Engine.Signal.valueOf(colon < 0 ? code : code.substring(0, colon));
                out.add(signal(c, s, colon < 0 ? null : code.substring(colon + 1)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    public static String summary(Context c, Engine.Verdict v) {
        List<String> r = reasons(c, v);
        String why = r.isEmpty() ? category(c, v.category) : String.join(" · ", r);
        return c.getString(R.string.score_line, v.score, why);
    }
}
