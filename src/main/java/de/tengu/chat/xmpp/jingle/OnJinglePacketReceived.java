package de.tengu.chat.xmpp.jingle;

import de.tengu.chat.entities.Account;
import de.tengu.chat.xmpp.PacketReceived;
import de.tengu.chat.xmpp.jingle.stanzas.JinglePacket;

public interface OnJinglePacketReceived extends PacketReceived {
	void onJinglePacketReceived(Account account, JinglePacket packet);
}
