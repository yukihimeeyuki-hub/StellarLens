package com.stellarlens.stellarlens_camera;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.widget.FrameLayout;

import androidx.annotation.RequiresApi;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CameraX implementation used on API 21 and above.
 */
@SuppressLint("WrongConstant")
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
final class CameraXController implements CameraController {

    private static final String TAG = "StellarLens-CameraX";

    private final CameraView host;
    private final Context context;
    private final PreviewView previewView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ProcessCameraProvider cameraProvider;
    private Preview preview;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private Camera camera;
    private CameraControl cameraControl;
    private CameraInfo cameraInfo;
    private boolean released;
    private long recordingToken;

    CameraXController(CameraView host) {
        this.host = host;
        this.context = host.getContext();
        this.previewView = new PreviewView(context);
        // TextureView-based implementation is more reliable across the wide range of
        // devices we support; SurfaceView mode has known issues on some OEM ROMs.
        this.previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        this.previewView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        host.addView(previewView);
        previewView.addOnLayoutChangeListener((v, left, top, right, bottom,
                                               oldLeft, oldTop, oldRight, oldBottom) -> {
            int rotation = displayRotation();
            if (imageCapture != null) imageCapture.setTargetRotation(rotation);
            if (videoCapture != null) videoCapture.setTargetRotation(rotation);
        });
    }

    @Override
    public void start() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(context);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindUseCases();
            } catch (Exception e) {
                Log.e(TAG, "Failed to get CameraProvider", e);
                host.notifyError("Camera init failed: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindUseCases() {
        if (cameraProvider == null || released || host.lifecycleOwner == null) return;
        cameraProvider.unbindAll();

        preview = new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).build();

        ImageCapture.Builder imageBuilder = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY);
        applyPhotoResolution(imageBuilder);
        imageCapture = imageBuilder.build();
        imageCapture.setTargetRotation(displayRotation());

        Recorder recorder = new Recorder.Builder()
                .setAspectRatio(AspectRatio.RATIO_16_9)
                .setQualitySelector(QualitySelector.fromOrderedList(
                        Arrays.asList(Quality.FHD, Quality.HD, Quality.SD),
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .build();
        videoCapture = VideoCapture.withOutput(recorder);
        videoCapture.setTargetRotation(displayRotation());

        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(host.lensFacing)
                .build();

        try {
            camera = cameraProvider.bindToLifecycle(
                    host.lifecycleOwner, selector, preview, imageCapture, videoCapture);
        } catch (Exception e) {
            // Some low-end devices cannot run preview + image capture + video at once.
            Log.w(TAG, "Three use cases are not supported, retrying without video", e);
            videoCapture = null;
            try {
                camera = cameraProvider.bindToLifecycle(
                        host.lifecycleOwner, selector, preview, imageCapture);
            } catch (Exception inner) {
                Log.e(TAG, "Bind failed", inner);
                host.notifyError("Camera bind failed: " + inner.getMessage());
                return;
            }
        }

        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        cameraControl = camera.getCameraControl();
        cameraInfo = camera.getCameraInfo();

        float desiredZoom = host.zoom;
        ZoomState zoomState = cameraInfo.getZoomState().getValue();
        if (zoomState != null) {
            host.minZoom = zoomState.getMinZoomRatio();
            host.maxZoom = zoomState.getMaxZoomRatio();
        }
        desiredZoom = Math.max(host.minZoom, Math.min(host.maxZoom, desiredZoom));
        host.zoom = desiredZoom;
        if (cameraControl != null) {
            cameraControl.setZoomRatio(desiredZoom);
        }

        applyFlashMode();
        host.cameraReady = true;
        Log.d(TAG, "Camera ready, zoom: " + host.minZoom + " - " + host.maxZoom);
        host.notifyReady();
    }

    private void applyPhotoResolution(ImageCapture.Builder builder) {
        switch (host.photoQuality) {
            case 0: builder.setTargetResolution(new Size(1280, 720)); break;
            case 2: builder.setTargetResolution(new Size(3840, 2160)); break;
            default: builder.setTargetResolution(new Size(1920, 1080)); break;
        }
    }

    private int displayRotation() {
        Display display = previewView.getDisplay();
        return display != null ? display.getRotation() : Surface.ROTATION_0;
    }

    private void applyFlashMode() {
        if (imageCapture == null) return;
        if (cameraInfo != null && !cameraInfo.hasFlashUnit()) return;
        imageCapture.setFlashMode(host.flashMode);
    }

    @Override
    public void takePhoto() {
        if (!host.cameraReady || imageCapture == null) {
            host.notifyError("Camera not ready");
            return;
        }
        imageCapture.setTargetRotation(displayRotation());
        final File file = new File(host.outputDir, "SL_" + System.currentTimeMillis() + ".jpg");
        imageCapture.takePicture(
                new ImageCapture.OutputFileOptions.Builder(file).build(),
                executor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(ImageCapture.OutputFileResults results) {
                        host.notifyPhoto(file.getAbsolutePath());
                    }

                    @Override
                    public void onError(ImageCaptureException exc) {
                        file.delete();
                        host.notifyError("Photo failed: " + exc.getMessage());
                    }
                });
    }

    @Override
    public void startRecording() {
        if (!host.cameraReady || videoCapture == null) {
            host.notifyError("Recording not supported on this device");
            return;
        }
        if (host.recording) {
            Log.w(TAG, "Already recording");
            return;
        }
        final File file = new File(host.outputDir, "SL_" + System.currentTimeMillis() + ".mp4");
        try {
            videoCapture.setTargetRotation(displayRotation());
            PendingRecording pending = videoCapture.getOutput().prepareRecording(
                    context, new FileOutputOptions.Builder(file).build());
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                pending = pending.withAudioEnabled();
            }
            final long token = ++recordingToken;
            host.recording = true;
            recording = pending.start(ContextCompat.getMainExecutor(context), event -> {
                if (!(event instanceof VideoRecordEvent.Finalize)) return;
                if (token != recordingToken || released) return;
                VideoRecordEvent.Finalize finalize = (VideoRecordEvent.Finalize) event;
                host.recording = false;
                recording = null;
                if (finalize.hasError()) {
                    file.delete();
                    host.notifyError("Video error: " + finalize.getError());
                } else {
                    host.notifyVideo(file.getAbsolutePath());
                }
            });
        } catch (Exception e) {
            host.recording = false;
            recording = null;
            file.delete();
            Log.e(TAG, "Recording failed", e);
            host.notifyError("Recording failed: " + e.getMessage());
        }
    }

    @Override
    public void stopRecording() {
        host.recording = false;
        if (recording != null) {
            try {
                recording.stop();
            } catch (Exception e) {
                host.notifyError("Stop recording failed: " + e.getMessage());
            }
            recording = null;
        }
    }

    @Override
    public void setZoom(float ratio) {
        float value = Math.max(host.minZoom, Math.min(host.maxZoom, ratio));
        host.zoom = value;
        if (cameraControl != null) {
            cameraControl.setZoomRatio(value);
        }
    }

    @Override
    public void switchCamera() {
        if (host.recording) stopRecording();
        host.lensFacing = host.lensFacing == CameraView.LENS_FACING_BACK
                ? CameraView.LENS_FACING_FRONT
                : CameraView.LENS_FACING_BACK;
        bindUseCases();
    }

    @Override
    public void setFlashMode(int mode) {
        applyFlashMode();
    }

    @Override
    public void setPhotoQuality(int quality) {
        if (host.cameraReady) {
            bindUseCases();
        }
    }

    @Override
    public void setOutputDir(File dir) {
        // Output directory is read from the host on every capture.
    }

    @Override
    public boolean hasFlash() {
        return cameraInfo != null && cameraInfo.hasFlashUnit();
    }

    @Override
    public void release() {
        released = true;
        recordingToken++;
        if (recording != null) {
            try {
                recording.close();
            } catch (Exception ignored) {
            }
            recording = null;
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        host.cameraReady = false;
        host.recording = false;
        host.removeView(previewView);
        executor.shutdown();
    }
}
