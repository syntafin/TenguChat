package de.tengu.chat.xmpp;

import de.tengu.chat.entities.Account;

public interface OnStatusChanged {
	public void onStatusChanged(Account account);
}
