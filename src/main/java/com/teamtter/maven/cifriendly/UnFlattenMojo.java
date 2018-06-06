package com.teamtter.maven.cifriendly;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import io.fabric8.updatebot.support.DecentXmlHelper;
import lombok.extern.slf4j.Slf4j;;

@Mojo(name = "unflatten",					// the goal
		defaultPhase = LifecyclePhase.NONE,	//
		requiresProject = true,				//
		threadSafe = false,					//
		aggregator = true,					// only execute on the parent pom, root of the maven tree (not on children)
		requiresDependencyCollection = ResolutionScope.NONE,	//
		requiresDependencyResolution = ResolutionScope.NONE)
@Slf4j
public class UnFlattenMojo extends AbstractMojo {

	@Parameter(property = "rootDir", defaultValue = "${session.executionRootDirectory}")
	private File	rootDir;

	@Parameter(property = "ignoreErrors", defaultValue = "false")
	private boolean	ignoreErrors;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		Path rootPath = rootDir.toPath();
		unflatten(rootPath, ignoreErrors);
	}

	protected static void unflatten(Path rootPath, boolean ignoreErrors) throws MojoFailureException {
		Path rootPom = Paths.get(rootPath.toString(), CIFriendlyUtils.POM_XML);

		String parentPomVersion;
		try {
			parentPomVersion = readVersionInParentPom(rootPath);
		} catch (IOException e) {
			throw new MojoFailureException("", e);
		}

		Predicate<Path> isPomFile = path -> path.getFileName().toString().equals(CIFriendlyUtils.POM_XML);
		PathConsumer pomFileUpdater = buildPomFileUpdater(rootPom, parentPomVersion, ignoreErrors);
		ChangePomFileVisitor pomVisitor = new ChangePomFileVisitor(CIFriendlyUtils.EXCLUDED_DIR_NAMES, isPomFile, pomFileUpdater);

		try {
			Files.walkFileTree(rootPath, pomVisitor);
		} catch (IOException e) {
			throw new MojoFailureException("", e);
		}
	}

	/** build the consumer that will receive pom files to update them */
	private static PathConsumer buildPomFileUpdater(Path rootPom, String parentPomVersion, boolean ignoreErrors) {
		return path -> {
			// Prepare XML parser & model
			XMLParser parser = new XMLParser();
			final String visitorContent = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			final Document doc = parser.parse(new XMLStringSource(visitorContent));

			// update the DOM
			replaceVersionwithRevision(doc, parentPomVersion, path, ignoreErrors);
			specificUpdateForRootPom(doc, parentPomVersion, path, rootPom);

			// write back the result
			Files.write(path, doc.toString().getBytes());
		};
	}

	/** add (or change the value) of the properties/revision node in parent pom with 'parentPomVersion' */
	private static void specificUpdateForRootPom(Document doc, String parentPomVersion, Path path, Path rootPom) throws IOException {
		if (Files.isSameFile(path, rootPom)) {
			log.info("parent pom found: {}", path);
			if (parentPomVersion.equals(CIFriendlyUtils.REVISION)) {
				log.info("Parent pom already unflattened => will not update it's node /project/properties/revision");
			} else {
				Element revisionPropertyNode = doc.getChild("/project/properties/revision");
				if (revisionPropertyNode == null) {

					Element propertiesNode = doc.getChild("/project/properties/");
					if (propertiesNode == null) {
						DecentXmlHelper.addChildElement(doc.getChild("/project"), "properties");
						propertiesNode = doc.getChild("/project/properties/");
					}

					DecentXmlHelper.addChildElement(propertiesNode, "revision", parentPomVersion);
					revisionPropertyNode = doc.getChild("/project/properties/revision");
				}
				revisionPropertyNode.setText(parentPomVersion);
			}
		}
	}

	private static void replaceVersionwithRevision(Document doc, String parentPomVersion, Path path, boolean ignoreErrors) throws IOException {
		Element visitorVersionNode = doc.getChild("/project/version");
		if (visitorVersionNode == null) {
			visitorVersionNode = doc.getChild("/project/parent/version");
		}
		String text = visitorVersionNode.getText();
		if ((!text.equals(parentPomVersion)) && (ignoreErrors == false)) {
			log.error("Found version {} in {} instead of version {}", text, path, parentPomVersion);
			throw new IOException("Unexpected version in pom " + path);
		}
		visitorVersionNode.setText(CIFriendlyUtils.REVISION);
		log.info("Rewriting pom {}", path);
	}

	private static String readVersionInParentPom(Path rootDir) throws IOException {
		XMLParser parser = new XMLParser();
		final String content = new String(Files.readAllBytes(Paths.get(rootDir.toString(), CIFriendlyUtils.POM_XML)), StandardCharsets.UTF_8);
		final Document doc = parser.parse(new XMLStringSource(content));
		final Element versionNode = doc.getChild("/project/version");
		final String version = versionNode.getText();
		log.info("version used to unflatten: {}", version);
		return version;
	}

}
