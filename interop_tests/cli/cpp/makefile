# Builds the C++ CLI binary for interop testing.
# Assumes it's invoked from repo root via interop_tests/makefile.

REPO_ROOT := ../../..
CPP_DIR   := $(REPO_ROOT)/cpp

CXX      := g++
CXXFLAGS := -std=c++17 -O2 -Wall -Wextra

UNAME := $(shell uname)
ifeq ($(UNAME), Darwin)
    CXXFLAGS += -I/opt/homebrew/include
    LDFLAGS  := -L/opt/homebrew/lib
else
    ifdef XXHASH_PREFIX
        CXXFLAGS += -I$(XXHASH_PREFIX)/include
        LDFLAGS  := -L$(XXHASH_PREFIX)/lib
    else
        LDFLAGS  :=
    endif
endif

LDLIBS  := -lxxhash
OUT_DIR := $(CPP_DIR)/build
BIN     := $(OUT_DIR)/hll_cli

SRCS := HLLCli.cpp \
        utils/base64.cpp \
        $(CPP_DIR)/src/HLLPlusPlus.cpp

.PHONY: all clean

all: $(BIN)

$(BIN): $(SRCS) $(CPP_DIR)/src/HLLPlusPlus.h utils/base64.h | $(OUT_DIR)
	$(CXX) $(CXXFLAGS) -I$(CPP_DIR)/src -Iutils $(LDFLAGS) -o $@ $(SRCS) $(LDLIBS)

$(OUT_DIR):
	mkdir -p $(OUT_DIR)

clean:
	rm -f $(BIN)