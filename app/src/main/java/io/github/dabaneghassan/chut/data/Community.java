// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.data;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.dabaneghassan.chut.BuildConfigLite;
import io.github.dabaneghassan.chut.R;
import io.github.dabaneghassan.chut.engine.PhoneNumbers;

/**
 * The community list: numbers reported by chut users, reviewed in public on GitHub.
 * A copy ships inside the app. Updating it only downloads one public file; nothing is sent.
 */
public final class Community {

    public static final String REPO = "https://github.com/dabane-ghassan/chut";
    public static final String LIST_URL = "https://raw.githubusercontent.com/dabane-ghassan/chut/main/community/numbers.csv";
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    private Community() {
    }

    /** Loads the copy shipped with this version of the app, once per version. */
    public static void loadBundledIfNeeded(Context c) {
        Prefs prefs = new Prefs(c);
        int version = BuildConfigLite.versionCode(c);
        if (prefs.number(Prefs.BUNDLED_VERSION, 0) == version) return;
        // A downloaded list newer than this build wins.
        if (prefs.time(Prefs.LAST_SYNC) > BuildConfigLite.installedAt(c) && Store.get(c).communitySize() > 0) {
            prefs.setNumber(Prefs.BUNDLED_VERSION, version);
            return;
        }
        try (InputStream in = c.getResources().openRawResource(R.raw.community)) {
            Map<String, String[]> rows = parse(in);
            if (!rows.isEmpty()) Store.get(c).replaceCommunity(rows);
            prefs.setNumber(Prefs.BUNDLED_VERSION, version);
        } catch (Exception ignored) {
        }
    }

    /** Downloads the latest list. Returns its size, or -1 on failure. Call off the main thread. */
    public static int update(Context c) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(LIST_URL).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "chut");
            if (conn.getResponseCode() != 200) return -1;
            Map<String, String[]> rows;
            try (InputStream in = conn.getInputStream()) {
                rows = parse(in);
            }
            if (rows.isEmpty()) return -1;
            Store.get(c).replaceCommunity(rows);
            new Prefs(c).setTime(Prefs.LAST_SYNC, System.currentTimeMillis());
            return rows.size();
        } catch (Exception e) {
            return -1;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** CSV: number,category,reports — lines starting with '#' are comments. */
    static Map<String, String[]> parse(InputStream in) throws java.io.IOException {
        Map<String, String[]> rows = new LinkedHashMap<>();
        int read = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                read += line.length() + 1;
                if (read > MAX_BYTES) break;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("number")) continue;
                String[] cols = line.split(",");
                String n = PhoneNumbers.normalize(cols[0]);
                if (!Store.isValid(n)) continue;
                String cat = cols.length > 1 && cols[1].trim().equals("scam") ? "scam" : "telemarketing";
                String reports = cols.length > 2 ? cols[2].trim() : "1";
                rows.put(n, new String[]{cat, reports});
            }
        }
        return rows;
    }

    /** Opens a pre-filled GitHub issue to report a number to the community list. */
    public static Intent reportIntent(String number, boolean scam) {
        String url = REPO + "/issues/new?template=report.yml"
                + "&title=" + Uri.encode("Signalement " + PhoneNumbers.pretty(number))
                + "&number=" + Uri.encode(number)
                + "&category=" + Uri.encode(scam ? "Arnaque" : "Démarchage");
        return new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    }
}
