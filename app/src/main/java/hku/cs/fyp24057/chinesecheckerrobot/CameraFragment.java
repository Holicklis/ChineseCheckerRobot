package hku.cs.fyp24057.chinesecheckerrobot;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class CameraFragment extends Fragment {
    private static final String TAG = "CameraFragment";
    private PreviewView previewView;
    private Button captureEmptyButton;
    private Button captureCurrentButton;
    private Button btnConfigureIp;
    private TextView resultText;
    private ImageCapture imageCapture;
    private BoardDetectionClient detectionClient;
    private boolean hasEmptyBoard = false;

    private String serverIp = "192.168.11.230";//default

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestRequiredPermissions();
        detectionClient = new BoardDetectionClient(serverIp);
    }

    private void requestRequiredPermissions() {
        String[] permissions = new String[]{
                Manifest.permission.CAMERA
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startCamera();
            } else {
                Toast.makeText(requireContext(), "Camera permission is required", Toast.LENGTH_LONG).show();
            }
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

        previewView = view.findViewById(R.id.previewView);
        captureEmptyButton = view.findViewById(R.id.captureEmptyButton);
        captureCurrentButton = view.findViewById(R.id.captureCurrentButton);
        btnConfigureIp = view.findViewById(R.id.btnConfigureIp);
        resultText = view.findViewById(R.id.resultText);

        setupButtons();

        if (allPermissionsGranted()) {
            startCamera();
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void setupButtons() {
        captureEmptyButton.setOnClickListener(v -> {
            captureEmptyButton.setEnabled(false);
            takePicture(true);
        });

        captureCurrentButton.setOnClickListener(v -> {
            if (!hasEmptyBoard) {
                Toast.makeText(requireContext(),
                        "Please capture empty board first",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            captureCurrentButton.setEnabled(false);
            takePicture(false);
        });
        btnConfigureIp.setOnClickListener(v -> showIpConfigDialog());
        // Initially disable current button until empty board is processed
        captureCurrentButton.setEnabled(false);
    }

    private void showIpConfigDialog() {
        new IpConfigDialog(
                requireContext(),
                serverIp,
                "Configure Server IP",
                newIp -> {
                    serverIp = newIp;
                    detectionClient = new BoardDetectionClient(serverIp);
                    Toast.makeText(requireContext(),
                            "Server IP updated to: " + newIp,
                            Toast.LENGTH_SHORT).show();
                }
        ).show();
    }

    private void takePicture(boolean isEmptyBoard) {
        if (imageCapture == null) {
            Log.e(TAG, "Cannot take picture, imageCapture is null");
            return;
        }

        imageCapture.takePicture(ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        Bitmap bitmap = imageProxyToBitmap(image);
                        if (bitmap != null) {
                            processImage(bitmap, isEmptyBoard);
                        }
                        image.close();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Image capture failed", exception);
                        Toast.makeText(requireContext(),
                                "Failed to capture image: " + exception.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        captureEmptyButton.setEnabled(true);
                        captureCurrentButton.setEnabled(hasEmptyBoard);
                    }
                });
    }

    private void processImage(Bitmap bitmap, boolean isEmptyBoard) {
        if (isEmptyBoard) {
            detectionClient.uploadEmptyBoard(bitmap, new BoardDetectionClient.DetectionCallback() {
                @Override
                public void onSuccess(List<String> boardState) {
                    requireActivity().runOnUiThread(() -> {
                        hasEmptyBoard = true;
                        captureEmptyButton.setEnabled(true);
                        captureCurrentButton.setEnabled(true);
                        Toast.makeText(requireContext(),
                                "Empty board processed successfully",
                                Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String error) {
                    requireActivity().runOnUiThread(() -> {
                        captureEmptyButton.setEnabled(true);
                        Toast.makeText(requireContext(),
                                "Error: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        } else {
            detectionClient.detectCurrentState(bitmap, new BoardDetectionClient.DetectionCallback() {
                @Override
                public void onSuccess(List<String> boardState) {
                    requireActivity().runOnUiThread(() -> {
                        captureCurrentButton.setEnabled(true);
                        if (boardState != null) {
                            StringBuilder display = new StringBuilder("Board State:\n\n");
                            for (String row : boardState) {
                                display.append(row).append('\n');
                            }
                            resultText.setText(display.toString());
                        }
                        Toast.makeText(requireContext(),
                                "Board state detected successfully",
                                Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String error) {
                    requireActivity().runOnUiThread(() -> {
                        captureCurrentButton.setEnabled(true);
                        resultText.setText("Error: " + error);
                        Toast.makeText(requireContext(),
                                "Error: " + error,
                                Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        if (planes.length > 0) {
            ByteBuffer buffer = planes[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }
        return null;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Set up the preview use case
                Preview preview = new Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)  // Use 4:3 aspect ratio
                        .build();

                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Configure image capture use case
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)  // Prioritize quality over speed
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)  // Match preview aspect ratio
                        .setTargetRotation(Surface.ROTATION_0)
                        .build();

                // Select back camera
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                // Unbind existing use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                Camera camera = cameraProvider.bindToLifecycle(
                        getViewLifecycleOwner(),
                        cameraSelector,
                        preview,
                        imageCapture
                );

                // Set up zoom if needed
                camera.getCameraControl().setLinearZoom(0.0f);  // Start with no zoom

                Log.d(TAG, "Camera started successfully");

            } catch (ExecutionException | InterruptedException e) {
                String msg = "Error starting camera: " + e.getMessage();
                Log.e(TAG, msg, e);
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "CameraFragment destroyed");
    }
}