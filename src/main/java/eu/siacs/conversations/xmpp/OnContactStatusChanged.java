package de.tengu.chat.xmpp;

import de.tengu.chat.entities.Contact;

public interface OnContactStatusChanged {
	public void onContactStatusChanged(final Contact contact, final boolean online);
}
