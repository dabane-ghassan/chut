// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.ui;

import android.app.AlertDialog;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import io.github.dabaneghassan.chut.MainActivity;
import io.github.dabaneghassan.chut.R;
import io.github.dabaneghassan.chut.data.Store;
import io.github.dabaneghassan.chut.engine.PhoneNumbers;

/**
 * Your own block and allow lists. Opt-in: the page only exists when "Show numbers" is on.
 */
public final class ListsScreen {

    private static final int MAX_SHOWN = 300;

    private ListsScreen() {
    }

    public static View build(MainActivity a) {
        Store store = Store.get(a);
        ScrollView scroll = new ScrollView(a);
        LinearLayout col = Ui.column(a);
        int pad = Ui.dp(a, 24);
        col.setPadding(pad, Ui.dp(a, 4), pad, Ui.dp(a, 32));
        scroll.addView(col);
        col.addView(a.backLink(MainActivity.PAGE_HOME, R.string.tab_home));
        col.addView(StatsScreen.header(a, a.getString(R.string.my_lists), null));

        if (a.listsTab > 1) a.listsTab = 0;
        int list = a.listsTab == 1 ? Store.ALLOWED : Store.BLOCKED;
        List<Store.Entry> mine = store.entries(list);
        col.addView(Ui.segmented(a, new String[]{a.getString(R.string.list_blocked), a.getString(R.string.list_allowed)},
                a.listsTab, i -> {
                    a.listsTab = i;
                    a.refresh();
                }));
        col.addView(Ui.space(a, 16));

        // Add
        LinearLayout row = Ui.row(a);
        EditText input = new EditText(a);
        input.setHint(list == Store.BLOCKED ? R.string.add_hint_block : R.string.add_hint_allow);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setTypeface(Ui.regular());
        input.setTextColor(a.getColor(R.color.ink));
        input.setHintTextColor(a.getColor(R.color.muted));
        input.setBackground(Ui.round(a.getColor(R.color.surface_alt), Ui.dp(a, 14)));
        input.setPadding(Ui.dp(a, 16), Ui.dp(a, 12), Ui.dp(a, 16), Ui.dp(a, 12));
        row.addView(input, Ui.weight(1));
        Runnable doAdd = () -> {
            String text = input.getText().toString();
            if (store.add(list, text, "manual")) {
                a.toast(a.getString(list == Store.BLOCKED ? R.string.toast_blocked : R.string.toast_trusted,
                        PhoneNumbers.pretty(PhoneNumbers.normalize(text))));
                a.refresh();
            } else {
                a.toast(a.getString(R.string.invalid_or_exists));
            }
        };
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(a, 8);
        row.addView(Ui.button(a, a.getString(R.string.add), true, v -> doAdd.run()), lp);
        input.setOnEditorActionListener((v, id, ev) -> {
            doAdd.run();
            return true;
        });
        col.addView(row);
        TextView tip = Ui.muted(a, a.getString(R.string.prefix_tip));
        tip.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 16));
        col.addView(tip);

        if (mine.isEmpty()) {
            TextView empty = Ui.muted(a, a.getString(list == Store.BLOCKED ? R.string.blocked_empty : R.string.allowed_empty));
            empty.setPadding(0, Ui.dp(a, 8), 0, 0);
            col.addView(empty);
        }
        for (int i = 0; i < mine.size() && i < MAX_SHOWN; i++) {
            col.addView(Ui.divider(a));
            col.addView(entryRow(a, store, mine.get(i), list));
        }
        if (mine.size() > MAX_SHOWN) col.addView(Ui.muted(a, a.getString(R.string.more_entries, mine.size() - MAX_SHOWN)));
        return scroll;
    }

    private static View entryRow(MainActivity a, Store store, Store.Entry e, int list) {
        LinearLayout row = Ui.row(a);
        LinearLayout texts = Ui.column(a);
        texts.addView(Ui.text(a, PhoneNumbers.pretty(e.number), 15, R.color.ink, Ui.semibold()));
        String kind = PhoneNumbers.isPrefixRule(e.number) ? a.getString(R.string.prefix_rule)
                : Labels.region(a, PhoneNumbers.region(e.number));
        texts.addView(Ui.muted(a, kind + " · " + DateUtils.getRelativeTimeSpanString(e.added)));
        row.addView(texts, Ui.weight(1));
        row.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 2));
        row.setBackground(Ui.pressable(a, 0x00000000, Ui.dp(a, 10)));
        row.setOnClickListener(v -> new AlertDialog.Builder(a)
                .setTitle(PhoneNumbers.pretty(e.number))
                .setPositiveButton(list == Store.BLOCKED ? R.string.unblock : R.string.remove_trust, (d, w) -> {
                    store.remove(e.number);
                    a.refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        return row;
    }
}
