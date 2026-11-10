// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Ghassan Dabane
package io.github.dabaneghassan.chut.engine;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The spam model chut learns on the phone from what you block.
 *
 * <p><b>What it learns.</b> Spam platforms rent numbers in blocks and rotate through them, at set
 * hours. The model sees each number as a set of features: every prefix from 2 to 9 digits
 * ("0512", "05123", … "051234045"), the type of line, and the time of the call when it is known.
 * A logistic regression gives each feature a weight; a number's spam probability is
 * σ(Σ weights of its features). A new number from a block you keep blocking gets a high
 * probability, even though you never saw it.
 *
 * <p><b>How it learns.</b> Positives are the numbers on your block list (and the hours they
 * called). Negatives are your allow list, the calls you let ring, and, because a block list
 * alone says nothing about what normal numbers look like, a background of 3,000 random French
 * numbers ("positive-unlabelled" learning). Training is stochastic gradient descent on the
 * log-loss with L2 regularisation, seeded so the same history always gives the same model.
 * Evidence has to add up: one blocked number does not make its neighbours spam, several
 * blocked numbers in the same block of 1,000 do. Training takes well under a second.
 *
 * <p><b>How well it works.</b> {@link #trainAndEvaluate} also runs a 5-fold cross-validation:
 * each fifth of your blocked numbers is hidden, the model is trained on the rest, and we count how
 * many hidden spam numbers it still recognises (recall) and how many random numbers it wrongly
 * flags (false alarms), at {@link #THRESHOLD}. The app shows both.
 *
 * <p>Features are hashed into {@link #DIM} weights (the "hashing trick"), so the model has a
 * fixed size of 64 KB whatever you block. Pure Java: unit-tested on the JVM.
 */
public final class Learner {

    public static final int BITS = 14;
    public static final int DIM = 1 << BITS;
    /** Fewer blocked numbers than this and the model stays quiet. */
    public static final int MIN_POSITIVES = 5;
    /** From this probability, the model's opinion counts in the spam score. */
    public static final double THRESHOLD = 0.4;

    // Tuned on synthetic spam blocks (see LearnerTest): with these values one
    // blocked number does not taint its neighbours, two in the same block of 1,000 numbers make
    // the others suspicious, and wide areas (10,000 numbers and more) stay clean.
    private static final int EPOCHS = 30;
    private static final double RATE = 0.25;
    private static final double L2 = 1.4e-4;
    private static final int MIN_PREFIX = 4;
    private static final int FORMAT = 1;

    private Learner() {
    }

    /** One training example. Hour is -1 when unknown. */
    public static final class Sample {
        public final String number;
        public final int hour;
        public final boolean spam;
        public final double weight;

        public Sample(String number, int hour, boolean spam, double weight) {
            this.number = number;
            this.hour = hour;
            this.spam = spam;
            this.weight = weight;
        }
    }

    /** A trained model. */
    public static final class Model {
        final float[] w;
        public final int positives;
        public final int negatives;
        public final long trainedAt;
        /** Cross-validated share of hidden spam numbers recognised, 0–1, or -1 when not measured. */
        public float recall = -1;
        /** Cross-validated share of random numbers wrongly flagged, 0–1, or -1 when not measured. */
        public float falseAlarms = -1;

        Model(float[] w, int positives, int negatives, long trainedAt) {
            this.w = w;
            this.positives = positives;
            this.negatives = negatives;
            this.trainedAt = trainedAt;
        }

        public boolean ready() {
            return positives >= MIN_POSITIVES;
        }

        /** Spam probability of a number (E.164), 0–1. Hour of the call, or -1. */
        public double predict(String e164, int hour) {
            if (!ready() || e164 == null || e164.isEmpty()) return 0;
            double z = 0;
            for (String f : features(e164, hour)) z += w[index(f)];
            return sigmoid(z);
        }

        /**
         * The block that makes this number look like spam, in national form ("0512340"): the
         * longest prefix whose weight is at least half of the strongest prefix's. Null when no
         * prefix points towards spam.
         */
        public String strongestPrefix(String e164) {
            List<String> prefixes = new ArrayList<>();
            double max = 0;
            for (String f : features(e164, -1)) {
                if (!f.startsWith("p")) continue;
                prefixes.add(f);
                max = Math.max(max, w[index(f)]);
            }
            if (max <= 0) return null;
            String best = null;
            for (String f : prefixes) if (w[index(f)] >= max / 2) best = f.substring(f.indexOf(':') + 1);
            return best;
        }

        public byte[] toBytes() {
            ByteBuffer b = ByteBuffer.allocate(4 * 4 + 8 + 8 + DIM * 4);
            b.putInt(FORMAT).putInt(DIM).putInt(positives).putInt(negatives).putLong(trainedAt);
            b.putFloat(recall).putFloat(falseAlarms);
            for (float v : w) b.putFloat(v);
            return b.array();
        }

        public static Model fromBytes(byte[] bytes) {
            if (bytes == null || bytes.length < 32) return null;
            ByteBuffer b = ByteBuffer.wrap(bytes);
            if (b.getInt() != FORMAT || b.getInt() != DIM) return null;
            int pos = b.getInt(), neg = b.getInt();
            long at = b.getLong();
            float recall = b.getFloat(), fa = b.getFloat();
            if (b.remaining() != DIM * 4) return null;
            float[] w = new float[DIM];
            for (int i = 0; i < DIM; i++) w[i] = b.getFloat();
            Model m = new Model(w, pos, neg, at);
            m.recall = recall;
            m.falseAlarms = fa;
            return m;
        }
    }

    // ---- Features ----------------------------------------------------------------------

    /** The features of a number: bias, prefixes of 2 to 9 digits, line type, hour bucket. */
    static List<String> features(String e164, int hour) {
        List<String> f = new ArrayList<>(12);
        f.add("bias");
        String d = e164.startsWith("+33") ? PhoneNumbers.national(e164) : e164;
        int max = Math.min(9, d.length() - 1);
        for (int k = MIN_PREFIX; k <= max; k++) f.add("p" + k + ":" + d.substring(0, k));
        f.add("k:" + PhoneNumbers.kind(e164).name());
        if (hour >= 0 && hour < 24) f.add("h:" + hour / 3);
        return f;
    }

    static int index(String feature) {
        int h = feature.hashCode();
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h & (DIM - 1);
    }

    static double sigmoid(double z) {
        if (z > 30) return 1;
        if (z < -30) return 0;
        return 1 / (1 + Math.exp(-z));
    }

    // ---- Training ----------------------------------------------------------------------

    /** Trains a model from your history. Background negatives are added here. */
    public static Model train(List<Sample> history, long seed, long now) {
        Random rnd = new Random(seed);
        List<Sample> data = withBackground(history, rnd);
        float[] w = fit(data, rnd);
        int pos = 0, neg = 0;
        Set<String> seenPos = new HashSet<>(), seenNeg = new HashSet<>();
        for (Sample s : history) {
            if (s.spam) {
                if (seenPos.add(s.number)) pos++;
            } else if (seenNeg.add(s.number)) {
                neg++;
            }
        }
        return new Model(w, pos, neg, now);
    }

    /** Trains on everything, and measures the model with a 5-fold cross-validation. */
    public static Model trainAndEvaluate(List<Sample> history, long seed, long now) {
        Model model = train(history, seed, now);
        if (!model.ready()) return model;

        // Folds are made per number, so a number's samples are never on both sides.
        List<String> spamNumbers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Sample s : history) if (s.spam && seen.add(s.number)) spamNumbers.add(s.number);
        Collections.sort(spamNumbers);
        Collections.shuffle(spamNumbers, new Random(seed + 1));

        int folds = Math.min(5, spamNumbers.size());
        int hits = 0, tested = 0, alarms = 0, randoms = 0;
        Random probe = new Random(seed + 2);
        for (int f = 0; f < folds; f++) {
            Set<String> hidden = new HashSet<>();
            for (int i = f; i < spamNumbers.size(); i += folds) hidden.add(spamNumbers.get(i));
            List<Sample> train = new ArrayList<>();
            for (Sample s : history) if (!hidden.contains(s.number)) train.add(s);
            Model m = train(train, seed + 10 + f, now);
            if (m.positives < 2) continue;
            for (String n : hidden) {
                tested++;
                if (m.predict(n, -1) >= THRESHOLD) hits++;
            }
            for (int i = 0; i < 200; i++) {
                randoms++;
                if (m.predict(randomFrenchNumber(probe), -1) >= THRESHOLD) alarms++;
            }
        }
        model.recall = tested == 0 ? -1 : (float) hits / tested;
        model.falseAlarms = randoms == 0 ? -1 : (float) alarms / randoms;
        return model;
    }

    private static final int BACKGROUND = 3000;

    /**
     * Adds a fixed-size background of random French numbers, each weighing as much as one call.
     * Its size, not the size of your list, sets the prior: most numbers are not spam, so a
     * region of the number space only turns "spam" when several blocked numbers point there.
     */
    private static List<Sample> withBackground(List<Sample> history, Random rnd) {
        List<Sample> data = new ArrayList<>(history);
        Set<String> spam = new HashSet<>();
        for (Sample s : history) if (s.spam) spam.add(s.number);
        if (spam.isEmpty()) return data;
        for (int i = 0; i < BACKGROUND; i++) {
            String x = randomFrenchNumber(rnd);
            if (!spam.contains(x)) data.add(new Sample(x, -1, false, 1));
        }
        return data;
    }

    /**
     * Logistic regression by SGD on the weighted log-loss plus (L2/2)·‖w‖². The L2 decay is
     * applied to every weight at every step, using the usual trick of keeping w = a·v with a
     * scalar a, so rare features (a single blocked number) cannot grow without limit.
     */
    private static float[] fit(List<Sample> data, Random rnd) {
        int n = data.size();
        double[] v = new double[DIM];
        if (n == 0) return new float[DIM];
        int[][] idx = new int[n][];
        for (int i = 0; i < n; i++) {
            List<String> f = features(data.get(i).number, data.get(i).hour);
            idx[i] = new int[f.size()];
            for (int j = 0; j < f.size(); j++) idx[i][j] = index(f.get(j));
        }
        List<Integer> order = new ArrayList<>(n);
        for (int i = 0; i < n; i++) order.add(i);
        double a = 1;
        for (int epoch = 0; epoch < EPOCHS; epoch++) {
            Collections.shuffle(order, rnd);
            double rate = RATE / (1 + 0.1 * epoch);
            for (int i : order) {
                Sample s = data.get(i);
                double z = 0;
                for (int k : idx[i]) z += v[k];
                z *= a;
                double g = (sigmoid(z) - (s.spam ? 1 : 0)) * s.weight;
                a *= 1 - rate * L2;
                double step = rate * g / a;
                for (int k : idx[i]) v[k] -= step;
                if (a < 1e-6) {
                    for (int k = 0; k < DIM; k++) v[k] *= a;
                    a = 1;
                }
            }
        }
        float[] w = new float[DIM];
        for (int k = 0; k < DIM; k++) w[k] = (float) (v[k] * a);
        return w;
    }

    /** A random, valid French number: the "unlabelled" background of normal numbers. */
    static String randomFrenchNumber(Random rnd) {
        int r = rnd.nextInt(100);
        String first = r < 50 ? (rnd.nextBoolean() ? "6" : "7") : r < 85 ? String.valueOf(1 + rnd.nextInt(5)) : "9";
        StringBuilder sb = new StringBuilder("+33").append(first);
        for (int i = 0; i < 8; i++) sb.append((char) ('0' + rnd.nextInt(10)));
        return sb.toString();
    }
}
