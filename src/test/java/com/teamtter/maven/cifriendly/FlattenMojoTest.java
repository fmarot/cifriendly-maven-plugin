package com.teamtter.maven.cifriendly;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.Test;

public class FlattenMojoTest {
	
	private Path resources = Paths.get("target/test-classes/");
	
	@Test
	public void testNormal() throws MojoFailureException, IOException {
		FlattenMojo flattenMojo = new FlattenMojo();
		Path rootPath = resources.resolve("flatten/normal");
		flattenMojo.flatten(rootPath, "1.0.2-SNAPSHOT");
		
		Path modifiedPomPath = rootPath.resolve("pom.xml");
		Path referencePomPath = rootPath.resolve("pom-reference.xml");
		assertTrue("The files differ ! " + modifiedPomPath + " - " + referencePomPath,
				FileUtils.contentEquals(modifiedPomPath.toFile(), referencePomPath.toFile()));
	}
	
	
//	@Test(expected)
//	public void testWithError() {
//		FlattenMojo flattenMojo = new FlattenMojo();
//		Path rootPath = resources.resolve("flatten/normal");
//		// should throw an Exception because the <version> and the <revision> tags are different
//		flattenMojo.flatten(rootPath, "1.0.2-SNAPSHOT");
//	}
}
