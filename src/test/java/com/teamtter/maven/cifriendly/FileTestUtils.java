package com.teamtter.maven.cifriendly;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;

public class FileTestUtils {

	public static void assertDirectoriesAreEquals(Path testPath, Path referenceTestPath) throws IOException {
		// TODO: ideally this method should check directories recursively
		assertTrue("The files differ ! " + testPath + " - " + referenceTestPath,
				FileUtils.contentEquals(
						testPath.resolve("pom.xml").toFile(),
						referenceTestPath.resolve("pom.xml").toFile()));
	}
}
