package com.teamtter.maven.cifriendly.extension;

import java.io.File;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.ModelProcessor;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "cifriendly")
public class CIFriendlyExtension extends AbstractMavenLifecycleParticipant {

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
			
			
			CIFriendlySession session = new CIFriendlySession(multiModuleProjectDir, localRepoBaseDir);
			sessionHolder.setSession(session);
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
