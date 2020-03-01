package com.example.gtimelinetracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

	private static final String LOG_TAG = "ACTIVITY";

	private static final int PERMISSIONS_LOCATION_REQUEST_CODE = 1;
	private static final int PERMISSIONS_ACTIVITY_RECOGNITION_REQUEST_CODE = 2;

	private TrackerService.TrackerBinder trackerBinder;
	private final TrackerServiceConnection trackerConnection = new TrackerServiceConnection();
	private boolean hasLocationPermission = false;
	private final Handler trackerMessageHandler = new TrackerMessageHandler(this);
	private final ActivityRecognition activityRecognition = ActivityRecognition.getInstance();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		requestLocationPermissions();
	}

	@Override
	protected void onDestroy() {
		log("Destroying activity");
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		log("Resuming activity");
		super.onResume();
		bindToTrackerService();
	}

	@Override
	protected void onPause() {
		log("Pausing activity");
		unbindService(trackerConnection);
		super.onPause();
	}

	public void handleAutomaticTrackingCheckboxClick(View v) {
		if (activityRecognition.isStarted()) {
			activityRecognition.stopTracking(this);
		} else {
			activityRecognition.startTracking(this);
		}
		updateAutomaticTrackingCheckbox();
	}

	public void handleClickStartTrackingButton(View v) {
		if (trackerBinder != null) {
			Button btn = (Button) v;
			btn.setEnabled(false);
			if (trackerBinder.isStarted()) {
				trackerBinder.stop();
			} else {
				trackerBinder.start();
			}
		}
	}

	private void bindToTrackerService() {
		Intent trackerIntent = createServiceIntent();
		startService(trackerIntent);
		bindService(trackerIntent, trackerConnection, 0);
	}

	private void updateAutomaticTrackingCheckbox() {
		CheckBox checkBox = findViewById(R.id.automaticTrackingCheckbox);
		checkBox.setChecked(activityRecognition.isStarted());
	}

	private void updateStartTrackingButton() {
		Button button = findViewById(R.id.startTrackingButton);
		if (!hasLocationPermission || trackerBinder == null) {
			button.setEnabled(false);
			button.setText(R.string.start_tracking_btn_label);
		} else {
			button.setEnabled(true);
			button.setText(trackerBinder.isStarted() ? R.string.stop_tracking_btn_label : R.string.start_tracking_btn_label);
		}
	}

	private void updateLocationText(Location location) {
		TextView text = findViewById(R.id.locationText);
		String locationAsString;
		if (location == null) {
			locationAsString = "";
		} else {
			locationAsString = String.format(Locale.getDefault(), "%s°, %s°",
					Location.convert(location.getLatitude(), Location.FORMAT_DEGREES),
					Location.convert(location.getLongitude(), Location.FORMAT_DEGREES));
		}
		text.setText(locationAsString);
	}

	@SuppressLint("ObsoleteSdkInt")
	private void requestLocationPermissions() {
		List<String> permissions = new ArrayList<>(3);
		permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
		if (Build.VERSION.SDK_INT >= 29) {
			permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
		} else if (Build.VERSION.SDK_INT >= 28) {
			permissions.add(Manifest.permission.FOREGROUND_SERVICE);
		}
		ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSIONS_LOCATION_REQUEST_CODE);
	}

	private void requestActivityRecognitionPermissions() {
		if (Build.VERSION.SDK_INT >= 29) {
			String[] permissions = {
					Manifest.permission.ACTIVITY_RECOGNITION, // requires API 29
					Manifest.permission.RECEIVE_BOOT_COMPLETED
			};
			ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_ACTIVITY_RECOGNITION_REQUEST_CODE);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
										   @NonNull String[] permissions, @NonNull int[] grantResults) {
		// If request is cancelled, the result arrays are empty.
		boolean hasPermission = grantResults.length > 0 && Arrays.binarySearch(grantResults, PackageManager.PERMISSION_DENIED) < 0;
		switch (requestCode) {
			case PERMISSIONS_LOCATION_REQUEST_CODE:
				if (hasPermission) {
					hasLocationPermission = true;
					requestActivityRecognitionPermissions();
					log("Permission to access location has been GRANTED");
				} else {
					log("Permission to access location has been DENIED");
				}
				break;
			case PERMISSIONS_ACTIVITY_RECOGNITION_REQUEST_CODE:
				if (hasPermission) {
					findViewById(R.id.automaticTrackingCheckbox).setEnabled(true);
					if (!activityRecognition.isStarted()) {
						activityRecognition.startTracking(this);
					}
					updateAutomaticTrackingCheckbox();
					log("Permission to activity recognition has been GRANTED");
				} else {
					log("Permission to activity recognition has been DENIED");
				}
				break;
		}
	}

	private Intent createServiceIntent() {
		return new Intent(MainActivity.this, TrackerService.class);
	}

	private static void log(String s, Object... args) {
		Log.d(LOG_TAG, String.format(s, args));
	}

	private class TrackerServiceConnection implements ServiceConnection {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			trackerBinder = (TrackerService.TrackerBinder) service;
			trackerBinder.setOutHandler(trackerMessageHandler);
			updateLocationText(trackerBinder.getLastLocation());
			updateStartTrackingButton();
			log("Tracker service is connected");
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			trackerBinder.setOutHandler(null);
			trackerBinder = null;
			updateLocationText(null);
			updateStartTrackingButton();
			if (!isActivityTransitionRunning()) {
				bindToTrackerService();
			}
			log("Tracker service is disconnected");
		}
	}

	private static class TrackerMessageHandler extends Handler {
		private final MainActivity activity;

		private TrackerMessageHandler(MainActivity activity) {
			this.activity = activity;
		}

		@Override
		public void handleMessage(@NonNull Message msg) {
			switch (msg.what) {
				case TrackerService.SentMessages.STATE_CHANGE:
					activity.updateStartTrackingButton();
					activity.updateLocationText(null);
					break;
				case TrackerService.SentMessages.LOCATION_CHANGE:
					activity.updateLocationText((Location) msg.obj);
					break;
			}
		}
	}
}
