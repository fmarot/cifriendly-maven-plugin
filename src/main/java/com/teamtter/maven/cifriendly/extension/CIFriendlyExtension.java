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

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.ModelProcessor;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import de.pdark.decentxml.XMLParser;
import de.pdark.decentxml.XMLStringSource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "cifriendly")
public class CIFriendlyExtension extends AbstractMavenLifecycleParticipant {

	private static final String		SNAPSHOT	= "-SNAPSHOT";

	@Requirement
	private PlexusContainer			container;

	@Requirement
	private ModelProcessor			modelProcessor;

	@Requirement
	private CIFriendlySessionHolder	sessionHolder;

	@Override
	public void afterSessionStart(MavenSession mavenSession) throws MavenExecutionException {
		if (CIFriendlyUtils.shouldSkip(mavenSession)) {
			log.info("    " + CIFriendlyUtils.EXTENSION_PREFIX + "execution has been skipped by request of the user");
			sessionHolder.setSession(null);
		} else {
			final File multiModuleProjectDir = mavenSession.getRequest().getMultiModuleProjectDirectory();
			log.info("afterSessionStart -> multiModuleProjectDir = {}", multiModuleProjectDir);
			// TODO: compute branch and target version

			String localRepoBaseDir = mavenSession.getLocalRepository().getBasedir();
			File startingPom = mavenSession.getRequest().getPom();
			String branchName = fetchGitBranch(startingPom);
			String currentVersion = fetchCurrentVersionFromPom(startingPom);
			String newVersion = computeNewVersion(currentVersion, branchName);

			CIFriendlySession session = new CIFriendlySession(multiModuleProjectDir, localRepoBaseDir, newVersion);
			sessionHolder.setSession(session);
		}
	}

	private String computeNewVersion(String currentVersion, String branchName) {
		boolean wasSnapshot = currentVersion.contains(SNAPSHOT);
		String versionNoSnapshot = currentVersion.replace(SNAPSHOT, "");
		if (versionNoSnapshot.endsWith("-" + branchName)) {
			log.info("Version {} already contains the branch name => version should stay unchanged", currentVersion);
			return currentVersion;
		} else {
			String newVersion = versionNoSnapshot + "-" + branchName + (wasSnapshot ? SNAPSHOT : "");
			log.info("New computed version = {}", newVersion);
			return newVersion;
		}
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

	private String fetchGitBranch(File startingPom) throws MavenExecutionException {
		try {
			Process p = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD").directory(startingPom.getParentFile()).start();
			p.waitFor();
			BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String branch = br.readLine().trim();
			return branch;
		} catch (IOException | InterruptedException e) {
			log.error("", e);
			throw new MavenExecutionException("error when fetching git branch of " + startingPom, e);
		}
	}

	@Override
	public void afterSessionEnd(MavenSession session) throws MavenExecutionException {
		sessionHolder.setSession(null);
	}

	@Override
	public void afterProjectsRead(MavenSession mavenSession) throws MavenExecutionException {
		if (!CIFriendlyUtils.shouldSkip(mavenSession)) {

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

				log.info(CIFriendlyUtils.EXTENSION_PREFIX + " has dinamically changes project(s) version(s)");

				CIFriendlySession session = sessionHolder.getSession().get();
				session.getOriginalProjects().forEach(gav -> {
					String msg = "    " + gav.toString() + " -> " + session.getComputedVersion();
					log.info(msg);
				});
			}
		}
	}

}
