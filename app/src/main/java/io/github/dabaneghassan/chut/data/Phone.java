// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.data;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.dabaneghassan.chut.engine.PhoneNumbers;

/** Read-only access to contacts and the call log, only when the user allowed it. */
public final class Phone {

    private Phone() {
    }

    public static boolean canReadContacts(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean canReadCallLog(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
    }

    /** True when the number belongs to one of your contacts. Never reads names. */
    public static boolean isContact(Context c, String number) {
        if (number == null || number.isEmpty() || !canReadContacts(c)) return false;
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        try (Cursor cur = c.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup._ID}, null, null, null)) {
            return cur != null && cur.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }

    /** A number from the call log that is not a contact. */
    public static final class Caller {
        public final String number;
        public int calls;
        public long last;

        Caller(String number, long last) {
            this.number = number;
            this.last = last;
        }
    }

    /** Unknown numbers that called recently, most frequent first, without those already on a list. */
    public static List<Caller> recentUnknownCallers(Context c, Store store) {
        List<Caller> out = new ArrayList<>();
        if (!canReadCallLog(c)) return out;
        Map<String, Caller> byNumber = new LinkedHashMap<>();
        String[] projection = {CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.CACHED_NAME};
        try (Cursor cur = c.getContentResolver().query(CallLog.Calls.CONTENT_URI, projection, null, null,
                CallLog.Calls.DATE + " DESC")) {
            int seen = 0;
            while (cur != null && cur.moveToNext() && seen++ < 500) {
                int type = cur.getInt(1);
                if (type == CallLog.Calls.OUTGOING_TYPE) continue;
                String name = cur.getString(3);
                if (name != null && !name.isEmpty()) continue;
                String n = PhoneNumbers.normalize(cur.getString(0));
                if (!Store.isValid(n) || store.listOf(n) >= 0) continue;
                Caller caller = byNumber.get(n);
                if (caller == null) {
                    caller = new Caller(n, cur.getLong(2));
                    byNumber.put(n, caller);
                }
                caller.calls++;
            }
        } catch (Exception ignored) {
        }
        out.addAll(byNumber.values());
        Collections.sort(out, (a, b) -> a.calls != b.calls ? b.calls - a.calls : Long.compare(b.last, a.last));
        return out;
    }
}
