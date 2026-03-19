CXX      := g++
CXXFLAGS := -std=c++17 -O2 -Wall -Wextra
CXXFLAGS += -I.. -I/opt/homebrew/include
LDFLAGS  := -L/opt/homebrew/lib
LDLIBS   := -lxxhash -lgtest -lgtest_main -pthread

SRC      := HLLPlusPlusTest.cpp ../src/HLLPlusPlus.cpp
BIN      := build/hll_test

.PHONY: all run clean

all: $(BIN)

$(BIN): $(SRC) | build
	$(CXX) $(CXXFLAGS) $(LDFLAGS) -o $@ $^ $(LDLIBS)

build:
	mkdir -p build

run: $(BIN)
	./$(BIN)

clean:
	rm -rf build