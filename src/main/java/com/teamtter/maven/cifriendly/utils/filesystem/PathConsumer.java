package com.teamtter.maven.cifriendly.utils.filesystem;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface PathConsumer {
	public void accept(Path path) throws IOException;
}
