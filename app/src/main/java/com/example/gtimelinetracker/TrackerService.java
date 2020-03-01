package com.example.gtimelinetracker;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.Executors;

public class TrackerService extends Service {
	interface SentMessages {
		int STATE_CHANGE = 1;
		int LOCATION_CHANGE = 2;
	}

	interface IntentExtras {
		String START_IMMEDIATELY = "immediate";
		String ACTIVITY_TYPE = "activity";
	}

	private static final String LOG_TAG = "TRACKER_SERVICE";
	private static final String NOTIFICATION_CHANNEL_ID = "TimelineTracker";
	private static final int NOTIFICATION_STARTED_ID = 1;
	private static final int DEFAULT_TRACKING_PERIOD = 1000;
	private static final int BICYCLE_TRACKING_PERIOD = 500;
	private static final int RUNNING_TRACKING_PERIOD = 500;

	private final TrackerBinder binder = new TrackerBinder();
	private Tracker tracker;
	// Handler used to send messages to activity
	private Handler outHandler;
	private Location lastLocation;

	class TrackerBinder extends Binder {
		Location getLastLocation() {
			return lastLocation;
		}

		boolean isStarted() {
			return tracker != null;
		}
		void start() {
			startTracking(DetectedActivity.UNKNOWN);
		}
		void stop() {
			stopTracking();
		}

		void setOutHandler(Handler handler) {
			TrackerService.this.outHandler = handler;
		}
	}

	public TrackerService() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		return this.binder;
	}

	@Override
	public void onCreate() {
		log("Creating service %S", this);
		super.onCreate();
	}


	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		boolean startImmediately = false;
		if (intent != null) {
			startImmediately = intent.getBooleanExtra(IntentExtras.START_IMMEDIATELY, false);
		}
		log("Starting service %s - immediate=%s", this, startImmediately);
		if (startImmediately) {
			startTracking(intent.getIntExtra(IntentExtras.ACTIVITY_TYPE, DetectedActivity.UNKNOWN));
		}
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		log("Destroying service %s", this);
		this.stopTracking();
		super.onDestroy();
	}

	private void startTracking(int activityType) {
		if (tracker != null) {
			if (tracker.activityType == activityType) return;
			tracker.stop();
		}
		tracker = new Tracker(activityType);
		Executors.newSingleThreadExecutor().execute(tracker);

		Notification notification = showStartNotification(activityType);
		startForeground(NOTIFICATION_STARTED_ID, notification);
	}

	private void stopTracking() {
		if (tracker != null) {
			tracker.stop();
			tracker = null;
		}
		stopForeground(true);
	}

	@SuppressLint("ObsoleteSdkInt")
	private Notification showStartNotification(int activityType) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel serviceChannel = new NotificationChannel(
					NOTIFICATION_CHANNEL_ID,
					getString(R.string.app_name),
					NotificationManager.IMPORTANCE_LOW
			);
			NotificationManager manager = getSystemService(NotificationManager.class);
			Objects.requireNonNull(manager).createNotificationChannel(serviceChannel);
		}

		Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
				.setSmallIcon(ActivityRecognition.getActivityIcon(activityType))
				.setContentText(getString(R.string.notification_active))
				.setPriority(NotificationCompat.PRIORITY_DEFAULT)
				.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0))
				.build();

		NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
		notificationManager.notify(NOTIFICATION_STARTED_ID, notification);
		return notification;
	}

	private static void log(String s, Object... args) {
		Log.d(LOG_TAG, String.format(s, args));
	}

	private class Tracker implements Runnable {
		final int activityType;
		// Handler used to receive stop message
		private Handler stopHandler;

		private Tracker(int activityType) {
			this.activityType = activityType;
		}

		@Override
		public void run() {
			log("Starting looper thread - activity=%s", ActivityRecognition.getActivityName(activityType));
			Looper.prepare();

			FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(TrackerService.this);
			LocationCallback locationCallback = new TrackerLocationCallback();
			LocationRequest locationRequest = LocationRequest.create();
			locationRequest.setInterval(getUpdateInterval());
			locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
			locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());

			stopHandler = new StopTrackerHandler();

			sendStateChange();

			Looper.loop();

			locationClient.removeLocationUpdates(locationCallback);
			sendStateChange();

			log("Looper thread finished");
		}

		void stop() {
			if (stopHandler != null) {
				stopHandler.sendEmptyMessage(StopTrackerHandler.STOP_MESSAGE);
			}
		}

		private void sendStateChange() {
			if (outHandler != null) {
				outHandler.sendEmptyMessage(SentMessages.STATE_CHANGE);
			}
		}

		private int getUpdateInterval() {
			int interval;
			switch (activityType) {
				case DetectedActivity.ON_BICYCLE:
					interval = BICYCLE_TRACKING_PERIOD;
					break;
				case DetectedActivity.RUNNING:
					interval = RUNNING_TRACKING_PERIOD;
					break;
				default:
					interval = DEFAULT_TRACKING_PERIOD;
			}
			return interval;
		}
	}

	private static class StopTrackerHandler extends Handler {
		static int STOP_MESSAGE = 1;

		@Override
		public void handleMessage(@NonNull Message msg) {
			if (msg.what == STOP_MESSAGE) {
				Objects.requireNonNull(Looper.myLooper()).quitSafely();
			}
		}
	}

	private class TrackerLocationCallback extends LocationCallback {
		public void onLocationResult(LocationResult result) {
			Location location = result.getLastLocation();
			lastLocation = location;
//			log("Received location result: %s", new Date(location.getTime()));
			if (outHandler != null) {
				Message msg = outHandler.obtainMessage(SentMessages.LOCATION_CHANGE);
				msg.obj = location;
				outHandler.sendMessage(msg);
			}
		}

		public void onLocationAvailability(LocationAvailability availability) {
			log("Received location availability: %s", availability);
		}
	}
}
