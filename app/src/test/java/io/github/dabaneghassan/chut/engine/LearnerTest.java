// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dabaneghassan.chut.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;

/**
 * Trains the model on a synthetic block history and checks what it learned. The history looks
 * like real telemarketing: platforms renting blocks of 1,000 numbers and calling from a few of
 * them, plus scattered one-off numbers. All numbers are generated, none is real.
 */
public class LearnerTest {

    /** Spam blocks: national prefix of 7 digits, and how many numbers of each were blocked. */
    static final String[][] BLOCKS = {
            {"0162001", "4"}, {"0270012", "3"}, {"0377023", "5"}, {"0424034", "3"}, {"0568045", "4"},
            {"0948056", "6"}, {"0948067", "3"}, {"0512078", "3"}, {"0187089", "2"}, {"0978090", "3"}
    };

    static List<String> history() {
        Random r = new Random(2026);
        List<String> out = new ArrayList<>();
        for (String[] b : BLOCKS) {
            int n = Integer.parseInt(b[1]);
            for (int i = 0; i < n; i++) out.add("+33" + b[0].substring(1) + String.format("%03d", r.nextInt(1000)));
        }
        for (int i = 0; i < 8; i++) out.add(Learner.randomFrenchNumber(r));   // one-off numbers
        return out;
    }

    static List<Learner.Sample> spam(List<String> numbers) {
        List<Learner.Sample> s = new ArrayList<>();
        for (String n : numbers) s.add(new Learner.Sample(n, -1, true, 1));
        return s;
    }

    @Test
    public void learnsTheSpamBlocks() {
        List<String> h = history();
        Learner.Model m = Learner.trainAndEvaluate(spam(h), 42, 0);
        assertTrue(m.ready());

        // Never-seen numbers in blocks the history keeps hitting.
        assertTrue(m.predict("+33948056999", -1) >= Learner.THRESHOLD);   // 6 blocked in that block
        assertTrue(m.predict("+33377023999", -1) >= Learner.THRESHOLD);   // 5
        assertTrue(m.predict("+33512078999", -1) >= Learner.THRESHOLD);   // 3, outside ARCEP ranges
        String why = m.strongestPrefix("+33948056999");
        assertTrue(why, why != null && why.length() >= 5 && "0948056999".startsWith(why));

        // Ordinary numbers stay well below.
        for (String n : new String[]{"+33612345678", "+33698765432", "+33142681234", "+33491123456", "+33556789012"}) {
            assertTrue(n + " " + m.predict(n, -1), m.predict(n, -1) < 0.2);
        }
        assertEquals(null, m.strongestPrefix("+33612345678"));

        // Few false alarms over thousands of random French numbers.
        Random r = new Random(7);
        int alarms = 0;
        for (int i = 0; i < 5000; i++) if (m.predict(Learner.randomFrenchNumber(r), -1) >= Learner.THRESHOLD) alarms++;
        assertTrue("false alarms " + alarms, alarms < 5000 * 0.02);

        // Cross-validation: the model recognises spam numbers it never saw.
        System.out.printf("cross-validated recall %.2f, false alarms %.3f%n", m.recall, m.falseAlarms);
        assertTrue("recall " + m.recall, m.recall >= 0.5);
        assertTrue("false alarms " + m.falseAlarms, m.falseAlarms >= 0 && m.falseAlarms < 0.03);
    }

    @Test
    public void oneBlockedNumberDoesNotTaintItsNeighbours() {
        List<String> h = new ArrayList<>(List.of("+33142000001", "+33299000002", "+33388000003", "+33467000004", "+33556000005"));
        h.add("+33512340001");
        Learner.Model m = Learner.train(spam(h), 42, 0);
        assertTrue(m.predict("+33512340099", -1) < Learner.THRESHOLD);
        // Three in the same block of 1,000 do.
        h.add("+33512340457");
        h.add("+33512340930");
        m = Learner.train(spam(h), 42, 0);
        assertTrue(m.predict("+33512340222", -1) >= Learner.THRESHOLD);
        assertTrue(m.predict("+33512999222", -1) < 0.2);                  // the wider area stays clean
    }

    @Test
    public void allowedNumbersTeachItWhatIsNotSpam() {
        List<Learner.Sample> data = spam(history());
        Learner.Model before = Learner.train(data, 42, 0);
        // You told chut that numbers in 09 48 05 69 x are fine (say, your bank's robot).
        for (int i = 0; i < 6; i++) data.add(new Learner.Sample("+3394805690" + i, -1, false, 1));
        Learner.Model after = Learner.train(data, 42, 0);
        assertTrue(after.predict("+33948056111", -1) < before.predict("+33948056111", -1) - 0.1);
        assertTrue(after.predict("+33948056907", -1) < before.predict("+33948056907", -1) - 0.2);
    }

    @Test
    public void learnsWhenSpamCalls() {
        // Spam always called at 10:xx, the calls you took came in the evening.
        List<Learner.Sample> data = new ArrayList<>();
        Random r = new Random(3);
        for (int i = 0; i < 40; i++) data.add(new Learner.Sample(Learner.randomFrenchNumber(r), 10, true, 1));
        for (int i = 0; i < 40; i++) data.add(new Learner.Sample(Learner.randomFrenchNumber(r), 20, false, 1));
        Learner.Model m = Learner.train(data, 42, 0);
        String unseen = "+33555123456";
        assertTrue(m.predict(unseen, 10) > m.predict(unseen, 20) + 0.2);
    }

    @Test
    public void staysQuietWithTooLittleHistory() {
        Learner.Model m = Learner.trainAndEvaluate(spam(List.of("+33512340001", "+33512340457")), 42, 0);
        assertFalse(m.ready());
        assertEquals(0.0, m.predict("+33512340222", -1), 0);
    }

    @Test
    public void isDeterministicAndSurvivesSaving() {
        List<Learner.Sample> data = spam(history());
        Learner.Model a = Learner.trainAndEvaluate(data, 42, 123);
        Learner.Model b = Learner.trainAndEvaluate(data, 42, 123);
        assertArrayEquals(a.w, b.w, 0f);
        Learner.Model c = Learner.Model.fromBytes(a.toBytes());
        assertNotNull(c);
        assertArrayEquals(a.w, c.w, 0f);
        assertEquals(a.positives, c.positives);
        assertEquals(a.trainedAt, c.trainedAt);
        assertEquals(a.recall, c.recall, 0f);
        assertEquals(a.predict("+33948056111", 9), c.predict("+33948056111", 9), 0);
        assertEquals(null, Learner.Model.fromBytes(new byte[10]));
    }
}
