package com.example.gtimelinetracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
	private static final String LOG_TAG = "BOOT_RECEIVER";

	@Override
	public void onReceive(Context context, Intent intent) {
		if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
			Log.d(LOG_TAG, "Starting activity recognition");
			ActivityRecognition.getInstance().startTracking(context);
		}
	}
}
