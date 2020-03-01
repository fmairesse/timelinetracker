package com.example.gtimelinetracker;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ActivityRecognition {
	private static final String LOG_TAG = "TRANSITION_RECOGNITION";
	private static final int[] TRACKED_ACTIVITIES = {
			DetectedActivity.ON_BICYCLE,
			DetectedActivity.RUNNING,
			DetectedActivity.WALKING,
			DetectedActivity.ON_FOOT
	};

	private static final Map<Integer, String> DETECTED_ACTIVITIES_NAMES = new HashMap<>();
	private static final Map<Integer, Integer> DETECTED_ACTIVITIES_ICONS = new HashMap<>();
	static {
		DETECTED_ACTIVITIES_NAMES.put(DetectedActivity.STILL, "STILL");
		DETECTED_ACTIVITIES_NAMES.put(DetectedActivity.ON_BICYCLE, "ON_BICYCLE");
		DETECTED_ACTIVITIES_NAMES.put(DetectedActivity.ON_FOOT, "ON_FOOT");
		DETECTED_ACTIVITIES_NAMES.put(DetectedActivity.RUNNING, "RUNNING");
		DETECTED_ACTIVITIES_NAMES.put(DetectedActivity.WALKING, "WALKING");
		DETECTED_ACTIVITIES_NAMES.put(DetectedActivity.UNKNOWN, "UNKNOWN");
		DETECTED_ACTIVITIES_ICONS.put(DetectedActivity.ON_BICYCLE, R.drawable.ic_activity_bicycle);
		DETECTED_ACTIVITIES_ICONS.put(DetectedActivity.ON_FOOT, R.drawable.ic_activity_walk);
		DETECTED_ACTIVITIES_ICONS.put(DetectedActivity.RUNNING, R.drawable.ic_activity_run);
		DETECTED_ACTIVITIES_ICONS.put(DetectedActivity.WALKING, R.drawable.ic_activity_walk);
	}

	private static ActivityRecognition instance;

	static ActivityRecognition getInstance() {
		if (instance == null) {
			instance = new ActivityRecognition();
			log("Created new TransitionRecognition %s", instance);
		} else {
		    log("Getting TransitionRecognition %s", instance);
        }
		return instance;
	}

	static String getActivityName(int activityType) {
		String activityName = DETECTED_ACTIVITIES_NAMES.get(activityType);
		return activityName == null ? "UNKNOWN" : activityName;
	}
	static int getActivityIcon(int activityType) {
		Integer icon = DETECTED_ACTIVITIES_ICONS.get(activityType);
		return icon == null ? R.drawable.ic_activity_unknown : icon;
	}

	private PendingIntent pendingIntent;
	private boolean started = false;

	private ActivityRecognition() {
	}

	void startTracking(Context context) {
        if (!started) {
            started = true;
            log("Starting activity tracking");
            List<ActivityTransition> transitions = new ArrayList<>();

			transitions.add(
					new ActivityTransition.Builder()
							.setActivityType(DetectedActivity.STILL)
							.setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
							.build());
            for (int activityType : TRACKED_ACTIVITIES) {
				transitions.add(
						new ActivityTransition.Builder()
								.setActivityType(activityType)
								.setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
								.build());
			}

            ActivityTransitionRequest request = new ActivityTransitionRequest(transitions);

            ActivityRecognitionClient activityRecognitionClient = com.google.android.gms.location.ActivityRecognition.getClient(context);

            Intent intent = new Intent(context, ActivityTransitionReceiver.class);
            pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

            Task<Void> task = activityRecognitionClient.requestActivityTransitionUpdates(request, pendingIntent);
            task.addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    log("Activity recognition success");
                }
            });

            task.addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.e(LOG_TAG, "Activity recognition failure", e);
                }
            });
        }
    }

	void stopTracking(Context context) {
		if (started) {
		    started = false;
			log("Stopping activity tracking");
			com.google.android.gms.location.ActivityRecognition.getClient(context).removeActivityTransitionUpdates(pendingIntent)
					.addOnSuccessListener(new OnSuccessListener<Void>() {
						@Override
						public void onSuccess(Void aVoid) {
							pendingIntent.cancel();
							pendingIntent = null;
                            log("Stopped activity tracking");
						}
					})
					.addOnFailureListener(new OnFailureListener() {
						@Override
						public void onFailure(@NonNull Exception e) {
							Log.e(LOG_TAG, "Transitions could not be unregistered", e);
						}
					});
			context.stopService(new Intent(context, TrackerService.class));
		}
	}

	boolean isStarted() {
		return started;
	}

	private static void log(String s, Object... args) {
		Log.d(LOG_TAG, String.format(s, args));
	}
}
