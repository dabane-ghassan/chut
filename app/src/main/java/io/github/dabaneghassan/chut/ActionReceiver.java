// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import io.github.dabaneghassan.chut.data.Lang;
import io.github.dabaneghassan.chut.data.Learning;
import io.github.dabaneghassan.chut.data.Store;
import io.github.dabaneghassan.chut.engine.PhoneNumbers;

/** "Block" and "Not spam" buttons on notifications. Each tap retrains the model: this is how chut learns from you. */
public class ActionReceiver extends BroadcastReceiver {

    static final String BLOCK = "io.github.dabaneghassan.chut.BLOCK";
    static final String TRUST = "io.github.dabaneghassan.chut.TRUST";
    static final String EXTRA_NUMBER = "number";
    static final String EXTRA_NOTIFICATION = "notification";

    @Override
    public void onReceive(Context context, Intent intent) {
        Context c = Lang.wrap(context);
        String number = intent.getStringExtra(EXTRA_NUMBER);
        if (number == null || number.isEmpty()) return;
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                Store store = Store.get(c);
                boolean block = BLOCK.equals(intent.getAction());
                store.add(block ? Store.BLOCKED : Store.ALLOWED, number, "notification");
                // Retrain here, while Android keeps the process alive for this receiver.
                Learning.retrainNow(c);
                c.getSystemService(NotificationManager.class).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0));
                String msg = c.getString(block ? R.string.toast_blocked : R.string.toast_trusted, PhoneNumbers.pretty(number));
                new android.os.Handler(c.getMainLooper()).post(() -> Toast.makeText(c, msg, Toast.LENGTH_SHORT).show());
            } finally {
                pending.finish();
            }
        }).start();
    }
}
