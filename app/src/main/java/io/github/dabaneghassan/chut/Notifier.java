// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import io.github.dabaneghassan.chut.data.Prefs;
import io.github.dabaneghassan.chut.engine.Engine;
import io.github.dabaneghassan.chut.engine.PhoneNumbers;
import io.github.dabaneghassan.chut.engine.Stats;
import io.github.dabaneghassan.chut.ui.Labels;

/** Quiet notifications: none of them makes a sound. */
public final class Notifier {

    static final String CH_SILENCED = "silenced";
    static final String CH_BLOCKED = "blocked";
    static final String CH_REPORT = "report";
    static final int ID_REPORT = 1;

    private Notifier() {
    }

    public static void createChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel silenced = new NotificationChannel(CH_SILENCED, c.getString(R.string.channel_silenced),
                NotificationManager.IMPORTANCE_LOW);
        silenced.setDescription(c.getString(R.string.channel_silenced_desc));
        NotificationChannel blocked = new NotificationChannel(CH_BLOCKED, c.getString(R.string.channel_blocked),
                NotificationManager.IMPORTANCE_MIN);
        NotificationChannel report = new NotificationChannel(CH_REPORT, c.getString(R.string.channel_report),
                NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(silenced);
        nm.createNotificationChannel(blocked);
        nm.createNotificationChannel(report);
    }

    static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 33
                || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    static void afterScreening(Context c, String number, Engine.Verdict v) {
        if (!allowed(c)) return;
        Prefs prefs = new Prefs(c);
        boolean silenced = v.action == Engine.Action.SILENCE;
        if (v.action == Engine.Action.ALLOW) return;
        if (silenced && !prefs.flag(Prefs.NOTIFY_SILENCED)) return;
        if (!silenced && !prefs.flag(Prefs.NOTIFY_BLOCKED)) return;
        createChannels(c);

        String who = number.isEmpty() ? c.getString(R.string.hidden_number) : PhoneNumbers.pretty(number);
        String title = c.getString(silenced ? R.string.notif_silenced : R.string.notif_blocked, who);
        String why = Labels.summary(c, v);
        int id = 1000 + (int) (System.currentTimeMillis() % 100000);

        Notification.Builder b = new Notification.Builder(c, silenced ? CH_SILENCED : CH_BLOCKED)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(why)
                .setStyle(new Notification.BigTextStyle().bigText(why))
                .setColor(c.getColor(R.color.brand))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentIntent(openApp(c));
        if (!number.isEmpty()) {
            if (silenced) b.addAction(action(c, ActionReceiver.BLOCK, number, id, R.string.action_block));
            b.addAction(action(c, ActionReceiver.TRUST, number, id, R.string.action_trust));
        }
        c.getSystemService(NotificationManager.class).notify(id, b.build());
    }

    static void weeklyReport(Context c, Stats week) {
        if (!allowed(c) || week.stopped() == 0) return;
        createChannels(c);
        String text = c.getResources().getQuantityString(R.plurals.report_text, week.stopped(), week.stopped(),
                week.blocked, week.silenced, week.minutesSaved());
        Notification n = new Notification.Builder(c, CH_REPORT)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(c.getString(R.string.report_title))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setColor(c.getColor(R.color.brand))
                .setAutoCancel(true)
                .setContentIntent(openApp(c))
                .build();
        c.getSystemService(NotificationManager.class).notify(ID_REPORT, n);
    }

    private static PendingIntent openApp(Context c) {
        Intent i = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static Notification.Action action(Context c, String what, String number, int notifId, int label) {
        Intent i = new Intent(c, ActionReceiver.class).setAction(what)
                .putExtra(ActionReceiver.EXTRA_NUMBER, number)
                .putExtra(ActionReceiver.EXTRA_NOTIFICATION, notifId);
        PendingIntent pi = PendingIntent.getBroadcast(c, (what + number).hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(null, c.getString(label), pi).build();
    }
}
