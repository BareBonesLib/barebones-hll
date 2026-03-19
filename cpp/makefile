CXX      := g++
CXXFLAGS := -std=c++17 -O3 -Wall -Wextra
LDLIBS   := -lxxhash

SRC      := src/HLLPlusPlus.cpp
OBJ      := build/HLLPlusPlus.o

UNAME := $(shell uname)
ifeq ($(UNAME), Darwin)
    LIB          := build/libbareboneshll.dylib
    SHARED_FLAGS := -dynamiclib
    CXXFLAGS     += -I/opt/homebrew/include
    LDFLAGS      := -L/opt/homebrew/lib
else
    LIB          := build/libbareboneshll.so
    SHARED_FLAGS := -shared -fPIC
    ifdef XXHASH_PREFIX
        CXXFLAGS += -I$(XXHASH_PREFIX)/include
        LDFLAGS  := -L$(XXHASH_PREFIX)/lib
    else
        $(warning [Linux] xxhash include/lib paths not set.)
        $(warning [Linux] If build fails, re-run with: make XXHASH_PREFIX=/path/to/xxhash)
        LDFLAGS  :=
    endif
endif

# verify xxhash header is reachable
ifeq ($(shell echo "\#include <xxhash.h>" | $(CXX) $(CXXFLAGS) -x c++ -fsyntax-only - 2>/dev/null && echo ok),)
    $(warning xxhash.h not found — install xxhash and set XXHASH_PREFIX if needed)
    $(warning   macOS:  brew install xxhash)
    $(warning   Ubuntu: apt install libxxhash-dev)
    $(warning   or:     make XXHASH_PREFIX=/path/to/xxhash)
endif

.PHONY: all clean

all: $(LIB)

$(LIB): $(OBJ)
	$(CXX) $(SHARED_FLAGS) $(LDFLAGS) -o $@ $^ $(LDLIBS)

build/HLLPlusPlus.o: $(SRC) src/HLLPlusPlus.h | build
	$(CXX) $(CXXFLAGS) -fPIC -c -o $@ $(SRC)

build:
	mkdir -p build

clean:
	rm -rf build