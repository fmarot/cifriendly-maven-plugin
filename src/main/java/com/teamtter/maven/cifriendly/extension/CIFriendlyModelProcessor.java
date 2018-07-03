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
import org.apache.maven.building.Source;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelProcessor;
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

    @Requirement
    private CIFriendlyConfiguration configurationProvider;

    @Requirement
    private CIFriendlySessionHolder sessionHolder;
    
    @Requirement		// @Component ???
    private MavenSession mavenSession; 

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

            Source source = (Source) options.get(ModelProcessor.SOURCE);
            if (source == null) {
                return model;
            }

            File location = new File(source.getLocation());
            if (!location.isFile()) {
                // their JavaDoc says Source.getLocation "could be a local file path, a URI or just an empty string."
                // if it doesn't resolve to a file then calling .getParentFile will throw an exception,
                // but if it doesn't resolve to a file then it isn't under getMultiModuleProjectDirectory,
                return model; // therefore the model shouldn't be modified.
            }

            CIFriendlySession ciFriendlySession = optSession.get();
            File relativePath = location.getParentFile().getCanonicalFile();
            File multiModuleDirectory = ciFriendlySession.getMultiModuleProjectDir();
            String calculatedVersion = ciFriendlySession.getComputedVersion();

            if (StringUtils.containsIgnoreCase(relativePath.getCanonicalPath(), multiModuleDirectory.getCanonicalPath())) {
                log.debug("handling version of project Model from " + location);

                ciFriendlySession.addProject(GAV.from(model.clone()));

                if (Objects.nonNull(model.getVersion())) {
                    // TODO evaluate how to set the version only when it was originally set in the pom file
                    model.setVersion(calculatedVersion);
                }

                if (Objects.nonNull(model.getParent())) {
                    // if the parent is part of the multi module project, let's update the parent version
                    String modelParentRelativePath = model.getParent().getRelativePath();
                    File relativePathParent = new File(
                            relativePath.getCanonicalPath() + File.separator + modelParentRelativePath)
                            .getParentFile().getCanonicalFile();
                    if (StringUtils.isNotBlank(modelParentRelativePath) 
                            && StringUtils.containsIgnoreCase(relativePathParent.getCanonicalPath(),
                            multiModuleDirectory.getCanonicalPath())) {
                        model.getParent().setVersion(calculatedVersion);
                    }
                }

                // we should only register the plugin once, on the main project
                // Registering the plugin is to ensure that
                if (relativePath.getCanonicalPath().equals(multiModuleDirectory.getCanonicalPath())) {
                    addAttachPomMojo(model);
                    // updateScmTag(ciFriendlySession.getCalculator(), model);
                }

                try {
                	String serializedSession = CIFriendlySession.serialize(ciFriendlySession);
					mavenSession.getUserProperties().put(CIFriendlyUtils.SESSION_MAVEN_PROPERTIES_KEY, serializedSession);
                } catch (Exception ex) {
                    throw new IOException("cannot serialize ciFriendlySession", ex);
                }
            } else {
                log.info("skipping Model from " + location);
            }
            
            // return the original model (but updated)
            return model;
        }
    }

    private void addAttachPomMojo(Model model) {
        if (Objects.isNull(model.getBuild())) {
            model.setBuild(new Build());
        }

        if (Objects.isNull(model.getBuild().getPlugins())) {
            model.getBuild().setPlugins(new ArrayList<>());
        }

        Optional<Plugin> pluginOptional = model.getBuild().getPlugins().stream()
                .filter(x -> CIFriendlyUtils.EXTENSION_GROUP_ID.equalsIgnoreCase(x.getGroupId())
                        && CIFriendlyUtils.EXTENSION_ARTIFACT_ID.equalsIgnoreCase(x.getArtifactId()))
                .findFirst();

        StringBuilder pluginVersion = new StringBuilder();

        try (InputStream inputStream = getClass()
                .getResourceAsStream("/META-INF/maven/" + CIFriendlyUtils.EXTENSION_GROUP_ID + "/"
                        + CIFriendlyUtils.EXTENSION_ARTIFACT_ID + "/pom" + ".properties")) {
            Properties properties = new Properties();
            properties.load(inputStream);
            pluginVersion.append(properties.getProperty("version"));
        } catch (IOException ignored) {
            // TODO we should not ignore in case we have to reuse it
            log.warn(ignored.getMessage(), ignored);
        }

        Plugin plugin = pluginOptional.orElseGet(() -> {
            Plugin plugin2 = new Plugin();
            plugin2.setGroupId(CIFriendlyUtils.EXTENSION_GROUP_ID);
            plugin2.setArtifactId(CIFriendlyUtils.EXTENSION_ARTIFACT_ID);
            plugin2.setVersion(pluginVersion.toString());

            model.getBuild().getPlugins().add(0, plugin2);
            return plugin2;
        });

        if (Objects.isNull(plugin.getExecutions())) {
            plugin.setExecutions(new ArrayList<>());
        }

        String pluginRunPhase = System.getProperty("jgitver.pom-replacement-phase", "prepare-package");
        Optional<PluginExecution> pluginExecutionOptional = plugin.getExecutions().stream()
                .filter(x -> pluginRunPhase.equalsIgnoreCase(x.getPhase())).findFirst();

        PluginExecution pluginExecution = pluginExecutionOptional.orElseGet(() -> {
            PluginExecution pluginExecution2 = new PluginExecution();
            pluginExecution2.setPhase(pluginRunPhase);

            plugin.getExecutions().add(pluginExecution2);
            return pluginExecution2;
        });

        if (Objects.isNull(pluginExecution.getGoals())) {
            pluginExecution.setGoals(new ArrayList<>());
        }

        if (!pluginExecution.getGoals().contains(CIFriendlyAttachModifiedPomsMojo.GOAL_ATTACH_MODIFIED_POMS)) {
            pluginExecution.getGoals().add(CIFriendlyAttachModifiedPomsMojo.GOAL_ATTACH_MODIFIED_POMS);
        }

        if (Objects.isNull(plugin.getDependencies())) {
            plugin.setDependencies(new ArrayList<>());
        }

        Optional<Dependency> dependencyOptional = plugin.getDependencies().stream()
                .filter(x -> CIFriendlyUtils.EXTENSION_GROUP_ID.equalsIgnoreCase(x.getGroupId())
                        && CIFriendlyUtils.EXTENSION_ARTIFACT_ID.equalsIgnoreCase(x.getArtifactId()))
                .findFirst();

        dependencyOptional.orElseGet(() -> {
            Dependency dependency = new Dependency();
            dependency.setGroupId(CIFriendlyUtils.EXTENSION_GROUP_ID);
            dependency.setArtifactId(CIFriendlyUtils.EXTENSION_ARTIFACT_ID);
            dependency.setVersion(pluginVersion.toString());

            plugin.getDependencies().add(dependency);
            return dependency;
        });
    }
}
