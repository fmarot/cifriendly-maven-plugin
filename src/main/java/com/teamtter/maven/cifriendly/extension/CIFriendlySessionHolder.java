package com.teamtter.maven.cifriendly.extension;

import java.util.Optional;

import org.codehaus.plexus.component.annotations.Component;

import lombok.Getter;

@Component(role = CIFriendlySessionHolder.class, instantiationStrategy = "singleton")
public class CIFriendlySessionHolder {
	
	@Getter
	private Optional<CIFriendlySession> session;
	
	public void setSession(CIFriendlySession session) {
		this.session = Optional.ofNullable(session); 
	}
	
}
