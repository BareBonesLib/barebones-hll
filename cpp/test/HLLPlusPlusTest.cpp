#include <gtest/gtest.h>
#include <xxhash.h>
#include <iomanip> 
#include "../src/HLLPlusPlus.h"

// ---------------------------------------------------------------------------
// Helper — hash a 64-bit integer with xxHash before feeding to the sketch
// ---------------------------------------------------------------------------
static inline uint64_t h(uint64_t value) {
    return XXH64(&value, sizeof(value), 0);
}

static void addRange(HLLPlusPlus& hll, uint64_t from, uint64_t to) {
    for (uint64_t i = from; i < to; i++) hll.add(h(i));
}

static double relError(long est, long actual) {
    return std::abs(est - actual) / (double) actual;
}

// ===========================================================================
//  Smoke tests
// ===========================================================================

TEST(Smoke, ValidConstruction) {
    EXPECT_NO_THROW(HLLPlusPlus(12, 6));
    EXPECT_NO_THROW(HLLPlusPlus(4,  4));   // min boundary
    EXPECT_NO_THROW(HLLPlusPlus(18, 6));   // max boundary
}

TEST(Smoke, InvalidP_Throws) {
    EXPECT_THROW(HLLPlusPlus(3,  6), std::invalid_argument);
    EXPECT_THROW(HLLPlusPlus(19, 6), std::invalid_argument);
}

TEST(Smoke, InvalidR_Throws) {
    EXPECT_THROW(HLLPlusPlus(12, 3), std::invalid_argument);
    EXPECT_THROW(HLLPlusPlus(12, 7), std::invalid_argument);
}

TEST(Smoke, EmptySketch_EstimateIsZero) {
    HLLPlusPlus hll(12, 6);
    EXPECT_EQ(0, hll.estimate());
}

TEST(Smoke, SingleElement_EstimateIsOne) {
    HLLPlusPlus hll(12, 6);
    hll.add(h(42));
    EXPECT_EQ(1, hll.estimate());
}

TEST(Smoke, Duplicates_CountedOnce) {
    HLLPlusPlus hll(12, 6);
    for (int i = 0; i < 1000; i++) hll.add(h(42));
    EXPECT_EQ(1, hll.estimate());
}

// ===========================================================================
//  Accuracy / error-rate tests
// ===========================================================================

// HLL++ std error = 1.04 / sqrt(2^p)
// p=12 → ~1.6%  — we allow 5% (3x theoretical)
// p=16 → ~0.4%  — we allow 2%

TEST(Accuracy, SmallCardinality_p12) {
    HLLPlusPlus hll(12, 6);
    addRange(hll, 0, 1'000);
    EXPECT_LE(relError(hll.estimate(), 1'000), 0.05);
}

TEST(Accuracy, MediumCardinality_p12) {
    HLLPlusPlus hll(12, 6);
    addRange(hll, 0, 100'000);
    EXPECT_LE(relError(hll.estimate(), 100'000), 0.05);
}

TEST(Accuracy, LargeCardinality_p12) {
    HLLPlusPlus hll(12, 6);
    addRange(hll, 0, 1'000'000);
    EXPECT_LE(relError(hll.estimate(), 1'000'000), 0.05);
}

TEST(Accuracy, HighPrecision_p16) {
    HLLPlusPlus hll(16, 6);
    addRange(hll, 0, 100'000);

    long est      = hll.estimate();
    long actual   = 100'000;
    double rel    = relError(est, actual);

    EXPECT_LE(rel, 0.02)
        << "  actual:    " << actual    << "\n"
        << "  estimate:  " << est       << "\n"
        << "  abs error: " << std::abs(est - actual) << "\n"
        << "  rel error: " << std::fixed << std::setprecision(4) << rel * 100 << "%\n"
        << "  allowed:   2.00%";
}

// ===========================================================================
//  Serialization / deserialization roundtrip
// ===========================================================================

TEST(Serde, EmptySketch_RoundtripPreservesEstimate) {
    HLLPlusPlus original(12, 6);
    auto bytes = original.serialize();
    auto restored = HLLPlusPlus::deserialize(bytes);
    EXPECT_EQ(original.estimate(), restored.estimate());
}

TEST(Serde, PopulatedSketch_RoundtripPreservesEstimate) {
    HLLPlusPlus original(12, 6);
    addRange(original, 0, 100'000);

    auto bytes = original.serialize();
    auto restored = HLLPlusPlus::deserialize(bytes);
    EXPECT_EQ(original.estimate(), restored.estimate());
}

TEST(Serde, SparseSketch_RoundtripPreservesEstimate) {
    // small cardinality stays in sparse representation
    HLLPlusPlus original(12, 6);
    addRange(original, 0, 100);

    auto bytes = original.serialize();
    auto restored = HLLPlusPlus::deserialize(bytes);
    EXPECT_EQ(original.estimate(), restored.estimate());
}

TEST(Serde, CorruptedBytes_Throws) {
    std::vector<uint8_t> garbage = {0x00, 0x01, 0x02};
    EXPECT_THROW(HLLPlusPlus::deserialize(garbage), std::exception);
}

// ===========================================================================
//  Merge tests
// ===========================================================================

TEST(Merge, DisjointSets_ApproximatesUnion) {
    HLLPlusPlus a(12, 6), b(12, 6);
    addRange(a, 0,      50'000);
    addRange(b, 50'000, 100'000);

    EXPECT_TRUE(a.merge(b));
    EXPECT_LE(relError(a.estimate(), 100'000), 0.05);
}

TEST(Merge, OverlappingSets_NoDoubleCounting) {
    HLLPlusPlus a(12, 6), b(12, 6);
    addRange(a, 0, 50'000);
    addRange(b, 0, 50'000);   // identical

    EXPECT_TRUE(a.merge(b));
    EXPECT_LE(relError(a.estimate(), 50'000), 0.05);
}

TEST(Merge, EmptyIntoPopulated_EstimateUnchanged) {
    HLLPlusPlus populated(12, 6), empty(12, 6);
    addRange(populated, 0, 50'000);
    long before = populated.estimate();

    EXPECT_TRUE(populated.merge(empty));
    EXPECT_EQ(before, populated.estimate());
}

TEST(Merge, IncompatibleParams_ReturnsFalse) {
    HLLPlusPlus a(12, 6), b(14, 6);   // different p
    EXPECT_FALSE(a.merge(b));
}