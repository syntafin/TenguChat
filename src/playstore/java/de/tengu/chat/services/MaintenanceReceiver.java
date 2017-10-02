package de.tengu.chat.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.iid.InstanceID;

import java.io.IOException;

import de.tengu.chat.Config;

public class MaintenanceReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(Config.LOGTAG,"received intent in maintenance receiver");
		if ("de.tengu.chat.RENEW_INSTANCE_ID".equals(intent.getAction())) {
			renewInstanceToken(context);

		}
	}

	private void renewInstanceToken(final Context context) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				InstanceID instanceID = InstanceID.getInstance(context);
				try {
					instanceID.deleteInstanceID();
					Intent intent = new Intent(context, XmppConnectionService.class);
					intent.setAction(XmppConnectionService.ACTION_GCM_TOKEN_REFRESH);
					context.startService(intent);
				} catch (IOException e) {
					Log.d(Config.LOGTAG,"unable to renew instance token",e);
				}
			}
		}).start();

	}
}
