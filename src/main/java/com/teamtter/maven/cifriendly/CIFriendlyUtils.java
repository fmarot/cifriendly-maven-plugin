package com.teamtter.maven.cifriendly;

import java.util.Arrays;
import java.util.List;

public class CIFriendlyUtils {
	public static final List<String>	EXCLUDED_DIR_NAMES	= Arrays.asList(".git", "target", "src", ".idea", ".settings");
	
	public static final String REVISION = "${revision}" ;
	
	public static final String POM_XML = "pom.xml" ;
	
}
