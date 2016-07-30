package de.tengu.chat.xmpp.jingle;

import de.tengu.chat.entities.DownloadableFile;

public interface OnFileTransmissionStatusChanged {
	void onFileTransmitted(DownloadableFile file);

	void onFileTransferAborted();
}
