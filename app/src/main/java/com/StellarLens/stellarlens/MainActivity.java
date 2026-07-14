package com.StellarLens.stellarlens;

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
import androidx.camera.core.ImageCapture;

import com.stellarlens.stellarlens_camera.CameraView;

/**
 * Test Activity for StellarLens Camera module.
 * This app exists solely to test the stellarlens_camera library.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "StellarLens-TestApp";

    private CameraView cameraView;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Status
        statusText = new TextView(this);
        statusText.setText("Camera demo - initializing...");
        statusText.setPadding(16, 48, 16, 8);
        root.addView(statusText);

        // Camera container
        FrameLayout camContainer = new FrameLayout(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        cp.weight = 1;
        camContainer.setLayoutParams(cp);

        cameraView = new CameraView(this);
        camContainer.addView(cameraView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(camContainer);

        // Controls
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
            String mode;
            switch (cameraView.getFlashMode()) {
                case ImageCapture.FLASH_MODE_OFF:
                    cameraView.setFlashMode(ImageCapture.FLASH_MODE_ON);
                    mode = "on";
                    break;
                case ImageCapture.FLASH_MODE_ON:
                    cameraView.setFlashMode(ImageCapture.FLASH_MODE_AUTO);
                    mode = "auto";
                    break;
                default:
                    cameraView.setFlashMode(ImageCapture.FLASH_MODE_OFF);
                    mode = "off";
                    break;
            }
            statusText.setText("Flash: " + mode);
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

        setContentView(root);

        // Wire lifecycle
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

    private void addButton(LinearLayout parent, String text, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.weight = 1;
        lp.setMargins(4, 0, 4, 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        parent.addView(btn);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraView != null) cameraView.releaseCamera();
    }
}
