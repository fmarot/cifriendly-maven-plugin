package com.teamtter.maven.cifriendly;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
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
		requiresDependencyCollection = ResolutionScope.NONE,	//
		requiresDependencyResolution = ResolutionScope.NONE)
@Slf4j
public class CIFriendlyMojo extends AbstractMojo {

	private List<String> excludedDirs = Arrays.asList(".git", "target", "src", ".idea", ".settings");

	@Parameter(defaultValue = "${project}", readonly = true)
	@Setter
	private MavenProject	mavenProject;
	
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		
		final String version = mavenProject.getVersion();
		log.info("Maven project version read: {}", version);
		
		Consumer<Path> pomFileConsumer = path -> {
			try (Stream<String> lines = Files.lines(path)) {
				List<String> replaced = lines
						.map(line -> line.replace("<version>${revision}</version>", "<version>" + version + "</version>"))
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

	class ChangePomFileVisitor extends SimpleFileVisitor<Path> {

		private List<String>	excludedDirs;
		private Predicate<Path>	fileMatchesPredicate;
		private Consumer<Path>	matchingFilesConsumer;

		public ChangePomFileVisitor(List<String> excludedDirs, Predicate<Path> fileMatchesPredicate, Consumer<Path> matchingFilesConsumer) {
			this.excludedDirs = excludedDirs;
			this.fileMatchesPredicate = fileMatchesPredicate;
			this.matchingFilesConsumer = matchingFilesConsumer;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
			String fileName = dir.getFileName().toString();
			boolean excluded = excludedDirs.contains(fileName);
			if (excluded) {
				log.debug("Skipping dir " + dir);
				return FileVisitResult.SKIP_SUBTREE;
			} else {
				return FileVisitResult.CONTINUE;
			}
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws IOException {
			log.debug("Visiting {}", file);
			if (attr.isSymbolicLink()) {
				return FileVisitResult.CONTINUE;
			} else if (attr.isRegularFile()) {
				if (fileMatchesPredicate.test(file)) {
					matchingFilesConsumer.accept(file);
				}
				return FileVisitResult.CONTINUE;
			} else {
				return FileVisitResult.CONTINUE;
			}
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
			log.trace("Directory done: {}", dir);
			return FileVisitResult.CONTINUE;
		}
	}
}
