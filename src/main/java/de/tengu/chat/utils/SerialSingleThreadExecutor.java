package de.tengu.chat.utils;

import android.os.Looper;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import de.tengu.chat.services.AttachFileToConversationRunnable;

public class SerialSingleThreadExecutor implements Executor {

	final Executor executor = Executors.newSingleThreadExecutor();
	protected final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
	private Runnable active;

	public SerialSingleThreadExecutor() {
		this(false);
	}

	public SerialSingleThreadExecutor(boolean prepareLooper) {
		if (prepareLooper) {
			execute(new Runnable() {
				@Override
				public void run() {
					Looper.prepare();
				}
			});
		}
	}

	public synchronized void execute(final Runnable r) {
		tasks.offer(new Runnable() {
			public void run() {
				try {
					r.run();
				} finally {
					scheduleNext();
				}
			}
		});
		if (active == null) {
			scheduleNext();
		}
	}

	protected synchronized void scheduleNext() {
		if ((active =  tasks.poll()) != null) {
			executor.execute(active);
		}
	}
}