package hku.cs.fyp24057.chinesecheckerrobot;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

public class CameraFragment extends Fragment {
    private static final String TAG = "CameraFragment";
    private PreviewView previewView;
    private ImageView processedImageView;
    private TextView boardStateText;
    private Button captureButton;
    private ImageCapture imageCapture;
    private BoardState currentBoardState;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Request permissions at onCreate
        requestRequiredPermissions();
    }

    private void requestRequiredPermissions() {
        String[] permissions = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            requestPermissions(permissions, 1001);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        previewView = view.findViewById(R.id.previewView);
        processedImageView = view.findViewById(R.id.processedImageView);
        boardStateText = view.findViewById(R.id.boardStateText);
        captureButton = view.findViewById(R.id.captureButton);

        boardStateText.setTypeface(Typeface.MONOSPACE);

        // Initialize board state
        currentBoardState = new BoardState();

        // Set up capture button
        captureButton.setOnClickListener(v -> {
            captureButton.setEnabled(false); // Prevent multiple clicks
            captureImage();
        });

        // Start camera if permissions are granted
        if (allPermissionsGranted()) {
            startCamera();
        }
    }

    private boolean allPermissionsGranted() {
        String[] permissions = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Set up preview use case
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Set up image capture use case with rotation
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(Surface.ROTATION_0) // Set default rotation
                        .build();

                // Select back camera
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                // Unbind any bound use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                        getViewLifecycleOwner(),
                        cameraSelector,
                        preview,
                        imageCapture
                );

                Log.d(TAG, "Camera started successfully");

            } catch (ExecutionException | InterruptedException e) {
                String msg = "Error starting camera: " + e.getMessage();
                Log.e(TAG, msg, e);
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void captureImage() {
        if (imageCapture == null) {
            Log.e(TAG, "imageCapture is null");
            Toast.makeText(requireContext(), "Camera not initialized", Toast.LENGTH_SHORT).show();
            captureButton.setEnabled(true);
            return;
        }

        Log.d(TAG, "Capturing image...");

        // Take picture
        imageCapture.takePicture(
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        Log.d(TAG, "Image captured successfully, processing...");
                        processImage(image);
                        image.close();
                        captureButton.setEnabled(true);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        String msg = "Error capturing image: " + exception.getMessage();
                        Log.e(TAG, msg, exception);
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                        captureButton.setEnabled(true);
                    }
                });
    }

    private void processImage(ImageProxy image) {
        try {
            Log.d(TAG, "Starting image processing");

            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            // Convert image to bitmap
            Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap");
                return;
            }

            // Rotate bitmap if needed
            Matrix matrix = new Matrix();
            matrix.postRotate(90); // Rotate 90 degrees clockwise
            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);

            // Convert to OpenCV Mat
            Mat mat = new Mat();
            Utils.bitmapToMat(rotatedBitmap, mat);

            Log.d(TAG, "Starting board processing");
            currentBoardState = BoardImageProcessor.processImage(mat, requireContext());
            Log.d(TAG, "Board processing completed");

            // Convert processed Mat back to Bitmap for display
            Bitmap processedBitmap = Bitmap.createBitmap(
                    mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, processedBitmap);

            // Update UI
            requireActivity().runOnUiThread(() -> {
                processedImageView.setImageBitmap(processedBitmap);
                processedImageView.setVisibility(View.VISIBLE);
                previewView.setVisibility(View.GONE);
                boardStateText.setText(currentBoardState.toString());
                Log.d(TAG, "UI updated with processed image");
            });

            // Cleanup
            mat.release();
            Log.d(TAG, "Image processing completed successfully");

        } catch (Exception e) {
            Log.e(TAG, "Error processing image: " + e.getMessage(), e);
            Toast.makeText(requireContext(), "Error processing image: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == 1001) {
            if (allPermissionsGranted()) {
                Log.d(TAG, "All permissions granted, starting camera");
                startCamera();
            } else {
                Log.e(TAG, "Permissions not granted");
                Toast.makeText(requireContext(),
                        "Permissions are required for camera and storage",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "CameraFragment destroyed");
    }
}