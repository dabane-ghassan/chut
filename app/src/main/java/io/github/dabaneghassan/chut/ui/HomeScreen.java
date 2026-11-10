// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.ui;

import android.text.InputType;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.ZoneId;
import java.util.List;

import io.github.dabaneghassan.chut.MainActivity;
import io.github.dabaneghassan.chut.R;
import io.github.dabaneghassan.chut.data.Prefs;
import io.github.dabaneghassan.chut.data.Store;
import io.github.dabaneghassan.chut.engine.PhoneNumbers;
import io.github.dabaneghassan.chut.engine.Stats;

/**
 * The one screen most people ever see: is protection on, how many calls it stopped, a field to
 * block a number. Phone numbers are not shown unless "Show numbers" is on in the settings.
 */
public final class HomeScreen {

    private HomeScreen() {
    }

    public static View build(MainActivity a) {
        Store store = Store.get(a);
        Prefs prefs = new Prefs(a);
        long now = System.currentTimeMillis();
        Stats s = Stats.compute(store.events(now - 400L * 86_400_000L), now, ZoneId.systemDefault(), 14, 1);

        ScrollView scroll = new ScrollView(a);
        LinearLayout col = Ui.column(a);
        int pad = Ui.dp(a, 24);
        col.setPadding(pad, Ui.dp(a, 16), pad, Ui.dp(a, 32));
        scroll.addView(col);

        // chut
        LinearLayout head = Ui.row(a);
        head.addView(a.logo(28));
        TextView name = Ui.display(a, "chut", 26, R.color.ink);
        name.setPadding(Ui.dp(a, 10), 0, 0, 0);
        head.addView(name, Ui.weight(1));
        col.addView(head);
        col.addView(Ui.space(a, 40));

        // Protection
        boolean on = a.isProtectionOn();
        LinearLayout status = Ui.row(a);
        status.addView(Ui.dot(a, on ? R.color.allow : R.color.block, 10));
        status.addView(Ui.text(a, a.getString(on ? R.string.status_on : R.string.status_off), 17, R.color.ink, Ui.semibold()));
        col.addView(status);
        if (!on) {
            TextView why = Ui.muted(a, a.getString(R.string.status_off_explain));
            why.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 14));
            col.addView(why);
            col.addView(Ui.button(a, a.getString(R.string.activate), true, v -> a.requestProtection()));
        } else if (a.needsNotificationPermission() && prefs.flag(Prefs.NOTIFY_SILENCED)) {
            TextView n = Ui.text(a, a.getString(R.string.allow_notifications) + "  ›", 14.5f, R.color.brand_ink, Ui.semibold());
            n.setPadding(Ui.dp(a, 22), Ui.dp(a, 8), 0, 0);
            n.setOnClickListener(v -> a.requestNotifications());
            col.addView(n);
        }
        col.addView(Ui.space(a, 44));

        // The number
        col.addView(Ui.display(a, String.valueOf(s.stopped()), 88, R.color.ink));
        col.addView(Ui.text(a, a.getString(R.string.stat_stopped), 17, R.color.ink, Ui.regular()));
        TextView sub = Ui.muted(a, a.getString(R.string.home_sub, s.stoppedToday, s.stoppedThisWeek));
        sub.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 20));
        col.addView(sub);
        col.addView(new Charts.Bars(a, s.perDay, null, 56));
        col.addView(Ui.space(a, 40));

        // Block a number
        LinearLayout row = Ui.row(a);
        EditText input = new EditText(a);
        input.setHint(R.string.add_hint_block);
        input.setContentDescription(a.getString(R.string.add_hint_block));
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setTypeface(Ui.regular());
        input.setTextColor(a.getColor(R.color.ink));
        input.setHintTextColor(a.getColor(R.color.muted));
        input.setBackground(Ui.round(a.getColor(R.color.surface_alt), Ui.dp(a, 14)));
        input.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 12));
        row.addView(input, Ui.weight(1));
        Runnable block = () -> {
            String text = input.getText().toString();
            if (store.add(Store.BLOCKED, text, "manual")) {
                input.setText("");
                a.toast(a.getString(R.string.toast_blocked, PhoneNumbers.pretty(PhoneNumbers.normalize(text))));
                a.refresh();
            } else {
                a.toast(a.getString(R.string.invalid_or_exists));
            }
        };
        TextView go = Ui.button(a, a.getString(R.string.action_block), true, v -> block.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(a, 8);
        row.addView(go, lp);
        input.setOnEditorActionListener((v, id, ev) -> {
            block.run();
            return true;
        });
        col.addView(row);
        col.addView(Ui.space(a, 28));

        // Elsewhere
        col.addView(Ui.divider(a));
        col.addView(Ui.linkRow(a, a.getString(R.string.page_stats), "›", v -> a.show(MainActivity.PAGE_STATS)));
        col.addView(Ui.linkRow(a, a.getString(R.string.tab_settings), "›", v -> a.show(MainActivity.PAGE_SETTINGS)));

        // Numbers: only for those who asked.
        if (prefs.flag(Prefs.SHOW_NUMBERS)) {
            col.addView(Ui.linkRow(a, a.getString(R.string.my_lists), "›", v -> a.show(MainActivity.PAGE_LISTS)));
            List<Stats.Event> recent = store.recent(5);
            if (!recent.isEmpty()) {
                col.addView(Ui.eyebrow(a, a.getString(R.string.latest_calls)));
                for (int i = 0; i < recent.size(); i++) {
                    if (i > 0) col.addView(Ui.divider(a));
                    col.addView(callRow(a, recent.get(i)));
                }
            }
        }
        return scroll;
    }

    public static View callRow(MainActivity a, Stats.Event e) {
        LinearLayout row = Ui.row(a);
        row.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 2));
        row.addView(Ui.dot(a, Labels.actionColor(e.action), 8));
        LinearLayout texts = Ui.column(a);
        String who = e.number.isEmpty() ? a.getString(R.string.hidden_number) : PhoneNumbers.pretty(e.number);
        texts.addView(Ui.text(a, who, 15, R.color.ink, Ui.semibold()));
        List<String> reasons = Labels.reasons(a, e.reasons);
        String why = reasons.isEmpty() ? Labels.category(a, e.category) : reasons.get(0);
        TextView w = Ui.muted(a, Labels.action(a, e.action) + " · " + why);
        w.setMaxLines(1);
        w.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(w);
        row.addView(texts, Ui.weight(1));
        TextView when = Ui.muted(a, DateUtils.getRelativeTimeSpanString(e.time, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE));
        when.setGravity(Gravity.END);
        when.setPadding(Ui.dp(a, 8), 0, 0, 0);
        row.addView(when);
        row.setOnClickListener(v -> a.showCall(e));
        row.setBackground(Ui.pressable(a, 0x00000000, Ui.dp(a, 10)));
        return row;
    }
}
