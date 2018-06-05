package com.teamtter.maven.cifriendly;

import java.io.File;
import de.pdark.decentxml.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

@Mojo(name = "unflatten",						// the goal
		defaultPhase = LifecyclePhase.NONE,	//
		requiresProject = true,				//
		threadSafe = false,					//
		aggregator = true,					// only execute on the parent pom, root of the maven tree (not on children)
		requiresDependencyCollection = ResolutionScope.NONE,	//
		requiresDependencyResolution = ResolutionScope.NONE)
@Slf4j
public class UnFlatten extends AbstractMojo {

	private List<String>	excludedDirs	= Arrays.asList(".git", "target", "src", ".idea", ".settings");

	@Parameter(defaultValue = "${project}", readonly = true)
	@Setter
	private MavenProject	mavenProject;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		
		try {
			// read the current version in parent pom
			XMLParser parser = new XMLParser();
	        String content = new String (Files.readAllBytes(Paths.get("pom.xml")), StandardCharsets.UTF_8);
			Document doc = parser.parse (new XMLStringSource (content));
			Element versionNode = doc.getChild("/project/version");
			String version = versionNode.getText();
			log.info("version used to unflatten: {}", version);
			
			// replace version with ${revision} in all pom recursively
			PathConsumer pomFileConsumer = path -> {
				String visitorContent = new String (Files.readAllBytes(path), StandardCharsets.UTF_8);
				Document visitorDoc = parser.parse (new XMLStringSource (visitorContent));
				Element visitorVersionNode = visitorDoc.getChild("/project/version");
				if (! visitorVersionNode.getText().equals(version)) {
					log.error("Found version {} in {} instead of ${revision}", visitorVersionNode, path);
					throw new IOException("Unexpected version in pom " + path);
				}
				versionNode.setText("${revision}");
				
				Files.write(path, visitorDoc.toString().getBytes());
			};
			
			Predicate<Path> isPomFile = path -> path.getFileName().toString().equals("pom.xml");
			
			ChangePomFileVisitor pomVisitor = new ChangePomFileVisitor(excludedDirs, isPomFile, pomFileConsumer);
			Files.walkFileTree(new File(".").toPath(), pomVisitor);
		} catch (IOException e) {
			throw new MojoFailureException("", e);
		}
		
		
		// add (or change the value) of the properties/revision node in parent pom with value red in 1

		
	}
	
	public static void main(String[] args) throws IOException {
        XMLParser parser = new XMLParser();
        String content = new String (Files.readAllBytes(Paths.get("../test-fake-module/pom.xml")), StandardCharsets.UTF_8);
		Document doc = parser.parse (new XMLStringSource (content));
		
		List<Element> children = doc.getRootElement().getChildren();
		Element child = doc.getChild("/project/properties/revision");
		
		children.forEach(child2 -> System.out.println(child2.getChildPath()));
		
		System.out.println(child.getText().toString());
	}

}
