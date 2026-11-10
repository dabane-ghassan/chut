// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.dabaneghassan.chut.R;

/** Two tiny chart views drawn straight on the canvas. */
public final class Charts {

    private Charts() {
    }

    /** Stacked bars: [i][0] blocked (bottom), [i][1] silenced (top). Labels under some bars. */
    public static final class Bars extends View {
        private final int[][] values;
        private final String[] labels;
        private final Paint block = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint silence = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint empty = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float density;

        public Bars(Context c, int[][] values, String[] labels, int heightDp) {
            super(c);
            this.values = values;
            this.labels = labels;
            density = c.getResources().getDisplayMetrics().density;
            block.setColor(c.getColor(R.color.block));
            silence.setColor(c.getColor(R.color.silence));
            empty.setColor(c.getColor(R.color.line));
            grid.setColor(c.getColor(R.color.line));
            grid.setStrokeWidth(Math.max(1, density / 2));
            label.setColor(c.getColor(R.color.muted));
            label.setTextSize(11 * c.getResources().getDisplayMetrics().scaledDensity);
            label.setTypeface(Ui.regular());
            label.setTextAlign(Paint.Align.CENTER);
            setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, heightDp)));
            int total = 0;
            for (int[] v : values) total += v[0] + v[1];
            setContentDescription(c.getString(R.string.chart_description, values.length, total));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int n = values.length;
            if (n == 0) return;
            float w = getWidth(), h = getHeight();
            float labelH = 18 * density;
            float chartH = h - labelH;
            int max = 1;
            for (int[] v : values) max = Math.max(max, v[0] + v[1]);
            float slot = w / n;
            float barW = Math.max(2 * density, Math.min(slot * 0.62f, 22 * density));
            float rad = Math.min(barW / 2, 4 * density);
            canvas.drawLine(0, chartH, w, chartH, grid);
            for (int i = 0; i < n; i++) {
                float cx = slot * i + slot / 2;
                int b = values[i][0], s = values[i][1];
                if (b + s == 0) {
                    r.set(cx - barW / 2, chartH - 2 * density, cx + barW / 2, chartH);
                    canvas.drawRoundRect(r, rad, rad, empty);
                } else {
                    float hb = (chartH - 6 * density) * b / max;
                    float hs = (chartH - 6 * density) * s / max;
                    // silenced on top, rounded; blocked below
                    if (s > 0) {
                        r.set(cx - barW / 2, chartH - hb - hs, cx + barW / 2, chartH - hb + (b > 0 ? rad : 0));
                        canvas.drawRoundRect(r, rad, rad, silence);
                    }
                    if (b > 0) {
                        r.set(cx - barW / 2, chartH - hb, cx + barW / 2, chartH);
                        canvas.drawRoundRect(r, s > 0 ? 0 : rad, s > 0 ? 0 : rad, block);
                        if (s == 0) canvas.drawRect(cx - barW / 2, chartH - Math.min(rad, hb), cx + barW / 2, chartH, block);
                    }
                }
                if (labels != null && i < labels.length && labels[i] != null) {
                    canvas.drawText(labels[i], cx, h - 4 * density, label);
                }
            }
            // max value label at top-left
            label.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(String.valueOf(max), 0, 11 * density, label);
            label.setTextAlign(Paint.Align.CENTER);
        }
    }

    /** A thin rounded bar showing a fraction. */
    public static final class Meter extends View {
        private final float fraction;
        private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        public Meter(Context c, float fraction, int colorRes) {
            super(c);
            this.fraction = Math.max(0, Math.min(1, fraction));
            fg.setColor(c.getColor(colorRes));
            bg.setColor(c.getColor(R.color.surface_alt));
            setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 8)));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float h = getHeight(), w = getWidth();
            r.set(0, 0, w, h);
            canvas.drawRoundRect(r, h / 2, h / 2, bg);
            if (fraction > 0) {
                r.set(0, 0, Math.max(h, w * fraction), h);
                canvas.drawRoundRect(r, h / 2, h / 2, fg);
            }
        }
    }

    /** Label, count and a meter: one line of a breakdown. */
    public static LinearLayout breakdownRow(Context c, String name, int value, int max, int colorRes) {
        LinearLayout col = Ui.column(c);
        col.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        LinearLayout top = Ui.row(c);
        top.addView(Ui.text(c, name, 14.5f, R.color.ink, Ui.semibold()), Ui.weight(1));
        TextView v = Ui.text(c, String.valueOf(value), 14.5f, R.color.muted, Ui.semibold());
        v.setFontFeatureSettings("tnum");
        top.addView(v);
        col.addView(top);
        View m = new Meter(c, max == 0 ? 0 : (float) value / max, colorRes);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) m.getLayoutParams();
        lp.topMargin = Ui.dp(c, 6);
        col.addView(m, lp);
        return col;
    }

    /** Legend for the stacked bars. */
    public static LinearLayout legend(Context c) {
        LinearLayout l = Ui.row(c);
        l.setPadding(0, Ui.dp(c, 8), 0, 0);
        l.addView(Ui.dot(c, R.color.block, 10));
        l.addView(Ui.muted(c, c.getString(R.string.legend_blocked)));
        View gap = new View(c);
        l.addView(gap, new LinearLayout.LayoutParams(Ui.dp(c, 18), 1));
        l.addView(Ui.dot(c, R.color.silence, 10));
        l.addView(Ui.muted(c, c.getString(R.string.legend_silenced)));
        return l;
    }
}
