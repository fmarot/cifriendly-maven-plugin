package com.teamtter.maven.cifriendly;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class UpdatePropertiesVersionWithBranchMojoTest {
	
	private Path resources = Paths.get("target/test-classes/updatePropertiesVersionWithBranch");
	
	@Test
	public void pomIsChanged() throws IOException {
		Path relativeTestPath = Paths.get("pomIsChanged");
		Path relativeReferenceTestPath = Paths.get("pomIsChanged-reference");
		Path testPath = resources.resolve(relativeTestPath);
		Path referenceTestPath = resources.resolve(relativeReferenceTestPath);
		
		UpdatePropertiesVersionWithBranchMojo mojo = new UpdatePropertiesVersionWithBranchMojo();
		mojo.updatePropertiesVersionWithBranch(testPath, "myNewBranch");
		
		FileTestUtils.assertDirectoriesAreEquals(testPath, referenceTestPath);
	}
}
