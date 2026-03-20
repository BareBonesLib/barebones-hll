"""
conftest.py — shared fixtures for HLL interop tests.

Lang-pair fixtures are generated automatically from registry.DRIVERS,
so adding a new language requires no changes here.
"""

import itertools
import pytest
from registry import DRIVERS

# ── cardinality presets ───────────────────────────────────────────────────────
SMALL_N  = 1_000
MEDIUM_N = 100_000
LARGE_N  = 1_000_000

DEFAULT_P = 12
DEFAULT_R = 6


def values(n: int) -> list[int]:
    """Deterministic list of n unique integers."""
    return list(range(n))

def valuesRange(start: int, end: int) -> list[int]:
    """Deterministic list range of unique integers from start to end (excluding end)."""
    return list(range(start, end))


def rel_error(estimate: int, actual: int) -> float:
    return abs(estimate - actual) / actual


# ── driver name lookup (for readable pytest IDs) ──────────────────────────────
_DRIVER_NAMES = {id(mod): name for name, mod in DRIVERS}

def driver_name(mod) -> str:
    return _DRIVER_NAMES.get(id(mod), repr(mod))


# ── lang-pair fixture: all ordered pairs (A, B) where A != B ─────────────────
def _lang_pairs():
    return [
        (na, da, nb, db)
        for (na, da), (nb, db) in itertools.product(DRIVERS, repeat=2)
        if na != nb
    ]

def _pair_id(pair):
    na, _, nb, _ = pair
    return f"{na}_to_{nb}"


@pytest.fixture(
    params=_lang_pairs(),
    ids=[_pair_id(p) for p in _lang_pairs()],
)
def lang_pair(request):
    """
    Yields (src_name, src_driver, dst_name, dst_driver) for every
    ordered pair of registered languages.

    Example pytest IDs:
      java_to_cpp
      cpp_to_java
      java_to_rust   <- appears automatically once rust is registered
    """
    return request.param


@pytest.fixture(
    params=DRIVERS,
    ids=[name for name, _ in DRIVERS],
)
def any_lang(request):
    """Yields (name, driver) for every registered language."""
    return request.param
