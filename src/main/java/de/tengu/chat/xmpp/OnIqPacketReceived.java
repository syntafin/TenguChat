package de.tengu.chat.xmpp;

import de.tengu.chat.entities.Account;
import de.tengu.chat.xmpp.stanzas.IqPacket;

public interface OnIqPacketReceived extends PacketReceived {
	public void onIqPacketReceived(Account account, IqPacket packet);
}
