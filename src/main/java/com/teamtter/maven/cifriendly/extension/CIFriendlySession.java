package com.teamtter.maven.cifriendly.extension;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedHashSet;
import java.util.Set;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.DefaultType;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.convert.AnnotationStrategy;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.Strategy;

import lombok.Getter;

/** The XML annotations are for for serializing our plugin session to be passed between the extension and the dynamically instantiated plugin */
@Root(name = "jgitver")
@Default(DefaultType.FIELD)
public class CIFriendlySession {

	@Getter
	@Element(name = "multiModuleProjectDir")
	private File	multiModuleProjectDir;

	@Getter
	@ElementList(name = "projects", entry = "gav")
	private Set<GAV> projects = new LinkedHashSet<>();
	//	private String	originalVersion;
	
	@Getter
	private String	computedVersion = "1.2.3-TODO-SNAPSHOT";

	public CIFriendlySession(File multiModuleProjectDir) {
		this.multiModuleProjectDir = multiModuleProjectDir;
	}

	public void addProject(GAV gav) {
		projects.add(gav);
	}
	
	/**
     * Serializes as a String the given configuration object.
     * @param session the object to serialize
     * @return a non null String representation of the given object serialized
     * @throws IOException if the serialized form cannot be written
     * @see CIFriendlySession#serializeFrom(String)
     */
    public static String serialize(CIFriendlySession session) throws Exception {
        Strategy strategy = new AnnotationStrategy();
        Serializer serializer = new Persister(strategy);
        StringWriter sw = new StringWriter();
        serializer.write(session, sw);
        return sw.toString();
    }

    /**
     * De-serializes the given string as a {@link CIFriendlySession}.
     * @param serializedSession the string to de-serialize
     * @return a non null configuration object
     * @throws Exception if the given string could not be interpreted by simplexml
     */
    public static CIFriendlySession deserializeFrom(String serializedSession) throws Exception {
        Strategy strategy = new AnnotationStrategy();
        Serializer serializer = new Persister(strategy);
        return serializer.read(CIFriendlySession.class, serializedSession);
    }

}
