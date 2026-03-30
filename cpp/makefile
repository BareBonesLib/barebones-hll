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
    INSTALL_LIB  := /usr/local/lib
    INSTALL_INC  := /usr/local/include/bareboneshll
else
    LIB          := build/libbareboneshll.so
    SHARED_FLAGS := -shared -fPIC
    INSTALL_LIB  := /usr/local/lib
    INSTALL_INC  := /usr/local/include/bareboneshll
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

.PHONY: all install uninstall clean

all: $(LIB)

$(LIB): $(OBJ)
	$(CXX) $(SHARED_FLAGS) $(LDFLAGS) -o $@ $^ $(LDLIBS)

build/HLLPlusPlus.o: $(SRC) src/HLLPlusPlus.h | build
	$(CXX) $(CXXFLAGS) -fPIC -c -o $@ $(SRC)

build:
	mkdir -p build

install: $(LIB)
	mkdir -p $(INSTALL_LIB) $(INSTALL_INC)
	cp $(LIB) $(INSTALL_LIB)/
	cp src/HLLPlusPlus.h $(INSTALL_INC)/
	@echo "Installed lib  → $(INSTALL_LIB)/$(notdir $(LIB))"
	@echo "Installed header → $(INSTALL_INC)/HLLPlusPlus.h"
ifeq ($(UNAME), Darwin)
	install_name_tool -id $(INSTALL_LIB)/$(notdir $(LIB)) $(INSTALL_LIB)/$(notdir $(LIB))
endif

uninstall:
	rm -f  $(INSTALL_LIB)/$(notdir $(LIB))
	rm -rf $(INSTALL_INC)
	@echo "Uninstalled"

clean:
	rm -rf build