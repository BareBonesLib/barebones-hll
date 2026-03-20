# HLL++ Interop Tests

Python-orchestrated test framework that verifies serialization and merge
compatibility across HLL++ implementations in different languages.

The core idea: a sketch created and serialized in one language must be
correctly deserialized and produce the same estimate in any other language.

---

## How it works

Each language has two pieces:

**CLI** — a small executable written in the target language that reads a
single JSON command from stdin and writes a single JSON result to stdout.
It is the only language-specific artifact the test framework needs.

**Driver** — a thin Python subprocess wrapper that knows where the CLI
binary lives and translates Python function calls into JSON commands.

**Tests** — completely language-agnostic. They call `src.serialize()`,
`dst.deserialize()` etc. through the driver interface. A single test file
covers all language pairs — when a new language is registered, every test
runs against all new pairs automatically with no changes to the test file.

```
registry.py      ← register languages here, nowhere else
conftest.py      ← generates all n² lang pairs from registry automatically
tests/
  test_interop.py   ← the one test file, forever
drivers/
  java_driver.py    ← subprocess wrapper → Java CLI
  cpp_driver.py     ← subprocess wrapper → C++ CLI
cli/
  java/             ← Java CLI source + makefile
  cpp/              ← C++ CLI source + makefile
```

---

## Directory structure

```
interop_tests/
├── makefile                         # top-level: builds all CLIs then runs tests
├── registry.py                      # language registry — only file to edit when adding a lang
├── conftest.py                      # pytest fixtures, auto-generates lang pairs from registry
├── requirements.txt
├── README.md
├── drivers/
│   ├── base_driver.py               # shared protocol return types
│   ├── java_driver.py               # subprocess wrapper → Java CLI
│   └── cpp_driver.py                # subprocess wrapper → C++ CLI
├── cli/
│   ├── java/
│   │   ├── makefile                 # copies HLLCli.java into java src tree, runs mvn package
│   │   └── HLLCli.java
│   └── cpp/
│       ├── makefile                 # compiles HLLCli.cpp against HLLPlusPlus
│       ├── HLLCli.cpp
│       └── utils/
│           ├── base64.h             # vendored, no brew dep needed
│           └── base64.cpp
└── tests/
    └── test_interop.py              # all interop tests, parametrized over lang pairs
```

---

## JSON communication protocol

Each CLI reads one JSON line from stdin and writes one JSON line to stdout.
Every operation is independently debuggable — just pipe JSON directly.

### serialize
Build a sketch from raw values and return its serialized form.

```json
// in
{"op": "serialize", "p": 12, "r": 6, "values": [0, 1, 2, ...]}

// out
{"bytes": "<base64>", "estimate": 12345}
```

### deserialize
Reconstruct a sketch from serialized bytes and return its estimate.

```json
// in
{"op": "deserialize", "bytes": "<base64>"}

// out
{"estimate": 12345}
```

### merge
Merge multiple serialized sketches (from any language) into one.

```json
// in
{"op": "merge", "sketches": ["<base64>", "<base64>", ...]}

// out
{"bytes": "<base64>", "estimate": 12345}
```

### Debugging manually

```bash
# Java CLI
echo '{"op":"serialize","p":12,"r":6,"values":[1,2,3]}' | \
  java -cp java/target/bareboneshll-jar-with-dependencies.jar \
       io.github.bareboneslib.bareboneshll.HLLCli

# C++ CLI
echo '{"op":"serialize","p":12,"r":6,"values":[1,2,3]}' | \
  ./cpp/build/hll_cli
```

---

## What tests are included

All tests live in `tests/test_interop.py` and run for every ordered language
pair registered in `registry.py`. With Java and C++ registered, each test
runs as `[java_to_cpp]` and `[cpp_to_java]`.

**TestSerdeInterop** — src serializes a sketch, dst deserializes it.
Asserts the estimate is preserved exactly and is within error bounds of the
true cardinality. Covers: empty sketch, sparse sketch, small/medium/large
cardinality, a matrix of p/r combinations, and the sparse→normal boundary.

**TestEstimateEquality** — both languages serialize the same input values
independently. Asserts their estimates match and their serialized bytes are
identical, confirming the implementations are bit-for-bit compatible.

**TestMergeInterop** — sketches from different languages are merged together.
Covers: disjoint sets, overlapping sets (no double-counting), merge result
consistency across languages, merging an empty sketch, and three-way merge.

---

## Makefiles

```
interop_tests/makefile            top-level runner, calls CLI makefiles then pytest
interop_tests/cli/cpp/makefile    builds hll_cli binary into cpp/build/
interop_tests/cli/java/makefile   copies HLLCli.java, runs mvn package
```

`interop_tests/makefile` targets:

| target | description |
|--------|-------------|
| `make` / `make all` | build all CLIs then run tests |
| `make build` | build all CLIs only |
| `make test` | run tests (CLIs must already be built) |
| `make clean` | clean all CLI artifacts |

Pass extra pytest flags with `PYTEST_ARGS`:
```bash
make test PYTEST_ARGS="-v -n auto"   # parallel run
make test PYTEST_ARGS="-v -k serde"  # run only serde tests
```

---

## Setup and running

### 1. Install dependencies

**C++ CLI deps** (macOS):
```bash
brew install xxhash nlohmann-json
```

**Java CLI deps**: just Maven — the jar is self-contained.

### 2. Set up Python environment

```bash
cd interop_tests
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### 3. Build CLIs and run all tests

```bash
cd interop_tests
source .venv/bin/activate
make
```

That's it — `make` builds both CLIs then runs the full test suite.

### 4. Run tests only (after CLIs are built)

```bash
cd interop_tests
source .venv/bin/activate
make test

# parallel (faster for large-n tests)
make test PYTEST_ARGS="-v -n auto"
```

---

## Adding new interop tests

In `tests/test_interop.py`, add a method to the appropriate class
(`TestSerdeInterop`, `TestEstimateEquality`, or `TestMergeInterop`) — or
create a new class if the scenario doesn't fit any of them. The `lang_pair`
fixture is injected automatically and gives you
`(src_name, src_driver, dst_name, dst_driver)`.

Minimal example:

```python
def test_your_scenario(self, lang_pair):
    src_name, src, dst_name, dst = lang_pair

    result   = src.serialize(DEFAULT_P, DEFAULT_R, values(1_000))
    restored = dst.deserialize(result.bytes_b64)

    assert restored.estimate == result.estimate, (
        f"Your failure message\n"
        f"  {src_name}: {result.estimate}\n"
        f"  {dst_name}: {restored.estimate}"
    )
```

Pytest auto-discovers it and runs it as:
```
TestSerdeInterop::test_your_scenario[java_to_cpp]
TestSerdeInterop::test_your_scenario[cpp_to_java]
```

Guidelines:

- Always unpack `lang_pair` as the first line — `src_name` and `dst_name` are for failure messages only
- Use `values(n)` and `valuesRange(start, end)` from `conftest.py` for deterministic input — never use `random` without a fixed seed, otherwise failures are non-reproducible
- Use `DEFAULT_P` / `DEFAULT_R` unless the test is specifically about parameter variations
- Always include `src_name` and `dst_name` in assertion messages — without them a failure in a parametrized test tells you nothing about which direction failed
- If the scenario doesn't belong to any existing class, create a new one — the `lang_pair` fixture works the same way

---

## Adding a new language

**1. Write the CLI** in `cli/<lang>/HLLCli.<ext>`.
It must read one JSON line from stdin and write one JSON line to stdout,
implementing the three ops: `serialize`, `deserialize`, `merge`.
Use `std::getline` / `BufferedReader.readLine()` or equivalent — do not
wait for EOF.

**2. Add a makefile** at `cli/<lang>/makefile` with `all` and `clean` targets
that produce a binary or jar reachable by the driver.

**3. Register the makefile** in `interop_tests/makefile`:
```makefile
CLI_MAKEFILES := cli/cpp/makefile \
                 cli/java/makefile \
                 cli/<lang>/makefile    # add this line
```

**4. Write the driver** at `drivers/<lang>_driver.py`, mirroring
`drivers/cpp_driver.py`. It needs three functions: `serialize`,
`deserialize`, `merge`.

**5. Register the driver** in `registry.py` — this is the only other file
to touch:
```python
from drivers import cpp_driver, java_driver, <lang>_driver

DRIVERS = [
    ("java", java_driver),
    ("cpp",  cpp_driver),
    ("<lang>", <lang>_driver),    # add this line
]
```

All n² test combinations for the new language are generated automatically.
No changes to `test_interop.py` or `conftest.py` needed.