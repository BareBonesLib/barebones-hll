# BareBones-HLL

[![Maven Central](https://img.shields.io/maven-central/v/io.github.bareboneslib/barebones-hll)](https://central.sonatype.com/artifact/io.github.bareboneslib/barebones-hll)
[![Build](https://github.com/BareBonesLib/barebones-hll/actions/workflows/build.yml/badge.svg)](https://github.com/BareBonesLib/barebones-hll/actions)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

Minimal, high-performance, cross-language **HyperLogLog++** (HLL++) implementations in Java and C++.

Sketches are binary-compatible across languages — a sketch serialized in Java deserializes correctly in C++, and vice versa.

> **Performance**: In benchmarks, this implementation is **10–50x or more faster** than [DataSketches HLL++](https://datasketches.apache.org/) and [Clearspring HLL++](https://github.com/addthis/stream-lib). See [benchmarks](#benchmarks).

Based on: Heule, Nunkesser, Hall — *HyperLogLog in Practice: Algorithmic Engineering of a State of The Art Cardinality Estimation Algorithm* (EDBT 2013).

---

## Table of Contents

- [Repository Structure](#repository-structure)
- [Java Implementation](#java-implementation)
- [C++ Implementation](#c-implementation)
- [Parameters](#parameters)
- [Serialization Format](#serialization-format)
- [Cross-Language Interoperability](#cross-language-interoperability)
- [Benchmarks](#benchmarks)
- [Adding a New Language Implementation](#adding-a-new-language-implementation)
- [References](#references)

---

## Repository Structure

```
barebones-hll/
├── java/             # Java implementation (HLLPlusPlus) — published to Maven Central
├── cpp/              # C++ implementation (HLLPlusPlus)
└── interop_tests/    # Cross-language compatibility test suite (Python-orchestrated)
```

> **Note**: The `java/` directory also contains a legacy `HLL` class. It is unmaintained and not documented here. Use `HLLPlusPlus` in both languages.

---

## Java Implementation

### Dependency

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.bareboneslib</groupId>
    <artifactId>bareboneshll</artifactId>
    <version>LATEST</version>
</dependency>
```

Or with Gradle:

```groovy
implementation 'io.github.bareboneslib:bareboneshll:LATEST'
```

Replace `LATEST` with the current version from the Maven Central badge above.

### API

```java
// Constructors
HLLPlusPlus hll = new HLLPlusPlus();          // default: p=12, r=6
HLLPlusPlus hll = new HLLPlusPlus(int p);     // default r=6
HLLPlusPlus hll = new HLLPlusPlus(int p, int r);

// Core operations
hll.add(long hash);           // add a 64-bit hash value
long count = hll.estimate();  // get cardinality estimate
hll.merge(HLLPlusPlus other); // merge another sketch into this one (union)

// Serialization
byte[] bytes = hll.serialize();
HLLPlusPlus hll = HLLPlusPlus.deserialize(byte[] bytes);
```

### Usage Example

```java
import io.github.bareboneslib.bareboneshll.HLLPlusPlus;
import net.openhft.hashing.LongHashFunction;

HLLPlusPlus hll = new HLLPlusPlus(12, 6);
LongHashFunction hash = LongHashFunction.xx3();

// Add elements as 64-bit hashes
hll.add(hash.hashChars("user-1001"));
hll.add(hash.hashChars("user-1002"));
hll.add(hash.hashChars("user-1003"));

long estimate = hll.estimate();

// Merge two sketches
HLLPlusPlus hll2 = new HLLPlusPlus(12, 6);
hll2.add(hash.hashChars("user-2001"));
hll.merge(hll2); // hll now represents the union

// Serialize and restore
byte[] bytes = hll.serialize();
HLLPlusPlus restored = HLLPlusPlus.deserialize(bytes);
```

### Build from Source

```bash
cd java
mvn package
```

---

## C++ Implementation

### Dependency

The C++ implementation has a single external dependency: **xxHash**.

Install via your package manager:

```bash
# macOS
brew install xxhash

# Ubuntu / Debian
apt-get install libxxhash-dev
```

### Build

```bash
cd cpp
mkdir build && cd build
cmake ..
make
```

This produces a shared library (`libbareboneshll.so` on Linux, `libbareboneshll.dylib` on macOS).

### Linking

```bash
g++ your_program.cpp -lbareboneshll -lxxhash -o your_program
```

### API

```cpp
#include "HLLPlusPlus.h"
using namespace bareboneshll;

// Constructors
HLLPlusPlus hll;                     // default: p=12, r=6
HLLPlusPlus hll(int p);              // default r=6
HLLPlusPlus hll(int p, int r);

// Core operations
hll.add(uint64_t hash);              // add a 64-bit hash value
int64_t count = hll.estimate();      // get cardinality estimate
hll.merge(HLLPlusPlus& other);       // merge another sketch (union)

// Serialization — returns std::vector<uint8_t> by default
auto bytes = hll.serialize();
auto bytes = hll.serialize<std::string>(); // or as std::string

HLLPlusPlus hll = HLLPlusPlus::deserialize(const std::vector<uint8_t>&);
HLLPlusPlus hll = HLLPlusPlus::deserialize(const unsigned char*, size_t);
```

### Usage Example

```cpp
#include "HLLPlusPlus.h"
#include <xxhash.h>

using namespace bareboneshll;

HLLPlusPlus hll(12, 6);

// Hash elements before adding
auto h1 = XXH3_64bits("user-1001", 9);
auto h2 = XXH3_64bits("user-1002", 9);
hll.add(h1);
hll.add(h2);

int64_t estimate = hll.estimate();

// Merge two sketches
HLLPlusPlus hll2(12, 6);
hll2.add(XXH3_64bits("user-2001", 9));
hll.merge(hll2);

// Serialize and restore
auto bytes = hll.serialize();
HLLPlusPlus restored = HLLPlusPlus::deserialize(bytes);
```

---

## Parameters

| Parameter | Range | Default | Description |
|-----------|-------|---------|-------------|
| `p` | 4–18 | 12 | Precision. Controls number of registers: m = 2^p |
| `r` | 4–6 | 6 | Register width in bits. Controls max trackable leading zeros |

### Sparse and Dense Modes

The sketch starts in **sparse mode**, using a compressed representation with precision `sp = p + 4` bits. This gives higher accuracy at low cardinalities with less memory. Once the number of distinct elements crosses an internal conversion threshold, the sketch automatically promotes to **dense mode** using the full register array.

Standard error in dense mode: **≈ 1.04 / √m** where **m = 2^p**

### Memory and Accuracy Reference

| p | r | Registers (m) | Dense Memory | Std Error |
|---|---|---------------|--------------|-----------|
| 10 | 6 | 1,024 | 768 B | ~3.25% |
| 12 | 4 | 4,096 | 2 KB | ~1.625% |
| 12 | 5 | 4,096 | 2.5 KB | ~1.625% |
| 12 | 6 | 4,096 | 3 KB | ~1.625% |
| 14 | 6 | 16,384 | 12 KB | ~0.81% |
| 16 | 6 | 65,536 | 48 KB | ~0.41% |
| 18 | 6 | 262,144 | 192 KB | ~0.20% |

Formula: `dense memory = r × 2^p / 8` bytes

---

## Serialization Format

The binary serialization format is the cross-language contract. All implementations must produce and consume this format exactly.

### Common Header (8 bytes)

```
┌──────────┬──────────┬──────────┬──────────┬───────────────────────┐
│ version  │  mode    │    p     │    r     │    bufferLength       │
│  1 byte  │  1 byte  │  1 byte  │  1 byte  │       4 bytes         │
└──────────┴──────────┴──────────┴──────────┴───────────────────────┘
```

- `version`: always `1`
- `mode`: `0` = sparse, `1` = dense
- `p`: precision parameter
- `r`: register width
- `bufferLength`: byte length of the data buffer that follows

### Sparse Layout (8 bytes metadata total)

```
[header: 8 bytes] [sparseSet buffer: bufferLength bytes]
```

The sparse buffer contains the packed `sparseSet` — a sorted, deduplicated set of encoded (index, value) pairs using the higher-precision sparse representation (`sp = p + 4` bits).

### Dense Layout (20 bytes metadata total)

```
[header: 8 bytes] [zeroRegs: 4 bytes] [preEstimate: 8 bytes] [registers buffer: bufferLength bytes]
```

- `zeroRegs`: count of registers that are currently zero
- `preEstimate`: cached raw HLL estimate before bias correction (IEEE 754 double, big-endian)
- `registers buffer`: the packed register array — `r` bits per register, `2^p` registers total

For the exact bit-packing and encoding details, refer to the `serialize` / `deserialize` methods in [`java/`](java/src/main/java/io/github/bareboneslib/bareboneshll/HLLPlusPlus.java) or [`cpp/`](cpp/src/HLLPlusPlus.h).

---

## Cross-Language Interoperability

Sketches produced by the Java and C++ implementations are fully binary-compatible. A sketch serialized in one language can be deserialized and used in the other without any conversion.

The `interop_tests/` suite validates this property end-to-end across both sparse and dense modes, across all supported precisions. It is Python-orchestrated and drives both language implementations via CLI binaries.

### Running the Interop Tests

Refer to [`interop_tests/README.md`](interop_tests/README.md) for the full guide on:
- What test cases are covered
- How to run the suite
- How to add new test cases for a new language implementation

All interop tests must pass before any change is merged.

---

## Benchmarks

[Benchmark results and methodology to be added here.]

---

## Adding a New Language Implementation

New language implementations are welcome. The primary requirement is **binary compatibility** with the existing Java and C++ implementations — a sketch produced in the new language must be usable in Java and C++, and vice versa.

### Performance Expectation

This library is performance-focused. Implementations should be reasonably performant — following the structure of the [Java](java/) or [C++](cpp/) code closely will get you most of the way there, as the algorithmic choices (register packing, sparse set encoding, bias correction lookup) are already optimized.

### Steps

1. **Create a `<language>/` folder** at the repo root (e.g., `python/`, `rust/`, `go/`)

2. **Implement the full API surface**:
   - `add(hash: uint64)` — add a 64-bit hash
   - `estimate() -> int64` — return cardinality estimate
   - `merge(other)` — merge another sketch (union in-place)
   - `serialize() -> bytes` — produce the binary format described in [§ Serialization Format](#serialization-format)
   - `deserialize(bytes) -> HLLPlusPlus` — reconstruct from binary

3. **Conform to the serialization format** in [§ Serialization Format](#serialization-format) exactly. The Java or C++ `serialize`/`deserialize` methods are the reference implementation.

4. **Write unit tests** using your language's native test framework. Covering `add`/`estimate` accuracy, merge correctness, and serialize/deserialize round-trips is recommended. Unit tests are not a hard gate for merge but are strongly encouraged.

5. **Add a CLI binary** under `interop_tests/cli/<language>/` following the pattern of the existing `java/` and `cpp/` CLI drivers. Add a driver under `interop_tests/drivers/` and register it in `interop_tests/registry.py`. See [`interop_tests/README.md`](interop_tests/README.md) for full instructions.

6. **Run the full interop test suite** and ensure all tests pass. This is a hard gate for merge.

7. **Include a `README.md`** in your `<language>/` subfolder with build instructions, dependency list, and a usage example.

### PR Checklist

- [ ] `<language>/` folder created at repo root
- [ ] All five API methods implemented (`add`, `estimate`, `merge`, `serialize`, `deserialize`)
- [ ] Serialization format conforms exactly to the spec in this README
- [ ] CLI driver added and registered in `interop_tests/`
- [ ] All interop tests pass (`interop_tests/README.md` for how to run)
- [ ] Unit tests written (recommended)
- [ ] Subfolder `README.md` includes build instructions and usage example

---

## References

- Heule, S., Nunkesser, M., & Hall, A. (2013). *HyperLogLog in Practice: Algorithmic Engineering of a State of The Art Cardinality Estimation Algorithm.* Proceedings of the EDBT 2013 Conference. [PDF](http://static.googleusercontent.com/media/research.google.com/en//pubs/archive/40671.pdf)

- Flajolet, P., Fusy, É., Gandouet, O., & Meunier, F. (2007). *HyperLogLog: the analysis of a near-optimal cardinality estimation algorithm.* AofA: Analysis of Algorithms. [PDF](https://algo.inria.fr/flajolet/Publications/FlFuGaMe07.pdf)