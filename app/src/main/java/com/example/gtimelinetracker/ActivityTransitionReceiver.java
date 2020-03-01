package com.example.gtimelinetracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.Objects;

public class ActivityTransitionReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "TRANSITION_RECOGNITION_ACTIVITY_RECEIVER";


    @Override
    public void onReceive(Context context, Intent intent) {
        if (ActivityTransitionResult.hasResult(intent)) {
            ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
            log("Transition");
            boolean stopTrackingPosition = false;
            int startActivityType = -1;
            for (ActivityTransitionEvent event : Objects.requireNonNull(result).getTransitionEvents()) {
                if (event.getTransitionType() != ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    continue;
                int activityType = event.getActivityType();
                log("   %s", ActivityRecognition.getActivityName(activityType));
                if (activityType == DetectedActivity.STILL) {
                    stopTrackingPosition = true;
                } else {
                    startActivityType = activityType;
                }
            }
            Intent trackerIntent = new Intent(context, TrackerService.class);
            if (startActivityType != -1) {
                log("Starting location tracker: %s", ActivityRecognition.getActivityName(startActivityType));
                trackerIntent.putExtra(TrackerService.IntentExtras.START_IMMEDIATELY, true);
                trackerIntent.putExtra(TrackerService.IntentExtras.ACTIVITY_TYPE, startActivityType);
                context.startForegroundService(trackerIntent);
            } else if (stopTrackingPosition) {
                log("Stopping location tracker");
                context.stopService(trackerIntent);
            }
        }
    }

    private static void log(String s, Object... args) {
        Log.d(LOG_TAG, String.format(s, args));
    }
}
