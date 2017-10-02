package de.tengu.chat.xmpp;

import de.tengu.chat.entities.Account;
import de.tengu.chat.xmpp.stanzas.PresencePacket;

public interface OnPresencePacketReceived extends PacketReceived {
	public void onPresencePacketReceived(Account account, PresencePacket packet);
}
