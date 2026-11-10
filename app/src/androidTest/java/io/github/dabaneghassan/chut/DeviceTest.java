// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dabaneghassan.chut;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.job.JobScheduler;
import android.content.Context;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.ZoneId;
import java.util.List;

import io.github.dabaneghassan.chut.data.Lang;
import io.github.dabaneghassan.chut.data.Learning;
import io.github.dabaneghassan.chut.data.Prefs;
import io.github.dabaneghassan.chut.data.Store;
import io.github.dabaneghassan.chut.engine.Engine;
import io.github.dabaneghassan.chut.engine.Learner;
import io.github.dabaneghassan.chut.engine.Stats;

/** Runs inside the app on a real Android system: database, model, language, notifications, jobs. */
@RunWith(AndroidJUnit4.class)
public class DeviceTest {

    private Context context;
    private Store store;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        store = Store.get(context);
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .grantRuntimePermission(context.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Test
    public void startsWithNoListAndImportsYours() {
        // chut ships no numbers: lists are only what each person adds.
        for (Store.Entry e : store.entries(Store.BLOCKED)) assertFalse(e.source, "preset".equals(e.source));
        // A list exported from a phone ("Blocked numbers"), imported if the user wants to.
        int added = store.addAll(Store.BLOCKED, "+33500000001,+33500000002,+33500000003", "file");
        assertEquals(3, added);
        assertEquals("+33500000002", store.blockedBy("+33500000002"));
        for (String n : new String[]{"+33500000001", "+33500000002", "+33500000003"}) store.remove(n);
    }

    @Test
    public void addRemoveAndPrefixRules() {
        assertTrue(store.add(Store.BLOCKED, "06 11 22 33 44", "test"));
        assertFalse(store.add(Store.BLOCKED, "+33611223344", "test"));       // already there
        assertFalse(store.add(Store.BLOCKED, "hello", "test"));              // not a number
        assertEquals(Store.BLOCKED, store.listOf("+33611223344"));
        assertTrue(store.add(Store.ALLOWED, "+33611223344", "test"));         // moves to the allow list
        assertEquals(Store.ALLOWED, store.listOf("+33611223344"));
        assertTrue(store.isAllowed("+33611223344"));
        store.remove("+33611223344");
        assertEquals(-1, store.listOf("+33611223344"));

        assertTrue(store.add(Store.BLOCKED, "0187*", "test"));
        assertEquals("+33187*", store.blockedBy("+33187000000"));
        store.remove("+33187*");
        assertNull(store.blockedBy("+33187000000"));
    }

    @Test
    public void importsPhoneExportsAndRoundTripsItsOwnExport() {
        // The "Blocked numbers" export format of Samsung phones, plus a line break and a duplicate.
        int added = store.addAll(Store.BLOCKED, "+33600000001,+33600000002\n06 00 00 00 03;+33600000001", "test");
        assertEquals(3, added);

        String export = store.exportCsv();
        assertTrue(export.contains("+33600000002,blocked,"));
        for (String n : new String[]{"+33600000001", "+33600000002", "+33600000003"}) store.remove(n);
        assertEquals(-1, store.listOf("+33600000002"));

        store.addAll(Store.ALLOWED, export, "test");   // the file says which list each number goes to
        assertEquals(Store.BLOCKED, store.listOf("+33600000002"));
        for (String n : new String[]{"+33600000001", "+33600000002", "+33600000003"}) store.remove(n);
    }

    @Test
    public void learnsFromBlocksAndKeepsTheModelOnDisk() throws Exception {
        // Three numbers in one block of 1,000, and two elsewhere: the model starts at five.
        String[] spam = {"+33512340001", "+33512340457", "+33512340930", "+33299000111", "+33388000222"};
        for (String n : spam) store.add(Store.BLOCKED, n, "test");
        Learner.Model m = Learning.retrainNow(context);
        assertTrue(m.ready());
        assertTrue(m.predict("+33512340222", 11) >= Learner.THRESHOLD);
        assertTrue(m.predict("+33698765432", 11) < Learner.THRESHOLD);

        // The model survives the app being killed: it is read back from disk.
        File f = new File(context.getFilesDir(), "model.bin");
        assertTrue(f.exists());
        Learner.Model fromDisk = Learner.Model.fromBytes(java.nio.file.Files.readAllBytes(f.toPath()));
        assertNotNull(fromDisk);
        assertEquals(m.predict("+33512340222", 11), fromDisk.predict("+33512340222", 11), 1e-6);
        assertNotNull(Learning.savedModel(context));

        // The engine uses it for numbers on no list.
        Engine.Verdict v = Engine.evaluate(new Engine.Call("05 12 34 02 22", false, 11, System.currentTimeMillis()),
                new Prefs(context).engineConfig(), store, fromDisk);
        assertTrue(v.reasonCodes(), v.has(Engine.Signal.LEARNED));
        assertTrue(v.action != Engine.Action.ALLOW);

        for (String n : spam) store.remove(n);
        Learning.retrainNow(context);
    }

    @Test
    public void listsDecideBeforeAnythingElse() {
        Engine.Config cfg = new Prefs(context).engineConfig();
        long now = System.currentTimeMillis();
        store.add(Store.BLOCKED, "06 55 44 33 22", "test");
        assertEquals(Engine.Action.BLOCK,
                Engine.evaluate(new Engine.Call("06 55 44 33 22", false, 10, now), cfg, store, null).action);
        store.remove("+33655443322");
        assertEquals(Engine.Action.BLOCK,
                Engine.evaluate(new Engine.Call("01 62 99 99 99", false, 10, now), cfg, store, null).action);
        store.add(Store.ALLOWED, "+33162999999", "test");
        Engine.Verdict v = Engine.evaluate(new Engine.Call("01 62 99 99 99", false, 10, now), cfg, store, null);
        assertEquals(Engine.Action.ALLOW, v.action);
        assertTrue(v.has(Engine.Signal.ALLOWLIST));
        store.remove("+33162999999");
    }

    @Test
    public void frenchByDefaultEnglishOnRequest() {
        Prefs prefs = new Prefs(context);
        String before = prefs.language();
        try {
            prefs.setLanguage("fr");
            assertEquals("Réglages", Lang.wrap(context).getString(R.string.tab_settings));
            assertEquals("Protection active", Lang.wrap(context).getString(R.string.status_on));
            prefs.setLanguage("en");
            assertEquals("Settings", Lang.wrap(context).getString(R.string.tab_settings));
            assertEquals("Protection is on", Lang.wrap(context).getString(R.string.status_on));
        } finally {
            prefs.setLanguage(before);
        }
        // A fresh install has no language saved: French.
        android.content.SharedPreferences raw = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String saved = raw.getString("language", null);
        raw.edit().remove("language").commit();
        assertEquals("fr", new Prefs(context).language());
        assertEquals("Accueil", Lang.wrap(context).getString(R.string.tab_home));
        if (saved != null) raw.edit().putString("language", saved).commit();
    }

    @Test
    public void silencedCallNotificationAndWeeklyReport() throws Exception {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.cancelAll();
        Engine.Config senior = new Engine.Config();
        senior.seniorMode = true;
        Engine.Verdict v = Engine.evaluate(new Engine.Call("06 12 34 56 78", false, 10, System.currentTimeMillis()),
                senior, store, null);
        assertEquals(Engine.Action.SILENCE, v.action);
        Notifier.afterScreening(Lang.wrap(context), "+33612345678", v);
        assertTrue(hasNotification(nm, "06 12 34 56 78"));

        Stats week = Stats.compute(List.of(new Stats.Event(System.currentTimeMillis(), "+33162000010",
                Engine.Action.BLOCK, Engine.Category.TELEMARKETING, "ARCEP_RANGE:0162", 70)),
                System.currentTimeMillis(), ZoneId.systemDefault(), 7, 1);
        Notifier.weeklyReport(Lang.wrap(context), week);
        assertTrue(hasNotification(nm, Lang.wrap(context).getString(R.string.report_title)));
        nm.cancelAll();
    }

    @Test
    public void backgroundJobsAreScheduledAndPersisted() {
        BackgroundJobs.schedule(context);
        JobScheduler js = context.getSystemService(JobScheduler.class);
        assertNotNull(js.getPendingJob(BackgroundJobs.JOB_RETRAIN));
        assertTrue(js.getPendingJob(BackgroundJobs.JOB_RETRAIN).isPersisted());
        assertNotNull(js.getPendingJob(BackgroundJobs.JOB_REPORT));
    }

    @Test
    public void historyFeedsTheStatistics() {
        long now = System.currentTimeMillis();
        Engine.Verdict v = Engine.evaluate(new Engine.Call("01 62 99 88 77", false, 10, now),
                new Engine.Config(), store, null);
        assertEquals(Engine.Category.TELEMARKETING, v.category);
        store.log("+33162998877", v, now);
        Stats s = Stats.compute(store.events(now - 1000), now + 1, ZoneId.systemDefault(), 7, 1);
        assertTrue(s.blocked >= 1);
        assertTrue(s.byCategory.containsKey(Engine.Category.TELEMARKETING));
    }

    private static boolean hasNotification(NotificationManager nm, String text) throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            for (StatusBarNotification sbn : nm.getActiveNotifications()) {
                CharSequence title = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE);
                if (title != null && title.toString().contains(text)) return true;
            }
            Thread.sleep(100);
        }
        return false;
    }
}
