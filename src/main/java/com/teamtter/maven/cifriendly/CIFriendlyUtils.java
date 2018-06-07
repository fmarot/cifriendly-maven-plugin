package com.teamtter.maven.cifriendly;

import java.util.Arrays;
import java.util.List;

import de.pdark.decentxml.Document;
import de.pdark.decentxml.Element;
import io.fabric8.updatebot.support.DecentXmlHelper;

public class CIFriendlyUtils {
	public static final List<String>	EXCLUDED_DIR_NAMES	= Arrays.asList(".git", "target", "src", ".idea", ".settings");

	public static final String			REVISION			= "${revision}";

	public static final String			POM_XML				= "pom.xml";

	public static void setPropertiesRevisionText(Document doc, String version) {
		Element revisionPropertyNode = doc.getChild("/project/properties/revision");
		if (revisionPropertyNode == null) {

			Element propertiesNode = doc.getChild("/project/properties/");
			if (propertiesNode == null) {
				DecentXmlHelper.addChildElement(doc.getChild("/project"), "properties");
				propertiesNode = doc.getChild("/project/properties/");
			}

			DecentXmlHelper.addChildElement(propertiesNode, "revision", version);
			revisionPropertyNode = doc.getChild("/project/properties/revision");
		}
		revisionPropertyNode.setText(version);
	}
}
