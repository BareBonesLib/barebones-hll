package io.github.siddheshdhinge.bareboneshll;

import net.openhft.hashing.LongHashFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HLLPlusPlusTest {
    private static final LongHashFunction hash = LongHashFunction.xx();

    private static final double ACCEPTABLE_ERROR = 0.03; // 3% error tolerance

    @Test
    @DisplayName("Default constructor initializes correctly")
    void testDefaultConstructor() {
        HLLPlusPlus hll = new HLLPlusPlus();
        long estimate = hll.estimate();
        assertEquals(0, estimate);
    }

    @Test
    @DisplayName("Invalid p throws exception")
    void testInvalidP() {
        assertThrows(IllegalArgumentException.class, () -> new HLLPlusPlus(3));
        assertThrows(IllegalArgumentException.class, () -> new HLLPlusPlus(19));
    }

    @Test
    @DisplayName("Invalid r throws exception")
    void testInvalidR() {
        assertThrows(IllegalArgumentException.class, () -> new HLLPlusPlus(10, 3));
        assertThrows(IllegalArgumentException.class, () -> new HLLPlusPlus(10, 7));
    }

    @Test
    @DisplayName("Estimate small cardinality correctly (sparse mode)")
    void testSmallCardinality() {
        HLLPlusPlus hll = new HLLPlusPlus(12);

        int n = 1000;
        for (int i = 0; i < n; i++) {
            hll.add(hash.hashLong(i));
        }

        long estimate = hll.estimate();
        assertRelativeError(n, estimate);
    }

    @Test
    @DisplayName("Estimate medium cardinality correctly (dense mode)")
    void testMediumCardinality() {
        HLLPlusPlus hll = new HLLPlusPlus(12);

        int n = 50_000;
        for (int i = 0; i < n; i++) {
            hll.add(hash.hashLong(i));
        }

        long estimate = hll.estimate();
        assertRelativeError(n, estimate);
    }

    @Test
    @DisplayName("Adding duplicates does not change estimate significantly")
    void testDuplicates() {
        HLLPlusPlus hll = new HLLPlusPlus(12);

        for (int i = 0; i < 10_000; i++) {
            hll.add(hash.hashLong(i));
            hll.add(hash.hashLong(i)); // duplicate
        }

        long estimate = hll.estimate();
        assertRelativeError(10_000, estimate);
    }

    @Test
    @DisplayName("Merge two HLLs correctly")
    void testMerge() {
        HLLPlusPlus h1 = new HLLPlusPlus(12);
        HLLPlusPlus h2 = new HLLPlusPlus(12);

        int n = 20_000;

        for (int i = 0; i < n; i++) {
            h1.add(hash.hashLong(i));
        }

        for (int i = n; i < 2 * n; i++) {
            h2.add(hash.hashLong(i));
        }

        assertTrue(h1.merge(h2));

        long estimate = h1.estimate();
        assertRelativeError(2L * n, estimate);
    }

    @Test
    @DisplayName("Merge incompatible HLLs returns false")
    void testInvalidMerge() {
        HLLPlusPlus h1 = new HLLPlusPlus(12);
        HLLPlusPlus h2 = new HLLPlusPlus(10);

        assertFalse(h1.merge(h2));
    }

    @Test
    @DisplayName("Serialization and Deserialization (Sparse)")
    void testSerializationSparse() {
        HLLPlusPlus hll = new HLLPlusPlus(12);

        for (int i = 0; i < 500; i++) {
            hll.add(hash.hashLong(i));
        }

        byte[] serialized = hll.serialize();
        HLLPlusPlus restored = HLLPlusPlus.deserialize(serialized);

        assertEquals(hll.estimate(), restored.estimate());
    }

    @Test
    @DisplayName("Serialization and Deserialization (Dense)")
    void testSerializationDense() {
        HLLPlusPlus hll = new HLLPlusPlus(12);

        for (int i = 0; i < 100_000; i++) {
            hll.add(hash.hashLong(i));
        }

        byte[] serialized = hll.serialize();
        HLLPlusPlus restored = HLLPlusPlus.deserialize(serialized);

        assertEquals(hll.estimate(), restored.estimate());
    }

    @Test
    @DisplayName("Randomized test with HashSet ground truth")
    void testRandomized() {
        HLLPlusPlus hll = new HLLPlusPlus(12);
        Set<Long> groundTruth = new HashSet<>();
        Random random = new Random(42);

        int n = 30_000;

        for (int i = 0; i < n; i++) {
            long value = random.nextLong();
            groundTruth.add(value);
            hll.add(hash.hashLong(value));
        }

        long estimate = hll.estimate();
        assertRelativeError(groundTruth.size(), estimate);
    }


    @Test
    @DisplayName("Randomized counts test with HashSet ground truth")
    void testRandomizedCounts() {
        HLLPlusPlus hll = new HLLPlusPlus(12);
        Set<Long> groundTruth = new HashSet<>();
        Random random = new Random(42);

        int n = 30_000;

        for (int i = 0; i < n * 5; i++) {
            long value = random.nextLong();
            groundTruth.add(value);
            hll.add(hash.hashLong(value));
        }

        for (int i = 0; i < n * 7; i++) {
            long value = random.nextLong();
            groundTruth.add(value);
            hll.addRandom(1);
        }

        long estimate = hll.estimate();
        assertRelativeError(groundTruth.size(), estimate);
    }

    // ----------------------------------------------------------------

    private void assertRelativeError(long expected, long estimate) {
        double error = Math.abs(estimate - expected) / (double) expected;
        System.out.println("Expected: " + expected + ", Estimate: " + estimate + ", Error: " + error);

        assertTrue(error < ACCEPTABLE_ERROR,
                "Expected: " + expected + ", Estimate: " + estimate +
                        ", Error: " + error);
    }
}