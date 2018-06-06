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

import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import de.pdark.decentxml.XMLParser;
import de.pdark.decentxml.XMLStringSource;
import lombok.extern.slf4j.Slf4j;;

@Mojo(name = "unflatten",					// the goal
		defaultPhase = LifecyclePhase.NONE,	//
		requiresProject = true,				//
		threadSafe = false,					//
		aggregator = true,					// only execute on the parent pom, root of the maven tree (not on children)
		requiresDependencyCollection = ResolutionScope.NONE,	//
		requiresDependencyResolution = ResolutionScope.NONE)
@Slf4j
public class UnFlatten extends AbstractMojo {

	private static final String	REVISION_VARIABLE	= "${revision}";

	private static final String	POM_XML				= "pom.xml";

	private static List<String>	excludedDirs		= Arrays.asList(".git", "target", "src", ".idea", ".settings");

	@Parameter(property = "rootDir", defaultValue = "${session.executionRootDirectory}")
	private File				rootDir;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		Path rootPath = rootDir.toPath();
		unflatten(rootPath);
	}

	protected static void unflatten(Path rootPath) throws MojoFailureException {
		Path rootPom = Paths.get(rootPath.toString(), POM_XML);

		String parentPomVersion;
		try {
			parentPomVersion = readVersionInParentPom(rootPath);
		} catch (IOException e) {
			throw new MojoFailureException("", e);
		}

		PathConsumer pomFileConsumer = path -> {
			// Prepare XML parser & model
			XMLParser parser = new XMLParser();
			final String visitorContent = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			final Document doc = parser.parse(new XMLStringSource(visitorContent));

			// update the DOM
			replaceVersionwithRevision(doc, parentPomVersion, path);
			specificUpdateForRootPom(doc, parentPomVersion, path, rootPom);

			// write back the result
			Files.write(path, doc.toString().getBytes());
		};

		Predicate<Path> isPomFile = path -> path.getFileName().toString().equals(POM_XML);
		ChangePomFileVisitor pomVisitor = new ChangePomFileVisitor(excludedDirs, isPomFile, pomFileConsumer);

		try {
			Files.walkFileTree(rootPath, pomVisitor);
		} catch (IOException e) {
			throw new MojoFailureException("", e);
		}
	}

	/** add (or change the value) of the properties/revision node in parent pom with 'parentPomVersion' */
	private static void specificUpdateForRootPom(Document doc, String parentPomVersion, Path path, Path rootPom) throws IOException {
		if (Files.isSameFile(path, rootPom)) {
			log.info("parent pom found: {}", path);
			if (parentPomVersion.equals(REVISION_VARIABLE)) {
				log.info("Parent pom already unflattened => will not update it's node /project/properties/revision");
			} else {
				Element revisionPropertyNode = doc.getChild("/project/properties/revision");
				if (revisionPropertyNode == null) {
					throw new IOException("this pom has no properties/revision section so it was never CI friendly => can not unflatten...");
				}
				revisionPropertyNode.setText(parentPomVersion);
			}
		}
	}

	private static void replaceVersionwithRevision(Document doc, String parentPomVersion, Path path) throws IOException {
		Element visitorVersionNode = doc.getChild("/project/version");
		if (visitorVersionNode == null) {
			visitorVersionNode = doc.getChild("/project/parent/version");
		}
		String text = visitorVersionNode.getText();
		if (!text.equals(parentPomVersion)) {
			log.error("Found version {} in {} instead of version {}", text, path, parentPomVersion);
			throw new IOException("Unexpected version in pom " + path);
		}
		visitorVersionNode.setText(REVISION_VARIABLE);
		log.info("Rewriting pom {}", path);
	}

	private static String readVersionInParentPom(Path rootDir) throws IOException {
		XMLParser parser = new XMLParser();
		final String content = new String(Files.readAllBytes(Paths.get(rootDir.toString(), POM_XML)), StandardCharsets.UTF_8);
		final Document doc = parser.parse(new XMLStringSource(content));
		final Element versionNode = doc.getChild("/project/version");
		final String version = versionNode.getText();
		log.info("version used to unflatten: {}", version);
		return version;
	}

	public static void main(String[] args) throws IOException {
		XMLParser parser = new XMLParser();
		String content = new String(Files.readAllBytes(Paths.get("../test-fake-module/pom.xml")), StandardCharsets.UTF_8);
		Document doc = parser.parse(new XMLStringSource(content));

		List<Element> children = doc.getRootElement().getChildren();
		Element child = doc.getChild("/project/properties/revision");

		children.forEach(child2 -> System.out.println(child2.getChildPath()));

		System.out.println(child.getText().toString());
	}

}
