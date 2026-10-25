
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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

    private boolean isEnabled = false; // Default OFF as requested
    private boolean isNearFaceOrPocket = false; // Proximity protection

    private long lastShakeTime = 0;
    private int shakeCount = 0;
    private float lastX, lastY, lastZ;
    private boolean initialized = false;

    public PocketShakeManager(Context context, OnDoubleShakeListener listener) {
        this.listener = listener;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        if (enabled) {
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
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
        if (!isEnabled) return;

        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            // If distance < 5cm or near pocket/ear, lock accidental shakes
            isNearFaceOrPocket = event.values[0] < 5.0f;
            return;
        }

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Ignore accidental pocket movements if proximity is covered
            if (isNearFaceOrPocket) return;

            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            if (!initialized) {
                lastX = x;
                lastY = y;
                lastZ = z;
                initialized = true;
                return;
            }

            float deltaX = Math.abs(x - lastX);
            float deltaY = Math.abs(y - lastY);
            float deltaZ = Math.abs(z - lastZ);

            lastX = x;
            lastY = y;
            lastZ = z;

            // Strict threshold for intentional hand shaking
            if ((deltaX + deltaY + deltaZ) > 13.5f) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastShakeTime < 900) {
                    shakeCount++;
                    if (shakeCount >= 2) {
                        // Strict Double-Shake Triggered!
                        shakeCount = 0;
                        lastShakeTime = 0;
                        triggerFeedbackAndNext();
                    }
                } else {
                    shakeCount = 1;
                    lastShakeTime = currentTime;
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void triggerFeedbackAndNext() {
        if (vibrator != null) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(70);
                }
            } catch (Exception ignored) {
            }
        }
        if (listener != null) {
            listener.onDoubleShakeDetected();
        }
    }
}
