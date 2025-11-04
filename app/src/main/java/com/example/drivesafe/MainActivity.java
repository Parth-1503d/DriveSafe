package com.example.drivesafe;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // UI Elements
    private TextView speedTextView;
    private TextView alertTextView;
    private ConstraintLayout mainLayout;

    // Location Services
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    // Sound Alert
    private ToneGenerator toneGenerator;
    private boolean isAlertPlaying = false;

    // App Variables
    private int speedLimitKmh = 60; // Default speed limit

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        speedTextView = findViewById(R.id.speed_text);
        alertTextView = findViewById(R.id.alert_text);
        EditText speedLimitEditText = findViewById(R.id.speed_limit_input);
        mainLayout = findViewById(R.id.main_layout);

        // Set up the location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize the ToneGenerator for the buzzer sound
        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100); // 100 is the volume

        // Listen for changes in the speed limit input
        speedLimitEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    speedLimitKmh = Integer.parseInt(s.toString());
                } catch (NumberFormatException e) {
                    speedLimitKmh = 0;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // This is where we define what to do when we get a new location
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        // The location object has a speed in meters/second.
                        // We convert it to km/h.
                        // 1 m/s = 3.6 km/h
                        float speedMps = location.getSpeed();
                        int currentSpeedKmh = (int) (speedMps * 3.6);

                        updateSpeedUI(currentSpeedKmh);
                    }
                }
            }
        };

        // Check for location permissions
        checkForPermissions();
    }

    private void checkForPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, so we request it
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Permission has already been granted, start location updates
            startLocationUpdates();
        }
    }

    private void startLocationUpdates() {
        // This sets up how often we want to get location updates
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000) // Every 1 second
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(500) // At least 0.5 seconds between updates
                .setMaxUpdateDelayMillis(2000) // Not more than 2 seconds delay
                .build();

        // Check permission again before starting updates (Android requires this)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    @SuppressLint("SetTextI18n")
    private void updateSpeedUI(int currentSpeed) {
        speedTextView.setText(String.valueOf(currentSpeed));

        if (currentSpeed > speedLimitKmh) {
            // We are over the speed limit!
            mainLayout.setBackgroundColor(Color.parseColor("#FF6B6B")); // Red background
            alertTextView.setText("SLOW DOWN!");
            alertTextView.setTextColor(Color.WHITE);
            speedTextView.setTextColor(Color.WHITE);

            // Play buzzer sound only once when crossing the limit
            if (!isAlertPlaying) {
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 200); // Beep for 200ms
                isAlertPlaying = true;
            }
        } else {
            // We are driving safely.
            mainLayout.setBackgroundColor(Color.WHITE); // White background
            alertTextView.setText("All good!");
            alertTextView.setTextColor(Color.parseColor("#4CAF50")); // Green text
            speedTextView.setTextColor(Color.BLACK);

            // Reset the alert flag when speed is back to normal
            isAlertPlaying = false;
        }
    }

    // This method is called after the user responds to the permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted!
                startLocationUpdates();
            } else {
                // Permission was denied.
                Toast.makeText(this, "Location permission is required to measure speed.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop location updates when the app is not visible to save battery
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Restart location updates when the app becomes visible again
        if (fusedLocationClient != null) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Release the tone generator resources to avoid memory leaks
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }
}

