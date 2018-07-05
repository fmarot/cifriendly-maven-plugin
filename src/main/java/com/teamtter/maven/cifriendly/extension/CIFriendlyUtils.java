package com.teamtter.maven.cifriendly.extension;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CIFriendlyUtils {

	public static final String	EXTENSION_PREFIX				= "cifriendly";
	public static final String	EXTENSION_GROUP_ID				= "com.teamtter.maven";
	public static final String	EXTENSION_ARTIFACT_ID			= "cifriendly-maven-plugin";
	private static final String	CUSTOM_POM_FILENAME				= "pom-" + EXTENSION_ARTIFACT_ID + ".xml";
	public static final String	EXTENSION_SKIP					= EXTENSION_PREFIX + ".skip";
	public static final String	SESSION_MAVEN_PROPERTIES_KEY	= EXTENSION_PREFIX + ".session";

	/** if user hav defined this variable, use this one, otherwise return the system variable */
	public static Optional<String> getUserOrEnvVariable(String variable, MavenSession session) {
		Optional<String> property = null;
		String userProp = session.getUserProperties().getProperty(variable);
		if (userProp == null) {
			property = Optional.ofNullable(session.getSystemProperties().getProperty(variable));
		} else {
			property = Optional.of(userProp);
		}
		return property;
	}

	public static boolean shouldSkip(MavenSession mavenSession) {
		return Boolean.parseBoolean(getUserOrEnvVariable(EXTENSION_SKIP, mavenSession).orElse("false"));
	}

	/**
	* Attach modified POM files to the projects so install/deployed files contains new version.
	* @param gavs list of registered GAVs of modified projects.
	* @param version the version to set
	* @param logger the logger to report to
	* @throws IOException if project model cannot be read correctly
	* @throws XmlPullParserException if project model cannot be interpreted correctly
	*/
	public static void attachModifiedPomFilesToTheProject(List<MavenProject> projects,
			Set<GAV> gavs, String version) throws IOException, XmlPullParserException {

		// cannot put the custom poms inside each 'target' because they are created only at session start and each individual 'target' may be deleted later => create a specific temp dir
		File tempDir = Files.createTempDirectory(EXTENSION_ARTIFACT_ID).toFile();
		tempDir.deleteOnExit();

		for (MavenProject project : projects) {
			Model model = loadInitialModel(project.getFile());
			GAV initalProjectGAV = GAV.from(model);     // SUPPRESS CHECKSTYLE AbbreviationAsWordInName

			log.debug("about to change file pom for: " + initalProjectGAV);

			if (gavs.contains(initalProjectGAV)) {
				model.setVersion(version);
				if (model.getScm() != null && project.getModel().getScm() != null) {
					model.getScm().setTag(project.getModel().getScm().getTag());
				}
			}

			if (model.getParent() != null) {
				GAV parentGAV = GAV.from(model.getParent());    // SUPPRESS CHECKSTYLE AbbreviationAsWordInName
				if (gavs.contains(parentGAV)) {
					// parent has been modified
					model.getParent().setVersion(version);
				}
			}

			File newPom = new File(tempDir, computeProjectCustomPomFilename(project));
			newPom.getParentFile().mkdirs();
			writeModelPom(model, newPom);
			log.info("    new pom file created for " + initalProjectGAV + " under " + newPom);

			project.setPomFile(newPom);	// requires Maven version > 3.2.4
		}
	}

	private static String computeProjectCustomPomFilename(MavenProject project) {
		return project.getGroupId() + "---" + project.getArtifactId() + "---" + project.getVersion() + "---" + CUSTOM_POM_FILENAME;
	}

	private static Model loadInitialModel(File pomFile) throws IOException, XmlPullParserException {
		try (FileReader fileReader = new FileReader(pomFile)) {
			return new MavenXpp3Reader().read(fileReader);
		}
	}

	/** Writes updated model to temporary pom file. */
	private static void writeModelPom(Model mavenModel, File pomFile) throws IOException {
		try (FileWriter fileWriter = new FileWriter(pomFile)) {
			new MavenXpp3Writer().write(fileWriter, mavenModel);
		}
	}

}
