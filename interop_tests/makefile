# Top-level interop test runner.
# Builds all CLI artifacts then runs the Python test suite.
#
# Usage:
#   make              — build all CLIs + run tests
#   make build        — build all CLIs only
#   make test         — run tests (assumes CLIs already built)
#   make clean        — clean all CLI artifacts
#
# Adding a new language:
#   1. Add a cli/<newlang>/makefile with `all` and `clean` targets
#   2. Add it to CLI_MAKEFILES below
#   3. Add the driver to drivers/ and register in registry.py

CLI_MAKEFILES := cli/cpp/makefile \
                 cli/java/makefile

PYTEST_ARGS ?= -v

.PHONY: all build test clean $(CLI_MAKEFILES)

all: build test

build: $(CLI_MAKEFILES)

# forward `all` to each CLI makefile
$(CLI_MAKEFILES):
	$(MAKE) -C $(dir $@) -f $(notdir $@) all

test:
	pytest tests/ $(PYTEST_ARGS)

clean:
	for f in $(CLI_MAKEFILES); do \
	    $(MAKE) -C $$(dirname $$f) -f $$(basename $$f) clean; \
	done