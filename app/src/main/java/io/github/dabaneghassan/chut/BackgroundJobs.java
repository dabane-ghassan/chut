// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import java.time.ZoneId;

import io.github.dabaneghassan.chut.data.Community;
import io.github.dabaneghassan.chut.data.Prefs;
import io.github.dabaneghassan.chut.data.Store;
import io.github.dabaneghassan.chut.engine.Stats;

/**
 * Two small jobs that Android runs when it suits the battery: the weekly report and,
 * if you turned it on, the daily community-list update. Nothing else runs in the background.
 */
public class BackgroundJobs extends JobService {

    static final int JOB_SYNC = 1;
    static final int JOB_REPORT = 2;
    private static final long DAY = 24 * 60 * 60 * 1000L;

    public static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        Prefs prefs = new Prefs(c);
        ComponentName me = new ComponentName(c, BackgroundJobs.class);
        if (prefs.flag(Prefs.SYNC)) {
            if (js.getPendingJob(JOB_SYNC) == null) {
                js.schedule(new JobInfo.Builder(JOB_SYNC, me)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPeriodic(DAY)
                        .setPersisted(true)
                        .build());
            }
        } else {
            js.cancel(JOB_SYNC);
        }
        if (prefs.flag(Prefs.WEEKLY)) {
            if (js.getPendingJob(JOB_REPORT) == null) {
                js.schedule(new JobInfo.Builder(JOB_REPORT, me)
                        .setPeriodic(7 * DAY)
                        .setPersisted(true)
                        .build());
            }
        } else {
            js.cancel(JOB_REPORT);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            boolean retry = false;
            try {
                if (params.getJobId() == JOB_SYNC) {
                    retry = Community.update(this) < 0;
                } else if (params.getJobId() == JOB_REPORT) {
                    long now = System.currentTimeMillis();
                    Stats week = Stats.compute(Store.get(this).events(now - 7 * DAY), now, ZoneId.systemDefault(), 7, 1);
                    Notifier.weeklyReport(this, week);
                }
            } catch (Throwable ignored) {
            }
            jobFinished(params, retry);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
