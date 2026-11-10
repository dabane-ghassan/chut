// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut;

import android.net.Uri;
import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telecom.Connection;
import android.telecom.TelecomManager;

import java.time.LocalTime;

import io.github.dabaneghassan.chut.data.Community;
import io.github.dabaneghassan.chut.data.Phone;
import io.github.dabaneghassan.chut.data.Prefs;
import io.github.dabaneghassan.chut.data.Store;
import io.github.dabaneghassan.chut.engine.Engine;

/**
 * Android binds this service for an incoming call, before the phone rings, once chut is the
 * "Caller ID & spam" app. There is no service running the rest of the time: chut uses no
 * battery between calls.
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
                int verification = Engine.VERIFICATION_UNKNOWN;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (details.getHandlePresentation() == TelecomManager.PRESENTATION_RESTRICTED) number = "";
                    // France's caller-ID authentication (MAN, like STIR/SHAKEN) reports here.
                    int status = details.getCallerNumberVerificationStatus();
                    if (status == Connection.VERIFICATION_STATUS_FAILED) verification = Engine.VERIFICATION_FAILED;
                    else if (status == Connection.VERIFICATION_STATUS_PASSED) verification = Engine.VERIFICATION_PASSED;
                }
                Community.loadBundledIfNeeded(this);
                Store store = Store.get(this);
                boolean contact = Phone.isContact(this, number);
                verdict = Engine.evaluate(new Engine.Call(number, verification, contact, LocalTime.now().getHour(), now),
                        new Prefs(this).engineConfig(), store);
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
            final String n = new Engine.Call(number, 0, false, 0, now).number;
            new Thread(() -> {
                try {
                    Store.get(this).log(n, v, now);
                    Notifier.afterScreening(this, n, v);
                } catch (Throwable ignored) {
                }
            }).start();
        }
    }
}
