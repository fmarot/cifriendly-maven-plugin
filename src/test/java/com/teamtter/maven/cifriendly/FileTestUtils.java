package com.teamtter.maven.cifriendly;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.io.FileUtils;

import com.teamtter.maven.cifriendly.utils.filesystem.DirectoryVisitor;
import com.teamtter.maven.cifriendly.utils.filesystem.PathConsumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileTestUtils {

	public static void assertDirectoriesAreEquals(Path testPath, Path referenceTestPath) throws IOException {
		List<String> excludedDirs = new ArrayList<>();
		Predicate<Path> fileMatchesPredicate = path -> true;
		// we have to visit the filesystem twice: all files from testPath must
		// exist and be = in referenceTestPath, but the reverse must also be true !
		// TODO: implement a cache in the visitor to avoid compare twice *****
		PathConsumer matchingFilesConsumer1 = new OneWayPathComparator(testPath, referenceTestPath);
		DirectoryVisitor dv1 = new DirectoryVisitor(excludedDirs, fileMatchesPredicate, matchingFilesConsumer1);
		Files.walkFileTree(testPath, dv1);
		PathConsumer matchingFilesConsumer2 = new OneWayPathComparator(referenceTestPath, testPath);	// same but reversed !
		DirectoryVisitor dv2 = new DirectoryVisitor(excludedDirs, fileMatchesPredicate, matchingFilesConsumer2);
		Files.walkFileTree(referenceTestPath, dv2);
	}
	
	/** called 'one-way because it needs a reverse pass to fullfill the comparison */
	static class OneWayPathComparator implements PathConsumer {

		private Path base;
		private Path compareTo;

		public OneWayPathComparator(Path base, Path compareTo) {
			this.base = base;
			this.compareTo = compareTo;
		}
		
		@Override
		public void accept(Path path) throws IOException {
			Path relativePath = base.relativize(path);
			Path toComparePath = compareTo.resolve(relativePath);
			compare2Path(path, toComparePath);
		}

		private void compare2Path(Path path, Path toComparePath) throws IOException {
			File file = path.toFile();
			if (file.isFile()) {
				log.info("Testing {} - {}", path, toComparePath);
				if (! toComparePath.toFile().exists()) {
					throw new IOException("File "+path+" has no match in "+ toComparePath);
				} else {
					assertTrue("The files differ ! " + path + " - " + toComparePath,
							FileUtils.contentEquals(file, toComparePath.toFile()));
				}
			} else {
				assertTrue(toComparePath + "directory does not exist to match " + path, toComparePath.toFile().isDirectory());
			}
		}
		
	}
}
