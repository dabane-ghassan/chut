// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import io.github.dabaneghassan.chut.data.Lang;
import io.github.dabaneghassan.chut.data.Learning;
import io.github.dabaneghassan.chut.data.Phone;
import io.github.dabaneghassan.chut.data.Store;
import io.github.dabaneghassan.chut.engine.Engine;
import io.github.dabaneghassan.chut.engine.PhoneNumbers;
import io.github.dabaneghassan.chut.engine.Stats;
import io.github.dabaneghassan.chut.ui.HomeScreen;
import io.github.dabaneghassan.chut.ui.Labels;
import io.github.dabaneghassan.chut.ui.ListsScreen;
import io.github.dabaneghassan.chut.ui.SettingsScreen;
import io.github.dabaneghassan.chut.ui.StatsScreen;
import io.github.dabaneghassan.chut.ui.Ui;

public class MainActivity extends Activity {

    public static final int TAB_HOME = 0, TAB_STATS = 1, TAB_LISTS = 2, TAB_SETTINGS = 3;

    private static final int REQ_ROLE = 1;
    private static final int REQ_NOTIFICATIONS = 2;
    private static final int REQ_IMPORT = 3;
    private static final int REQ_EXPORT = 4;
    private static final int REQ_CONTACTS = 5;
    private static final int REQ_CALL_LOG = 6;

    private int tab = TAB_HOME;
    private FrameLayout content;
    private LinearLayout nav;
    private final TextView[] navItems = new TextView[4];
    /** Which list an import goes to. */
    private int importList = Store.BLOCKED;
    /** Set by the lists screen: which of its tabs is open. */
    public int listsTab = 0;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));   // French unless English was chosen in chut
    }

    /** Switches chut's language and redraws everything in it. */
    public void setLanguage(String lang) {
        new io.github.dabaneghassan.chut.data.Prefs(this).setLanguage(lang);
        Notifier.createChannels(Lang.wrap(this));
        recreate();
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.init(this);
        Notifier.createChannels(this);
        BackgroundJobs.schedule(this);
        if (saved != null) {
            tab = saved.getInt("tab", TAB_HOME);
            listsTab = saved.getInt("listsTab", 0);
        }

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(getColor(R.color.bg));
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        nav = buildNav();
        root.addView(nav, Ui.matchWrap());
        setContentView(root);
        edgeToEdge(root);

        // Opens the database (adding the preset list on first launch) and trains the model if needed.
        new Thread(() -> {
            Learning.model(this);
            runOnUiThread(this::refresh);
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
        out.putInt("listsTab", listsTab);
    }

    @Override
    public void onBackPressed() {
        if (tab != TAB_HOME) show(TAB_HOME);
        else super.onBackPressed();
    }

    // ---- Navigation -------------------------------------------------------------------

    public void show(int t) {
        tab = t;
        refresh();
    }

    /** Rebuilds the open screen from the current data. */
    public void refresh() {
        if (content == null) return;
        View v;
        switch (tab) {
            case TAB_STATS: v = StatsScreen.build(this); break;
            case TAB_LISTS: v = ListsScreen.build(this); break;
            case TAB_SETTINGS: v = SettingsScreen.build(this); break;
            default: v = HomeScreen.build(this); break;
        }
        content.removeAllViews();
        content.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        for (int i = 0; i < navItems.length; i++) {
            boolean on = i == tab;
            navItems[i].setTextColor(getColor(on ? R.color.brand_ink : R.color.muted));
            navItems[i].setBackground(on ? Ui.round(getColor(R.color.brand_soft), Ui.dp(this, 16)) : null);
            navItems[i].getCompoundDrawables()[1].setTint(getColor(on ? R.color.brand_ink : R.color.muted));
        }
    }

    private LinearLayout buildNav() {
        LinearLayout bar = Ui.row(this);
        bar.setBackgroundColor(getColor(R.color.card));
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        int[] labels = {R.string.tab_home, R.string.tab_stats, R.string.tab_lists, R.string.tab_settings};
        int[] icons = {R.drawable.ic_tab_home, R.drawable.ic_tab_stats, R.drawable.ic_tab_lists, R.drawable.ic_tab_settings};
        for (int i = 0; i < 4; i++) {
            final int index = i;
            TextView t = Ui.text(this, getString(labels[i]), 12.5f, R.color.muted, Ui.semibold());
            t.setGravity(Gravity.CENTER);
            t.setCompoundDrawablesWithIntrinsicBounds(0, icons[i], 0, 0);
            t.getCompoundDrawables()[1].mutate();
            t.setCompoundDrawablePadding(Ui.dp(this, 2));
            t.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            t.setOnClickListener(v -> show(index));
            LinearLayout.LayoutParams lp = Ui.weight(1);
            lp.leftMargin = Ui.dp(this, 4);
            lp.rightMargin = Ui.dp(this, 4);
            bar.addView(t, lp);
            navItems[i] = t;
        }
        return bar;
    }

    private void edgeToEdge(View root) {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(getColor(R.color.card));
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(night ? 0 : light, light);
            }
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (!night) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            root.setSystemUiVisibility(flags);
        }
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets sys = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                top = sys.top;
                bottom = Math.max(sys.bottom, ime.bottom);
                left = sys.left;
                right = sys.right;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            v.setPadding(left, 0, right, 0);
            content.setPadding(0, top, 0, 0);
            nav.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6) + bottom);
            return insets;
        });
    }

    // ---- Protection role ----------------------------------------------------------------

    public boolean isProtectionOn() {
        RoleManager rm = getSystemService(RoleManager.class);
        return rm != null && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    public void requestProtection() {
        RoleManager rm = getSystemService(RoleManager.class);
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_ROLE);
        } else {
            toast(getString(R.string.role_unavailable));
        }
    }

    public boolean needsNotificationPermission() {
        return Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    public void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
    }

    public void requestContacts() {
        requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
    }

    // ---- Import / export ----------------------------------------------------------------

    public void importFile(int list) {
        importList = list;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/*", "application/csv", "application/octet-stream"});
        startActivityForResult(i, REQ_IMPORT);
    }

    public void exportFile() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv")
                .putExtra(Intent.EXTRA_TITLE, "chut-export.csv");
        startActivityForResult(i, REQ_EXPORT);
    }

    public void importFromCallLog() {
        if (!Phone.canReadCallLog(this)) {
            requestPermissions(new String[]{Manifest.permission.READ_CALL_LOG}, REQ_CALL_LOG);
            return;
        }
        Store store = Store.get(this);
        List<Phone.Caller> callers = Phone.recentUnknownCallers(this, store);
        if (callers.isEmpty()) {
            toast(getString(R.string.calllog_empty));
            return;
        }
        int n = Math.min(callers.size(), 60);
        String[] items = new String[n];
        boolean[] checked = new boolean[n];
        for (int i = 0; i < n; i++) {
            Phone.Caller c = callers.get(i);
            Engine.Verdict v = Engine.evaluate(new Engine.Call(c.number, false, -1, System.currentTimeMillis()),
                    new io.github.dabaneghassan.chut.data.Prefs(this).engineConfig(), store, Learning.model(this));
            String tag = v.action == Engine.Action.ALLOW ? "" : "  ·  " + Labels.category(this, v.category);
            items[i] = PhoneNumbers.pretty(c.number) + "  ×" + c.calls + tag;
            checked[i] = v.action != Engine.Action.ALLOW;      // pre-tick what chut finds suspicious
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.calllog_title)
                .setMultiChoiceItems(items, checked, (d, which, on) -> checked[which] = on)
                .setPositiveButton(R.string.block_selected, (d, w) -> {
                    int added = 0;
                    for (int i = 0; i < n; i++) if (checked[i] && store.add(Store.BLOCKED, callers.get(i).number, "call log")) added++;
                    toast(getResources().getQuantityString(R.plurals.imported, added, added));
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ROLE) {
            if (isProtectionOn() && needsNotificationPermission()) requestNotifications();
            refresh();
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_IMPORT) {
            new Thread(() -> {
                int added;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] b = new byte[8192];
                    int r, total = 0;
                    while (in != null && (r = in.read(b)) > 0 && (total += r) < 16 * 1024 * 1024) buf.write(b, 0, r);
                    added = Store.get(this).addAll(importList, buf.toString("UTF-8"), "file");
                } catch (Exception e) {
                    added = -1;
                }
                final int a = added;
                runOnUiThread(() -> {
                    toast(a < 0 ? getString(R.string.import_failed) : getResources().getQuantityString(R.plurals.imported, a, a));
                    refresh();
                });
            }).start();
        } else if (requestCode == REQ_EXPORT) {
            new Thread(() -> {
                boolean ok;
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    out.write(Store.get(this).exportCsv().getBytes(StandardCharsets.UTF_8));
                    ok = true;
                } catch (Exception e) {
                    ok = false;
                }
                final boolean done = ok;
                runOnUiThread(() -> toast(getString(done ? R.string.export_done : R.string.export_failed)));
            }).start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        boolean granted = results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_CALL_LOG && granted) importFromCallLog();
        refresh();
    }

    // ---- Shared dialogs -------------------------------------------------------------------

    /** Details of a screened call, with what you can do about the number. */
    public void showCall(Stats.Event e) {
        Store store = Store.get(this);
        LinearLayout box = Ui.column(this);
        int p = Ui.dp(this, 22);
        box.setPadding(p, Ui.dp(this, 18), p, 0);
        String who = e.number.isEmpty() ? getString(R.string.hidden_number) : PhoneNumbers.pretty(e.number);
        box.addView(Ui.display(this, who, 30, R.color.ink));
        TextView verdict = Ui.text(this, Labels.action(this, e.action) + " · " + getString(R.string.score_only, e.score),
                15, Labels.actionColor(e.action), Ui.semibold());
        verdict.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 2));
        box.addView(verdict);
        box.addView(Ui.muted(this, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(e.time))
                + (e.number.isEmpty() ? "" : " · " + Labels.region(this, PhoneNumbers.region(e.number)))));
        box.addView(Ui.space(this, 12));
        List<String> reasons = Labels.reasons(this, e.reasons);
        if (reasons.isEmpty()) reasons.add(getString(R.string.no_reason));
        for (String r : reasons) {
            TextView t = Ui.body(this, "•  " + r);
            t.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));
            box.addView(t);
        }
        if (!e.number.isEmpty()) {
            int calls = store.callsSince(e.number, 0);
            box.addView(Ui.space(this, 8));
            box.addView(Ui.muted(this, getResources().getQuantityString(R.plurals.calls_from_number, calls, calls)));
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this).setView(box);
        if (!e.number.isEmpty()) {
            int list = store.listOf(e.number);
            if (list == Store.BLOCKED) {
                b.setPositiveButton(R.string.unblock, (d, w) -> {
                    store.remove(e.number);
                    refresh();
                });
            } else {
                b.setPositiveButton(R.string.action_block, (d, w) -> {
                    store.add(Store.BLOCKED, e.number, "history");
                    toast(getString(R.string.toast_blocked, PhoneNumbers.pretty(e.number)));
                    refresh();
                });
            }
            if (list != Store.ALLOWED) {
                b.setNegativeButton(R.string.action_trust, (d, w) -> {
                    store.add(Store.ALLOWED, e.number, "history");
                    toast(getString(R.string.toast_trusted, PhoneNumbers.pretty(e.number)));
                    refresh();
                });
            }
        } else {
            b.setPositiveButton(android.R.string.ok, null);
        }
        b.show();
    }

    public void openUrl(String url) {
        openIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void openIntent(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            toast(getString(R.string.no_browser));
        }
    }

    public void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /** The logo drawn at a given size. */
    public ImageView logo(int sizeDp) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(R.drawable.ic_logo);
        iv.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, sizeDp), Ui.dp(this, sizeDp)));
        iv.setContentDescription(getString(R.string.app_name));
        return iv;
    }
}
