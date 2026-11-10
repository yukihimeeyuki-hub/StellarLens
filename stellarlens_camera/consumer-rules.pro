# Keep the uni-app entry points: the component is referenced by name from
# assets/dcloud_uniplugins.json and the plugin may be registered by hosts.
-keep class com.stellarlens.stellarlens_camera.StellarLensCameraComponent { *; }
-keep class com.stellarlens.stellarlens_camera.StellarLensCameraPlugin { *; }
-keep class com.stellarlens.stellarlens_camera.CameraView { *; }