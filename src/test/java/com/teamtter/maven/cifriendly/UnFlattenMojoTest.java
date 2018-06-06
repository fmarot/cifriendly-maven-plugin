package com.teamtter.maven.cifriendly;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileSystemUtils;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.Assert;
import org.junit.Test;

public class UnFlattenMojoTest {

	Path resources = Paths.get("target/test-classes/unflatten");

	@Test
	public void testSinglePom() throws MojoFailureException, IOException {
		Path relativeTestPath = Paths.get("singlePom");
		Path relativeReferenceTestPath = Paths.get("singlePom-reference");
		Path testPath = resources.resolve(relativeTestPath);
		Path referenceTestPath = resources.resolve(relativeReferenceTestPath);
		boolean ignoreErrors = false;
		UnFlattenMojo.unflatten(testPath, ignoreErrors);
		assertDirectoriesAreEquals(testPath, referenceTestPath);
		// execute it a second time to make sure the results are always the same (the second time /properties/revision/X.Y.Z-SNAPSHOT must not be replaced by ${revision} !)
		UnFlattenMojo.unflatten(testPath, ignoreErrors);
		assertDirectoriesAreEquals(testPath, referenceTestPath);
	}

	private void assertDirectoriesAreEquals(Path testPath, Path referenceTestPath) throws IOException {
		assertTrue("The files differ!", FileUtils.contentEquals(
				testPath.resolve("pom.xml").toFile(),
				referenceTestPath.resolve("pom.xml").toFile()));
	}

	@Test
	public void testMultiModuleMavenProject() {

	}
}
