package com.teamtter.maven.cifriendly.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VersionComputer {

	private static final String SNAPSHOT = "-SNAPSHOT";

	public static String computeNewVersion(String currentVersion, String branch) {
		log.info("Computing version for {} - {}", currentVersion, branch);
		String newVersion = "";
		boolean wasSnapshot = false;
		if (currentVersion.contains(SNAPSHOT)) {
			currentVersion = currentVersion.replace(SNAPSHOT, "");
			wasSnapshot = true;
		}

		if (branch.equals(currentVersion)) {
			log.debug("OK, branch {} equals version {} so pom won't be changed (ignoring -SNAPSHOT presence)");
		} else {
			Pattern p = Pattern.compile("((\\d+\\.)*)\\d+");
			Matcher m = p.matcher(currentVersion);
			m.find();
			newVersion = m.group();
			newVersion = newVersion + "-" + branch;
		}

		if (wasSnapshot) {
			newVersion += SNAPSHOT;
		}
		return newVersion;
	}
}
