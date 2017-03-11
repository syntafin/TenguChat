package de.tengu.chat.xmpp;

import de.tengu.chat.entities.Account;
import de.tengu.chat.xmpp.stanzas.MessagePacket;

public interface OnMessagePacketReceived extends PacketReceived {
	public void onMessagePacketReceived(Account account, MessagePacket packet);
}
