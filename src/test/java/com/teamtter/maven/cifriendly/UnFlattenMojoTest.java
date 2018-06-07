package com.teamtter.maven.cifriendly;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.MojoFailureException;
import org.junit.Test;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UnFlattenMojoTest {

	private Path resources = Paths.get("target/test-classes/unflatten");

	@Test
	public void testSinglePom() throws MojoFailureException, IOException {
		Path relativeTestPath = Paths.get("singlePom");
		Path relativeReferenceTestPath = Paths.get("singlePom-reference");
		Path testPath = resources.resolve(relativeTestPath);
		Path referenceTestPath = resources.resolve(relativeReferenceTestPath);
		boolean ignoreErrors = false;
		UnFlattenMojo.unflatten(testPath, ignoreErrors);
		FileTestUtils.assertDirectoriesAreEquals(testPath, referenceTestPath);
		// execute it a second time to make sure the results are always the same (the second time /properties/revision/X.Y.Z-SNAPSHOT must not be replaced by ${revision} !)
		UnFlattenMojo.unflatten(testPath, ignoreErrors);
		FileTestUtils.assertDirectoriesAreEquals(testPath, referenceTestPath);
	}

	@Test
	public void testMultiModuleMavenProject() {
		log.warn("NO TEST for multimodule projects => TODO");
	}
}
