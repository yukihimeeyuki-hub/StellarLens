package com.stellarlens.stellarlens_camera;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import java.io.File;

/**
 * Camera preview / capture view with a single public API on every supported Android version.
 *
 * <p>API 21+ is backed by CameraX; API 19-20 falls back to the legacy
 * {@link android.hardware.Camera} API. Callbacks are always delivered on the main thread.</p>
 */
public class CameraView extends FrameLayout {

    private static final String TAG = "StellarLens-CameraView";

    public static final int LENS_FACING_FRONT = 0;
    public static final int LENS_FACING_BACK = 1;

    public static final int FLASH_MODE_AUTO = 0;
    public static final int FLASH_MODE_ON = 1;
    public static final int FLASH_MODE_OFF = 2;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    LifecycleOwner lifecycleOwner;
    CameraController controller;

    int lensFacing = LENS_FACING_BACK;
    int flashMode = FLASH_MODE_AUTO;
    int photoQuality = 1;
    File outputDir;
    float zoom = 1f;
    float minZoom = 1f;
    float maxZoom = 1f;
    boolean cameraReady;
    boolean recording;

    private float lastPinchDistance = -1f;

    private OnCameraReadyListener onCameraReadyListener;
    private OnPhotoTakenListener onPhotoTakenListener;
    private OnVideoSavedListener onVideoSavedListener;
    private OnErrorListener onErrorListener;

    public interface OnCameraReadyListener { void onCameraReady(); }
    public interface OnPhotoTakenListener { void onPhotoTaken(String filePath); }
    public interface OnVideoSavedListener { void onVideoSaved(String filePath); }
    public interface OnErrorListener { void onError(String message); }

    public CameraView(@NonNull Context context) { super(context); init(); }
    public CameraView(@NonNull Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public CameraView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        outputDir = getContext().getCacheDir();
    }

    public void setLifecycleOwner(@NonNull LifecycleOwner owner) {
        this.lifecycleOwner = owner;
    }

    public void startCamera() {
        if (controller != null) {
            controller.release();
            controller = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (lifecycleOwner == null) {
                notifyError("LifecycleOwner not set");
                return;
            }
            try {
                controller = new CameraXController(this);
            } catch (Throwable t) {
                notifyError("CameraX init failed: " + t.getMessage());
                return;
            }
        } else {
            controller = new LegacyCameraController(this);
        }
        controller.start();
    }

    public void takePhoto() {
        if (controller == null) {
            notifyError("Camera not initialized");
            return;
        }
        controller.takePhoto();
    }

    public void startRecording() {
        if (controller == null) {
            notifyError("Camera not initialized");
            return;
        }
        controller.startRecording();
    }

    public void stopRecording() {
        if (controller != null) controller.stopRecording();
        recording = false;
    }

    public boolean isRecording() {
        return recording;
    }

    public void setZoom(float ratio) {
        if (controller != null) {
            controller.setZoom(ratio);
        } else {
            zoom = Math.max(1f, ratio);
        }
    }

    public float getZoom() { return zoom; }
    public float getMinZoom() { return minZoom; }
    public float getMaxZoom() { return maxZoom; }

    public void switchCamera() {
        if (controller != null) {
            controller.switchCamera();
        } else {
            lensFacing = lensFacing == LENS_FACING_BACK
                    ? LENS_FACING_FRONT
                    : LENS_FACING_BACK;
        }
    }

    public int getLensFacing() { return lensFacing; }

    public void setFlashMode(int mode) {
        if (mode < FLASH_MODE_AUTO || mode > FLASH_MODE_OFF) return;
        flashMode = mode;
        if (controller != null) controller.setFlashMode(mode);
    }

    public int getFlashMode() { return flashMode; }

    public void setFlashModeString(String mode) {
        if (mode == null) return;
        switch (mode.toLowerCase()) {
            case "on": setFlashMode(FLASH_MODE_ON); break;
            case "off": setFlashMode(FLASH_MODE_OFF); break;
            default: setFlashMode(FLASH_MODE_AUTO); break;
        }
    }

    public String getFlashModeString() {
        switch (flashMode) {
            case FLASH_MODE_ON: return "on";
            case FLASH_MODE_OFF: return "off";
            default: return "auto";
        }
    }

    public void setPhotoQuality(int quality) {
        if (quality < 0 || quality > 2) return;
        photoQuality = quality;
        if (controller != null) controller.setPhotoQuality(quality);
    }

    public int getPhotoQuality() { return photoQuality; }

    public void setOutputDir(File dir) {
        if (dir == null) return;
        outputDir = dir;
        if (controller != null) controller.setOutputDir(dir);
    }

    public File getOutputDir() { return outputDir; }

    public boolean hasFlash() {
        return controller != null && controller.hasFlash();
    }

    public boolean isCameraReady() { return cameraReady; }

    public void setOnCameraReadyListener(OnCameraReadyListener l) { onCameraReadyListener = l; }
    public void setOnPhotoTakenListener(OnPhotoTakenListener l) { onPhotoTakenListener = l; }
    public void setOnVideoSavedListener(OnVideoSavedListener l) { onVideoSavedListener = l; }
    public void setOnErrorListener(OnErrorListener l) { onErrorListener = l; }

    public void releaseCamera() {
        if (controller != null) {
            controller.release();
            controller = null;
        }
        cameraReady = false;
        recording = false;
        minZoom = 1f;
        maxZoom = 1f;
    }

    void notifyReady() {
        mainHandler.post(() -> {
            if (onCameraReadyListener != null) onCameraReadyListener.onCameraReady();
        });
    }

    void notifyPhoto(String path) {
        mainHandler.post(() -> {
            if (onPhotoTakenListener != null) onPhotoTakenListener.onPhotoTaken(path);
        });
    }

    void notifyVideo(String path) {
        mainHandler.post(() -> {
            if (onVideoSavedListener != null) onVideoSavedListener.onVideoSaved(path);
        });
    }

    void notifyError(String message) {
        Log.e(TAG, message);
        mainHandler.post(() -> {
            if (onErrorListener != null) onErrorListener.onError(message);
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                lastPinchDistance = -1f;
            }
            // Consume the single-finger down event so that the following pinch moves are delivered.
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX(0) - event.getX(1);
                float dy = event.getY(0) - event.getY(1);
                float distance = (float) Math.hypot(dx, dy);
                if (lastPinchDistance > 0f && distance > 0f) {
                    setZoom(getZoom() * distance / lastPinchDistance);
                }
                lastPinchDistance = distance;
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                lastPinchDistance = -1f;
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        releaseCamera();
    }
}
