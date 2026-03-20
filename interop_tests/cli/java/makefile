# Builds the Java CLI jar for interop testing.
# Copies HLLCli.java into the Java source tree, then runs mvn package.

REPO_ROOT := ../../..
JAVA_DIR  := $(REPO_ROOT)/java
PKG_PATH  := src/main/java/io/github/bareboneslib/bareboneshll
JAR       := $(JAVA_DIR)/target/bareboneshll-jar-with-dependencies.jar
CLI_DST   := $(JAVA_DIR)/$(PKG_PATH)/HLLCli.java

.PHONY: all clean

all: $(JAR)

$(JAR): $(CLI_DST)
	cd $(JAVA_DIR) && mvn package -q

$(CLI_DST): HLLCli.java
	cp HLLCli.java $(CLI_DST)

clean:
	rm -f $(CLI_DST)
	cd $(JAVA_DIR) && mvn clean -q