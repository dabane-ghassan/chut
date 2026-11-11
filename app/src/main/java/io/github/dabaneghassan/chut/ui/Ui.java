// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.function.IntConsumer;

import io.github.dabaneghassan.chut.R;

/** Small helpers to build the screens in code, with the chut look. */
public final class Ui {

    private static Typeface regular, semibold, display;

    private Ui() {
    }

    public static void init(Context c) {
        if (regular != null) return;
        try {
            regular = Typeface.createFromAsset(c.getAssets(), "fonts/Barlow-Regular.ttf");
            semibold = Typeface.createFromAsset(c.getAssets(), "fonts/Barlow-SemiBold.ttf");
            display = Typeface.createFromAsset(c.getAssets(), "fonts/BarlowCondensed-ExtraBold.ttf");
        } catch (Exception e) {
            regular = Typeface.DEFAULT;
            semibold = Typeface.DEFAULT_BOLD;
            display = Typeface.DEFAULT_BOLD;
        }
    }

    public static Typeface regular() {
        return regular;
    }

    public static Typeface semibold() {
        return semibold;
    }

    public static Typeface display() {
        return display;
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static int color(Context c, int res) {
        return c.getColor(res);
    }

    public static GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    public static Drawable pressable(Context c, int color, float radius) {
        GradientDrawable mask = round(0xFFFFFFFF, radius);
        return new RippleDrawable(ColorStateList.valueOf(color(c, R.color.ripple)), round(color, radius), mask);
    }

    // ---- Text -------------------------------------------------------------------------

    public static TextView text(Context c, CharSequence s, float sp, int colorRes, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color(c, colorRes));
        t.setTypeface(tf);
        t.setLineSpacing(0, 1.12f);
        return t;
    }

    public static TextView display(Context c, CharSequence s, float sp, int colorRes) {
        TextView t = text(c, s, sp, colorRes, display);
        t.setLineSpacing(0, 0.95f);
        t.setIncludeFontPadding(false);
        t.setFontFeatureSettings("tnum");
        return t;
    }

    public static TextView title(Context c, CharSequence s) {
        return text(c, s, 19, R.color.ink, semibold);
    }

    public static TextView body(Context c, CharSequence s) {
        return text(c, s, 15, R.color.ink, regular);
    }

    public static TextView muted(Context c, CharSequence s) {
        return text(c, s, 13.5f, R.color.muted, regular);
    }

    /** Small uppercase label above a section. */
    public static TextView eyebrow(Context c, CharSequence s) {
        TextView t = text(c, s.toString().toUpperCase(), 12.5f, R.color.muted, semibold);
        t.setLetterSpacing(0.12f);
        t.setPadding(dp(c, 4), dp(c, 18), 0, dp(c, 8));
        return t;
    }

    // ---- Layout -----------------------------------------------------------------------

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout card(Context c) {
        return card(c, R.color.card);
    }

    public static LinearLayout card(Context c, int bgRes) {
        LinearLayout l = column(c);
        l.setBackground(round(color(c, bgRes), dp(c, 20)));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.bottomMargin = dp(c, 10);
        l.setLayoutParams(lp);
        return l;
    }

    public static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static View space(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dp)));
        return v;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(color(c, R.color.line));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1) / 2));
        lp.topMargin = dp(c, 10);
        lp.bottomMargin = dp(c, 10);
        v.setLayoutParams(lp);
        return v;
    }

    /** A coloured dot. */
    public static View dot(Context c, int colorRes, int sizeDp) {
        View v = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color(c, colorRes));
        v.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp));
        lp.rightMargin = dp(c, 12);
        v.setLayoutParams(lp);
        return v;
    }

    // ---- Controls ---------------------------------------------------------------------

    /** Filled (primary) or tonal button. */
    public static TextView button(Context c, CharSequence label, boolean primary, View.OnClickListener l) {
        TextView b = text(c, label, 15, primary ? R.color.on_brand : R.color.brand_ink, semibold);
        b.setGravity(Gravity.CENTER);
        b.setBackground(pressable(c, color(c, primary ? R.color.brand : R.color.brand_soft), dp(c, 14)));
        b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        b.setOnClickListener(l);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    /** Title, optional explanation and a switch. Tapping anywhere on the row toggles it. */
    public static LinearLayout switchRow(Context c, int title, int subtitle, boolean checked,
                                         CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = row(c);
        row.setPadding(0, dp(c, 8), 0, dp(c, 8));
        LinearLayout texts = column(c);
        texts.addView(text(c, c.getString(title), 15.5f, R.color.ink, semibold));
        if (subtitle != 0) {
            TextView s = muted(c, c.getString(subtitle));
            s.setPadding(0, dp(c, 2), dp(c, 8), 0);
            texts.addView(s);
        }
        row.addView(texts, weight(1));
        Switch sw = new Switch(c);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(l);
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        row.setBackground(pressable(c, 0x00000000, dp(c, 12)));
        return row;
    }

    /** A row you tap to open something: title, value on the right. */
    public static LinearLayout linkRow(Context c, CharSequence title, CharSequence value, View.OnClickListener l) {
        LinearLayout row = row(c);
        row.setPadding(0, dp(c, 12), 0, dp(c, 12));
        row.addView(text(c, title, 15.5f, R.color.ink, semibold), weight(1));
        if (value != null) {
            TextView v = text(c, value, 14, R.color.brand_ink, semibold);
            v.setMaxLines(1);
            v.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(v);
        }
        row.setOnClickListener(l);
        row.setBackground(pressable(c, 0x00000000, dp(c, 12)));
        return row;
    }

    /** Pill-shaped segmented choice. */
    public static LinearLayout segmented(Context c, String[] labels, int selected, IntConsumer onPick) {
        LinearLayout row = row(c);
        row.setBackground(round(color(c, R.color.surface_alt), dp(c, 14)));
        int p = dp(c, 4);
        row.setPadding(p, p, p, p);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView t = text(c, labels[i], 14, R.color.ink, semibold);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(c, 6), dp(c, 9), dp(c, 6), dp(c, 9));
            t.setMaxLines(1);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setOnClickListener(v -> {
                if (v.isSelected()) return;
                select(row, index);
                onPick.accept(index);
            });
            row.addView(t, weight(1));
        }
        select(row, selected);
        return row;
    }

    /** Marks one choice as selected, in place: the screen is not rebuilt. */
    private static void select(LinearLayout row, int selected) {
        Context c = row.getContext();
        for (int i = 0; i < row.getChildCount(); i++) {
            TextView t = (TextView) row.getChildAt(i);
            boolean on = i == selected;
            t.setSelected(on);
            t.setTextColor(color(c, on ? R.color.on_brand : R.color.ink));
            t.setBackground(on ? round(color(c, R.color.brand), dp(c, 11)) : pressable(c, 0x00000000, dp(c, 11)));
        }
    }
}
