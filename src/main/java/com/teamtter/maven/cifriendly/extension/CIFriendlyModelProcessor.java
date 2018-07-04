package com.teamtter.maven.cifriendly.extension;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.plugin.LegacySupport;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

import com.teamtter.maven.cifriendly.extension.mojo.CIFriendlyAttachModifiedPomsMojo;

import lombok.extern.slf4j.Slf4j;

/**
 * Maven will automatically pick this ModelProcessor as a replacement, using CIFriendly while loading POMs in order to adapt versions.
 */
@Slf4j
@Component(role = ModelProcessor.class)
public class CIFriendlyModelProcessor extends DefaultModelProcessor {

	static final Plugin attachModifiedPomsPlugin = buildAttachModifiedPomPlugin();

	@Requirement
	private CIFriendlyConfiguration	configurationProvider;

	@Requirement
	private CIFriendlySessionHolder	sessionHolder;

	@Requirement
	private LegacySupport			legacySupport	= null;	// FIXME: it would be better to get the mavenSession directly than to use this legacySupport, but don't know how to get it...

	public CIFriendlyModelProcessor() {
		super();
	}

	@Override
	public Model read(File input, Map<String, ?> options) throws IOException {
		return provisionModel(super.read(input, options), options);
	}

	@Override
	public Model read(Reader input, Map<String, ?> options) throws IOException {
		return provisionModel(super.read(input, options), options);
	}

	@Override
	public Model read(InputStream input, Map<String, ?> options) throws IOException {
		return provisionModel(super.read(input, options), options);
	}

	private Model provisionModel(Model model, Map<String, ?> options) throws IOException {
		Optional<CIFriendlySession> optSession = sessionHolder.getSession();
		if (!optSession.isPresent()) {
			// don't do anything in case no CIFriendly is there (execution could have been skipped)
			return model;
		} else {

			FileModelSource source = (FileModelSource) options.get(ModelProcessor.SOURCE);
			if (source == null) {
				return model;
			}

			File modelSourceFile = new File(source.getLocation());
			if (!modelSourceFile.isFile()) {
				// their JavaDoc says Source.getLocation "could be a local file path, a URI or just an empty string."
				// if it doesn't resolve to a file then calling .getParentFile will throw an exception,
				// but if it doesn't resolve to a file then it isn't under getMultiModuleProjectDirectory,
				return model; // therefore the model shouldn't be modified.
			}

			CIFriendlySession ciFriendlySession = optSession.get();	// we KNOW there is a session (tested before)
			String relativePathCanonical = modelSourceFile.getParentFile().getCanonicalFile().getCanonicalPath();
			String multimoduleDirCanonical = ciFriendlySession.getMultiModuleProjectDir().getCanonicalPath();
			String calculatedVersion = ciFriendlySession.getComputedVersion();

//			if (StringUtils.containsIgnoreCase(relativePathCanonical, multimoduleDirCanonical)) {	// #reportUpstream
			if (! relativePathCanonical.startsWith(sessionHolder.getSession().get().getLocalRepoBaseDir())) {
				updateModel(model, modelSourceFile, ciFriendlySession, relativePathCanonical, multimoduleDirCanonical, calculatedVersion);
			} else {
				log.debug("skipping Model from {}", modelSourceFile);					
			}

			// return the original model (but updated)
			return model;
		}
	}

	private void updateModel(Model model, File location, CIFriendlySession ciFriendlySession,
			String relativePathCanonical, String multimoduleDirCanonical, String calculatedVersion)
			throws IOException {
		log.info("{} - handling version of project Model from {}", model.getArtifactId(), location);

		ciFriendlySession.addOriginalProject(GAV.from(model.clone()));	// why use clone() ???

		if (Objects.nonNull(model.getVersion())) {
			// TODO evaluate how to set the version only when it was originally set in the pom file
			model.setVersion(calculatedVersion);
		}

		Parent modelParent = model.getParent();
		if (Objects.nonNull(modelParent)) {
			// if the parent is part of the multi module project, let's update the parent version
			String modelParentRelativePath = modelParent.getRelativePath();
			
			log.info("    {} parent {} version is {}", System.identityHashCode(modelParent),  modelParent.getArtifactId(), modelParent.getVersion());
			
			File modelParentFile = new File(relativePathCanonical, modelParentRelativePath).getParentFile().getCanonicalFile();
			if (/*StringUtils.isNotBlank(modelParentRelativePath)
					&& */StringUtils.containsIgnoreCase(modelParentFile.getCanonicalPath(), multimoduleDirCanonical)) {
				log.info("    Setting version on the parent model ! modelParentFile = {}", modelParentFile);
				modelParent.setVersion(calculatedVersion);
			} else {
				log.info("    modelParentRelativePath = {}", modelParentRelativePath);
				log.info("    modelParentFile = {}", modelParentFile);
				log.info("    multimoduleDirCanonical = {}", multimoduleDirCanonical);
			}
		}

		// we should only register the plugin once, on the main project
		log.info("    relativePathCanonical = {}  -  multimoduleDirCanonical = {}", relativePathCanonical, multimoduleDirCanonical);
		if (relativePathCanonical.equals(multimoduleDirCanonical)) {
			// BUG: with the above 'if', the parent is not updated if we cd into a subdirectory
			// => so to build a specific module, we have to be at the root and build with: mvn -pl path/to/specific/module
			log.info("    Will addAttachPomMojo on {}", multimoduleDirCanonical);
			addAttachPomMojo(model);
		}

		try {
			String serializedSession = CIFriendlySession.serialize(ciFriendlySession);
			MavenSession mavenSession = legacySupport.getSession();
			mavenSession.getUserProperties().put(CIFriendlyUtils.SESSION_MAVEN_PROPERTIES_KEY, serializedSession);
		} catch (Exception ex) {
			throw new IOException("cannot serialize ciFriendlySession", ex);
		}
	}

	private void addAttachPomMojo(Model model) {
		log.info("    WILL addAttachPomMojo on {}", model);
		
		Build modelBuild = model.getBuild();
		if (Objects.isNull(modelBuild)) {
			modelBuild = new Build();
			model.setBuild(modelBuild);
		}
		if (Objects.isNull(modelBuild.getPlugins())) {
			modelBuild.setPlugins(new ArrayList<>());
		}

		if (! modelBuild.getPlugins().contains(attachModifiedPomsPlugin) ) {
			modelBuild.getPlugins().add(0, attachModifiedPomsPlugin);
		}
	}

	/** warning: we should make sure the plugin is not explicitly defined in any pom */
	private static Plugin buildAttachModifiedPomPlugin() {
		String pluginVersion = "";

		String pomPropertiesResource = "/META-INF/maven/" + CIFriendlyUtils.EXTENSION_GROUP_ID + "/" + CIFriendlyUtils.EXTENSION_ARTIFACT_ID + "/pom.properties";
		try (InputStream inputStream = CIFriendlyModelProcessor.class.getResourceAsStream(pomPropertiesResource)) {
			Properties properties = new Properties();
			properties.load(inputStream);
			pluginVersion = properties.getProperty("version");
		} catch (IOException e) {
			log.error("Unable to build plugin from " + pomPropertiesResource, e);
		}

		Plugin plugin = new Plugin();
		plugin.setGroupId(CIFriendlyUtils.EXTENSION_GROUP_ID);
		plugin.setArtifactId(CIFriendlyUtils.EXTENSION_ARTIFACT_ID);
		plugin.setVersion(pluginVersion.toString());

		plugin.setExecutions(new ArrayList<>());

		PluginExecution pluginExecution = new PluginExecution();
		String pluginRunPhase = System.getProperty("cifriendly.pom-replacement-phase", "prepare-package");
		pluginExecution.setPhase(pluginRunPhase);

		plugin.getExecutions().add(pluginExecution);

			pluginExecution.setGoals(new ArrayList<>());

			pluginExecution.getGoals().add(CIFriendlyAttachModifiedPomsMojo.GOAL_ATTACH_MODIFIED_POMS);

			plugin.setDependencies(new ArrayList<>());

		Dependency dependency = new Dependency();
		dependency.setGroupId(CIFriendlyUtils.EXTENSION_GROUP_ID);
		dependency.setArtifactId(CIFriendlyUtils.EXTENSION_ARTIFACT_ID);
		dependency.setVersion(pluginVersion.toString());
		plugin.getDependencies().add(dependency);
		return plugin;
	}
}
