package com.example.timelinetrigger;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.Executors;

public class TrackerService extends Service {
    private static final String LOG_TAG = "TRACKER_SERVICE";
    private boolean started = false;

    public class TrackerBinder extends Binder {
        public boolean isStarted() {
            return TrackerService.this.started;
        }

        public void start() {
            started = true;
            Executors.newSingleThreadExecutor().execute(new Tracker());
        }

        public void stop() {
            started = false;
        }
    }

    private TrackerBinder binder = new TrackerBinder();

    public TrackerService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return this.binder;
    }

    @Override
    public void onCreate() {
        log("Creating service");
        super.onCreate();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("Starting service");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        log("Destroying service");
        super.onDestroy();
    }

    private static void log(String s, Object... args) {
        Log.d(LOG_TAG, String.format(s, args));
    }

    private class Tracker implements Runnable {
        @Override
        public void run() {
            while (started) {
                try {
                    Thread.sleep(5000);
                    log("Getting location");
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
