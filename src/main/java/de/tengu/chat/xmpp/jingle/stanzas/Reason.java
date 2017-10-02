package de.tengu.chat.xmpp.jingle.stanzas;

import de.tengu.chat.xml.Element;

public class Reason extends Element {
	private Reason(String name) {
		super(name);
	}

	public Reason() {
		super("reason");
	}
}
