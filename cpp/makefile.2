# =====================
# Compiler & flags
# =====================
CXX := g++
CXXFLAGS := -std=c++17 -O3 -Wall -Wextra
CXXFLAGS += -Isrc -Isrc/benchmark -Isrc/benchmark/implementations
CXXFLAGS += -I/opt/homebrew/include

LDFLAGS  := -L/opt/homebrew/lib
LDLIBS   := -lxxhash

# =====================
# Directories
# =====================
SRC_DIR    := src
BENCH_DIR  := $(SRC_DIR)/benchmark
BUILD_DIR  := build

BIN_BENCH  := benchmark_runner
BIN_HLL    := hllplusplusmain

# =====================
# Benchmark sources
# =====================
BENCH_SOURCES := \
	$(SRC_DIR)/HLLPlusPlus.cpp \
	$(BENCH_DIR)/UUIDFactory.cpp \
	$(BENCH_DIR)/RandomFactory.cpp \
	$(BENCH_DIR)/SketchTestRunner.cpp \
	$(BENCH_DIR)/main.cpp

BENCH_OBJECTS := $(BENCH_SOURCES:%.cpp=$(BUILD_DIR)/%.o)

# =====================
# Default target
# =====================
all: benchmark

# =====================
# Benchmark target
# =====================
benchmark: $(BIN_BENCH)

$(BIN_BENCH): $(BENCH_OBJECTS)
	$(CXX) $^ -o $@ $(LDFLAGS) $(LDLIBS)

# =====================
# HLL standalone target
# =====================
hll: $(BIN_HLL)

$(BIN_HLL):
	$(CXX) -std=c++17 -O3 -Wall -Wextra \
		-Isrc -I/opt/homebrew/include \
		-L/opt/homebrew/lib \
		src/HLLPlusPlusMain.cpp \
		src/HLLPlusPlus.cpp \
		-lxxhash \
		-o $(BIN_HLL)

# =====================
# Compile rule
# =====================
$(BUILD_DIR)/%.o: %.cpp
	@mkdir -p $(dir $@)
	$(CXX) $(CXXFLAGS) -c $< -o $@

# =====================
# Clean
# =====================
clean:
	rm -rf $(BUILD_DIR) $(BIN_BENCH) $(BIN_HLL)

.PHONY: all benchmark hll clean
