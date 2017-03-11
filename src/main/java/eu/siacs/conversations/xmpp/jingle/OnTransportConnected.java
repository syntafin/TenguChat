package de.tengu.chat.xmpp.jingle;

public interface OnTransportConnected {
	public void failed();

	public void established();
}
