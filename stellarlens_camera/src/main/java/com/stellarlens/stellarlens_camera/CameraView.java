package com.stellarlens.stellarlens_camera;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.view.PreviewView;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraView extends FrameLayout {

    private static final String TAG = "StellarLens-CameraView";
    private static final float PINCH_ZOOM_SENSITIVITY = 0.002f;

    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private Preview preview;
    private ImageCapture imageCapture;
    private VideoCapture videoCapture;
    private Camera camera;
    private CameraInfo cameraInfo;
    private CameraControl cameraControl;
    private CameraSelector cameraSelector;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;

    private LifecycleOwner lifecycleOwner;
    private boolean isCameraReady = false;
    private boolean isRecording = false;

    private float currentZoom = 1.0f;
    private float maxZoom = 10.0f;
    private float minZoom = 1.0f;
    private float lastPinchDistance = -1f;

    private int flashMode = ImageCapture.FLASH_MODE_AUTO;
    private int photoQuality = 1;
    private File outputDir;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

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
        previewView = new PreviewView(getContext());
        addView(previewView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();
        outputDir = getContext().getCacheDir();
    }

    public void setLifecycleOwner(@NonNull LifecycleOwner owner) { this.lifecycleOwner = owner; }

    public void startCamera() {
        if (lifecycleOwner == null) { Log.e(TAG, "LifecycleOwner not set"); notifyError("LifecycleOwner not set"); return; }
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(getContext());
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Failed to get CameraProvider", e);
                notifyError("Camera init failed: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(getContext()));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;
        cameraProvider.unbindAll();

        preview = new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).build();

        ImageCapture.Builder icb = new ImageCapture.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY);
        switch (photoQuality) {
            case 0: icb.setTargetResolution(new android.util.Size(1280, 720)); break;
            case 1: icb.setTargetResolution(new android.util.Size(1920, 1080)); break;
            case 2: icb.setTargetResolution(new android.util.Size(3840, 2160)); break;
        }
        imageCapture = icb.build();

        videoCapture = new VideoCapture.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).build();

        try {
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture, videoCapture);
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            cameraControl = camera.getCameraControl();
            cameraInfo = camera.getCameraInfo();

            ZoomState zs = cameraInfo.getZoomState().getValue();
            if (zs != null) { minZoom = zs.getMinZoomRatio(); maxZoom = zs.getMaxZoomRatio(); currentZoom = zs.getZoomRatio(); }

            isCameraReady = true;
            Log.d(TAG, "Camera ready, zoom: " + minZoom + " - " + maxZoom);
            if (onCameraReadyListener != null) onCameraReadyListener.onCameraReady();
        } catch (Exception e) {
            Log.e(TAG, "Bind failed", e);
            notifyError("Camera bind failed: " + e.getMessage());
        }
    }

    public void takePhoto() {
        if (!isCameraReady || imageCapture == null) { notifyError("Camera not ready"); return; }
        File f = new File(outputDir, "SL_" + System.currentTimeMillis() + ".jpg");
        imageCapture.takePicture(new ImageCapture.OutputFileOptions.Builder(f).build(), executor, new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(@NonNull ImageCapture.OutputFileResults r) {
                String p = f.getAbsolutePath();
                Log.d(TAG, "Photo: " + p);
                if (onPhotoTakenListener != null) onPhotoTakenListener.onPhotoTaken(p);
            }
            @Override public void onError(@NonNull ImageCaptureException e) {
                Log.e(TAG, "Photo error", e);
                notifyError("Photo failed: " + e.getMessage());
            }
        });
    }

    public void startRecording() {
        if (!isCameraReady) { notifyError("Camera not ready"); return; }
        if (isRecording) { Log.w(TAG, "Already recording"); return; }
        File f = new File(outputDir, "SL_" + System.currentTimeMillis() + ".mp4");
        videoCapture.startRecording(new VideoCapture.OutputFileOptions.Builder(f).build(), executor, new VideoCapture.OnVideoSavedCallback() {
            @Override public void onVideoSaved(@NonNull VideoCapture.OutputFileResults r) {
                isRecording = false;
                String p = f.getAbsolutePath();
                Log.d(TAG, "Video: " + p);
                if (onVideoSavedListener != null) onVideoSavedListener.onVideoSaved(p);
            }
            @Override public void onError(int error, @NonNull String msg, @Nullable Throwable cause) {
                isRecording = false;
                Log.e(TAG, "Video error: " + msg, cause);
                notifyError("Video error: " + msg);
            }
        });
        isRecording = true;
    }

    public void stopRecording() { if (isRecording) { videoCapture.stopRecording(); isRecording = false; } }
    public boolean isRecording() { return isRecording; }

    public void setZoom(float ratio) {
        if (cameraControl == null) return;
        currentZoom = Math.max(minZoom, Math.min(maxZoom, ratio));
        cameraControl.setZoomRatio(currentZoom);
    }

    public float getZoom() { return currentZoom; }
    public float getMinZoom() { return minZoom; }
    public float getMaxZoom() { return maxZoom; }

    public void switchCamera() {
        lensFacing = (lensFacing == CameraSelector.LENS_FACING_BACK) ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();
        bindCameraUseCases();
    }

    public int getLensFacing() { return lensFacing; }

    public void setFlashMode(int mode) { this.flashMode = mode; if (imageCapture != null) imageCapture.setFlashMode(mode); }
    public int getFlashMode() { return flashMode; }

    public void setFlashModeString(String mode) {
        switch (mode.toLowerCase()) {
            case "on": setFlashMode(ImageCapture.FLASH_MODE_ON); break;
            case "off": setFlashMode(ImageCapture.FLASH_MODE_OFF); break;
            default: setFlashMode(ImageCapture.FLASH_MODE_AUTO); break;
        }
    }

    public String getFlashModeString() {
        switch (flashMode) {
            case ImageCapture.FLASH_MODE_ON: return "on";
            case ImageCapture.FLASH_MODE_OFF: return "off";
            default: return "auto";
        }
    }

    public void setPhotoQuality(int q) { if (q < 0 || q > 2) return; this.photoQuality = q; if (isCameraReady) bindCameraUseCases(); }
    public int getPhotoQuality() { return photoQuality; }
    public void setOutputDir(File dir) { this.outputDir = dir; }
    public File getOutputDir() { return outputDir; }

    public void setOnCameraReadyListener(OnCameraReadyListener l) { onCameraReadyListener = l; }
    public void setOnPhotoTakenListener(OnPhotoTakenListener l) { onPhotoTakenListener = l; }
    public void setOnVideoSavedListener(OnVideoSavedListener l) { onVideoSavedListener = l; }
    public void setOnErrorListener(OnErrorListener l) { onErrorListener = l; }

    private void notifyError(String msg) { Log.e(TAG, msg); if (onErrorListener != null) onErrorListener.onError(msg); }

    public boolean hasFlash() {
        if (cameraInfo == null) return false;
        Integer f = Camera2CameraInfo.from(cameraInfo).getCameraCharacteristic(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        return f != null && f != 0;
    }

    public void releaseCamera() { if (cameraProvider != null) cameraProvider.unbindAll(); isCameraReady = false; }

    @Override
    protected void onDetachedFromWindow() { super.onDetachedFromWindow(); releaseCamera(); executor.shutdown(); }
}
