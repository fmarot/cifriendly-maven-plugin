package com.teamtter.maven.cifriendly;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
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
public class FlattenMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true)
	@Setter
	private MavenProject	mavenProject;

	@Parameter(property = "rootDir", defaultValue = "${session.executionRootDirectory}")
	private File			rootDir;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		Path rootPath = rootDir.toPath();
		final String version = mavenProject.getVersion();
		log.info("Maven project version read: {}", version);

		flatten(rootPath, version);
	}

	protected void flatten(Path rootPath, String version) throws MojoFailureException {
		String pattern = "<version>" + CIFriendlyUtils.REVISION + "</version>";
		String replacement = "<version>" + version + "</version>";

		PathConsumer pomFileConsumer = new PathConsumer() {

			@Override
			public void accept(Path path) throws IOException {
				List<Charset> charsetStrategies = Arrays.asList(StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1, StandardCharsets.US_ASCII);

				Iterator<Charset> it = charsetStrategies.iterator();
				while (it.hasNext()) {
					Charset charset = it.next();
					try {
						accept(path, charset);
						// if no Exception was thrown, we can quit the loop
						break;
					} catch (UncheckedIOException e) {
						log.info("Exception {} for {} when trying Charset {}... Will try next charset. You should consider encoding your pom in UTF8 ;)", e, path, charset);
						if (!it.hasNext()) {
							throw new IOException("No Charset found able to decode file " + path, e);
						}
					}
				}
			}

			private void accept(Path path, Charset charset) throws IOException {
				try (Stream<String> lines = Files.lines(path, charset)) {
					List<String> replaced = lines
							.peek(line -> {
								if (line.contains(pattern)) {
									log.info("Will replace pattern {} with {} in {}", pattern, replacement, path);
								}
							})
							.map(line -> line.replace(pattern, replacement))
							.collect(Collectors.toList());
					Files.write(path, replaced);
				}
			}
		};

		Predicate<Path> isPomFile = path -> path.getFileName().toString().equals(CIFriendlyUtils.POM_XML);

		DirectoryVisitor pomVisitor = new DirectoryVisitor(CIFriendlyUtils.EXCLUDED_DIR_NAMES, isPomFile, pomFileConsumer);
		try {
			Files.walkFileTree(rootPath, pomVisitor);
		} catch (IOException e) {
			throw new MojoFailureException("", e);
		}
	}

	/*
	try {
		XMLParser parser = new XMLParser();
		String pomContent;
		Path pomPath = rootPath.resolve("pom.xml");
		pomContent = new String(Files.readAllBytes(pomPath), StandardCharsets.UTF_8);
		final Document doc = parser.parse(new XMLStringSource(pomContent));
	
		Element pomVersionNode = doc.getChild("/project/version");
		if (pomVersionNode == null) {
			pomVersionNode = doc.getChild("/project/parent/version");
		}
		String pomVersion = pomVersionNode.getText();
		
		Element pomRevisionNode = doc.getChild("/project/properties/revision");
		if (pomRevisionNode == null) {
			throw new MojoFailureException("no revision property in "+ pomPath +" => unable to flatten");
		}
		
		String revisionVersion = pomRevisionNode.getText(); 
		
	
		PathConsumer pomFileConsumer = path -> {
			XMLParser visitorParser = new XMLParser();
			String visitorPomContent;
			Path visitorPomPath = rootPath.resolve("pom.xml");
			
			TODO BLABLABLA
		};
	
		Predicate<Path> isPomFile = path -> path.getFileName().toString().equals(CIFriendlyUtils.POM_XML);
	
		DirectoryVisitor pomVisitor = new DirectoryVisitor(CIFriendlyUtils.EXCLUDED_DIR_NAMES, isPomFile, pomFileConsumer);
	
		Files.walkFileTree(rootPath, pomVisitor);
	} catch (IOException e) {
		throw new MojoFailureException("", e);
	}
	 */

}
