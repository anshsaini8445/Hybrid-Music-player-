package com.hybridmusic.player;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class PocketShakeManager implements SensorEventListener {

    public interface OnDoubleShakeListener {
        void onDoubleShakeDetected();
    }

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Sensor proximitySensor;
    private final Vibrator vibrator;
    private final OnDoubleShakeListener listener;

    // Default OFF when app opens so user is never annoyed
    private boolean isEnabled = false;
    private boolean isPocketOrCovered = false;

    private long firstShakeTimestamp = 0;
    private long lastTriggerTimestamp = 0;
    private int validShakeCount = 0;
    private int lastDirection = 0;

    public PocketShakeManager(Context context, OnDoubleShakeListener listener) {
        this.listener = listener;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
        this.proximitySensor = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY) : null;
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        this.validShakeCount = 0;
        this.lastDirection = 0;
        if (sensorManager == null) return;

        if (enabled) {
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
            if (proximitySensor != null) {
                sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        } else {
            sensorManager.unregisterListener(this);
        }
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isEnabled || event == null) return;

        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            float maxRange = proximitySensor != null ? proximitySensor.getMaximumRange() : 5.0f;
            isPocketOrCovered = event.values[0] < Math.min(5.0f, maxRange);
            return;
        }

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Pocket protection: block accidental shakes when inside pocket
            if (isPocketOrCovered) {
                validShakeCount = 0;
                return;
            }

            long now = System.currentTimeMillis();
            // 1.5s cooldown after a successful track change
            if (now - lastTriggerTimestamp < 1500) return;

            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            double gForce = Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;

            // Require deliberate firm horizontal hand shake
            if (gForce > 2.35 && Math.abs(x) > 11.5f) {
                int currentDir = x > 0 ? 1 : -1;
                if (validShakeCount == 0) {
                    validShakeCount = 1;
                    lastDirection = currentDir;
                    firstShakeTimestamp = now;
                } else if (now - firstShakeTimestamp <= 750 && currentDir != lastDirection) {
                    validShakeCount++;
                    lastDirection = currentDir;
                    if (validShakeCount >= 2) {
                        validShakeCount = 0;
                        lastTriggerTimestamp = now;
                        triggerVibrationAndSkip();
                    }
                } else if (now - firstShakeTimestamp > 750) {
                    validShakeCount = 1;
                    lastDirection = currentDir;
                    firstShakeTimestamp = now;
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void triggerVibrationAndSkip() {
        if (vibrator != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(65, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(65);
                }
            } catch (Exception ignored) {
            }
        }
        if (listener != null) {
            listener.onDoubleShakeDetected();
        }
    }
}
