package com.stellarlens.stellarlens_camera;

import android.content.Context;
import android.util.Log;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;

import com.alibaba.fastjson.JSONObject;

import io.dcloud.feature.uniapp.UniSDKInstance;
import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.bridge.UniJSCallback;
import io.dcloud.feature.uniapp.ui.action.AbsComponentData;
import io.dcloud.feature.uniapp.ui.component.AbsVContainer;
import io.dcloud.feature.uniapp.ui.component.UniComponent;
import io.dcloud.feature.uniapp.ui.component.UniComponentProp;

import java.io.File;

public class StellarLensCameraComponent extends UniComponent<CameraView> {

    private static final String TAG = "StellarLens-Component";

    private CameraView cameraView;
    private boolean isReady = false;

    public StellarLensCameraComponent(UniSDKInstance instance, AbsVContainer parent, AbsComponentData data) {
        super(instance, parent, data);
    }

    @Override
    protected CameraView initComponentHostView(@NonNull Context context) {
        cameraView = new CameraView(context);
        cameraView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        cameraView.setOnCameraReadyListener(() -> {
            isReady = true;
            fireEvent("ready", null);
            Log.d(TAG, "Camera ready");
        });

        cameraView.setOnPhotoTakenListener(path -> {
            JSONObject data = new JSONObject();
            data.put("tempFilePath", path);
            fireEvent("phototaken", data);
        });

        cameraView.setOnVideoSavedListener(path -> {
            JSONObject data = new JSONObject();
            data.put("tempFilePath", path);
            fireEvent("videosaved", data);
        });

        cameraView.setOnErrorListener(msg -> {
            JSONObject data = new JSONObject();
            data.put("message", msg);
            fireEvent("error", data);
        });

        return cameraView;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        if (getContext() instanceof LifecycleOwner) {
            cameraView.setLifecycleOwner((LifecycleOwner) getContext());
            cameraView.startCamera();
            Log.d(TAG, "Camera started");
        } else {
            Log.e(TAG, "Context is not LifecycleOwner");
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (cameraView != null) {
            cameraView.releaseCamera();
        }
    }

    @UniJSMethod(uiThread = true)
    public void takePhoto(UniJSCallback callback) {
        if (cameraView == null) {
            if (callback != null) callback.invoke(errorResult("Camera not initialized"));
            return;
        }
        cameraView.setOnPhotoTakenListener(path -> {
            JSONObject result = new JSONObject();
            result.put("tempFilePath", path);
            result.put("success", true);
            if (callback != null) callback.invoke(result);
        });
        cameraView.takePhoto();
    }

    @UniJSMethod(uiThread = true)
    public void startRecording(UniJSCallback callback) {
        if (cameraView == null) {
            if (callback != null) callback.invoke(errorResult("Camera not initialized"));
            return;
        }
        cameraView.setOnVideoSavedListener(path -> {
            JSONObject result = new JSONObject();
            result.put("tempFilePath", path);
            result.put("success", true);
            if (callback != null) callback.invoke(result);
        });
        cameraView.startRecording();
    }

    @UniJSMethod(uiThread = true)
    public void stopRecording() {
        if (cameraView != null) cameraView.stopRecording();
    }

    @UniJSMethod(uiThread = true)
    public void isRecording(UniJSCallback callback) {
        boolean recording = cameraView != null && cameraView.isRecording();
        JSONObject result = new JSONObject();
        result.put("recording", recording);
        if (callback != null) callback.invoke(result);
    }

    @UniJSMethod(uiThread = true)
    public void setZoom(float zoom) {
        if (cameraView != null) cameraView.setZoom(zoom);
    }

    @UniJSMethod(uiThread = true)
    public void getZoom(UniJSCallback callback) {
        if (cameraView == null) return;
        JSONObject result = new JSONObject();
        result.put("zoom", cameraView.getZoom());
        result.put("minZoom", cameraView.getMinZoom());
        result.put("maxZoom", cameraView.getMaxZoom());
        if (callback != null) callback.invoke(result);
    }

    @UniJSMethod(uiThread = true)
    public void switchCamera() {
        if (cameraView != null) cameraView.switchCamera();
    }

    @UniJSMethod(uiThread = true)
    public void setFlashMode(String mode) {
        if (cameraView != null) cameraView.setFlashModeString(mode);
    }

    @UniJSMethod(uiThread = true)
    public void getFlashMode(UniJSCallback callback) {
        if (cameraView == null) return;
        JSONObject result = new JSONObject();
        result.put("flashMode", cameraView.getFlashModeString());
        if (callback != null) callback.invoke(result);
    }

    @UniJSMethod(uiThread = true)
    public void setPhotoQuality(int quality) {
        if (cameraView != null) cameraView.setPhotoQuality(quality);
    }

    @UniJSMethod(uiThread = true)
    public void setOutputDir(String dirPath) {
        if (cameraView != null) cameraView.setOutputDir(new File(dirPath));
    }

    @UniJSMethod(uiThread = true)
    public void getCameraInfo(UniJSCallback callback) {
        if (cameraView == null) return;
        JSONObject result = new JSONObject();
        result.put("hasFlash", cameraView.hasFlash());
        result.put("isFrontCamera", cameraView.getLensFacing() == 0);
        result.put("isReady", isReady);
        result.put("zoom", cameraView.getZoom());
        result.put("minZoom", cameraView.getMinZoom());
        result.put("maxZoom", cameraView.getMaxZoom());
        if (callback != null) callback.invoke(result);
    }

    @UniComponentProp(name = "camera-facing")
    public void setCameraFacing(String facing) {
        if (cameraView == null) return;
        boolean needsSwitch = false;
        if ("front".equals(facing) && cameraView.getLensFacing() != 0) {
            needsSwitch = true;
        } else if ("back".equals(facing) && cameraView.getLensFacing() != 1) {
            needsSwitch = true;
        }
        if (needsSwitch) cameraView.switchCamera();
    }

    @UniComponentProp(name = "flash-mode")
    public void setFlashModeProp(String mode) {
        if (cameraView != null) cameraView.setFlashModeString(mode);
    }

    @UniComponentProp(name = "zoom")
    public void setZoomProp(float zoom) {
        if (cameraView != null) cameraView.setZoom(zoom);
    }

    @UniComponentProp(name = "quality")
    public void setQualityProp(int quality) {
        if (cameraView != null) cameraView.setPhotoQuality(quality);
    }

    private JSONObject errorResult(String msg) {
        JSONObject result = new JSONObject();
        result.put("success", false);
        result.put("message", msg);
        return result;
    }
}
