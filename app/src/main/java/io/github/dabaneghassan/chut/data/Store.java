// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import io.github.dabaneghassan.chut.engine.Engine;
import io.github.dabaneghassan.chut.engine.Learner;
import io.github.dabaneghassan.chut.engine.PhoneNumbers;
import io.github.dabaneghassan.chut.engine.Stats;

/** The phone's local database: your lists and the call history. Nothing leaves it. */
public final class Store extends SQLiteOpenHelper implements Engine.Lists {

    public static final int BLOCKED = 0;
    public static final int ALLOWED = 1;
    /** Source of the numbers 1.1 shipped with; removed when upgrading. */
    static final String SOURCE_PRESET = "preset";

    private static final long HISTORY_DAYS = 400;
    private static volatile Store instance;
    private final Context context;

    public static Store get(Context context) {
        if (instance == null) {
            synchronized (Store.class) {
                if (instance == null) instance = new Store(context.getApplicationContext());
            }
        }
        return instance;
    }

    private Store(Context context) {
        super(context, "chut.db", null, 3);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE numbers (number TEXT PRIMARY KEY, list INTEGER NOT NULL, source TEXT, added INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE calls (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, number TEXT NOT NULL, "
                + "action INTEGER NOT NULL, category TEXT, reasons TEXT, score INTEGER)");
        db.execSQL("CREATE INDEX calls_time ON calls(time)");
        db.execSQL("CREATE INDEX calls_number ON calls(number)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS community");   // the community list is gone in 1.1
        }
        if (oldVersion < 3) {
            // 1.1 shipped a list of numbers; lists are now only what each person adds.
            db.delete("numbers", "source=?", new String[]{SOURCE_PRESET});
        }
    }

    /** One entry of a personal list. */
    public static final class Entry {
        public final String number;
        public final String source;
        public final long added;

        Entry(String number, String source, long added) {
            this.number = number;
            this.source = source;
            this.added = added;
        }
    }

    // ---- Engine.Lists ------------------------------------------------------------------

    @Override
    public boolean isAllowed(String number) {
        return count("SELECT COUNT(*) FROM numbers WHERE list=1 AND number=?", number) > 0
                || matchPrefix(ALLOWED, number) != null;
    }

    @Override
    public String blockedBy(String number) {
        if (count("SELECT COUNT(*) FROM numbers WHERE list=0 AND number=?", number) > 0) return number;
        return matchPrefix(BLOCKED, number);
    }

    @Override
    public int callsSince(String number, long sinceMillis) {
        return count("SELECT COUNT(*) FROM calls WHERE number=? AND time>=?", number, String.valueOf(sinceMillis));
    }

    private String matchPrefix(int list, String number) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT number FROM numbers WHERE list=? AND number LIKE '%*'", new String[]{String.valueOf(list)})) {
            while (c.moveToNext()) {
                String p = c.getString(0);
                if (number.startsWith(p.substring(0, p.length() - 1))) return p;
            }
        }
        return null;
    }

    // ---- Personal lists ----------------------------------------------------------------

    /** Checks that a normalised number or prefix rule is worth storing. */
    public static boolean isValid(String n) {
        if (n == null || n.isEmpty()) return false;
        String digits = n.replace("+", "").replace("*", "");
        if (PhoneNumbers.isPrefixRule(n)) return digits.length() >= 3;
        if (n.startsWith("+")) return digits.length() >= 8 && digits.length() <= 15;
        return digits.length() >= 3 && digits.length() <= 6;    // short codes such as 3179
    }

    /** Adds (or moves) a number to a list. Returns false for an invalid number or one already there. */
    public boolean add(int list, String raw, String source) {
        boolean added = insert(list, raw, source);
        if (added) Learning.retrainSoon(context);
        return added;
    }

    private boolean insert(int list, String raw, String source) {
        String n = PhoneNumbers.normalize(raw);
        if (!isValid(n)) return false;
        if (count("SELECT COUNT(*) FROM numbers WHERE number=? AND list=?", n, String.valueOf(list)) > 0) return false;
        ContentValues v = new ContentValues();
        v.put("number", n);
        v.put("list", list);
        v.put("source", source);
        v.put("added", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("numbers", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        return true;
    }

    /**
     * Imports numbers separated by commas, semicolons, tabs or new lines (the "Blocked numbers"
     * export of Samsung and Google phones), or a chut export file, which keeps each number's list.
     */
    public int addAll(int list, String text, String source) {
        if (text == null) return 0;
        int added = 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (String rawLine : text.split("\\r?\\n")) {
                String line = rawLine.trim();
                if (line.startsWith("# history")) break;           // chut export: lists end here
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] cols = line.split(",");
                if (cols.length >= 2 && (cols[1].trim().equals("blocked") || cols[1].trim().equals("allowed"))) {
                    if (insert(cols[1].trim().equals("blocked") ? BLOCKED : ALLOWED, cols[0], source)) added++;
                    continue;
                }
                for (String part : line.split("[,;\\t]+")) {
                    if (insert(list, part, source)) added++;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (added > 0) Learning.retrainSoon(context);
        return added;
    }

    public void remove(String number) {
        if (getWritableDatabase().delete("numbers", "number=?", new String[]{number}) > 0) Learning.retrainSoon(context);
    }

    /** BLOCKED, ALLOWED or -1. */
    public int listOf(String number) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT list FROM numbers WHERE number=?", new String[]{number})) {
            return c.moveToFirst() ? c.getInt(0) : -1;
        }
    }

    public List<Entry> entries(int list) {
        List<Entry> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT number, source, added FROM numbers WHERE list=? ORDER BY added DESC, number",
                new String[]{String.valueOf(list)})) {
            while (c.moveToNext()) out.add(new Entry(c.getString(0), c.getString(1), c.getLong(2)));
        }
        return out;
    }

    public int size(int list) {
        return count("SELECT COUNT(*) FROM numbers WHERE list=?", String.valueOf(list));
    }

    // ---- Training data for the model ----------------------------------------------------

    /**
     * What the model learns from: blocked numbers (spam, with the hours they called), allowed
     * numbers (not spam, weight 2: you said so), and calls that rang and were never blocked
     * afterwards (probably not spam, weight 0.3).
     */
    public List<Learner.Sample> trainingSamples() {
        List<Learner.Sample> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT number, list FROM numbers WHERE number NOT LIKE '%*'", null)) {
            while (c.moveToNext()) {
                boolean spam = c.getInt(1) == BLOCKED;
                out.add(new Learner.Sample(c.getString(0), -1, spam, spam ? 1 : 2));
            }
        }
        ZoneId zone = ZoneId.systemDefault();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT calls.number, calls.time, calls.action, numbers.list FROM calls "
                        + "LEFT JOIN numbers ON numbers.number = calls.number WHERE calls.number <> ''", null)) {
            while (c.moveToNext()) {
                int hour = Instant.ofEpochMilli(c.getLong(1)).atZone(zone).getHour();
                boolean listedSpam = !c.isNull(3) && c.getInt(3) == BLOCKED;
                boolean listedHam = !c.isNull(3) && c.getInt(3) == ALLOWED;
                if (listedSpam) out.add(new Learner.Sample(c.getString(0), hour, true, 1));
                else if (listedHam) out.add(new Learner.Sample(c.getString(0), hour, false, 2));
                else if (c.getInt(2) == Engine.Action.ALLOW.ordinal()) out.add(new Learner.Sample(c.getString(0), hour, false, 0.3));
            }
        }
        return out;
    }

    // ---- Call history ------------------------------------------------------------------

    public void log(String number, Engine.Verdict v, long time) {
        ContentValues cv = new ContentValues();
        cv.put("time", time);
        cv.put("number", number == null ? "" : number);
        cv.put("action", v.action.ordinal());
        cv.put("category", v.category.name());
        cv.put("reasons", v.reasonCodes());
        cv.put("score", v.score);
        SQLiteDatabase db = getWritableDatabase();
        db.insert("calls", null, cv);
        db.delete("calls", "time<?", new String[]{String.valueOf(time - HISTORY_DAYS * 86_400_000L)});
    }

    public List<Stats.Event> events(long since) {
        return query("SELECT time, number, action, category, reasons, score FROM calls WHERE time>=? ORDER BY time",
                String.valueOf(since));
    }

    public List<Stats.Event> recent(int limit) {
        return query("SELECT time, number, action, category, reasons, score FROM calls ORDER BY time DESC LIMIT " + limit);
    }

    public void clearHistory() {
        getWritableDatabase().delete("calls", null, null);
        Learning.retrainSoon(context);
    }

    private List<Stats.Event> query(String sql, String... args) {
        List<Stats.Event> out = new ArrayList<>();
        Engine.Action[] actions = Engine.Action.values();
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) {
                Engine.Category cat;
                try {
                    cat = Engine.Category.valueOf(c.getString(3));
                } catch (Exception e) {
                    cat = Engine.Category.CLEAN;
                }
                int a = c.getInt(2);
                out.add(new Stats.Event(c.getLong(0), c.getString(1), actions[Math.max(0, Math.min(a, actions.length - 1))],
                        cat, c.getString(4), c.getInt(5)));
            }
        }
        return out;
    }

    // ---- Export --------------------------------------------------------------------------

    /** Your lists and history as one CSV file. chut can import it back. */
    public String exportCsv() {
        StringBuilder sb = new StringBuilder("# chut export\nnumber,list,source,added\n");
        for (int list : new int[]{BLOCKED, ALLOWED}) {
            for (Entry e : entries(list)) {
                sb.append(e.number).append(',').append(list == BLOCKED ? "blocked" : "allowed").append(',')
                        .append(e.source == null ? "" : e.source).append(',').append(e.added).append('\n');
            }
        }
        sb.append("\n# history\ntime,number,verdict,category,score,reasons\n");
        for (Stats.Event e : events(0)) {
            sb.append(e.time).append(',').append(e.number).append(',').append(e.action.name().toLowerCase())
                    .append(',').append(e.category.name().toLowerCase()).append(',').append(e.score).append(',')
                    .append(e.reasons).append('\n');
        }
        return sb.toString();
    }

    private int count(String sql, String... args) {
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
}
