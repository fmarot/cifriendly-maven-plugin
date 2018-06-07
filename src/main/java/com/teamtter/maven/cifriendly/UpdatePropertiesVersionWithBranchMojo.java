package com.teamtter.maven.cifriendly;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
		try {
			updatePropertiesVersionWithBranch(rootPath, branch);
		} catch (IOException e) {
			throw new MojoExecutionException("pdatePropertiesVersionWithBranch failed", e);
		}
	}

	protected void updatePropertiesVersionWithBranch(Path rootPath, String newBranch) throws IOException {
		XMLParser parser = new XMLParser();
		Path rootPomPath = rootPath.resolve("pom.xml");
		final String visitorContent = new String(Files.readAllBytes(rootPomPath), StandardCharsets.UTF_8);
		final Document doc = parser.parse(new XMLStringSource(visitorContent));

		checkPreconditions(doc);

		Element revisionPropertyNode = doc.getChild("/project/properties/revision");
		if (revisionPropertyNode == null) {
			log.warn("No properties/revision node in {}", rootPomPath);
			outputResult(WAS_NOT_UPDATED, rootPath);
		} else {
			String currentVersion = revisionPropertyNode.getText();
			String newVersion = VersionComputer.computeNewVersion(currentVersion, newBranch);
			if (currentVersion.equals(newVersion)) {
				log.info("current version {} is already correct in {} => pom not updated", rootPomPath);
				outputResult(WAS_NOT_UPDATED, rootPath);
			} else {
				CIFriendlyUtils.setPropertiesRevisionText(doc, newVersion);
				Files.write(rootPomPath, doc.toString().getBytes());
				outputResult(WAS_UPDATED, rootPath);
			}
		}
	}

	/** the version of the pom MUST be ${revision} */
	private void checkPreconditions(Document doc) throws IOException {
		Element versionNode = doc.getChild("/project/version");
		if (!versionNode.getText().equals(CIFriendlyUtils.REVISION)) {
			throw new IOException("The root pom must contain a version with value " + CIFriendlyUtils.REVISION);
		}
	}

	private void outputResult(String updatedOrNot, Path rootPath) throws IOException {
		if (outputResultInFile) {
			Files.write(rootPath.resolve("updatePropertiesVersionWithBranch"), updatedOrNot.getBytes(), TRUNCATE_EXISTING, CREATE_NEW);
		} else {
			log.debug("Not writing result {} to file due to outputResultInFile={}", updatedOrNot, outputResultInFile);
		}
	}
}
