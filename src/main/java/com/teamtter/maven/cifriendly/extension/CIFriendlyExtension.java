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

	private CIFriendlySessionHolder	sessionHolder;

	@Override
	public void afterSessionStart(MavenSession mavenSession) throws MavenExecutionException {
		if (CIFriendlyUtils.shouldSkip(mavenSession)) {
			log.info("    " + CIFriendlyUtils.EXTENSION_PREFIX + "execution has been skipped by request of the user");
			sessionHolder.setSession(null);
		} else {
			final File multiModuleProjectDir = mavenSession.getRequest().getMultiModuleProjectDirectory();

			sessionHolder.setSession(new CIFriendlySession(multiModuleProjectDir));
		}
	}

	@Override
	public void afterSessionEnd(MavenSession session) throws MavenExecutionException {
		sessionHolder.setSession(null);
	}

	@Override
	public void afterProjectsRead(MavenSession mavenSession) throws MavenExecutionException {
		if (!CIFriendlyUtils.shouldSkip(mavenSession)) {
			File projectBaseDir = mavenSession.getCurrentProject().getBasedir();
			if (projectBaseDir != null) {

				log.info(CIFriendlyUtils.EXTENSION_PREFIX + " is about to change project(s) version(s)");

				CIFriendlySession session = sessionHolder.getSession().get();
				session.getProjects().forEach(gav -> {
					String msg = "    " + gav.toString() + " -> " + session.getComputedVersion();
					log.info(msg);
				});
			}
		}
	}
}
