package de.tengu.chat.services;

import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import net.ypresto.androidtranscoder.MediaTranscoder;
import net.ypresto.androidtranscoder.format.MediaFormatStrategy;
import net.ypresto.androidtranscoder.format.MediaFormatStrategyPresets;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import de.tengu.chat.Config;
import de.tengu.chat.R;
import de.tengu.chat.crypto.PgpEngine;
import de.tengu.chat.entities.DownloadableFile;
import de.tengu.chat.entities.Message;
import de.tengu.chat.persistance.FileBackend;
import de.tengu.chat.ui.UiCallback;
import de.tengu.chat.utils.MimeUtils;

public class AttachFileToConversationRunnable implements Runnable, MediaTranscoder.Listener {

	private final XmppConnectionService mXmppConnectionService;
	private final Message message;
	private final Uri uri;
	private final UiCallback<Message> callback;
	private final boolean isVideoMessage;
	private int currentProgress = -1;

	public AttachFileToConversationRunnable(XmppConnectionService xmppConnectionService, Uri uri, Message message, UiCallback<Message> callback) {
		this.uri = uri;
		this.mXmppConnectionService = xmppConnectionService;
		this.message = message;
		this.callback = callback;
		final String mimeType = MimeUtils.guessMimeTypeFromUri(mXmppConnectionService, uri);
		this.isVideoMessage = (mimeType != null && mimeType.startsWith("video/") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2);
	}

	public boolean isVideoMessage() {
		return this.isVideoMessage;
	}

	private void processAsFile() {
		final String path = mXmppConnectionService.getFileBackend().getOriginalPath(uri);
		if (path != null) {
			message.setRelativeFilePath(path);
			mXmppConnectionService.getFileBackend().updateFileParams(message);
			if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
				mXmppConnectionService.getPgpEngine().encrypt(message, callback);
			} else {
				callback.success(message);
			}
		} else {
			try {
				mXmppConnectionService.getFileBackend().copyFileToPrivateStorage(message, uri);
				mXmppConnectionService.getFileBackend().updateFileParams(message);
				if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
					final PgpEngine pgpEngine = mXmppConnectionService.getPgpEngine();
					if (pgpEngine != null) {
						pgpEngine.encrypt(message, callback);
					} else if (callback != null) {
						callback.error(R.string.unable_to_connect_to_keychain, null);
					}
				} else {
					callback.success(message);
				}
			} catch (FileBackend.FileCopyException e) {
				callback.error(e.getResId(), message);
			}
		}
	}

	private void processAsVideo() throws FileNotFoundException {
		Log.d(Config.LOGTAG,"processing file as video");
		mXmppConnectionService.startForcingForegroundNotification();
		message.setRelativeFilePath(message.getUuid() + ".mp4");
		final DownloadableFile file = mXmppConnectionService.getFileBackend().getFile(message);
		final int runtime = mXmppConnectionService.getFileBackend().getMediaRuntime(uri);
		MediaFormatStrategy formatStrategy = runtime >= 8000 ? MediaFormatStrategyPresets.createExportPreset960x540Strategy() : MediaFormatStrategyPresets.createAndroid720pStrategy();
		file.getParentFile().mkdirs();
		ParcelFileDescriptor parcelFileDescriptor = mXmppConnectionService.getContentResolver().openFileDescriptor(uri, "r");
		FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
		Future<Void> future = MediaTranscoder.getInstance().transcodeVideo(fileDescriptor, file.getAbsolutePath(), formatStrategy, this);
		try {
			future.get();
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public void onTranscodeProgress(double progress) {
		final int p = (int) Math.round(progress * 100);
		if (p > currentProgress) {
			currentProgress = p;
			mXmppConnectionService.getNotificationService().updateFileAddingNotification(p,message);
		}
	}

	@Override
	public void onTranscodeCompleted() {
		mXmppConnectionService.stopForcingForegroundNotification();
		mXmppConnectionService.getFileBackend().updateFileParams(message);
		if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
			mXmppConnectionService.getPgpEngine().encrypt(message, callback);
		} else {
			callback.success(message);
		}
	}

	@Override
	public void onTranscodeCanceled() {
		mXmppConnectionService.stopForcingForegroundNotification();
		processAsFile();
	}

	@Override
	public void onTranscodeFailed(Exception e) {
		mXmppConnectionService.stopForcingForegroundNotification();
		Log.d(Config.LOGTAG,"video transcoding failed",e);
		processAsFile();
	}

	@Override
	public void run() {
		if (isVideoMessage) {
			try {
				processAsVideo();
			} catch (Throwable e) {
				processAsFile();
			}
		} else {
			processAsFile();
		}
	}

}
