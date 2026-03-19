CXX      := g++
CXXFLAGS := -std=c++17 -O3 -Wall -Wextra
CXXFLAGS += -I/opt/homebrew/include
LDFLAGS  := -L/opt/homebrew/lib
LDLIBS   := -lxxhash

SRC      := src/HLLPlusPlus.cpp
OBJ      := build/HLLPlusPlus.o

UNAME := $(shell uname)
ifeq ($(UNAME), Darwin)
    LIB := build/libbareboneshll.dylib
    SHARED_FLAGS := -dynamiclib
else
    LIB := build/libbareboneshll.so
    SHARED_FLAGS := -shared -fPIC
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