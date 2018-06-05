package com.teamtter.maven.cifriendly;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface PathConsumer {
	public void accept(Path path) throws IOException;
}
