package com.teamtter.maven.cifriendly;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import de.pdark.decentxml.XMLParser;
import de.pdark.decentxml.XMLStringSource;
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
		
		String parentPomVersion;
		try {
			parentPomVersion = readVersionInParentPom();			
		} catch (IOException e) {
			throw new MojoFailureException("", e);
		}
			
		PathConsumer pomFileConsumer = path -> {
			XMLParser parser = new XMLParser();
			final String visitorContent = new String (Files.readAllBytes(path), StandardCharsets.UTF_8);
			final Document doc = parser.parse (new XMLStringSource (visitorContent));
			
			replaceVersionwithRevision(doc, parentPomVersion, path);
			
			specificUpdateForRootPom(parentPomVersion, path, doc);
			
			Files.write(path, doc.toString().getBytes());
		};
		
		Predicate<Path> isPomFile = path -> path.getFileName().toString().equals("pom.xml");
		ChangePomFileVisitor pomVisitor = new ChangePomFileVisitor(excludedDirs, isPomFile, pomFileConsumer);
			
		try {
			Files.walkFileTree(new File(".").toPath(), pomVisitor);
		} catch (IOException e) {
			throw new MojoFailureException("", e);
		}
	}

	private void specificUpdateForRootPom(String parentPomVersion, Path path, final Document doc) throws IOException {
		// add (or change the value) of the properties/revision node in parent pom with 'parentPomVersion'
		if (Files.isSameFile(path, Paths.get("pom.xml"))) {
			log.info("parent pom found");
			Element revisionPropertyNode = doc.getChild("/project/properties/revision");
			// TODO: handle the case where no revision node exist and create it
			revisionPropertyNode.setText(parentPomVersion);
		}
	}
	
	private void replaceVersionwithRevision(Document doc, String parentPomVersion, Path path) throws IOException {
		Element visitorVersionNode = doc.getChild("/project/version");
		if (visitorVersionNode == null) {
			visitorVersionNode = doc.getChild("/project/parent/version");
		}
		String text = visitorVersionNode.getText();
		if (! text.equals(parentPomVersion)) {
			log.error("Found version {} in {} instead of version {}", text, path, parentPomVersion);
			throw new IOException("Unexpected version in pom " + path);
		}
		visitorVersionNode.setText("${revision}");
		log.info("Rewriting pom {}", path);
	}

	private String readVersionInParentPom() throws IOException {
		XMLParser parser = new XMLParser();
		final String content = new String (Files.readAllBytes(Paths.get("pom.xml")), StandardCharsets.UTF_8);
        final Document doc = parser.parse (new XMLStringSource (content));
		final Element versionNode = doc.getChild("/project/version");
		final String version = versionNode.getText();
		log.info("version used to unflatten: {}", version);
		return version;
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
