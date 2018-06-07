package com.teamtter.maven.cifriendly;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;;

@Slf4j
class DirectoryVisitor extends SimpleFileVisitor<Path> {

	private List<String>	excludedDirs;
	private Predicate<Path>	fileMatchesPredicate;
	private PathConsumer	matchingFilesConsumer;

	public DirectoryVisitor(List<String> excludedDirs, Predicate<Path> fileMatchesPredicate, PathConsumer matchingFilesConsumer) {
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