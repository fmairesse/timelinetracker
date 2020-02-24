package com.example.timelinetrigger;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity implements ServiceConnection {

    private static String LOG_TAG = "MainActivity";

    private static final int PERMISSION_REQUEST_ACCESS_FINE_LOCATION = 1;

    private TrackerService.TrackerBinder trackerBinder;
    private boolean hasLocationPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        findViewById(R.id.startButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (trackerBinder.isStarted()) {
                    trackerBinder.stop();
                } else {
                    trackerBinder.start();
                }
                updateStartButton();
            }
        });

        // Permission is not granted
        // Should we show an explanation?
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            this.showPermissionRationale();
        } else {
            requestLocationPermission();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = new Intent(this, TrackerService.class);
        bindService(intent, this, Context.BIND_AUTO_CREATE);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        this.trackerBinder = (TrackerService.TrackerBinder) service;
        updateStartButton();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        this.trackerBinder = null;
        updateStartButton();
    }

    private void updateStartButton() {
        Button button = findViewById(R.id.startButton);
        if (trackerBinder == null && !hasLocationPermission) {
            button.setEnabled(false);
        } else {
            button.setEnabled(true);
            button.setText(this.trackerBinder.isStarted() ? R.string.stop_btn_label : R.string.start_btn_label);
        }
    }

    private void requestLocationPermission() {
        // No explanation needed; request the permission
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                PERMISSION_REQUEST_ACCESS_FINE_LOCATION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_ACCESS_FINE_LOCATION:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    hasLocationPermission = true;
                    updateStartButton();
                    log("Permission to access location has been GRANTED");
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    log("Permission to access location has been DENIED");
                }
                break;
            default:
                break;
        }
    }


    private void showPermissionRationale() {
        new AlertDialog.Builder(this).setCancelable(false)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage("Come on, grant the permission !")
                .setCancelable(true)
                .setNeutralButton("All right", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        requestLocationPermission();
                    }
                }).show();
    }

    private static void log(String s, Object... args) {
        Log.d(LOG_TAG, String.format(s, args));
    }

}
