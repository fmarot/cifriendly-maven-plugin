// @formatter:off
package com.teamtter.maven.cifriendly.extension.mojo;

import java.util.Objects;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import com.teamtter.maven.cifriendly.extension.CIFriendlySession;
import com.teamtter.maven.cifriendly.extension.CIFriendlyUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Works in conjunction with CIFriendlyModelProcessor.
 */
@Slf4j
@Mojo(name = CIFriendlyAttachModifiedPomsMojo.GOAL_ATTACH_MODIFIED_POMS,	//
        instantiationStrategy = InstantiationStrategy.SINGLETON,			//
        threadSafe = true)
public class CIFriendlyAttachModifiedPomsMojo extends AbstractMojo {
    private static final String UPDATED_POMS_ALREADY_ATTACHED = "-";

	public static final String GOAL_ATTACH_MODIFIED_POMS = "attach-modified-poms";

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;
    
    /** for tests only, to verify we do not stumble onto https://github.com/bdemers/maven-external-version/issues/7 */
    @Parameter(defaultValue = "${project.version}", readonly = true)
    private String projectVersion;
    
    @Override
    public void execute() throws MojoExecutionException {
        if (Objects.isNull(mavenSession.getUserProperties().get(CIFriendlyUtils.SESSION_MAVEN_PROPERTIES_KEY))) {
            log.warn(GOAL_ATTACH_MODIFIED_POMS + "shouldn't be executed alone. The Mojo "
                    + "should be dynamically added to the build by the extension");
            return;
        }

        String serializedSession = mavenSession.getUserProperties().getProperty((CIFriendlyUtils.SESSION_MAVEN_PROPERTIES_KEY));
        if (UPDATED_POMS_ALREADY_ATTACHED.equalsIgnoreCase(serializedSession)) {
            // We don't need to attach modified poms anymore.
        	log.info("UPDATED_POMS_ALREADY_ATTACHED => skipping attachement - projectVersion={}", projectVersion);
            return;
        }

        try {
            CIFriendlySession ciFriendlySession = CIFriendlySession.deserializeFrom(serializedSession);
            CIFriendlyUtils.attachModifiedPomFilesToTheProject(mavenSession.getAllProjects(),
                    ciFriendlySession.getOriginalProjects(),
                    ciFriendlySession.getComputedVersion(),
                    log);
            mavenSession.getUserProperties().setProperty(CIFriendlyUtils.SESSION_MAVEN_PROPERTIES_KEY, UPDATED_POMS_ALREADY_ATTACHED);
        } catch (Exception ex) {
            throw new MojoExecutionException("Unable to execute goal: "
                    + CIFriendlyAttachModifiedPomsMojo.GOAL_ATTACH_MODIFIED_POMS, ex);
        }
    }
}
