package com.stellarlens.stellarlens_camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * Camera implementation for API 19-20, where CameraX is not available.
 */
@SuppressWarnings("deprecation")
final class LegacyCameraController implements CameraController {

    private static final String TAG = "StellarLens-Legacy";

    private final CameraView host;
    private final Context context;
    private final TextureView textureView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Camera camera;
    private int cameraId = -1;
    private boolean surfaceReady;
    private boolean previewing;
    private boolean capturing;
    private MediaRecorder mediaRecorder;
    private File recordingFile;
    private boolean released;

    LegacyCameraController(CameraView host) {
        this.host = host;
        this.context = host.getContext();
        this.textureView = new TextureView(context);
        this.textureView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        host.addView(textureView);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                surfaceReady = true;
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                if (camera == null || host.recording) return;
                try {
                    camera.stopPreview();
                    camera.setPreviewTexture(surface);
                    camera.setDisplayOrientation(calculateDisplayOrientation(cameraId));
                    camera.startPreview();
                    previewing = true;
                } catch (Exception e) {
                    Log.e(TAG, "Preview restart failed", e);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                surfaceReady = false;
                if (host.recording) stopRecording();
                releaseCameraInternal();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
    }

    @Override
    public void start() {
        if (textureView.isAvailable()) {
            surfaceReady = true;
            openCamera();
        }
    }

    private void openCamera() {
        if (!surfaceReady || camera != null || released) return;
        int id = findCameraId(host.lensFacing);
        if (id < 0) {
            host.notifyError("No camera available");
            return;
        }
        SurfaceTexture texture = textureView.getSurfaceTexture();
        if (texture == null) {
            host.notifyError("Preview surface is not ready");
            return;
        }
        Camera opened = null;
        try {
            opened = Camera.open(id);
            camera = opened;
            cameraId = id;

            Camera.Parameters parameters = opened.getParameters();
            Camera.Size previewSize = choosePreviewSize(parameters.getSupportedPreviewSizes());
            if (previewSize != null) {
                parameters.setPreviewSize(previewSize.width, previewSize.height);
            }
            applyPictureSize(parameters);
            applyFocusMode(parameters);
            applyFlash(parameters);
            applyZoom(parameters, host.zoom);
            opened.setParameters(parameters);
            updateZoomRange(parameters);
            opened.setDisplayOrientation(calculateDisplayOrientation(id));
            opened.setPreviewTexture(texture);
            opened.startPreview();
            previewing = true;
            host.cameraReady = true;
            Log.d(TAG, "Legacy camera ready, zoom: " + host.minZoom + " - " + host.maxZoom);
            host.notifyReady();
        } catch (Exception e) {
            Log.e(TAG, "Camera open failed", e);
            releaseCameraInternal();
            host.notifyError("Camera open failed: " + e.getMessage());
        }
    }

    private int findCameraId(int lensFacing) {
        int wanted = lensFacing == CameraView.LENS_FACING_FRONT
                ? Camera.CameraInfo.CAMERA_FACING_FRONT
                : Camera.CameraInfo.CAMERA_FACING_BACK;
        int count = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < count; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == wanted) return i;
        }
        return count > 0 ? 0 : -1;
    }

    private void applyPictureSize(Camera.Parameters parameters) {
        Camera.Size size = choosePictureSize(
                parameters.getSupportedPictureSizes(), host.photoQuality);
        if (size != null) {
            parameters.setPictureSize(size.width, size.height);
        }
    }

    private void applyFocusMode(Camera.Parameters parameters) {
        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes == null) return;
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
    }

    private void applyFlash(Camera.Parameters parameters) {
        List<String> supported = parameters.getSupportedFlashModes();
        if (supported == null) return;
        String mode;
        switch (host.flashMode) {
            case CameraView.FLASH_MODE_ON: mode = Camera.Parameters.FLASH_MODE_ON; break;
            case CameraView.FLASH_MODE_OFF: mode = Camera.Parameters.FLASH_MODE_OFF; break;
            default: mode = Camera.Parameters.FLASH_MODE_AUTO; break;
        }
        if (supported.contains(mode)) {
            parameters.setFlashMode(mode);
        }
    }

    private void applyZoom(Camera.Parameters parameters, float ratio) {
        if (!parameters.isZoomSupported()) {
            host.minZoom = 1f;
            host.maxZoom = 1f;
            host.zoom = 1f;
            return;
        }
        List<Integer> ratios = parameters.getZoomRatios();
        int index = closestZoomIndex(ratios, ratio);
        parameters.setZoom(index);
    }

    private void updateZoomRange(Camera.Parameters parameters) {
        if (!parameters.isZoomSupported()) {
            host.minZoom = 1f;
            host.maxZoom = 1f;
            host.zoom = 1f;
            return;
        }
        List<Integer> ratios = parameters.getZoomRatios();
        host.minZoom = ratios.get(0) / 100f;
        host.maxZoom = ratios.get(ratios.size() - 1) / 100f;
        host.zoom = ratios.get(parameters.getZoom()) / 100f;
    }

    private int closestZoomIndex(List<Integer> ratios, float ratio) {
        int target = Math.round(ratio * 100f);
        int best = 0;
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 0; i < ratios.size(); i++) {
            int diff = Math.abs(ratios.get(i) - target);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }

    @Override
    public void takePhoto() {
        if (camera == null || !previewing) {
            host.notifyError("Camera not ready");
            return;
        }
        if (host.recording) {
            host.notifyError("Cannot take a photo while recording on this device");
            return;
        }
        if (capturing) return;
        capturing = true;
        final File file = new File(host.outputDir, "SL_" + System.currentTimeMillis() + ".jpg");
        final Camera activeCamera = camera;
        try {
            Camera.Parameters parameters = activeCamera.getParameters();
            parameters.setRotation(calculateJpegRotation(cameraId));
            activeCamera.setParameters(parameters);
            activeCamera.takePicture(null, null, (data, cam) -> {
                boolean saved = writeBytes(file, data);
                mainHandler.post(() -> {
                    if (saved) {
                        host.notifyPhoto(file.getAbsolutePath());
                    } else {
                        file.delete();
                        host.notifyError("Photo save failed");
                    }
                    capturing = false;
                    if (cam == camera) {
                        try {
                            cam.startPreview();
                            previewing = true;
                        } catch (Exception e) {
                            Log.e(TAG, "Preview restart failed", e);
                        }
                    }
                });
            });
            previewing = false;
        } catch (Exception e) {
            capturing = false;
            file.delete();
            Log.e(TAG, "Photo failed", e);
            host.notifyError("Photo failed: " + e.getMessage());
        }
    }

    @Override
    public void startRecording() {
        if (camera == null || !previewing) {
            host.notifyError("Camera not ready");
            return;
        }
        if (host.recording) {
            Log.w(TAG, "Already recording");
            return;
        }
        if (capturing) {
            host.notifyError("Cannot record while capturing a photo");
            return;
        }
        CamcorderProfile profile = chooseCamcorderProfile(cameraId);
        if (profile == null) {
            host.notifyError("Recording not supported on this device");
            return;
        }
        File file = new File(host.outputDir, "SL_" + System.currentTimeMillis() + ".mp4");
        boolean withAudio = hasAudioPermission();
        MediaRecorder recorder = new MediaRecorder();
        boolean unlocked = false;
        try {
            camera.unlock();
            unlocked = true;
            recorder.setCamera(camera);
            if (withAudio) {
                recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            }
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            if (withAudio) {
                recorder.setProfile(profile);
            } else {
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                recorder.setVideoEncoder(profile.videoCodec);
                recorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
                recorder.setVideoFrameRate(profile.videoFrameRate);
                recorder.setVideoEncodingBitRate(profile.videoBitRate);
            }
            recorder.setOutputFile(file.getAbsolutePath());
            recorder.setOrientationHint(calculateJpegRotation(cameraId));
            recorder.prepare();
            recorder.start();
            mediaRecorder = recorder;
            recordingFile = file;
            host.recording = true;
            if (!withAudio) {
                Log.w(TAG, "RECORD_AUDIO not granted, recording without audio");
            }
        } catch (Exception e) {
            Log.e(TAG, "Recording failed", e);
            try {
                recorder.reset();
                recorder.release();
            } catch (Exception ignored) {
            }
            file.delete();
            if (unlocked) {
                try {
                    camera.lock();
                    camera.startPreview();
                    previewing = true;
                } catch (Exception ignored) {
                }
            }
            host.notifyError("Recording failed: " + e.getMessage());
        }
    }

    @Override
    public void stopRecording() {
        MediaRecorder recorder = mediaRecorder;
        File file = recordingFile;
        mediaRecorder = null;
        recordingFile = null;
        boolean success = true;
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException e) {
                Log.w(TAG, "Stopping recorder failed", e);
                success = false;
            }
            try {
                recorder.reset();
            } catch (Exception ignored) {
            }
            try {
                recorder.release();
            } catch (Exception ignored) {
            }
        }
        host.recording = false;
        if (camera != null) {
            try {
                camera.lock();
                camera.startPreview();
                previewing = true;
            } catch (Exception e) {
                Log.w(TAG, "Camera relock failed", e);
            }
        }
        if (recorder == null || released) return;
        if (success && file != null && file.length() > 0) {
            host.notifyVideo(file.getAbsolutePath());
        } else {
            if (file != null) file.delete();
            host.notifyError("Video save failed");
        }
    }

    @Override
    public void setZoom(float ratio) {
        float clamped = Math.max(host.minZoom, Math.min(host.maxZoom, ratio));
        if (camera == null) {
            host.zoom = clamped;
            return;
        }
        try {
            Camera.Parameters parameters = camera.getParameters();
            if (!parameters.isZoomSupported()) return;
            List<Integer> ratios = parameters.getZoomRatios();
            int index = closestZoomIndex(ratios, clamped);
            parameters.setZoom(index);
            camera.setParameters(parameters);
            host.zoom = ratios.get(index) / 100f;
        } catch (Exception e) {
            Log.e(TAG, "Zoom failed", e);
            host.notifyError("Zoom failed: " + e.getMessage());
        }
    }

    @Override
    public void switchCamera() {
        if (host.recording) stopRecording();
        releaseCameraInternal();
        host.lensFacing = host.lensFacing == CameraView.LENS_FACING_BACK
                ? CameraView.LENS_FACING_FRONT
                : CameraView.LENS_FACING_BACK;
        openCamera();
    }

    @Override
    public void setFlashMode(int mode) {
        if (camera == null) return;
        try {
            Camera.Parameters parameters = camera.getParameters();
            applyFlash(parameters);
            camera.setParameters(parameters);
        } catch (Exception e) {
            Log.e(TAG, "Flash mode failed", e);
        }
    }

    @Override
    public void setPhotoQuality(int quality) {
        if (camera == null) return;
        try {
            Camera.Parameters parameters = camera.getParameters();
            applyPictureSize(parameters);
            camera.setParameters(parameters);
        } catch (Exception e) {
            Log.e(TAG, "Photo quality failed", e);
        }
    }

    @Override
    public void setOutputDir(File dir) {
        // Output directory is read from the host on every capture.
    }

    @Override
    public boolean hasFlash() {
        if (camera == null) return false;
        List<String> supported = camera.getParameters().getSupportedFlashModes();
        return supported != null && supported.contains(Camera.Parameters.FLASH_MODE_ON);
    }

    @Override
    public void release() {
        released = true;
        if (host.recording) stopRecording();
        releaseCameraInternal();
        host.removeView(textureView);
    }

    private void releaseCameraInternal() {
        previewing = false;
        if (camera != null) {
            try {
                camera.setPreviewCallback(null);
            } catch (Exception ignored) {
            }
            try {
                camera.stopPreview();
            } catch (Exception ignored) {
            }
            try {
                camera.release();
            } catch (Exception ignored) {
            }
            camera = null;
        }
        cameraId = -1;
        host.cameraReady = false;
    }

    private CamcorderProfile chooseCamcorderProfile(int id) {
        int[] qualities = {
                CamcorderProfile.QUALITY_1080P,
                CamcorderProfile.QUALITY_720P,
                CamcorderProfile.QUALITY_480P,
                CamcorderProfile.QUALITY_HIGH,
                CamcorderProfile.QUALITY_LOW
        };
        for (int quality : qualities) {
            try {
                CamcorderProfile profile = CamcorderProfile.get(id, quality);
                if (profile != null) return profile;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private int calculateDisplayOrientation(int id) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(id, info);
        int degrees = displayRotationDegrees();
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            int result = (info.orientation + degrees) % 360;
            return (360 - result) % 360;
        }
        return (info.orientation - degrees + 360) % 360;
    }

    private int calculateJpegRotation(int id) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(id, info);
        int degrees = displayRotationDegrees();
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (info.orientation + degrees) % 360;
        }
        return (info.orientation - degrees + 360) % 360;
    }

    private int displayRotationDegrees() {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) return 0;
        int rotation = windowManager.getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_90: return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
            default: return 0;
        }
    }

    private Camera.Size choosePreviewSize(List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) return null;
        Camera.Size best = null;
        double bestDiff = Double.MAX_VALUE;
        long bestArea = Long.MAX_VALUE;
        for (Camera.Size size : sizes) {
            double diff = Math.abs((double) size.width / size.height - 16.0 / 9.0);
            long area = (long) size.width * size.height;
            if (diff < bestDiff - 1e-6 || (Math.abs(diff - bestDiff) <= 1e-6 && area < bestArea)) {
                bestDiff = diff;
                bestArea = area;
                best = size;
            }
        }
        return best;
    }

    private Camera.Size choosePictureSize(List<Camera.Size> sizes, int quality) {
        if (sizes == null || sizes.isEmpty()) return null;
        int targetWidth;
        int targetHeight;
        switch (quality) {
            case 0: targetWidth = 1280; targetHeight = 720; break;
            case 2: targetWidth = 3840; targetHeight = 2160; break;
            default: targetWidth = 1920; targetHeight = 1080; break;
        }
        long targetArea = (long) targetWidth * targetHeight;
        double targetRatio = (double) targetWidth / targetHeight;
        Camera.Size best = null;
        long bestScore = Long.MAX_VALUE;
        for (Camera.Size size : sizes) {
            long area = (long) size.width * size.height;
            double ratio = (double) size.width / size.height;
            long score = Math.abs(area - targetArea)
                    + (long) (Math.abs(ratio - targetRatio) * 1_000_000);
            if (score < bestScore) {
                bestScore = score;
                best = size;
            }
        }
        return best;
    }

    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean writeBytes(File file, byte[] data) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(data);
            out.flush();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to write photo", e);
            return false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
