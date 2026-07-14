package com.stellarlens.stellarlens_camera;

import android.util.Log;

import io.dcloud.feature.uniapp.UniSDKEngine;

/**
 * StellarLens Camera Plugin - Entry point for UniApp plugin registration.
 *
 * Call StellarLensCameraPlugin.register() in your Application.onCreate()
 * after UniSDKEngine has been initialized.
 *
 * After registration, use <stellar-lens-camera> in your UniApp template:
 *
 * <template>
 *   <view>
 *     <stellar-lens-camera
 *       ref="camera"
 *       style="width: 750rpx; height: 500rpx;"
 *       camera-facing="back"
 *       flash-mode="auto"
 *       @ready="onCameraReady"
 *     />
 *     <button @click="takePhoto">Take Photo</button>
 *   </view>
 * </template>
 */
public class StellarLensCameraPlugin {

    private static final String TAG = "StellarLens-Plugin";
    private static boolean registered = false;

    /**
     * Register the StellarLens camera component with UniApp.
     * Must be called after UniSDKEngine has been initialized.
     */
    public static void register() {
        if (registered) {
            Log.w(TAG, "Already registered, skipping");
            return;
        }
        try {
            UniSDKEngine.registerUniComponent(
                    "stellar-lens-camera",
                    StellarLensCameraComponent.class,
                    false
            );
            registered = true;
            Log.i(TAG, "StellarLens camera component registered successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register camera component", e);
        }
    }

    /**
     * Check if the component has been registered.
     */
    public static boolean isRegistered() {
        return registered;
    }
}
