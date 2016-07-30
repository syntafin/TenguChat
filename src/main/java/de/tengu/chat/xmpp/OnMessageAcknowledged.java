package de.tengu.chat.xmpp;

import de.tengu.chat.entities.Account;

public interface OnMessageAcknowledged {
	public void onMessageAcknowledged(Account account, String id);
}
