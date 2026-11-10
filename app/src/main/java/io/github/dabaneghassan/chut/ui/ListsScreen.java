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

/** Your block list (with the numbers chut ships with) and your allow list. No limits. */
public final class ListsScreen {

    private static final int MAX_SHOWN = 400;

    private ListsScreen() {
    }

    public static View build(MainActivity a) {
        Store store = Store.get(a);
        ScrollView scroll = new ScrollView(a);
        LinearLayout col = Ui.column(a);
        int pad = Ui.dp(a, 16);
        col.setPadding(pad, Ui.dp(a, 8), pad, Ui.dp(a, 24));
        scroll.addView(col);
        col.addView(StatsScreen.header(a, a.getString(R.string.tab_lists), a.getString(R.string.lists_subtitle)));

        if (a.listsTab > 1) a.listsTab = 0;
        String[] tabs = {
                a.getString(R.string.list_blocked_n, store.size(Store.BLOCKED)),
                a.getString(R.string.list_allowed_n, store.size(Store.ALLOWED))
        };
        col.addView(Ui.segmented(a, tabs, a.listsTab, i -> {
            a.listsTab = i;
            a.refresh();
        }));
        col.addView(Ui.space(a, 12));
        if (a.listsTab == 0) {
            TextView learn = Ui.muted(a, a.getString(R.string.lists_learning_note));
            learn.setPadding(Ui.dp(a, 4), 0, Ui.dp(a, 4), Ui.dp(a, 10));
            col.addView(learn);
        }

        int list = a.listsTab == 1 ? Store.ALLOWED : Store.BLOCKED;

        // Add
        LinearLayout add = Ui.card(a);
        LinearLayout row = Ui.row(a);
        EditText input = new EditText(a);
        input.setHint(list == Store.BLOCKED ? R.string.add_hint_block : R.string.add_hint_allow);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setTypeface(Ui.regular());
        input.setTextColor(a.getColor(R.color.ink));
        input.setHintTextColor(a.getColor(R.color.muted));
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
        TextView addBtn = Ui.button(a, a.getString(R.string.add), true, v -> doAdd.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(a, 8);
        row.addView(addBtn, lp);
        input.setOnEditorActionListener((v, id, ev) -> {
            doAdd.run();
            return true;
        });
        add.addView(row);
        TextView tip = Ui.muted(a, a.getString(R.string.prefix_tip));
        tip.setPadding(0, Ui.dp(a, 6), 0, 0);
        add.addView(tip);
        col.addView(add);

        // Import
        LinearLayout imp = Ui.card(a);
        imp.addView(Ui.linkRow(a, a.getString(R.string.import_paste), null, v -> paste(a, store, list)));
        imp.addView(Ui.linkRow(a, a.getString(R.string.import_file), null, v -> a.importFile(list)));
        if (list == Store.BLOCKED) {
            imp.addView(Ui.linkRow(a, a.getString(R.string.import_calllog), null, v -> a.importFromCallLog()));
        }
        imp.addView(Ui.linkRow(a, a.getString(R.string.export_csv), null, v -> a.exportFile()));
        col.addView(imp);

        // Entries
        List<Store.Entry> entries = store.entries(list);
        LinearLayout box = Ui.card(a);
        if (entries.isEmpty()) {
            box.addView(Ui.muted(a, a.getString(list == Store.BLOCKED ? R.string.blocked_empty : R.string.allowed_empty)));
        }
        for (int i = 0; i < entries.size() && i < MAX_SHOWN; i++) {
            if (i > 0) box.addView(Ui.divider(a));
            box.addView(entryRow(a, store, entries.get(i), list));
        }
        if (entries.size() > MAX_SHOWN) {
            box.addView(Ui.muted(a, a.getString(R.string.more_entries, entries.size() - MAX_SHOWN)));
        }
        col.addView(box);
        return scroll;
    }

    private static View entryRow(MainActivity a, Store store, Store.Entry e, int list) {
        LinearLayout row = Ui.row(a);
        row.addView(Ui.dot(a, list == Store.BLOCKED ? R.color.block : R.color.allow, 8));
        LinearLayout texts = Ui.column(a);
        texts.addView(Ui.text(a, PhoneNumbers.pretty(e.number), 15.5f, R.color.ink, Ui.semibold()));
        String kind = PhoneNumbers.isPrefixRule(e.number) ? a.getString(R.string.prefix_rule)
                : Labels.region(a, PhoneNumbers.region(e.number));
        String when = Store.SOURCE_PRESET.equals(e.source) ? a.getString(R.string.source_preset)
                : DateUtils.getRelativeTimeSpanString(e.added).toString();
        texts.addView(Ui.muted(a, kind + " · " + when));
        row.addView(texts, Ui.weight(1));
        row.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 2));
        row.setBackground(Ui.pressable(a, 0x00000000, Ui.dp(a, 10)));
        row.setOnClickListener(v -> {
            AlertDialog.Builder b = new AlertDialog.Builder(a)
                    .setTitle(PhoneNumbers.pretty(e.number))
                    .setPositiveButton(list == Store.BLOCKED ? R.string.unblock : R.string.remove_trust, (d, w) -> {
                        store.remove(e.number);
                        a.refresh();
                    })
                    .setNegativeButton(android.R.string.cancel, null);
            b.show();
        });
        return row;
    }

    private static void paste(MainActivity a, Store store, int list) {
        EditText box = new EditText(a);
        box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.setMinLines(4);
        box.setHint(R.string.paste_hint);
        int p = Ui.dp(a, 20);
        box.setPadding(p, p, p, p);
        new AlertDialog.Builder(a)
                .setTitle(R.string.import_paste)
                .setView(box)
                .setPositiveButton(R.string.import_do, (d, w) -> {
                    int added = store.addAll(list, box.getText().toString(), "paste");
                    a.toast(a.getResources().getQuantityString(R.plurals.imported, added, added));
                    a.refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
