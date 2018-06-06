package com.teamtter.maven.cifriendly;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import de.pdark.decentxml.XMLParser;
import de.pdark.decentxml.XMLStringSource;
import lombok.extern.slf4j.Slf4j;

@Mojo(name = "updatePropertiesVersionWithBranch",					// the goal
		defaultPhase = LifecyclePhase.NONE,	//
		requiresProject = true,				//
		threadSafe = false,					//
		aggregator = true,					// only execute on the parent pom, root of the maven tree (not on children)
		requiresDependencyCollection = ResolutionScope.NONE,	//
		requiresDependencyResolution = ResolutionScope.NONE)
@Slf4j
public class UpdatePropertiesVersionWithBranchMojo extends AbstractMojo {

	private static final String	WAS_NOT_UPDATED	= "0";
	private static final String	WAS_UPDATED		= "1";

	@Parameter(property = "branch", required = true)
	private String				branch;

	@Parameter(property = "outputResultInFile", defaultValue = "true")
	private boolean				outputResultInFile;

	@Parameter(property = "rootDir", defaultValue = "${session.executionRootDirectory}")
	private File				rootDir;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		Path rootPath = rootDir.toPath();

		XMLParser parser = new XMLParser();
		Path rootPomPath = new File(rootDir, "pom.xml").toPath();
		final String visitorContent = new String(Files.readAllBytes(rootPomPath), StandardCharsets.UTF_8);
		final Document doc = parser.parse(new XMLStringSource(visitorContent));
		Element revisionPropertyNode = doc.getChild("/project/properties/revision");
		if (revisionPropertyNode == null) {
			log.warn("No properties/revision node in {}", rootPomPath);
			outputResult(WAS_NOT_UPDATED);
		}
	}

	private void outputResult(String updatedOrNot) throws IOException {
		if (outputResultInFile) {
			Files.write(rootDir.toPath().resolve("updatePropertiesVersionWithBranch"), updatedOrNot.getBytes(), OpenOption.);
		}
	}
}
