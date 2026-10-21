package dev.bedrockadder.paper.deploy;

import java.io.IOException;
import java.nio.file.Path;

/** Delivers a finished bundle to wherever the BedrockAdder Geyser extension reads it from. */
public interface Deployer {

    /** Human-readable target, shown by {@code /bedrockadder status}. */
    String describe();

    void deploy(Path bundleDirectory) throws IOException;

    default void close() {
    }
}
