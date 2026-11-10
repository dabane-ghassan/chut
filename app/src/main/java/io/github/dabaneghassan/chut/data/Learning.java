// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.data;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.dabaneghassan.chut.engine.Learner;

/**
 * Keeps the learned model on disk and up to date. It is retrained in the background a moment
 * after your lists change, and every week with the new call history. The screening service
 * reads it from a small cache and never waits for training.
 */
public final class Learning {

    private static final String FILE = "model.bin";
    private static final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chut-learning");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final AtomicBoolean pending = new AtomicBoolean(false);
    private static volatile Learner.Model cached;
    private static volatile long cachedStamp = -1;

    private Learning() {
    }

    private static File file(Context c) {
        return new File(c.getApplicationContext().getFilesDir(), FILE);
    }

    /** The current model, training it now if there is none yet. */
    public static Learner.Model model(Context c) {
        File f = file(c);
        long stamp = f.exists() ? f.lastModified() : 0;
        Learner.Model m = cached;
        if (m != null && stamp == cachedStamp) return m;
        if (f.exists()) {
            try {
                m = Learner.Model.fromBytes(Files.readAllBytes(f.toPath()));
            } catch (Exception e) {
                m = null;
            }
        }
        if (m == null) m = retrainNow(c);
        cached = m;
        cachedStamp = file(c).exists() ? file(c).lastModified() : 0;
        return m;
    }

    /**
     * The saved model, without ever training on the caller's thread: when there is none yet,
     * training starts in the background and this returns null. Used while a call waits.
     */
    public static Learner.Model savedModel(Context c) {
        File f = file(c);
        if (!f.exists()) {
            retrainSoon(c);
            return null;
        }
        Learner.Model m = cached;
        if (m != null && f.lastModified() == cachedStamp) return m;
        try {
            m = Learner.Model.fromBytes(Files.readAllBytes(f.toPath()));
        } catch (Exception e) {
            m = null;
        }
        if (m == null) {
            retrainSoon(c);
            return null;
        }
        cached = m;
        cachedStamp = f.lastModified();
        return m;
    }

    /** Trains on the whole history now, saves and returns the model. Off the main thread if you can. */
    public static synchronized Learner.Model retrainNow(Context c) {
        Learner.Model m = Learner.trainAndEvaluate(Store.get(c).trainingSamples(), 42, System.currentTimeMillis());
        File f = file(c);
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(m.toBytes());
            out.getFD().sync();
        } catch (Exception e) {
            return m;
        }
        if (tmp.renameTo(f)) {
            cached = m;
            cachedStamp = f.lastModified();
        }
        return m;
    }

    /** Retrains in the background; several changes in a row cause one training. */
    public static void retrainSoon(Context c) {
        Context app = c.getApplicationContext();
        if (!pending.compareAndSet(false, true)) return;
        worker.execute(() -> {
            try {
                Thread.sleep(400);
            } catch (InterruptedException ignored) {
            }
            pending.set(false);
            try {
                retrainNow(app);
            } catch (Throwable ignored) {
            }
        });
    }
}
