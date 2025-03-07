package hku.cs.fyp24057.chinesecheckerrobot;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class IntegratedAIGameFragment extends Fragment {

    private EditText etDebugBoardX;
    private EditText etDebugBoardY;
    private Button btnLookupCoords;
    private TextView tvMappedPosition;
    private boolean isMoving = false;

    private Button btnShowDebugInfo;

    private static final String TAG = "IntegratedAIGameFrag";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int DETECTION_PORT = 5001;
    private static final int AI_PORT = 5002;

    // UI Elements
    private PreviewView previewView;
    private Button btnCaptureEmpty;
    private Button btnDetectCurrent;
    private Button btnGetAIMove;
    private Button btnExecuteMove;
    private Button btnConfigureIp;
    private TextView tvBoardState;
    private TextView tvAIResponse;

    // Camera Control
    private ImageCapture imageCapture;
    private boolean hasEmptyBoard = false;

    // Network Clients
    private String serverIp = "192.168.11.175"; // Default
    private String robotIp = "192.168.11.172"; // Default
    private OkHttpClient client;
    private BoardDetectionClient detectionClient;

    // Robot Control
    private RobotController robotController;

    // Game State
    private List<String> currentBoardState;
    private JSONObject lastRecommendedMove;
    private JSONArray lastRecommendedMoveSequence = null;

    private Button btnConfigureServerIp;
    private Button btnConfigureRobotIp;

    private Button btnStartGripper;
    private Button btnStopGripper;

    private Button btnResetArm;

    private Button btnDetectPosition;

    private boolean isPlayer1Turn = true; // Default, can be toggled

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize the clients with separate IPs
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        detectionClient = new BoardDetectionClient(serverIp); // Use server IP
        robotController = RobotController.getInstance();
        robotController.setRobotIp(robotIp); // Use robot IP

        Log.d(TAG, "Initialized with server IP: " + serverIp);
        Log.d(TAG, "Initialized with robot IP: " + robotIp);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_integrated_ai_game, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize UI elements
        previewView = view.findViewById(R.id.previewView);
        btnCaptureEmpty = view.findViewById(R.id.btnCaptureEmpty);
        btnDetectCurrent = view.findViewById(R.id.btnDetectCurrent);
        btnGetAIMove = view.findViewById(R.id.btnGetAIMove);
        btnExecuteMove = view.findViewById(R.id.btnExecuteMove);

        // Replace single config button with two separate buttons
        btnConfigureServerIp = view.findViewById(R.id.btnConfigureServerIp);
        btnConfigureRobotIp = view.findViewById(R.id.btnConfigureRobotIp);

        tvBoardState = view.findViewById(R.id.tvBoardState);
        tvAIResponse = view.findViewById(R.id.tvAIResponse);

        // Initialize debug UI elements
        etDebugBoardX = view.findViewById(R.id.etDebugBoardX);
        etDebugBoardY = view.findViewById(R.id.etDebugBoardY);
        btnShowDebugInfo = view.findViewById(R.id.btnShowDebugInfo);
        btnLookupCoords = view.findViewById(R.id.btnLookupCoords);
        tvMappedPosition = view.findViewById(R.id.tvMappedPosition);

        btnStartGripper = view.findViewById(R.id.btnStartGripper);
        btnStopGripper = view.findViewById(R.id.btnStopGripper);

        btnResetArm = view.findViewById(R.id.btnResetArm);

        btnDetectPosition = view.findViewById(R.id.btnDetectPosition);

        // Set up listeners
        setupButtons();

        // Start the camera if permissions are granted
        if (ContextCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissions(
                    new String[]{android.Manifest.permission.CAMERA},
                    1001);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 &&
                    grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(requireContext(),
                        "Camera permission is required",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupButtons() {
        btnCaptureEmpty.setOnClickListener(v -> {
            captureEmptyBoard();
        });

        btnDetectCurrent.setOnClickListener(v -> {
            if (!hasEmptyBoard) {
                Toast.makeText(requireContext(),
                        "Please capture empty board first",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            detectCurrentBoard();
        });

        btnGetAIMove.setOnClickListener(v -> {
            if (currentBoardState == null || currentBoardState.isEmpty()) {
                Toast.makeText(requireContext(),
                        "Please detect current board state first",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            getAIMove();
        });

        btnExecuteMove.setOnClickListener(v -> {
            if (lastRecommendedMoveSequence != null) {
                executeAIMoveSequence(lastRecommendedMoveSequence);
            } else {
                Toast.makeText(requireContext(),
                        "No AI move sequence available. Get AI move first.",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // Replace single configIP with separate handlers
        btnConfigureServerIp.setOnClickListener(v -> {
            showServerIpConfigDialog();
        });

        btnConfigureRobotIp.setOnClickListener(v -> {
            showRobotIpConfigDialog();
        });

        btnShowDebugInfo.setOnClickListener(v -> {
            showDebugInfo();
        });

        btnLookupCoords.setOnClickListener(v -> {
            lookupAndMoveToPosition();
        });

        // Initially disable buttons that depend on previous steps
        btnDetectCurrent.setEnabled(false);
        btnGetAIMove.setEnabled(false);
        btnExecuteMove.setEnabled(false);

        btnStartGripper.setOnClickListener(v -> {
            if (isMoving) {
                Toast.makeText(requireContext(),
                        "Robot is currently moving. Please wait.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            controlGripper(true);
        });

        btnStopGripper.setOnClickListener(v -> {
            if (isMoving) {
                Toast.makeText(requireContext(),
                        "Robot is currently moving. Please wait.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            controlGripper(false);
        });

        btnResetArm.setOnClickListener(v -> {
            if (isMoving) {
                Toast.makeText(requireContext(),
                        "Robot is currently moving. Please wait.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // Set moving flag
            isMoving = true;

            // Update UI
            tvAIResponse.append("\nResetting arm to home position...");
            Toast.makeText(requireContext(),
                    "Resetting robot arm to home position",
                    Toast.LENGTH_SHORT).show();

            // Execute reset command
            robotController.reset();

            // Allow new movements after a short delay (500ms)
            new Handler().postDelayed(() -> {
                isMoving = false;
                tvAIResponse.append("\nReset completed.");
            }, 500);
        });

        btnDetectPosition.setOnClickListener(v -> {
            detectCurrentPosition();
        });

    }


    private void detectCurrentPosition() {
        if (isMoving) {
            Toast.makeText(requireContext(),
                    "Robot is currently moving. Please wait.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        tvAIResponse.append("\nRequesting current position...");

        // Get position feedback from the robot controller
        JSONObject positionFeedback = robotController.getPositionFeedback();

        if (positionFeedback != null) {
            try {
                float x = (float) positionFeedback.getDouble("x");
                float y = (float) positionFeedback.getDouble("y");
                float z = (float) positionFeedback.getDouble("z");
                float t = (float) positionFeedback.getDouble("t");

                String positionInfo = String.format(Locale.US,
                        "\n\nCurrent Position:\nX: %.2f\nY: %.2f\nZ: %.2f\nTorque: %.2f",
                        x, y, z, t);

                // Add full JSON to see all available information
                positionInfo += "\n\nRaw Feedback: " + positionFeedback.toString();

                tvAIResponse.append(positionInfo);
                Log.d(TAG, "Position feedback: " + positionFeedback);

                // Scroll to the bottom of the text view
                final ScrollView scrollView = (ScrollView) tvAIResponse.getParent().getParent();
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));

            } catch (JSONException e) {
                tvAIResponse.append("\nError parsing position data: " + e.getMessage());
                Log.e(TAG, "Error parsing position data", e);
            }
        } else {
            tvAIResponse.append("\nFailed to retrieve position data");
            Log.e(TAG, "Failed to get position feedback");
        }
    }
    private void controlGripper(boolean close) {
        isMoving = true;

        robotController.controlGripper(close);

        // Update UI to show what happened
        Toast.makeText(requireContext(),
                close ? "Starting gripper (closing)" : "Stopping gripper (opening)",
                Toast.LENGTH_SHORT).show();

        // Log the event
        Log.d(TAG, close ? "Gripper closing command sent" : "Gripper opening command sent");
        tvAIResponse.append("\n" + (close ? "Gripper closing command sent" : "Gripper opening command sent"));

        // Allow movement again after a short delay
        new Handler().postDelayed(() -> {
            isMoving = false;
        }, 1000);
    }

    // Split the IP configuration into two separate methods
    private void showServerIpConfigDialog() {
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
                    Log.d(TAG, "Updated server IP to: " + newIp);
                }
        ).show();
    }

    private void showRobotIpConfigDialog() {
        new IpConfigDialog(
                requireContext(),
                robotIp,
                "Configure Robot IP",
                newIp -> {
                    robotIp = newIp;
                    robotController.setRobotIp(newIp);
                    Toast.makeText(requireContext(),
                            "Robot IP updated to: " + newIp,
                            Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Updated robot IP to: " + newIp);
                }
        ).show();
    }

    private void showDebugInfo() {
        StringBuilder debugInfo = new StringBuilder();
        debugInfo.append("CURRENT CONFIGURATION\n\n");
        debugInfo.append("Server IP: ").append(serverIp).append("\n");
        debugInfo.append("Robot IP: ").append(robotController.getRobotIp()).append("\n");
        debugInfo.append("Robot Controller: ").append(robotController.getClass().getName()).append("\n");
        debugInfo.append("Detection Client: ").append(detectionClient.getClass().getName()).append("\n");
        debugInfo.append("isMoving: ").append(isMoving).append("\n");
        debugInfo.append("hasEmptyBoard: ").append(hasEmptyBoard).append("\n");

        // Show debug info in the mapped position area
        tvMappedPosition.setText(debugInfo.toString());

        // Log the information
        Log.d(TAG, "Debug Info: " + debugInfo.toString());

        Toast.makeText(requireContext(),
                "Debug info displayed",
                Toast.LENGTH_SHORT).show();
    }

    private void lookupAndMoveToPosition() {
        if (isMoving) {
            Toast.makeText(requireContext(),
                    "Robot is currently moving. Please wait.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Parse board coordinates from input fields
            int boardX = Integer.parseInt(etDebugBoardX.getText().toString().trim());
            int boardY = Integer.parseInt(etDebugBoardY.getText().toString().trim());

            // Lookup mapped coordinates
            CellCoordinate cellCoord = BoardCoordinatesAdapter.getInstance()
                    .getBoardCellCoordinate(boardX, boardY);

            if (cellCoord == null) {
                tvMappedPosition.setText("No mapping found for board coordinates (" +
                        boardX + "," + boardY + ")");
                return;
            }

            // Display the mapped coordinates with current robot IP
            String mappedInfo = String.format(Locale.US,
                    "Mapped Position:\n" +
                            "Board(%d,%d) → Grid(%d,%d)\n" +
                            "Robot X=%.2f, Y=%.2f, Z=%.2f, T=%.2f\n" +
                            "Using Robot IP: %s",
                    boardX, boardY,
                    cellCoord.getGridX(), cellCoord.getGridY(),
                    cellCoord.getX(), cellCoord.getY(), cellCoord.getZ(), cellCoord.getTorque(),
                    robotController.getRobotIp());

            tvMappedPosition.setText(mappedInfo);

            // Verify that the robot controller has the correct IP before moving
            Log.d(TAG, "Robot move using IP: " + robotController.getRobotIp());
            if (!robotController.getRobotIp().equals(robotIp)) {
                Log.w(TAG, "IP mismatch! Controller IP: " + robotController.getRobotIp() +
                        " vs. stored IP: " + robotIp);
                // Force update the IP to be safe
                robotController.setRobotIp(robotIp);
            }

            // Lock UI and set moving flag
            isMoving = true;
            btnLookupCoords.setEnabled(false);

            // Use the improved controller to move with verification
            CompletableFuture<Boolean> moveFuture = robotController.executeVerifiedMovement(
                    cellCoord.getX(), cellCoord.getY(), cellCoord.getZ(), cellCoord.getTorque(),
                    new RobotController.MovementCallback() {
                        @Override
                        public void onSuccess() {
                            requireActivity().runOnUiThread(() -> {
                                isMoving = false;
                                btnLookupCoords.setEnabled(true);
                                Toast.makeText(requireContext(),
                                        "Movement completed successfully",
                                        Toast.LENGTH_SHORT).show();
                            });
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            requireActivity().runOnUiThread(() -> {
                                isMoving = false;
                                btnLookupCoords.setEnabled(true);
                                Toast.makeText(requireContext(),
                                        "Movement failed: " + errorMessage,
                                        Toast.LENGTH_SHORT).show();
                            });
                        }

                        @Override
                        public void onProgress(String status) {
                            requireActivity().runOnUiThread(() -> {
                                tvMappedPosition.append("\n" + status);
                            });
                        }
                    });

        } catch (NumberFormatException e) {
            tvMappedPosition.setText("Invalid coordinates. Please enter valid numbers.");
        }
    }

    private void captureEmptyBoard() {
        btnCaptureEmpty.setEnabled(false);
        takePicture(true);
    }

    private void detectCurrentBoard() {
        btnDetectCurrent.setEnabled(false);
        takePicture(false);
    }

    private void takePicture(boolean isEmptyBoard) {
        if (imageCapture == null) {
            Log.e(TAG, "Cannot take picture, imageCapture is null");
            if (isEmptyBoard) {
                btnCaptureEmpty.setEnabled(true);
            } else {
                btnDetectCurrent.setEnabled(true);
            }
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
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(),
                                    "Failed to capture image: " + exception.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            if (isEmptyBoard) {
                                btnCaptureEmpty.setEnabled(true);
                            } else {
                                btnDetectCurrent.setEnabled(true);
                            }
                        });
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
                        btnCaptureEmpty.setEnabled(true);
                        btnDetectCurrent.setEnabled(true);
                        Toast.makeText(requireContext(),
                                "Empty board processed successfully",
                                Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String error) {
                    requireActivity().runOnUiThread(() -> {
                        btnCaptureEmpty.setEnabled(true);
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
                        btnDetectCurrent.setEnabled(true);
                        btnGetAIMove.setEnabled(true);

                        if (boardState != null) {
                            currentBoardState = boardState;

                            // Display the board state
                            StringBuilder display = new StringBuilder("Detected Board State:\n\n");
                            for (String row : boardState) {
                                display.append(row).append('\n');
                            }
                            tvBoardState.setText(display.toString());
                        }

                        Toast.makeText(requireContext(),
                                "Board state detected successfully",
                                Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String error) {
                    requireActivity().runOnUiThread(() -> {
                        btnDetectCurrent.setEnabled(true);
                        tvBoardState.setText("Error: " + error);
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
            return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }
        return null;
    }

    private void getAIMove() {
        btnGetAIMove.setEnabled(false);
        tvAIResponse.setText("Requesting AI move...");

        try {
            // Prepare the request to the AI server
            JSONObject jsonPayload = new JSONObject();

            // Convert the detected board state format (G/R/.) to AI format (O/X/.)
            List<String> convertedBoardState = new ArrayList<>();
            for (String row : currentBoardState) {
                // Replace 'G' with 'O' (human player) and 'R' with 'X' (robot)
                String convertedRow = row.replace('G', 'O').replace('R', 'X');
                convertedBoardState.add(convertedRow);
            }

            // Add the converted board state
            JSONArray boardState = new JSONArray();
            for (String row : convertedBoardState) {
                boardState.put(row);
            }

            // Log the converted board for debugging
            Log.d(TAG, "Converted board state for AI:");
            for (String row : convertedBoardState) {
                Log.d(TAG, row);
            }

            jsonPayload.put("board_state", boardState);
            jsonPayload.put("is_player1", false); // Robot is always player2 (X)
            jsonPayload.put("depth", 3); // Default depth, can be made configurable
            jsonPayload.put("eval_func", 1); // Default eval function, can be made configurable
            jsonPayload.put("use_heuristic", true);

            // Send the request to the AI server
            String url = String.format("http://%s:%d/get_ai_move", serverIp, AI_PORT);

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(jsonPayload.toString(), JSON))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    requireActivity().runOnUiThread(() -> {
                        btnGetAIMove.setEnabled(true);
                        tvAIResponse.setText("Error: " + e.getMessage());
                        Toast.makeText(requireContext(),
                                "Failed to connect to AI server: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    final String responseData = response.body().string();
                    requireActivity().runOnUiThread(() -> {
                        btnGetAIMove.setEnabled(true);
                        try {
                            JSONObject jsonResponse = new JSONObject(responseData);
                            if (jsonResponse.getString("status").equals("success")) {
                                JSONArray moveSequence = jsonResponse.getJSONArray("move_sequence");
                                lastRecommendedMoveSequence = moveSequence;
                                String info = "AI Move Sequence: " + moveSequence.toString();
                                tvAIResponse.setText(info);
                                btnExecuteMove.setEnabled(true);
                            }
                            else if (jsonResponse.getString("status").equals("no_move_possible")) {
                                // Handle scenario where no moves are possible
                                tvAIResponse.setText("No valid moves available. Your turn!");
                                btnExecuteMove.setEnabled(false);}
                            else {
                                tvAIResponse.setText("Error: " + jsonResponse.optString("message", "Unknown error"));
                            }
                        } catch (JSONException e) {
                            tvAIResponse.setText("Error parsing response: " + e.getMessage());
                        }
                    });
                }
            });

        } catch (JSONException e) {
            btnGetAIMove.setEnabled(true);
            tvAIResponse.setText("Error creating JSON payload: " + e.getMessage());
        }
    }

    private void executeAIMoveSequence(JSONArray moveSequence) {
        Log.d(TAG, "executeAIMoveSequence called");

        // Show immediate feedback
        Toast.makeText(requireContext(), "Executing AI move...", Toast.LENGTH_SHORT).show();

        if (moveSequence == null || moveSequence.length() < 2) {
            Toast.makeText(requireContext(), "Invalid move sequence", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable UI to prevent double clicks
        btnExecuteMove.setEnabled(false);
        tvAIResponse.append("\n\nExecuting move sequence...");

        try {
            // Set moving flag
            isMoving = true;

            // Step 1: Convert JSON array of board coordinates into CellCoordinates
            List<CellCoordinate> path = new ArrayList<>();
            for (int i = 0; i < moveSequence.length(); i++) {
                try {
                    JSONObject coordObj = moveSequence.getJSONObject(i);
                    int boardX = coordObj.getInt("x");
                    int boardY = coordObj.getInt("y");

                    Log.d(TAG, "Processing board coordinates: (" + boardX + "," + boardY + ")");

                    // Convert board coords -> robot coords
                    CellCoordinate cellCoord = BoardCoordinatesAdapter.getInstance()
                            .getBoardCellCoordinate(boardX, boardY);

                    if (cellCoord == null) {
                        Log.e(TAG, "No mapping for board coords (" + boardX + "," + boardY + ")");
                        continue;
                    }

                    path.add(cellCoord);
                    Log.d(TAG, "Added cell coordinate: (" + cellCoord.getGridX() + "," +
                            cellCoord.getGridY() + ") at robot position (X=" + cellCoord.getX() +
                            ", Y=" + cellCoord.getY() + ", Z=" + cellCoord.getZ() + ")");
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing coordinate at index " + i, e);
                }
            }

            if (path.size() < 2) {
                requireActivity().runOnUiThread(() -> {
                    isMoving = false;
                    btnExecuteMove.setEnabled(true);
                    tvAIResponse.append("\nError: Could not create a valid path with at least 2 points");
                    Toast.makeText(requireContext(), "Invalid path from AI", Toast.LENGTH_SHORT).show();

                });
                return;
            }

            // Log the path for debugging
            tvAIResponse.append("\nPath created with " + path.size() + " points");

            // Reset the arm first for safety
            tvAIResponse.append("\nResetting arm position...");
            robotController.reset();

            // Wait for reset to complete
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Execute the move with marble handling
            new Thread(() -> {
                try {
                    boolean success = executeMove(path);
                    requireActivity().runOnUiThread(() -> {
                        isMoving = false;
                        btnExecuteMove.setEnabled(true);
                        if (success) {
                            tvAIResponse.append("\nMove sequence completed successfully!");
                            Toast.makeText(requireContext(),
                                    "Move sequence completed successfully",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            tvAIResponse.append("\nMove sequence failed.");
                            Toast.makeText(requireContext(),
                                    "Move sequence failed",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error in move thread", e);
                    requireActivity().runOnUiThread(() -> {
                        isMoving = false;
                        btnExecuteMove.setEnabled(true);
                        tvAIResponse.append("\nError: " + e.getMessage());
                        Toast.makeText(requireContext(),
                                "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Error executing AI move sequence", e);
            requireActivity().runOnUiThread(() -> {
                isMoving = false;
                btnExecuteMove.setEnabled(true);
                tvAIResponse.append("\nError: " + e.getMessage());
                Toast.makeText(requireContext(),
                        "Error: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            });
        }
    }

    /**
     * Execute a complete move with marble handling
     * @param path List of coordinates representing the move path
     * @return true if successful, false if any step fails
     */
//    private boolean executeMove(List<CellCoordinate> path) {
//        // Constants for move parameters
//        final int MAX_RETRIES = 3;
//        final int RETRY_DELAY_MS = 2000;
//        final int MOVEMENT_WAIT_MS = 5000;
//        final int GRIPPER_WAIT_MS = 3000;
//        final float SAFE_Z = -80f;
//
//        // Get origin and destination
//        CellCoordinate origin = path.get(0);
//        CellCoordinate destination = path.get(path.size() - 1);
//
//        try {
//            // Step 1: Move to a safe position above the origin
//            updateProgress("Moving above origin piece");
//            if (!moveWithRetry(origin.getX(), origin.getY(), SAFE_Z, origin.getTorque(),
//                    MAX_RETRIES, RETRY_DELAY_MS, MOVEMENT_WAIT_MS)) {
//                updateProgress("Failed to move above origin");
//                return false;
//            }
//
//            // Step 2: Move down to the piece
//            updateProgress("Moving down to pick up piece");
//            if (!moveWithRetry(origin.getX(), origin.getY(), origin.getZ(), origin.getTorque(),
//                    MAX_RETRIES, RETRY_DELAY_MS, MOVEMENT_WAIT_MS)) {
//                updateProgress("Failed to move down to piece");
//                return false;
//            }
//
//            // Step 3: Close gripper to grab piece
//            updateProgress("Closing gripper to grab piece");
//            robotController.controlGripper(true);
//            Thread.sleep(GRIPPER_WAIT_MS);
//
//            // Step 4: Lift piece to safe height
//            updateProgress("Lifting piece");
//            if (!moveWithRetry(origin.getX(), origin.getY(), SAFE_Z, origin.getTorque(),
//                    MAX_RETRIES, RETRY_DELAY_MS, MOVEMENT_WAIT_MS)) {
//                updateProgress("Failed to lift piece");
//                // Release grip in case of failure
//                robotController.controlGripper(false);
//                return false;
//            }
//
//            // Step 5: Move through any intermediate points if they exist
//            if (path.size() > 2) {
//                for (int i = 1; i < path.size() - 1; i++) {
//                    CellCoordinate intermediate = path.get(i);
//                    updateProgress("Moving through position " + (i+1) + " of " + path.size());
//
//                    if (!moveWithRetry(intermediate.getX(), intermediate.getY(), SAFE_Z,
//                            intermediate.getTorque(), MAX_RETRIES, RETRY_DELAY_MS, MOVEMENT_WAIT_MS)) {
//                        updateProgress("Failed to move through intermediate position " + (i+1));
//                        // Release grip in case of failure
//                        robotController.controlGripper(false);
//                        return false;
//                    }
//                }
//            }
//
//            // Step 6: Move to position above destination
//            updateProgress("Moving above destination");
//            if (!moveWithRetry(destination.getX(), destination.getY(), SAFE_Z, destination.getTorque(),
//                    MAX_RETRIES, RETRY_DELAY_MS, MOVEMENT_WAIT_MS)) {
//                updateProgress("Failed to move above destination");
//                // Release grip in case of failure
//                robotController.controlGripper(false);
//                return false;
//            }
//
//            // Step 7: Lower piece to final position
//            updateProgress("Lowering piece to destination");
//            if (!moveWithRetry(destination.getX(), destination.getY(), destination.getZ(),
//                    destination.getTorque(), MAX_RETRIES, RETRY_DELAY_MS, MOVEMENT_WAIT_MS)) {
//                updateProgress("Failed to lower piece to destination");
//                // Release grip anyway to drop piece
//                robotController.controlGripper(false);
//                return false;
//            }
//
//            // Step 8: Open gripper to release piece
//            updateProgress("Opening gripper to release piece");
//            robotController.controlGripper(false);
//            Thread.sleep(GRIPPER_WAIT_MS);
//
//            // Step 9: Lift arm back to safe height
//            updateProgress("Lifting arm from destination");
//            if (!moveWithRetry(destination.getX(), destination.getY(), SAFE_Z, destination.getTorque(),
//                    MAX_RETRIES, RETRY_DELAY_MS, MOVEMENT_WAIT_MS)) {
//                updateProgress("Failed to lift arm from destination (but piece was placed)");
//                // Not a critical failure as the piece was placed
//                return true;
//            }
//
//            // Step 10: Return to home position
//            updateProgress("Returning to home position");
//            robotController.reset();
//            Thread.sleep(MOVEMENT_WAIT_MS);
//
//            return true;
//
//        } catch (Exception e) {
//            Log.e(TAG, "Error during move execution", e);
//            updateProgress("Error: " + e.getMessage());
//            // Try to release grip in case of exception
//            try {
//                robotController.controlGripper(false);
//            } catch (Exception ex) {
//                Log.e(TAG, "Error releasing gripper", ex);
//            }
//            return false;
//        }
//    }

    private boolean executeMove(List<CellCoordinate> path) {
        // Constants
        final int GRIPPER_WAIT_MS = 3000;
        final CountDownLatch moveLatch = new CountDownLatch(1);
        final AtomicBoolean moveSuccess = new AtomicBoolean(false);

        try {
            // Step 1: Move to origin position to pick up marble
            CellCoordinate origin = path.get(0);
            updateProgress("Moving to pick up marble");

            // Execute movement with callback
            CompletableFuture<Boolean> originMove = robotController.executeVerifiedMovement(
                    origin.getX(), origin.getY(), origin.getZ(), origin.getTorque(),
                    new RobotController.MovementCallback() {
                        @Override
                        public void onSuccess() {
                            updateProgress("Successfully reached pick up position");
                            moveSuccess.set(true);
                            moveLatch.countDown();
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            updateProgress("Failed to reach pick up position: " + errorMessage);
                            moveLatch.countDown();
                        }

                        @Override
                        public void onProgress(String status) {
                            updateProgress(status);
                        }
                    }
            );

            // Wait for movement to complete
            moveLatch.await();
            if (!moveSuccess.get()) {
                updateProgress("Failed to reach pick up position");
                return false;
            }

            // Step 2: Close gripper to grab marble
            updateProgress("Grabbing marble");
            robotController.controlGripper(true);
            Thread.sleep(GRIPPER_WAIT_MS);

            // Step 3: Move through all intermediate points in path
            for (int i = 1; i < path.size(); i++) {
                final CellCoordinate nextPosition = path.get(i);
                final int moveIndex = i;
                final CountDownLatch stepLatch = new CountDownLatch(1);
                final AtomicBoolean stepSuccess = new AtomicBoolean(false);

                updateProgress("Moving to position " + moveIndex + " of " + (path.size() - 1));

                CompletableFuture<Boolean> stepMove = robotController.executeVerifiedMovement(
                        nextPosition.getX(), nextPosition.getY(), nextPosition.getZ(), nextPosition.getTorque(),
                        new RobotController.MovementCallback() {
                            @Override
                            public void onSuccess() {
                                updateProgress("Successfully reached position " + moveIndex);
                                stepSuccess.set(true);
                                stepLatch.countDown();
                            }

                            @Override
                            public void onFailure(String errorMessage) {
                                updateProgress("Failed to reach position " + moveIndex + ": " + errorMessage);
                                stepLatch.countDown();
                            }

                            @Override
                            public void onProgress(String status) {
                                updateProgress(status);
                            }
                        }
                );

                // Wait for step to complete
                stepLatch.await();
                if (!stepSuccess.get()) {
                    updateProgress("Failed to reach position " + moveIndex);
                    robotController.controlGripper(false); // Release grip on failure
                    return false;
                }
            }

            // Step 4: Release marble at final position
            updateProgress("Releasing marble");
            robotController.controlGripper(false);
            Thread.sleep(GRIPPER_WAIT_MS);

            // Step 5: Return to home position
            updateProgress("Returning to home position");
            final CountDownLatch resetLatch = new CountDownLatch(1);

            // Send reset command asynchronously
            new Thread(() -> {
                robotController.reset();
                try {
                    Thread.sleep(2000); // Give reset time to execute
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                resetLatch.countDown();
            }).start();

            // Wait for reset to complete
            resetLatch.await();

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error during move execution", e);
            updateProgress("Error: " + e.getMessage());
            try {
                robotController.controlGripper(false); // Make sure to release gripper on error
            } catch (Exception ex) {
                Log.e(TAG, "Error releasing gripper", ex);
            }
            return false;
        }
    }

    /**
     * Helper method for movement with retry logic
     */
    /**
     * Helper method for movement with retry logic
     */
    private boolean moveWithRetry(float x, float y, float z, float torque,
                                  int maxRetries, int retryDelayMs, int waitTimeMs) {
        // Set position tolerance to 2mm (expressed in robot units)
        final float POSITION_TOLERANCE = 2.0f;
        // Maximum number of retry attempts
        final int MAX_TRIES = 5;
        // Step size of 1mm for each adjustment
        final float CORRECTION_STEP = 1.0f;

        for (int attempt = 1; attempt <= MAX_TRIES; attempt++) {
            try {
                // Calculate the target for this attempt
                float targetX = x;
                float targetY = y;
                float targetZ = z;

                // Only adjust the target on retry attempts
                if (attempt > 1) {
                    // Get current position to calculate error direction
                    JSONObject currentPos = robotController.getPositionFeedback();
                    if (currentPos != null) {
                        try {
                            float currentX = (float) currentPos.getDouble("x");
                            float currentY = (float) currentPos.getDouble("y");

                            // Calculate the error from the ORIGINAL target
                            float diffX = x - currentX;
                            float diffY = y - currentY;

                            // For first correction (attempt 2), just use the difference
//                            if (attempt == 2) {
                                if (attempt >= 2) {
                                targetX = x + diffX;
                                targetY = y + diffY;
                                updateProgress(String.format(Locale.US,
                                        "First correction (attempt %d):\n" +
                                                "Original target: (X=%.2f, Y=%.2f, Z=%.2f)\n" +
                                                "Current position: (X=%.2f, Y=%.2f)\n" +
                                                "Error: (X=%.2f, Y=%.2f)\n" +
                                                "Applying exact error correction\n" +
                                                "Adjusted target: (X=%.2f, Y=%.2f, Z=%.2f)",
                                        attempt,
                                        x, y, z,
                                        currentX, currentY,
                                        diffX, diffY,
                                        targetX, targetY, targetZ));
                            }
                            // For subsequent corrections, add increasing step size
                            else {
                                float additionalCorrection = CORRECTION_STEP * (attempt - 2);
                                targetX = x + diffX* 0 + (diffX >= 0 ? additionalCorrection : -additionalCorrection);
                                targetY = y + diffY* 0 + (diffY >= 0 ? additionalCorrection : -additionalCorrection);

                                updateProgress(String.format(Locale.US,
                                        "Correction attempt %d:\n" +
                                                "Original target: (X=%.2f, Y=%.2f, Z=%.2f)\n" +
                                                "Current position: (X=%.2f, Y=%.2f)\n" +
                                                "Error: (X=%.2f, Y=%.2f)\n" +
                                                "Additional correction: %.2f mm\n" +
                                                "Adjusted target: (X=%.2f, Y=%.2f, Z=%.2f)",
                                        attempt,
                                        x, y, z,
                                        currentX, currentY,
                                        diffX, diffY,
                                        additionalCorrection,
                                        targetX, targetY, targetZ));
                            }
                        } catch (JSONException e) {
                            updateProgress("Error parsing current position for adjustment: " + e.getMessage());
                        }
                    } else {
                        updateProgress("Couldn't get current position for adjustment. Using original target.");
                    }
                }

                // Send the movement command
                updateProgress("Sending movement command to: X=" + targetX + ", Y=" + targetY +
                        ", Z=" + targetZ + ", T=" + torque);
                robotController.moveTo(targetX, targetY, targetZ, torque);

                // Wait for movement to complete
                updateProgress("Waiting for movement to complete...");
                Thread.sleep(waitTimeMs);

                // Check position feedback
                updateProgress("Getting position feedback...");
                JSONObject feedback = robotController.getPositionFeedback();
                if (feedback != null) {
                    float currentX = (float) feedback.getDouble("x");
                    float currentY = (float) feedback.getDouble("y");
                    float currentZ = (float) feedback.getDouble("z");
                    float currentT = (float) feedback.getDouble("t");

                    // Calculate differences from the ORIGINAL TARGET
                    float diffX = x - currentX;
                    float diffY = y - currentY;

                    // Only check X and Y coordinates (exclude Z)
                    boolean isPositionCorrect =
                            Math.abs(diffX) <= POSITION_TOLERANCE &&
                                    Math.abs(diffY) <= POSITION_TOLERANCE;

                    if (isPositionCorrect) {
                        updateProgress("Position reached successfully with tolerance of " + POSITION_TOLERANCE + "mm");
                        updateProgress(String.format(Locale.US,
                                "Target: (X=%.2f, Y=%.2f, Z=%.2f, T=%.2f)",
                                x, y, z, torque));
                        updateProgress(String.format(Locale.US,
                                "Final position: (X=%.2f, Y=%.2f, Z=%.2f, T=%.2f)",
                                currentX, currentY, currentZ, currentT));
                        return true;
                    } else {
                        String positionInfo = String.format(Locale.US,
                                "Position not reached (attempt %d/%d)\n" +
                                        "Original target: (X=%.2f, Y=%.2f, Z=%.2f, T=%.2f)\n" +
                                        "Actual position: (X=%.2f, Y=%.2f, Z=%.2f, T=%.2f)\n" +
                                        "Error: (X=%.2f, Y=%.2f)\n" +
                                        "Tolerance: %.2f",
                                attempt, MAX_TRIES,
                                x, y, z, torque,
                                currentX, currentY, currentZ, currentT,
                                diffX, diffY,
                                POSITION_TOLERANCE);

                        updateProgress(positionInfo);
                        updateProgress("Raw feedback: " + feedback.toString());
                    }
                } else {
                    updateProgress("Could not get position feedback");
                }

                // If we're here, position wasn't reached - wait before retry
                if (attempt < MAX_TRIES) {
                    updateProgress("Waiting " + retryDelayMs + "ms before retry...");
                    Thread.sleep(retryDelayMs);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error during move attempt " + attempt, e);
                updateProgress("Error: " + e.getMessage());

                try {
                    if (attempt < MAX_TRIES) {
                        Thread.sleep(retryDelayMs);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        updateProgress("Failed to reach position after " + MAX_TRIES + " attempts");
        return false;  // All retries failed
    }

    /**
     * Update progress on the UI thread
     */
    private void updateProgress(String message) {
        Log.d(TAG, message);
        requireActivity().runOnUiThread(() -> {
            tvAIResponse.append("\n" + message);
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Set up the preview use case
                Preview preview = new Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build();

                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Configure image capture use case
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
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
                camera.getCameraControl().setLinearZoom(0.0f);

                Log.d(TAG, "Camera started successfully");

            } catch (ExecutionException | InterruptedException e) {
                String msg = "Error starting camera: " + e.getMessage();
                Log.e(TAG, msg, e);
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }
}