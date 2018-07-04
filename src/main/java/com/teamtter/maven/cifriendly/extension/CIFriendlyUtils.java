package com.teamtter.maven.cifriendly.extension;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;

public class CIFriendlyUtils {

	public static final String	EXTENSION_PREFIX				= "cifriendly";
	public static final String	EXTENSION_GROUP_ID				= "com.teamtter.maven";
	public static final String	EXTENSION_ARTIFACT_ID			= "cifriendly-maven-plugin";
	public static final String	EXTENSION_SKIP					= EXTENSION_PREFIX + ".skip";
	public static final String	SESSION_MAVEN_PROPERTIES_KEY	= EXTENSION_PREFIX + ".session";

	public static boolean shouldSkip(MavenSession mavenSession) {
		return Boolean.parseBoolean(mavenSession.getSystemProperties().getProperty(EXTENSION_SKIP, "false"))
				|| Boolean.parseBoolean(mavenSession.getUserProperties().getProperty(EXTENSION_SKIP, "false"));
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
			Set<GAV> gavs, String version,
			Logger logger) throws IOException, XmlPullParserException {
		for (MavenProject project : projects) {
			Model model = loadInitialModel(project.getFile());
			GAV initalProjectGAV = GAV.from(model);     // SUPPRESS CHECKSTYLE AbbreviationAsWordInName

			logger.debug("about to change file pom for: " + initalProjectGAV);

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

			File newPom = createPomDumpFile();
			writeModelPom(model, newPom);
			logger.debug("    new pom file created for " + initalProjectGAV + " under " + newPom);

			setProjectPomFile(project, newPom, logger);
			logger.debug("    pom file set");
		}
	}

	private static Model loadInitialModel(File pomFile) throws IOException, XmlPullParserException {
		try (FileReader fileReader = new FileReader(pomFile)) {
			return new MavenXpp3Reader().read(fileReader);
		}
	}

	/**
	 * Creates temporary file to save updated pom mode.
	 */
	private static File createPomDumpFile() throws IOException {
		File tmp = File.createTempFile("pom", ".cifriendly-maven-plugin.xml");
		tmp.deleteOnExit();
		return tmp;
	}

	/**
	 * Writes updated model to temporary pom file.
	 */
	private static void writeModelPom(Model mavenModel, File pomFile) throws IOException {
		try (FileWriter fileWriter = new FileWriter(pomFile)) {
			new MavenXpp3Writer().write(fileWriter, mavenModel);
		}
	}

	/**
	 * Changes the pom file of the given project.
	 * @param project the project to change the pom
	 * @param newPom the pom file to set on the project
	 * @param logger a logger to use 
	 */
	public static void setProjectPomFile(MavenProject project, File newPom, Logger logger) {
		try {
			project.setPomFile(newPom);
		} catch (Throwable unused) {
			logger.warn("maven version might be <= 3.2.4, changing pom file using old mechanism");
			File initialBaseDir = project.getBasedir();
			project.setFile(newPom);
			File newBaseDir = project.getBasedir();
			try {
				if (!initialBaseDir.getCanonicalPath().equals(newBaseDir.getCanonicalPath())) {
					changeBaseDir(project, initialBaseDir);
				}
			} catch (Exception ex) {
				GAV gav = GAV.from(project);
				logger.warn("cannot reset basedir of project " + gav.toString(), ex);
			}
		}
	}

	/**
	 * Changes basedir(dangerous).
	 *
	 * @param project project.
	 * @param initialBaseDir initialBaseDir.
	 * @throws NoSuchFieldException NoSuchFieldException.
	 * @throws IllegalAccessException IllegalAccessException.
	 */
	// WARNING: It breaks the build process, because it changes the basedir to 'tmp'/... where other plugins are not able to
	// find the classes and resources during the phases.
	public static void changeBaseDir(MavenProject project, File initialBaseDir) throws NoSuchFieldException,
			IllegalAccessException {
		Field basedirField = project.getClass().getField("basedir");
		basedirField.setAccessible(true);
		basedirField.set(project, initialBaseDir);
	}

}
