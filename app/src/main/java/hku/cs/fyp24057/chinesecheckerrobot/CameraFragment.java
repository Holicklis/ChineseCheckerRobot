package hku.cs.fyp24057.chinesecheckerrobot;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

public class CameraFragment extends Fragment {
    private PreviewView previewView;
    private ImageView processedImageView;
    private TextView boardStateText;
    private Button captureButton;
    private ImageCapture imageCapture;
    private BoardState currentBoardState;

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
        captureButton.setOnClickListener(v -> captureImage());

        // Check and request camera permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1001);
        } else {
            startCamera();
        }
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

                // Set up image capture use case
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
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

            } catch (ExecutionException | InterruptedException e) {
                String msg = "Error starting camera: " + e.getMessage();
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(requireContext(), "Camera permission is required",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void captureImage() {
        if (imageCapture == null) {
            Toast.makeText(requireContext(), "Camera not initialized",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Take picture
        imageCapture.takePicture(
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        processImage(image);
                        image.close();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        String msg = "Error capturing image: " + exception.getMessage();
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void processImage(ImageProxy image) {
        try {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            // Convert image to bitmap
            Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

            // Convert to OpenCV Mat with error handling
            Mat mat = new Mat();
            if (mat.nativeObj == 0) {
                Log.e("OpenCV", "Failed to create Mat object");
                Toast.makeText(requireContext(), "Failed to process image", Toast.LENGTH_SHORT).show();
                return;
            }

            Utils.bitmapToMat(bitmap, mat);

            // Process the image
            currentBoardState = BoardImageProcessor.processImage(mat);

            // Convert processed Mat back to Bitmap for display
            Bitmap processedBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, processedBitmap);

            // Update UI
            requireActivity().runOnUiThread(() -> {
                processedImageView.setImageBitmap(processedBitmap);
                processedImageView.setVisibility(View.VISIBLE);
                previewView.setVisibility(View.GONE);
                boardStateText.setText(currentBoardState.toString());
            });

            // Cleanup
            mat.release();
        } catch (Exception e) {
            Log.e("OpenCV", "Error processing image: " + e.getMessage());
            Toast.makeText(requireContext(), "Error processing image", Toast.LENGTH_SHORT).show();
        } finally {
            image.close();
        }
    }

    private int countMarbles() {
        int black = 0;
        int white = 0;
        for (int row = 0; row < 17; row++) {
            for (int col = 0; col < 13; col++) {
                int pos = currentBoardState.getPosition(row, col);
                if (pos == BoardState.BLACK) black++;
                else if (pos == BoardState.WHITE) white++;
            }
        }
        return black + white;
    }
}