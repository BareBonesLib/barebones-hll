"""
test_interop.py — cross-language serialization and merge tests.

This single file replaces test_java_to_cpp.py, test_cpp_to_java.py,
test_cross_merge.py, and test_estimate_equality.py.

Tests are parametrized over all ordered language pairs via the
`lang_pair` fixture in conftest.py.  When a new language is added to
registry.DRIVERS every test here runs for all new pairs automatically —
no changes to this file needed.

Pytest IDs look like:
  TestSerdeInterop::test_small_cardinality[java_to_cpp]
  TestSerdeInterop::test_small_cardinality[cpp_to_java]
  TestMergeInterop::test_disjoint_sets[java_to_cpp]
  ...
"""

import pytest
from conftest import DEFAULT_P, DEFAULT_R, rel_error, values, valuesRange

MAX_REL_ERROR = 0.05   # 5% ≈ 3× theoretical std error for p=12


# ===========================================================================
#  Helpers
# ===========================================================================

def _fmt(src_name, dst_name, n, src_est, dst_est):
    """Standard failure message for serde tests."""
    return (
        f"\n  direction:     {src_name} → {dst_name}"
        f"\n  actual n:      {n}"
        f"\n  src estimate:  {src_est}"
        f"\n  dst estimate:  {dst_est}"
        f"\n  abs diff:      {abs(src_est - dst_est)}"
        f"\n  rel error:     {rel_error(dst_est, n)*100:.2f}%"
        f"  (max {MAX_REL_ERROR*100:.0f}%)"
    )


# ===========================================================================
#  Serialization / deserialization
# ===========================================================================

class TestSerdeInterop:
    """
    src serializes a sketch → dst deserializes it.
    Asserts:
      1. dst estimate == src estimate  (same bytes → same state)
      2. dst estimate is within MAX_REL_ERROR of actual cardinality
    """

    def _run(self, src_name, src, dst_name, dst, n):
        result  = src.serialize(DEFAULT_P, DEFAULT_R, values(n))
        restored = dst.deserialize(result.bytes_b64)

        assert restored.estimate == result.estimate, (
            f"Estimate mismatch after {src_name}→{dst_name} roundtrip"
            + _fmt(src_name, dst_name, n, result.estimate, restored.estimate)
        )
        if n > 0:
            err = rel_error(restored.estimate, n)
            assert err <= MAX_REL_ERROR, (
                f"Estimate out of bounds after {src_name}→{dst_name} serde"
                + _fmt(src_name, dst_name, n, result.estimate, restored.estimate)
            )

    def test_empty_sketch(self, lang_pair):
        src_name, src, dst_name, dst = lang_pair
        result   = src.serialize(DEFAULT_P, DEFAULT_R, [])
        restored = dst.deserialize(result.bytes_b64)
        assert restored.estimate == 0, (
            f"Empty sketch should deserialize to 0 in {dst_name}\n"
            f"  got: {restored.estimate}"
        )

    def test_sparse_sketch(self, lang_pair):
        """Small n stays in sparse representation — dst must handle sparse format."""
        src_name, src, dst_name, dst = lang_pair
        self._run(src_name, src, dst_name, dst, n=50)

    def test_small_cardinality(self, lang_pair):
        src_name, src, dst_name, dst = lang_pair
        self._run(src_name, src, dst_name, dst, n=1_000)

    def test_medium_cardinality(self, lang_pair):
        src_name, src, dst_name, dst = lang_pair
        self._run(src_name, src, dst_name, dst, n=100_000)

    def test_large_cardinality(self, lang_pair):
        src_name, src, dst_name, dst = lang_pair
        self._run(src_name, src, dst_name, dst, n=1_000_000)

    @pytest.mark.parametrize("p,r", [(4, 4), (8, 4), (12, 6), (16, 6), (18, 6)])
    def test_params_matrix(self, lang_pair, p, r):
        src_name, src, dst_name, dst = lang_pair
        n        = 10_000
        result   = src.serialize(p, r, values(n))
        restored = dst.deserialize(result.bytes_b64)
        assert restored.estimate == result.estimate, (
            f"Estimate mismatch for p={p} r={r} {src_name}→{dst_name}\n"
            f"  src: {result.estimate}  dst: {restored.estimate}"
        )

    def test_sparse_to_normal_boundary(self, lang_pair):
        """Estimates stay equal across the sparse→normal conversion boundary."""
        src_name, src, dst_name, dst = lang_pair
        checkpoints = [10, 50, 100, 500, 1_000, 5_000, 10_000]
        for n in checkpoints:
            result   = src.serialize(DEFAULT_P, DEFAULT_R, values(n))
            restored = dst.deserialize(result.bytes_b64)
            assert restored.estimate == result.estimate, (
                f"Diverged at sparse→normal boundary n={n} "
                f"{src_name}→{dst_name}\n"
                f"  src: {result.estimate}  dst: {restored.estimate}"
            )


# ===========================================================================
#  Estimate equality (same input, both langs serialize independently)
# ===========================================================================

class TestEstimateEquality:
    """
    Both langs serialize the same input independently.
    Their estimates (and ideally bytes) must match.
    Uses `any_lang` fixture — runs for every individual lang, comparing
    against all others via a second parametrize.
    """

    @pytest.mark.parametrize("n", [50, 1_000, 10_000, 100_000, 1_000_000])
    def test_same_input_same_estimate(self, lang_pair, n):
        src_name, src, dst_name, dst = lang_pair
        vals = values(n)
        a    = src.serialize(DEFAULT_P, DEFAULT_R, vals)
        b    = dst.serialize(DEFAULT_P, DEFAULT_R, vals)

        assert a.estimate == b.estimate, (
            f"Estimate diverges for same input n={n} "
            f"({src_name} vs {dst_name})\n"
            f"  {src_name}: {a.estimate}\n"
            f"  {dst_name}: {b.estimate}\n"
            f"  diff:       {abs(a.estimate - b.estimate)}"
        )

    @pytest.mark.parametrize("n", [50, 1_000, 10_000, 100_000, 1_000_000])
    def test_same_input_same_bytes(self, lang_pair, n):
        src_name, src, dst_name, dst = lang_pair
        vals = values(n)
        a    = src.serialize(DEFAULT_P, DEFAULT_R, vals)
        b    = dst.serialize(DEFAULT_P, DEFAULT_R, vals)

        assert a.bytes_b64 == b.bytes_b64, (
            f"Serialized bytes differ for same input n={n} "
            f"({src_name} vs {dst_name})\n"
            f"  {src_name} bytes: {a.bytes_b64[:60]}...\n"
            f"  {dst_name} bytes: {b.bytes_b64[:60]}...\n"
            f"  estimates:  {src_name}={a.estimate}  {dst_name}={b.estimate}"
        )


# ===========================================================================
#  Cross-language merge
# ===========================================================================

class TestMergeInterop:
    """
    Sketches built in src are merged in dst (and vice-versa via parametrize).
    """

    def test_disjoint_sets(self, lang_pair):
        """src serializes set A; dst serializes set B; dst merges both."""
        src_name, src, dst_name, dst = lang_pair
        n        = 50_000
        sketch_a = src.serialize(DEFAULT_P, DEFAULT_R, valuesRange(0,     n))
        sketch_b = dst.serialize(DEFAULT_P, DEFAULT_R, valuesRange(n, 2 * n))

        merged = dst.merge([sketch_a.bytes_b64, sketch_b.bytes_b64])
        err    = rel_error(merged.estimate, 2 * n)

        assert err <= MAX_REL_ERROR, (
            f"Disjoint merge out of bounds ({src_name}+{dst_name} merged in {dst_name})\n"
            f"  actual:    {2*n}\n"
            f"  estimate:  {merged.estimate}\n"
            f"  rel error: {err*100:.2f}%  (max {MAX_REL_ERROR*100:.0f}%)"
        )

    def test_overlapping_sets_no_double_count(self, lang_pair):
        """Identical sets from different langs merged — must not double-count."""
        src_name, src, dst_name, dst = lang_pair
        n        = 50_000
        sketch_a = src.serialize(DEFAULT_P, DEFAULT_R, values(n))
        sketch_b = dst.serialize(DEFAULT_P, DEFAULT_R, values(n))

        merged = dst.merge([sketch_a.bytes_b64, sketch_b.bytes_b64])
        err    = rel_error(merged.estimate, n)

        assert err <= MAX_REL_ERROR, (
            f"Overlapping cross-lang merge double-counted\n"
            f"  actual:    {n}\n"
            f"  estimate:  {merged.estimate}\n"
            f"  rel error: {err*100:.2f}%"
        )

    def test_merge_result_consistent_across_langs(self, lang_pair):
        """Merging the same pair of sketches in src vs dst must give the same estimate."""
        src_name, src, dst_name, dst = lang_pair
        n        = 50_000
        sketch_a = src.serialize(DEFAULT_P, DEFAULT_R, valuesRange(0,     n))
        sketch_b = dst.serialize(DEFAULT_P, DEFAULT_R, valuesRange(n, 2 * n))

        merged_in_src = src.merge([sketch_a.bytes_b64, sketch_b.bytes_b64])
        merged_in_dst = dst.merge([sketch_a.bytes_b64, sketch_b.bytes_b64])

        assert merged_in_src.estimate == merged_in_dst.estimate, (
            f"Merge result differs between languages\n"
            f"  merged in {src_name}: {merged_in_src.estimate}\n"
            f"  merged in {dst_name}: {merged_in_dst.estimate}"
        )

    def test_merge_empty_from_other_lang(self, lang_pair):
        """Merging an empty sketch from src into dst populated sketch is a no-op."""
        src_name, src, dst_name, dst = lang_pair
        n         = 50_000
        populated = dst.serialize(DEFAULT_P, DEFAULT_R, values(n))
        empty     = src.serialize(DEFAULT_P, DEFAULT_R, [])

        merged = dst.merge([populated.bytes_b64, empty.bytes_b64])

        assert merged.estimate == populated.estimate, (
            f"Merging empty {src_name} sketch changed {dst_name} estimate\n"
            f"  before: {populated.estimate}\n"
            f"  after:  {merged.estimate}"
        )

    def test_three_way_merge(self, lang_pair):
        """Three sketches alternating src/dst/src covering disjoint sets."""
        src_name, src, dst_name, dst = lang_pair
        n        = 30_000
        sketches = [
            src.serialize(DEFAULT_P, DEFAULT_R, valuesRange(0,       n)).bytes_b64,
            dst.serialize(DEFAULT_P, DEFAULT_R, valuesRange(n,   2 * n)).bytes_b64,
            src.serialize(DEFAULT_P, DEFAULT_R, valuesRange(2*n, 3 * n)).bytes_b64,
        ]
        merged = dst.merge(sketches)
        err    = rel_error(merged.estimate, 3 * n)

        assert err <= MAX_REL_ERROR, (
            f"Three-way merge out of bounds ({src_name}/{dst_name}/{src_name} → {dst_name})\n"
            f"  actual:    {3*n}\n"
            f"  estimate:  {merged.estimate}\n"
            f"  rel error: {err*100:.2f}%"
        )
