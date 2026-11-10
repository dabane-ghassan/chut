// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dabaneghassan.chut.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;

/** Trains the model on the real block list chut ships with and checks what it learned. */
public class LearnerTest {

    static List<String> preset() throws Exception {
        File f = new File("src/main/res/raw/preset_blocklist.txt");
        if (!f.exists()) f = new File("app/src/main/res/raw/preset_blocklist.txt");
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            String n = PhoneNumbers.normalize(line);
            if (!n.isEmpty()) out.add(n);
        }
        return out;
    }

    static List<Learner.Sample> spam(List<String> numbers) {
        List<Learner.Sample> s = new ArrayList<>();
        for (String n : numbers) s.add(new Learner.Sample(n, -1, true, 1));
        return s;
    }

    @Test
    public void learnsTheSpamBlocksOfThePresetList() throws Exception {
        List<String> preset = preset();
        assertEquals(62, preset.size());
        Learner.Model m = Learner.trainAndEvaluate(spam(preset), 42, 0);
        assertTrue(m.ready());
        assertEquals(62, m.positives);

        // Never-seen numbers in blocks the list keeps hitting.
        assertTrue(m.predict("+33377259111", -1) >= Learner.THRESHOLD);   // 03 77 25 9x: 3 blocked
        assertTrue(m.predict("+33948490500", -1) >= Learner.THRESHOLD);   // 09 48 49 0x: 3 blocked
        assertTrue(m.predict("+33162308000", -1) >= Learner.THRESHOLD);   // 01 62 30 8x: 2 blocked
        // The explanation is the dense block around it: 03 77 2x has 9 blocked numbers.
        String why = m.strongestPrefix("+33377259111");
        assertTrue(why, why != null && why.length() >= 5 && "0377259111".startsWith(why));
        assertEquals(null, m.strongestPrefix("+33612345678"));

        // Ordinary numbers stay well below.
        for (String n : new String[]{"+33612345678", "+33698765432", "+33142681234", "+33491123456", "+33556789012"}) {
            assertTrue(n + " " + m.predict(n, -1), m.predict(n, -1) < 0.2);
        }

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
    public void allowedNumbersTeachItWhatIsNotSpam() throws Exception {
        List<Learner.Sample> data = spam(preset());
        Learner.Model before = Learner.train(data, 42, 0);
        // You told chut that numbers in 03 77 25 9x are fine (say, your bank's robot).
        for (String n : new String[]{"+33377259900", "+33377259901", "+33377259902", "+33377259903", "+33377259904", "+33377259905"}) {
            data.add(new Learner.Sample(n, -1, false, 1));
        }
        Learner.Model after = Learner.train(data, 42, 0);
        // Nearby numbers become much less suspicious…
        assertTrue(after.predict("+33377259111", -1) < before.predict("+33377259111", -1) - 0.2);
        // …and the sub-block you vouched for is cleared.
        assertTrue(before.predict("+33377259907", -1) >= Learner.THRESHOLD);
        assertTrue(after.predict("+33377259907", -1) < Learner.THRESHOLD);
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
        List<Learner.Sample> few = spam(List.of("+33377259405", "+33377259522"));
        Learner.Model m = Learner.trainAndEvaluate(few, 42, 0);
        assertFalse(m.ready());
        assertEquals(0.0, m.predict("+33377259111", -1), 0);
    }

    @Test
    public void isDeterministicAndSurvivesSaving() throws Exception {
        List<Learner.Sample> data = spam(preset());
        Learner.Model a = Learner.trainAndEvaluate(data, 42, 123);
        Learner.Model b = Learner.trainAndEvaluate(data, 42, 123);
        assertArrayEquals(a.w, b.w, 0f);
        Learner.Model c = Learner.Model.fromBytes(a.toBytes());
        assertNotNull(c);
        assertArrayEquals(a.w, c.w, 0f);
        assertEquals(a.positives, c.positives);
        assertEquals(a.trainedAt, c.trainedAt);
        assertEquals(a.recall, c.recall, 0f);
        assertEquals(a.predict("+33377259111", 9), c.predict("+33377259111", 9), 0);
        assertEquals(null, Learner.Model.fromBytes(new byte[10]));
    }
}
