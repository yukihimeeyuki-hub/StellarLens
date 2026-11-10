package com.StellarLens.stellarlens;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
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

import com.stellarlens.stellarlens_camera.CameraView;

/**
 * Test Activity for the StellarLens Camera module.
 * This app exists solely to test the stellarlens_camera library.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "StellarLens-TestApp";
    private static final int PERMISSIONS_REQUEST = 100;

    private CameraView cameraView;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        if (!hasCameraPermission()) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
            }, PERMISSIONS_REQUEST);
            return;
        }
        setupUi();
    }

    private void setupUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        statusText = new TextView(this);
        statusText.setText("Camera demo - initializing...");
        statusText.setPadding(16, 16, 16, 8);
        root.addView(statusText);

        FrameLayout camContainer = new FrameLayout(this);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        containerParams.weight = 1;
        camContainer.setLayoutParams(containerParams);

        cameraView = new CameraView(this);
        camContainer.addView(cameraView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(camContainer);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(8, 8, 8, 16);
        controls.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        addButton(controls, "Capture", v -> {
            cameraView.takePhoto();
            statusText.setText("Capturing photo...");
        });

        addButton(controls, "Flip", v -> {
            cameraView.switchCamera();
            statusText.setText("Switching camera...");
        });

        addButton(controls, "Zoom+", v -> {
            cameraView.setZoom(cameraView.getZoom() * 1.5f);
            statusText.setText("Zoom: " + String.format("%.1f", cameraView.getZoom()) + "x");
        });

        addButton(controls, "Zoom-", v -> {
            cameraView.setZoom(cameraView.getZoom() / 1.5f);
            statusText.setText("Zoom: " + String.format("%.1f", cameraView.getZoom()) + "x");
        });

        addButton(controls, "Flash", v -> {
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

        addButton(controls, "Record", v -> {
            if (cameraView.isRecording()) {
                cameraView.stopRecording();
                statusText.setText("Recording stopped");
            } else {
                cameraView.startRecording();
                statusText.setText("Recording...");
            }
        });

        root.addView(controls);
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

        cameraView.setOnCameraReadyListener(() ->
                runOnUiThread(() -> statusText.setText("Camera ready"))
        );
        cameraView.setOnPhotoTakenListener(path ->
                runOnUiThread(() -> {
                    statusText.setText("Photo: " + path.substring(path.lastIndexOf("/") + 1));
                    Toast.makeText(this, "Photo saved", Toast.LENGTH_SHORT).show();
                })
        );
        cameraView.setOnVideoSavedListener(path ->
                runOnUiThread(() -> {
                    statusText.setText("Video: " + path.substring(path.lastIndexOf("/") + 1));
                    Toast.makeText(this, "Video saved", Toast.LENGTH_SHORT).show();
                })
        );
        cameraView.setOnErrorListener(msg ->
                runOnUiThread(() -> {
                    statusText.setText("Error: " + msg);
                    Log.e(TAG, msg);
                })
        );

        cameraView.startCamera();
    }

    private void addButton(LinearLayout parent, String text, android.view.View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.weight = 1;
        lp.setMargins(4, 0, 4, 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        parent.addView(btn);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSIONS_REQUEST) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupUi();
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraView != null) cameraView.releaseCamera();
    }
}
