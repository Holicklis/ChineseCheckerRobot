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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

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
    private String serverIp = "192.168.11.230"; // Default
    private String robotIp = "192.168.10.227";
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

            // Execute movement in background
            new Thread(() -> {
                try {
                    // Use precision sequence to move to this position
                    robotController.moveToWithPrecisionSequence(
                            cellCoord.getX(),
                            cellCoord.getY(),
                            cellCoord.getZ(),
                            cellCoord.getTorque()
                    );

                    // Wait for movement to complete (4 steps * delay)
                    Thread.sleep(4 * 2000);

                    // Re-enable UI on main thread
                    requireActivity().runOnUiThread(() -> {
                        isMoving = false;
                        btnLookupCoords.setEnabled(true);
                        Toast.makeText(requireContext(),
                                "Movement completed",
                                Toast.LENGTH_SHORT).show();
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Error moving to position: " + e.getMessage(), e);
                    requireActivity().runOnUiThread(() -> {
                        isMoving = false;
                        btnLookupCoords.setEnabled(true);
                        Toast.makeText(requireContext(),
                                "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();

        } catch (NumberFormatException e) {
            tvMappedPosition.setText("Invalid coordinates. Please enter valid numbers.");
        }
    }

    private void showIpConfigDialog() {
        new IpConfigDialog(
                requireContext(),
                serverIp,
                "Configure Server IP",
                newIp -> {
                    // Update the server IP for detection client
                    serverIp = newIp;
                    detectionClient = new BoardDetectionClient(serverIp);

                    // IMPORTANT: Also update the robot controller's IP
                    robotController.setRobotIp(newIp);

                    Toast.makeText(requireContext(),
                            "IP updated to: " + newIp + " (for both server and robot)",
                            Toast.LENGTH_SHORT).show();

                    // Log the update for debugging
                    Log.d(TAG, "Updated IP to " + newIp + " for both server and robot controller");
                }
        ).show();
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

    private String formatMoveInfo(JSONObject move) throws JSONException {
        JSONObject origin = move.getJSONObject("origin");
        JSONObject destination = move.getJSONObject("destination");

        return String.format("Move from: (%d, %d)\nMove to: (%d, %d)",
                origin.getInt("x"), origin.getInt("y"),
                destination.getInt("x"), destination.getInt("y"));
    }

    /**
     * Executes a move from the AI server
     */
    private void executeAIMove(JSONObject move) {
        try {
            // Disable the button to prevent multiple executions
            btnExecuteMove.setEnabled(false);
            tvAIResponse.append("\n\nExecuting move...");

            JSONObject origin = move.getJSONObject("origin");
            JSONObject destination = move.getJSONObject("destination");

            // If the AI returns a path/sequence, it would be in a format like:
            // {"origin": {"x": 3, "y": 13}, "path": [{"x": 4, "y": 12}, {"x": 5, "y": 11}, ...]}
            JSONArray pathSequence = move.optJSONArray("path");

            // Get the origin coordinates
            int originX = origin.getInt("x");
            int originY = origin.getInt("y");
            CellCoordinate originCoord = BoardCoordinatesAdapter.getInstance()
                    .getBoardCellCoordinate(originX, originY);

            if (originCoord == null) {
                tvAIResponse.append("\nError: Could not find coordinates for origin (" +
                        originX + "," + originY + ")");
                btnExecuteMove.setEnabled(true);
                return;
            }

            // Get the destination coordinates
            int destX = destination.getInt("x");
            int destY = destination.getInt("y");
            CellCoordinate destCoord = BoardCoordinatesAdapter.getInstance()
                    .getBoardCellCoordinate(destX, destY);

            if (destCoord == null) {
                tvAIResponse.append("\nError: Could not find coordinates for destination (" +
                        destX + "," + destY + ")");
                btnExecuteMove.setEnabled(true);
                return;
            }

            // Process intermediate points if available
            List<CellCoordinate> intermediatePoints = null;
            if (pathSequence != null && pathSequence.length() > 0) {
                intermediatePoints = new ArrayList<>();

                for (int i = 0; i < pathSequence.length(); i++) {
                    JSONObject point = pathSequence.getJSONObject(i);
                    int x = point.getInt("x");
                    int y = point.getInt("y");

                    CellCoordinate coord = BoardCoordinatesAdapter.getInstance()
                            .getBoardCellCoordinate(x, y);

                    if (coord != null) {
                        intermediatePoints.add(coord);
                    } else {
                        Log.w(TAG, "No mapping found for intermediate point (" + x + "," + y + ")");
                    }
                }
            }

            // Execute the move
            robotController.executeCheckerMove(originCoord, destCoord, intermediatePoints);

            // Schedule UI update after expected move duration
            int totalSteps = 9 + (intermediatePoints == null ? 0 : intermediatePoints.size());
            int estimatedDuration = totalSteps * 2000; // 2 seconds per step

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                tvAIResponse.append("\nMove completed!");
                btnExecuteMove.setEnabled(true);
                btnGetAIMove.setEnabled(false);
                btnDetectCurrent.setEnabled(true);

                // Toggle turn for next move
                isPlayer1Turn = !isPlayer1Turn;
            }, estimatedDuration);

        } catch (JSONException e) {
            tvAIResponse.append("\nError parsing move: " + e.getMessage());
            btnExecuteMove.setEnabled(true);
        }
    }

    /**
     * Execute a simplified move sequence from the AI recommendations.
     * Follows a clear, sequential process with adequate delays between steps.
     * @param moveSequence JSONArray of coordinates from the AI
     */
    private void executeAIMoveSequence(JSONArray moveSequence) {
        if (moveSequence == null || moveSequence.length() < 2) {
            Toast.makeText(requireContext(), "Invalid move sequence", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable UI to prevent double clicks
        btnExecuteMove.setEnabled(false);
        tvAIResponse.append("\n\nExecuting move sequence...");

        new Thread(() -> {
            try {
                // Step 1: Convert JSON array of board coordinates into CellCoordinates
                List<CellCoordinate> path = new ArrayList<>();
                for (int i = 0; i < moveSequence.length(); i++) {
                    JSONObject coordObj = moveSequence.getJSONObject(i);
                    int boardX = coordObj.getInt("x");
                    int boardY = coordObj.getInt("y");

                    // Convert board coords -> robot coords
                    CellCoordinate cellCoord = BoardCoordinatesAdapter.getInstance()
                            .getBoardCellCoordinate(boardX, boardY);
                    if (cellCoord == null) {
                        Log.e(TAG, "No mapping for board coords (" + boardX + "," + boardY + ")");
                        continue;
                    }
                    path.add(cellCoord);
                }

                if (path.size() < 2) {
                    requireActivity().runOnUiThread(() -> {
                        btnExecuteMove.setEnabled(true);
                        Toast.makeText(requireContext(), "Invalid path from AI", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // Log the path for debugging
                StringBuilder pathInfo = new StringBuilder("Path: ");
                for (CellCoordinate coord : path) {
                    pathInfo.append(String.format("(%.1f,%.1f) ", coord.getX(), coord.getY()));
                }
                Log.d(TAG, pathInfo.toString());

                // Step 2: Get references
                RobotController robot = RobotController.getInstance();
                final int STEP_DELAY = 5000; // 4 seconds between steps

                // Step 3: Reset to initial position
                updateStatus("Resetting to home position...");
                robot.reset();
                Thread.sleep(STEP_DELAY);

                // Step 4: First position is starting cell
                CellCoordinate startCell = path.get(0);
                float safeZ = -40.0f; // Safe height to avoid collisions

                // Step 5: Move above starting cell
                updateStatus("Moving above starting position...");
                robot.moveToWithPrecisionSequence(
                        startCell.getX(),
                        startCell.getY(),
                        safeZ,
                        startCell.getTorque()
                );
                Thread.sleep(STEP_DELAY);

                // Step 6: Move down to starting cell
                updateStatus("Moving down to pick up marble...");
                robot.moveToWithPrecisionSequence(
                        startCell.getX(),
                        startCell.getY(),
                        startCell.getZ(),
                        startCell.getTorque()
                );
                Thread.sleep(STEP_DELAY);

                // Step 7: Close gripper (grab piece)
                updateStatus("Grabbing marble...");
                robot.controlGripper(true);  // Close gripper
                Thread.sleep(STEP_DELAY);

                // Step 8: Move up to safe height
                updateStatus("Moving back up to safe height...");
                robot.moveToWithPrecisionSequence(
                        startCell.getX(),
                        startCell.getY(),
                        safeZ,
                        startCell.getTorque()
                );
                Thread.sleep(STEP_DELAY);

                // Step 9: Process intermediate positions (if any)
                for (int i = 1; i < path.size() - 1; i++) {
                    CellCoordinate midCell = path.get(i);
                    updateStatus("Moving to intermediate position " + i + "...");

                    // Move above the intermediate cell
                    robot.moveToWithPrecisionSequence(
                            midCell.getX(),
                            midCell.getY(),
                            safeZ,
                            midCell.getTorque()
                    );
                    Thread.sleep(STEP_DELAY);
                }

                // Step 10: Move to destination (last cell)
                CellCoordinate destCell = path.get(path.size() - 1);
                updateStatus("Moving above destination...");
                robot.moveToWithPrecisionSequence(
                        destCell.getX(),
                        destCell.getY(),
                        safeZ,
                        destCell.getTorque()
                );
                Thread.sleep(STEP_DELAY);

                // Step 11: Lower to destination
                updateStatus("Moving down to place marble...");
                robot.moveToWithPrecisionSequence(
                        destCell.getX(),
                        destCell.getY(),
                        destCell.getZ(),
                        destCell.getTorque()
                );
                Thread.sleep(STEP_DELAY);

                // Step 12: Open gripper (release piece)
                updateStatus("Releasing marble...");
                robot.controlGripper(false);  // Open gripper
                Thread.sleep(STEP_DELAY);

                // Step 13: Move back up
                updateStatus("Moving back to safe height...");
                robot.moveToWithPrecisionSequence(
                        destCell.getX(),
                        destCell.getY(),
                        safeZ,
                        destCell.getTorque()
                );
                Thread.sleep(STEP_DELAY);

                // Move completed
                requireActivity().runOnUiThread(() -> {
                    btnExecuteMove.setEnabled(true);
                    tvAIResponse.append("\nMove sequence completed!");
                    Toast.makeText(requireContext(), "Move sequence completed", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error executing AI move sequence", e);
                requireActivity().runOnUiThread(() -> {
                    btnExecuteMove.setEnabled(true);
                    tvAIResponse.append("\nError: " + e.getMessage());
                    Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * Helper method to update status on UI thread
     */
    private void updateStatus(String status) {
        Log.d(TAG, status);
        requireActivity().runOnUiThread(() -> {
            tvAIResponse.append("\n" + status);
        });
    }

    private void executeRobotMove(CellCoordinate origin, CellCoordinate destination) {
        // Disable button to prevent multiple executions
        btnExecuteMove.setEnabled(false);
        tvAIResponse.append("\n\nExecuting robot move...");

        // Define the sequence of operations required for a complete move
        final int STEP_DELAY_MS = 2000; // Time between each step

        new Thread(() -> {
            try {
                // Step 1: Move to position above the origin
                Log.d(TAG, "Step 1: Moving to position above the origin piece");
                robotController.moveToWithPrecisionSequence(
                        origin.getX(), origin.getY(), 100.0f, origin.getTorque());
                Thread.sleep(STEP_DELAY_MS);

                // Step 2: Lower to the piece
                Log.d(TAG, "Step 2: Lowering to grab the piece");
                robotController.moveToWithPrecisionSequence(
                        origin.getX(), origin.getY(), origin.getZ(), origin.getTorque());
                Thread.sleep(STEP_DELAY_MS);

                // Step 3: Close gripper (grab piece)
                Log.d(TAG, "Step 3: Grabbing the piece");
                // You would add code here to activate the gripper
                Thread.sleep(STEP_DELAY_MS);

                // Step 4: Lift piece up
                Log.d(TAG, "Step 4: Lifting the piece");
                robotController.moveToWithPrecisionSequence(
                        origin.getX(), origin.getY(), 100.0f, origin.getTorque());
                Thread.sleep(STEP_DELAY_MS);

                // Step 5: Move to position above destination
                Log.d(TAG, "Step 5: Moving above the destination");
                robotController.moveToWithPrecisionSequence(
                        destination.getX(), destination.getY(), 100.0f, destination.getTorque());
                Thread.sleep(STEP_DELAY_MS);

                // Step 6: Lower to destination position
                Log.d(TAG, "Step 6: Lowering to destination position");
                robotController.moveToWithPrecisionSequence(
                        destination.getX(), destination.getY(), destination.getZ(), destination.getTorque());
                Thread.sleep(STEP_DELAY_MS);

                // Step 7: Open gripper (release piece)
                Log.d(TAG, "Step 7: Releasing the piece");
                // You would add code here to deactivate the gripper
                Thread.sleep(STEP_DELAY_MS);

                // Step 8: Move back up to safe height
                Log.d(TAG, "Step 8: Moving back to safe height");
                robotController.moveToWithPrecisionSequence(
                        destination.getX(), destination.getY(), 100.0f, destination.getTorque());

                // Update the board state in the UI
                requireActivity().runOnUiThread(() -> {
                    tvAIResponse.append("\nMove completed successfully!");
                    tvAIResponse.append("\nPlease detect the current board state again to continue the game.");
                    btnExecuteMove.setEnabled(false);
                    btnGetAIMove.setEnabled(false);
                    btnDetectCurrent.setEnabled(true);

                    // Toggle turn for next move
                    isPlayer1Turn = !isPlayer1Turn;
                });

            } catch (Exception e) {
                Log.e(TAG, "Error executing robot move: " + e.getMessage(), e);
                requireActivity().runOnUiThread(() -> {
                    tvAIResponse.append("\nError during move: " + e.getMessage());
                    btnExecuteMove.setEnabled(true);
                });
            }
        }).start();
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