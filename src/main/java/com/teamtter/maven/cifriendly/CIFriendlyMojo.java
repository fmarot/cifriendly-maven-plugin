package com.teamtter.maven.cifriendly;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;;

@Mojo(name = "flatten",						// the goal
		defaultPhase = LifecyclePhase.NONE,	//
		requiresProject = true,				//
		threadSafe = false,					//
		aggregator = true,					// only execute on the parent pom, root of the maven tree (not on children)
		requiresDependencyCollection = ResolutionScope.NONE,	//
		requiresDependencyResolution = ResolutionScope.NONE)
@Slf4j
public class CIFriendlyMojo extends AbstractMojo {

	private List<String>	excludedDirs	= Arrays.asList(".git", "target", "src", ".idea", ".settings");

	@Parameter(defaultValue = "${project}", readonly = true)
	@Setter
	private MavenProject	mavenProject;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		final String version = mavenProject.getVersion();
		log.info("Maven project version read: {}", version);
		
		String pattern = "<version>${revision}</version>";
		String replacement = "<version>" + version + "</version>";
		
		Consumer<Path> pomFileConsumer = path -> {
			try (Stream<String> lines = Files.lines(path)) {
				List<String> replaced = lines
						.peek(line -> {
							if (line.contains(pattern)) {
								log.info("Will replace pattern {} with {} in {}", pattern, replacement, path);
							}	
						})
						.map(line -> line.replace(pattern, replacement))
						.collect(Collectors.toList());
				Files.write(path, replaced);
			} catch (IOException e) {
				throw new RuntimeException("Unable to modify file " + path);
			}
		};

		Predicate<Path> isPomFile = path -> path.getFileName().toString().equals("pom.xml");

		ChangePomFileVisitor pomVisitor = new ChangePomFileVisitor(excludedDirs, isPomFile, pomFileConsumer);
		try {
			Files.walkFileTree(new File(".").toPath(), pomVisitor);
		} catch (IOException e) {
			throw new MojoFailureException("", e);
		}
	}

}
