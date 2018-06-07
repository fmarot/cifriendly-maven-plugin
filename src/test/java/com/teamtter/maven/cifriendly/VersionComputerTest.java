package com.teamtter.maven.cifriendly;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;

public class VersionComputerTest {
	
	@Test
	public void testVersionComputerNoSNAPSHOT() {
		String branchName = "titi";
		Map<String, String> map = new HashMap<String, String>() {{
			put("1.0", "1.0");
			put("1.2.333333.44.5555555-666666", "1.2.333333.44.5555555");
			put("1", "1");
			put("1_99", "1");
		}};
		
		for (Entry<String, String> entry : map.entrySet()) {
			assertEquals(entry.getValue() + "-" + branchName, VersionComputer.computeNewVersion(entry.getKey(), branchName));
		}
	}

	@Test
	public void testVersionComputerWithSNAPSHOT() {
		String branchName = "titi";
		Map<String, String> map = new HashMap<String, String>() {{
			put("1.0-SNAPSHOT", "1.0-titi-SNAPSHOT");
			put("1.2.333333.44.5555555-666666-SNAPSHOT", "1.2.333333.44.5555555-titi-SNAPSHOT");
			put("1-SNAPSHOT", "1-titi-SNAPSHOT");
			put("1_99-SNAPSHOT", "1-titi-SNAPSHOT");
		}};
		
		for (Entry<String, String> entry : map.entrySet()) {
			assertEquals(entry.getValue(), VersionComputer.computeNewVersion(entry.getKey(), branchName));
		}
	}
}
