package com.stellarlens.stellarlens_camera;

import java.io.File;

/**
 * Abstraction over the two camera implementations:
 * CameraX on API 21+ and the legacy {@code android.hardware.Camera} API on API 19-20.
 * All mutable state lives in {@link CameraView}; controllers read and update it.
 */
interface CameraController {

    void start();

    void takePhoto();

    void startRecording();

    void stopRecording();

    void setZoom(float ratio);

    void switchCamera();

    void setFlashMode(int mode);

    void setPhotoQuality(int quality);

    void setOutputDir(File dir);

    boolean hasFlash();

    void release();
}
