package io.github.bareboneslib.bareboneshll;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import net.openhft.hashing.LongHashFunction;

public class HLLPlusPlusTest {
    LongHashFunction hash = LongHashFunction.xx();
    // ------------------------------------------------------------------ //
    //  Smoke tests                                                         //
    // ------------------------------------------------------------------ //

    @Test
    void constructor_defaultParams_doesNotThrow() {
        assertDoesNotThrow(() -> new HLLPlusPlus(12, 6));
    }

    @Test
    void constructor_minBoundary_doesNotThrow() {
        assertDoesNotThrow(() -> new HLLPlusPlus(4, 4));
    }

    @Test
    void constructor_maxBoundary_doesNotThrow() {
        assertDoesNotThrow(() -> new HLLPlusPlus(18, 6));
    }

    @Test
    void constructor_invalidP_throws() {
        assertThrows(IllegalArgumentException.class, () -> new HLLPlusPlus(3, 6));
        assertThrows(IllegalArgumentException.class, () -> new HLLPlusPlus(19, 6));
    }

    @Test
    void constructor_invalidR_throws() {
        assertThrows(IllegalArgumentException.class, () -> new HLLPlusPlus(12, 3));
        assertThrows(IllegalArgumentException.class, () -> new HLLPlusPlus(12, 7));
    }

    @Test
    void estimate_emptySketch_returnsZero() {
        HLLPlusPlus hll = new HLLPlusPlus(12, 6);
        assertEquals(0, hll.estimate());
    }

    @Test
    void estimate_singleElement_returnsOne() {
        HLLPlusPlus hll = new HLLPlusPlus(12, 6);
        hll.add(hash.hashLong(42L));
        assertEquals(1, hll.estimate());
    }

    @Test
    void estimate_duplicates_countedOnce() {
        HLLPlusPlus hll = new HLLPlusPlus(12, 6);
        for (int i = 0; i < 1000; i++) hll.add(hash.hashLong(42L));
        assertEquals(1, hll.estimate());
    }

    // ------------------------------------------------------------------ //
    //  Accuracy / error-rate tests                                         //
    // ------------------------------------------------------------------ //

    /**
     * HLL++ theoretical std error = 1.04 / sqrt(m), m = 2^p.
     * For p=12 that's ~1.6%. We allow 3x that as a generous bound.
     */
    @Test
    void accuracy_smallCardinality_withinErrorBound() {
        assertWithinError(12, 6, 1_000, 0.05);
    }

    @Test
    void accuracy_mediumCardinality_withinErrorBound() {
        assertWithinError(12, 6, 100_000, 0.05);
    }

    @Test
    void accuracy_largeCardinality_withinErrorBound() {
        assertWithinError(12, 6, 1_000_000, 0.05);
    }

    @Test
    void accuracy_highPrecision_tighterBound() {
        // p=16 → std error ~0.4%, allow 2%
        assertWithinError(16, 6, 100_000, 0.02);
    }

    private void assertWithinError(int p, int r, long n, double maxRelError) {
        HLLPlusPlus hll = new HLLPlusPlus(p, r);
        for (long i = 0; i < n; i++) hll.add(hash.hashLong(i));
        long est = hll.estimate();
        double relError = Math.abs(est - n) / (double) n;
        assertTrue(relError <= maxRelError,
                String.format("p=%d n=%d est=%d relError=%.4f > maxAllowed=%.4f",
                        p, n, est, relError, maxRelError));
    }

    // ------------------------------------------------------------------ //
    //  Serialization / deserialization roundtrip                           //
    // ------------------------------------------------------------------ //

    @Test
    void serdeRoundtrip_emptySketch_estimatePreserved() {
        HLLPlusPlus original = new HLLPlusPlus(12, 6);
        HLLPlusPlus restored = HLLPlusPlus.deserialize(original.serialize());
        assertEquals(original.estimate(), restored.estimate());
    }

    @Test
    void serdeRoundtrip_populatedSketch_estimatePreserved() {
        HLLPlusPlus original = new HLLPlusPlus(12, 6);
        for (long i = 0; i < 100_000; i++) original.add(hash.hashLong(i));

        byte[] bytes = original.serialize();
        HLLPlusPlus restored = HLLPlusPlus.deserialize(bytes);

        assertEquals(original.estimate(), restored.estimate());
    }

    @Test
    void serdeRoundtrip_sparseSketch_estimatePreserved() {
        // small cardinality stays in sparse representation
        HLLPlusPlus original = new HLLPlusPlus(12, 6);
        for (long i = 0; i < 100; i++) original.add(hash.hashLong(i));

        HLLPlusPlus restored = HLLPlusPlus.deserialize(original.serialize());
        assertEquals(original.estimate(), restored.estimate());
    }

    @Test
    void serialize_nullInput_throws() {
        assertThrows(Exception.class, () -> HLLPlusPlus.deserialize(null));
    }

    @Test
    void serialize_corruptedBytes_throws() {
        assertThrows(Exception.class,
                () -> HLLPlusPlus.deserialize(new byte[]{0x00, 0x01, 0x02}));
    }

    // ------------------------------------------------------------------ //
    //  Merge tests                                                         //
    // ------------------------------------------------------------------ //

    @Test
    void merge_disjointSets_estimateApproximatesUnion() {
        HLLPlusPlus a = new HLLPlusPlus(12, 6);
        HLLPlusPlus b = new HLLPlusPlus(12, 6);

        for (long i = 0;        i < 50_000; i++) a.add(hash.hashLong(i));
        for (long i = 50_000;   i < 100_000; i++) b.add(hash.hashLong(i));

        boolean ok = a.merge(b);
        assertTrue(ok);

        long est = a.estimate();
        double relError = Math.abs(est - 100_000) / 100_000.0;
        assertTrue(relError <= 0.05,
                String.format("merge estimate=%d relError=%.4f", est, relError));
    }

    @Test
    void merge_overlappingSets_doesNotDoubleCount() {
        HLLPlusPlus a = new HLLPlusPlus(12, 6);
        HLLPlusPlus b = new HLLPlusPlus(12, 6);

        // identical sets — estimate should stay ~N, not ~2N
        for (long i = 0; i < 50_000; i++) { a.add(hash.hashLong(i)); b.add(hash.hashLong(i)); }

        a.merge(b);
        long est = a.estimate();
        double relError = Math.abs(est - 50_000) / 50_000.0;
        assertTrue(relError <= 0.05,
                String.format("overlap merge estimate=%d relError=%.4f", est, relError));
    }

    @Test
    void merge_emptyIntoPopulated_estimateUnchanged() {
        HLLPlusPlus populated = new HLLPlusPlus(12, 6);
        HLLPlusPlus empty     = new HLLPlusPlus(12, 6);

        for (long i = 0; i < 50_000; i++) populated.add(hash.hashLong(i));
        long before = populated.estimate();

        populated.merge(empty);
        assertEquals(before, populated.estimate());
    }

    @Test
    void merge_incompatibleParams_returnsFalse() {
        HLLPlusPlus a = new HLLPlusPlus(12, 6);
        HLLPlusPlus b = new HLLPlusPlus(14, 6); // different p
        assertFalse(a.merge(b));
    }
}