package com.stellarlens.stellarlens_camera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Standalone test Activity for the StellarLens camera module.
 * Used by the app module to verify CameraView functionality
 * outside of a UniApp context.
 */
public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "StellarLens-Activity";
    private static final int CAMERA_PERMISSION_REQUEST = 100;

    private CameraView cameraView;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        if (!hasCameraPermission()) {
            requestCameraPermission();
            return;
        }

        setupUI();
    }

    private void setupUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        statusText = new TextView(this);
        statusText.setText("Initializing camera...");
        statusText.setPadding(16, 16, 16, 8);
        root.addView(statusText);

        FrameLayout cameraContainer = new FrameLayout(this);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0);
        containerParams.weight = 1;
        cameraContainer.setLayoutParams(containerParams);

        cameraView = new CameraView(this);
        cameraView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        cameraContainer.addView(cameraView);
        root.addView(cameraContainer);

        LinearLayout buttonBar = new LinearLayout(this);
        buttonBar.setOrientation(LinearLayout.HORIZONTAL);
        buttonBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        buttonBar.setPadding(8, 8, 8, 16);

        Button btnPhoto = new Button(this);
        btnPhoto.setText("Capture");
        btnPhoto.setOnClickListener(v -> {
            cameraView.takePhoto();
            statusText.setText("Capturing...");
        });
        buttonBar.addView(btnPhoto);

        Button btnSwitch = new Button(this);
        btnSwitch.setText("Flip");
        btnSwitch.setOnClickListener(v -> cameraView.switchCamera());
        buttonBar.addView(btnSwitch);

        Button btnZoomIn = new Button(this);
        btnZoomIn.setText("Zoom+");
        btnZoomIn.setOnClickListener(v -> {
            cameraView.setZoom(cameraView.getZoom() * 1.5f);
            statusText.setText("Zoom: " + String.format("%.1f", cameraView.getZoom()) + "x");
        });
        buttonBar.addView(btnZoomIn);

        Button btnZoomOut = new Button(this);
        btnZoomOut.setText("Zoom-");
        btnZoomOut.setOnClickListener(v -> {
            cameraView.setZoom(cameraView.getZoom() / 1.5f);
            statusText.setText("Zoom: " + String.format("%.1f", cameraView.getZoom()) + "x");
        });
        buttonBar.addView(btnZoomOut);

        Button btnFlash = new Button(this);
        btnFlash.setText("Flash");
        btnFlash.setOnClickListener(v -> {
            switch (cameraView.getFlashMode()) {
                case CameraView.FLASH_MODE_AUTO:
                    cameraView.setFlashMode(CameraView.FLASH_MODE_ON);
                    break;
                case CameraView.FLASH_MODE_ON:
                    cameraView.setFlashMode(CameraView.FLASH_MODE_OFF);
                    break;
                default:
                    cameraView.setFlashMode(CameraView.FLASH_MODE_AUTO);
                    break;
            }
            statusText.setText("Flash: " + cameraView.getFlashModeString());
        });
        buttonBar.addView(btnFlash);

        root.addView(buttonBar);
        root.setBackgroundColor(Color.BLACK);
        statusText.setTextColor(Color.WHITE);
        setContentView(root);

        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.setAppearanceLightStatusBars(false);
        windowInsetsController.setAppearanceLightNavigationBars(false);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        cameraView.setLifecycleOwner(this);

        cameraView.setOnCameraReadyListener(() -> runOnUiThread(() -> {
            statusText.setText("Camera ready");
            Log.d(TAG, "Camera is ready");
        }));

        cameraView.setOnPhotoTakenListener(path -> runOnUiThread(() -> {
            statusText.setText("Photo saved: " + path);
            Toast.makeText(this, "Photo saved", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Photo saved at: " + path);
        }));

        cameraView.setOnVideoSavedListener(path -> runOnUiThread(() -> {
            statusText.setText("Video saved: " + path);
            Toast.makeText(this, "Video saved", Toast.LENGTH_SHORT).show();
        }));

        cameraView.setOnErrorListener(msg -> runOnUiThread(() -> {
            statusText.setText("Error: " + msg);
            Log.e(TAG, "Camera error: " + msg);
        }));

        cameraView.startCamera();
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                },
                CAMERA_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != CAMERA_PERMISSION_REQUEST) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupUI();
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraView != null) {
            cameraView.releaseCamera();
        }
    }
}
