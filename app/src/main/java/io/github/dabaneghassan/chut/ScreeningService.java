// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut;

import android.net.Uri;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telecom.TelecomManager;

import java.time.LocalTime;

import io.github.dabaneghassan.chut.data.Lang;
import io.github.dabaneghassan.chut.data.Learning;
import io.github.dabaneghassan.chut.data.Phone;
import io.github.dabaneghassan.chut.data.Prefs;
import io.github.dabaneghassan.chut.data.Store;
import io.github.dabaneghassan.chut.engine.Engine;

/**
 * Android binds this service for an incoming call, before the phone rings, once chut is the
 * "Caller ID & spam" app. It works whether or not chut is open: Android starts it for the call,
 * even after the app was closed, force-stopped or the phone restarted. Nothing runs between
 * calls, so chut uses no battery.
 */
public class ScreeningService extends CallScreeningService {

    @Override
    public void onScreenCall(Call.Details details) {
        CallResponse.Builder response = new CallResponse.Builder();
        Engine.Verdict verdict = null;
        String number = "";
        long now = System.currentTimeMillis();
        try {
            if (details.getCallDirection() == Call.Details.DIRECTION_INCOMING) {
                Uri handle = details.getHandle();
                number = handle == null ? "" : handle.getSchemeSpecificPart();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        && details.getHandlePresentation() == TelecomManager.PRESENTATION_RESTRICTED) {
                    number = "";
                }
                Store store = Store.get(this);
                boolean contact = Phone.isContact(this, number);
                Prefs prefs = new Prefs(this);
                Engine.Config cfg = prefs.engineConfig();
                verdict = Engine.evaluate(new Engine.Call(number, contact, LocalTime.now().getHour(), now),
                        cfg, store, cfg.learn ? Learning.savedModel(this) : null);
                if (verdict.action == Engine.Action.BLOCK) {
                    response.setDisallowCall(true)
                            .setRejectCall(true)
                            .setSkipCallLog(false)          // keep it in the phone's history
                            .setSkipNotification(true);
                } else if (verdict.action == Engine.Action.SILENCE) {
                    response.setSilenceCall(true);
                }
            }
        } catch (Throwable ignored) {
            verdict = null;   // never stop a call because of a bug: let it ring
        }
        respondToCall(details, response.build());

        if (verdict != null && !verdict.has(Engine.Signal.CONTACT)) {
            final Engine.Verdict v = verdict;
            final String n = new Engine.Call(number, false, 0, now).number;
            new Thread(() -> {
                try {
                    BackgroundJobs.schedule(this);   // even if the app was never opened
                    Store.get(this).log(n, v, now);
                    Notifier.afterScreening(Lang.wrap(this), n, v);
                } catch (Throwable ignored) {
                }
            }).start();
        }
    }
}
