package com.teamtter.maven.cifriendly.extension;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.component.annotations.Component;

import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import de.pdark.decentxml.XMLParser;
import de.pdark.decentxml.XMLStringSource;
import lombok.extern.slf4j.Slf4j;

@Slf4j	// Starting with Maven 3.1.0, SLF4J Logger can be used directly too, without Plexus
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "cifriendly")
public class CIFriendlyExtension extends AbstractMavenLifecycleParticipant {

	private static final String		SNAPSHOT	= "-SNAPSHOT";

	//	@Inject
	//	private PlexusContainer			container;

	//	@Inject
	//	private ModelProcessor			modelProcessor;

	@Inject
	private CIFriendlySessionHolder	sessionHolder;

	@Override
	public void afterSessionStart(MavenSession mavenSession) throws MavenExecutionException {
		if (CIFriendlyExtensionUtils.shouldSkip(mavenSession)) {
			log.info("    " + CIFriendlyExtensionUtils.EXTENSION_PREFIX + "execution has been skipped by request of the user");
			sessionHolder.setSession(null);
		} else {
			final File multiModuleProjectDir = mavenSession.getRequest().getMultiModuleProjectDirectory();
			log.info("afterSessionStart -> multiModuleProjectDir = {}", multiModuleProjectDir);

			String localRepoBaseDir = mavenSession.getLocalRepository().getBasedir();

			File startingPom = mavenSession.getRequest().getPom();
			String currentVersion = fetchCurrentVersionFromPom(startingPom);

			String newVersion = computeNewVersion(mavenSession, currentVersion);

			CIFriendlySession session = new CIFriendlySession(multiModuleProjectDir, localRepoBaseDir, newVersion);
			sessionHolder.setSession(session);

			alterMavenSessionIfMavenReleaseOngoing(mavenSession, currentVersion);
		}
	}

	private String computeNewVersion(MavenSession mavenSession, String currentVersion) throws MavenExecutionException {
		Optional<String> newVersion = CIFriendlyExtensionUtils.getUserOrEnvVariable(CIFriendlyExtensionUtils.EXTENSION_PREFIX + ".newVersion", mavenSession);
		return newVersion.orElseGet(() -> computeNewVersionFromGit(mavenSession, currentVersion));
	}

	private String computeNewVersionFromGit(MavenSession mavenSession, String currentVersion) {
		Optional<String> scmBranch = CIFriendlyExtensionUtils.getUserOrEnvVariable("scmBranch", mavenSession);
		log.info("Received parameter scmBranch={}", scmBranch);

		File startingPom = mavenSession.getRequest().getPom();
		String branchName = scmBranch.orElseGet(() -> fetchGitBranch(startingPom));

		boolean wasSnapshot = currentVersion.contains(SNAPSHOT);
		String versionNoSnapshot = currentVersion.replace(SNAPSHOT, "");
		if (versionNoSnapshot.endsWith("-" + branchName)) {
			log.info("Version {} already contains the branch name => version should stay unchanged", currentVersion);
			return currentVersion;
		} else {
			String computedNewVersion = versionNoSnapshot + "-" + branchName + (wasSnapshot ? SNAPSHOT : "");
			log.info("New computed version = {}", computedNewVersion);
			return computedNewVersion;
		}
	}

	/** if we are in a release, then we must adapt the behavior of the release plugin otherwise the resulting pom
	 * will contain the branch suffix in the version ! Which we do not want. We only want to add +1 to the version,
	 * not add the suffix. */
	private void alterMavenSessionIfMavenReleaseOngoing(MavenSession mavenSession, String currentVersion) {
		if (ongoingMavenRelease(mavenSession)) {
			if (!CIFriendlyExtensionUtils.getUserOrEnvVariable("developmentVersion", mavenSession).isPresent()) {
				String nextNewVersion = computeNextDevelopmentVersion(currentVersion);
				log.info("Ongoing Maven release detected and -DdevelopmentVersion not explicitly set"
						+ " => will force the -DdevelopmentVersion of the release plugin to: {}", nextNewVersion);
				mavenSession.getUserProperties().put("developmentVersion", nextNewVersion);
			}
		}
	}

	private String computeNextDevelopmentVersion(String currentVersion) {
		String developmentVersion;
		try {
			developmentVersion = new DefaultVersionInfo(currentVersion).getNextVersion().getSnapshotVersionString();
		} catch (VersionParseException e) {
			developmentVersion = currentVersion + "-ERROR";
			log.error("Unable to compute next developmentVersion version from " + currentVersion + ". "
					+ "New developmentVersion will be set to " + currentVersion, e);
		}
		return developmentVersion;
	}

	private boolean ongoingMavenRelease(MavenSession mavenSession) {
		boolean isRelease = mavenSession.getRequest().getGoals().stream().anyMatch(goal -> goal.endsWith("release:prepare") || goal.endsWith("release:perform"));
		return isRelease;
	}

	private String fetchCurrentVersionFromPom(File startingPom) throws MavenExecutionException {

		List<Charset> charsetStrategies = Arrays.asList(StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1, StandardCharsets.US_ASCII);
		String version = "ERROR-NO-VERSION-FOUND";

		Iterator<Charset> it = charsetStrategies.iterator();
		while (it.hasNext()) {
			Charset charset = it.next();
			try {
				XMLParser parser = new XMLParser();
				final String pomContent = new String(Files.readAllBytes(startingPom.toPath()), charset);
				final Document doc = parser.parse(new XMLStringSource(pomContent));
				Element versionNode = doc.getChild("/project/version");
				if (versionNode == null) {
					versionNode = doc.getChild("/project/parent/version");
				}
				version = versionNode.getTrimmedText();
				log.debug("version found in {} is {}", startingPom, version);

				// if no Exception was thrown, we can quit the loop
				break;
			} catch (UncheckedIOException | IOException e) {
				log.info("Exception {} for {} when trying Charset {}... Will try next charset. You should consider encoding your pom in UTF8 ;)", e, startingPom, charset);
				if (!it.hasNext()) {
					throw new MavenExecutionException("No Charset found able to decode file " + startingPom, e);
				}
			}
		}

		return version;
	}

	private String fetchGitBranch(File startingPom) {
		try {
			File rootBuildDir = startingPom.getParentFile();
			String rootBuildDirPath = rootBuildDir.toPath().normalize().toString().replace("\\", "/");
			log.info("rootBuildDirPath = {}", rootBuildDirPath);
			if (rootBuildDirPath.contains("target/checkout")) {
				rootBuildDir = new File(rootBuildDirPath.substring(0, rootBuildDirPath.indexOf("target/checkout")));
				log.warn("Detected execution from maven release-like plugin "
						+ "=> will try to find the branch not in {} but in {}", rootBuildDirPath, rootBuildDir);
			}

			Process p = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD").directory(rootBuildDir).start();
			p.waitFor();
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String branch = br.readLine().trim();
			return branch;
		} catch (IOException | InterruptedException e) {
			log.error("", e);
			throw new RuntimeException("error when fetching git branch of " + startingPom, e);
		}
	}

	@Override
	public void afterSessionEnd(MavenSession session) throws MavenExecutionException {
		sessionHolder.setSession(null);
	}

	@Override
	public void afterProjectsRead(MavenSession mavenSession) throws MavenExecutionException {
		if (!CIFriendlyExtensionUtils.shouldSkip(mavenSession)) {

			// In *my understanding*, we simply cannot set the version on the projects (MavenProject class)
			// because they should be read-only and mostly immutable. Besides, it is too late, they may
			// have already been interpreted by other plugin/extension mechanisms...
			// So we have to implement a modelProcessor replacement to tackle the version change in the 
			// Model (org.apache.maven.model.Model) using a custom ModelProcessor.

			//	mavenSession.getAllProjects().forEach(project -> {
			//		project.setVersion("TOTO");
			//		log.info("" + project.getArtifactId());
			//	});

			File projectBaseDir = mavenSession.getCurrentProject().getBasedir();
			if (projectBaseDir != null) {

				log.info(CIFriendlyExtensionUtils.EXTENSION_PREFIX + " has dinamically changes project(s) version(s)");

				CIFriendlySession session = sessionHolder.getSession().get();
				session.getOriginalProjects().forEach(gav -> {
					String msg = "    " + gav.toString() + " -> " + session.getComputedVersion();
					log.info(msg);
				});
			}
		}
	}

}
