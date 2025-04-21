package hku.cs.fyp24057.chinesecheckerrobot;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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

    private static final String TAG = "IntegratedAIGameFrag";

    // UI references
    private PreviewView previewView;
    private EditText etDebugBoardX, etDebugBoardY;
    private Button btnLookupCoords, btnShowDebugInfo;
    private TextView tvMappedPosition;
    private TextView tvBoardState, tvAIResponse;

    private Button btnCaptureEmpty;
    private Button btnDetectCurrent;
    private Button btnGetAIMove;
    private Button btnExecuteMove;
    private Button btnConfigureServerIp;
    private Button btnConfigureRobotIp;
    private Button btnStartGripper;
    private Button btnStopGripper;
    private Button btnResetArm;
    private Button btnDetectPosition;
    private Button btnAutoPlay; // The new "Auto Play" button

    // State
    private boolean isMoving = false;
    private boolean hasEmptyBoard = false;
    private List<String> currentBoardState = null;           // set after detectCurrentBoard() finishes
    private JSONArray lastRecommendedMoveSequence = null;    // set after getAIMove() finishes

    // Camera
    private ImageCapture imageCapture;

    private enum AutoPlayState {
        IDLE, DETECTING_BOARD, CALCULATING_MOVE, EXECUTING_MOVE, COMPLETED
    }
    private AutoPlayState autoPlayState = AutoPlayState.IDLE;

    // Network / Robot
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private BoardDetectionClient detectionClient;
    private RobotController robotController;
    private String serverIp = "192.168.11.192";  // Example defaults
    private String robotIp = "192.168.11.172";

    // For AI requests
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int AI_PORT = 5002;

    // Create a Handler for polling; we'll remove callbacks in onDestroyView
    private final Handler pollHandler = new Handler(Looper.getMainLooper());

    private MediaPlayer soundPlayer;

    // Lifecycle
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize detection and robot with default IPs
        detectionClient = new BoardDetectionClient(serverIp);
        robotController = RobotController.getInstance();
        robotController.setRobotIp(robotIp);

        initSoundPlayer();

        Log.d(TAG, "Initialized with server IP: " + serverIp);
        Log.d(TAG, "Initialized with robot IP: " + robotIp);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_integrated_ai_game, container, false);

        // Make sure the root can take focus immediately
        rootView.setFocusableInTouchMode(true);
        rootView.requestFocus();
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

//        setupInputFields();

        // Hook up UI
        previewView = view.findViewById(R.id.previewView);
        tvBoardState = view.findViewById(R.id.tvBoardState);
        tvAIResponse = view.findViewById(R.id.tvAIResponse);

//        etDebugBoardX = view.findViewById(R.id.etDebugBoardX);
//        etDebugBoardY = view.findViewById(R.id.etDebugBoardY);
        tvMappedPosition = view.findViewById(R.id.tvMappedPosition);

        btnLookupCoords = view.findViewById(R.id.btnLookupCoords);
        btnShowDebugInfo = view.findViewById(R.id.btnShowDebugInfo);
        btnCaptureEmpty = view.findViewById(R.id.btnCaptureEmpty);
        btnDetectCurrent = view.findViewById(R.id.btnDetectCurrent);
        btnGetAIMove = view.findViewById(R.id.btnGetAIMove);
        btnExecuteMove = view.findViewById(R.id.btnExecuteMove);
//        btnConfigureServerIp = view.findViewById(R.id.btnConfigureServerIp);
        btnConfigureRobotIp = view.findViewById(R.id.btnConfigureRobotIp);
        btnStartGripper = view.findViewById(R.id.btnStartGripper);
        btnStopGripper = view.findViewById(R.id.btnStopGripper);
        btnResetArm = view.findViewById(R.id.btnResetArm);
        btnDetectPosition = view.findViewById(R.id.btnDetectPosition);
        btnAutoPlay = view.findViewById(R.id.btnAutoPlay);
        view.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_E) {
                Log.d(TAG, "E/e key pressed -> autoPlay()!");
                if (btnAutoPlay != null && btnAutoPlay.isEnabled()) {
                    btnAutoPlay.performClick();
                    return true; // consume event
                }
            }
            return false; // let others handle it otherwise
        });
        setupButtons();
//        setupKeyboardListener();

        // Start camera if permission granted
        if (ContextCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 1001);
        }

        //sound

    }
    private void initSoundPlayer() {
        // Create and configure the media player
        soundPlayer = MediaPlayer.create(requireContext(), R.raw.button_click);
        soundPlayer.setOnCompletionListener(mp -> {
            // Reset the player when sound completes
            mp.seekTo(0);
        });
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Cancel any pending polling callbacks to avoid updating UI after view is destroyed
        pollHandler.removeCallbacksAndMessages(null);

        // Stop and release media player
        if (soundPlayer != null) {
            soundPlayer.release();
            soundPlayer = null;
        }

        // Cancel any pending polling callbacks
        pollHandler.removeCallbacksAndMessages(null);

        super.onDestroyView();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
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

    /**
     * Sets up all button listeners.
     */
    private void setupButtons() {
        btnCaptureEmpty.setOnClickListener(v -> captureEmptyBoard());

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

//        btnConfigureServerIp.setOnClickListener(v -> showServerIpConfigDialog());
        btnConfigureRobotIp.setOnClickListener(v -> showRobotIpConfigDialog());

        btnShowDebugInfo.setOnClickListener(v -> showDebugInfo());
//        btnLookupCoords.setOnClickListener(v -> lookupAndMoveToPosition());
        btnLookupCoords.setOnClickListener(v -> showBoardCoordsDialog());


        btnStartGripper.setOnClickListener(v -> {
            if (!isMoving) {
                controlGripper(true);
            } else {
                Toast.makeText(requireContext(),
                        "Robot is currently moving. Please wait.",
                        Toast.LENGTH_SHORT).show();
            }
        });
        btnStopGripper.setOnClickListener(v -> {
            if (!isMoving) {
                controlGripper(false);
            } else {
                Toast.makeText(requireContext(),
                        "Robot is currently moving. Please wait.",
                        Toast.LENGTH_SHORT).show();
            }
        });

        btnResetArm.setOnClickListener(v -> {
            if (!isMoving) {
                isMoving = true;
                safeRunOnUiThread(() -> tvAIResponse.append("\nResetting arm to home position..."));
                robotController.reset();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    isMoving = false;
                    safeRunOnUiThread(() -> tvAIResponse.append("\nReset completed."));
                }, 500);
            } else {
                Toast.makeText(requireContext(),
                        "Robot is currently moving. Please wait.",
                        Toast.LENGTH_SHORT).show();
            }
        });

        btnDetectPosition.setOnClickListener(v -> {
            if (isMoving) {
                Toast.makeText(requireContext(),
                        "Robot is currently moving. Please wait.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            safeRunOnUiThread(() -> tvAIResponse.append("\nRequesting current position..."));
            JSONObject positionFeedback = robotController.getPositionFeedback();
            if (positionFeedback != null) {
                try {
                    float x = (float) positionFeedback.getDouble("x");
                    float y = (float) positionFeedback.getDouble("y");
                    float z = (float) positionFeedback.getDouble("z");
                    float t = (float) positionFeedback.getDouble("t");

                    final String positionInfo = String.format(Locale.US,
                            "\n\nCurrent Position:\nX: %.2f\nY: %.2f\nZ: %.2f\nTorque: %.2f",
                            x, y, z, t) + "\n\nRaw Feedback: " + positionFeedback.toString();
                    safeRunOnUiThread(() -> tvAIResponse.append(positionInfo));

                    final ScrollView scrollView = (ScrollView) tvAIResponse.getParent().getParent();
                    scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
                } catch (JSONException e) {
                    safeRunOnUiThread(() -> tvAIResponse.append("\nError parsing position data: " + e.getMessage()));
                    Log.e(TAG, "Error parsing position data", e);
                }
            } else {
                safeRunOnUiThread(() -> tvAIResponse.append("\nFailed to retrieve position data"));
                Log.e(TAG, "Failed to get position feedback");
            }
        });

        // Auto Play button – calls our poll‑based autoPlay
        btnAutoPlay.setOnClickListener(v -> {
            playButtonSound();
            autoPlay();
        });

        // Initially disable these until each step is complete
        btnDetectCurrent.setEnabled(false);
        btnGetAIMove.setEnabled(false);
        btnExecuteMove.setEnabled(false);

        updateAutoPlayButtonState();
    }

    // ------------------------------------------------------------------
    //  AUTO PLAY IMPLEMENTATION (Poll‑based)
    // ------------------------------------------------------------------

    /**
     * autoPlay() – reuses your existing detectCurrentBoard(), getAIMove(), and executeAIMoveSequence()
     * in sequence. It polls every second to see if each step is complete.
     */
    private void autoPlay() {
        if (isMoving) {
            Toast.makeText(requireContext(), "Robot is currently moving. Please wait.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasEmptyBoard) {
            safeRunOnUiThread(() -> tvAIResponse.append("\n⚠️ Please capture an empty board first!\n"));
            Toast.makeText(requireContext(), "Please capture empty board first", Toast.LENGTH_SHORT).show();
            return;
        }
        autoPlayState = AutoPlayState.DETECTING_BOARD;
        // Play the sound effect
        playButtonSound();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Continue with the rest of autoPlay logic
            safeRunOnUiThread(() -> tvAIResponse.setText("Starting autoPlay...\n1) Detecting current board...\n"));
            // Call detectCurrentBoard() as if the button was pressed
            detectCurrentBoard();
            pollForBoardDetection();
        }, 100);
    }

    private void pollForBoardDetection() {
        pollHandler.postDelayed(() -> {
            if (!isAdded() || getView() == null) return; // do not update if fragment is detached
            if (currentBoardState != null && !currentBoardState.isEmpty()) {
                safeRunOnUiThread(() -> tvAIResponse.append("Board detected!\n2) Getting AI move...\n"));
                getAIMove();
                pollForAIMove();
            } else {
                safeRunOnUiThread(() -> tvAIResponse.append("."));
                pollForBoardDetection();
            }
        }, 1000);
    }

    private void pollForAIMove() {
        pollHandler.postDelayed(() -> {
            if (!isAdded() || getView() == null) return;
            if (lastRecommendedMoveSequence != null) {
                safeRunOnUiThread(() -> tvAIResponse.append("\nGot AI move.\n3) Executing AI move...\n"));
                executeAIMoveSequence(lastRecommendedMoveSequence);
                pollForMoveExecution();
            } else {
                safeRunOnUiThread(() -> tvAIResponse.append("."));
                pollForAIMove();
            }
        }, 1000);
    }

    private void pollForMoveExecution() {
        pollHandler.postDelayed(() -> {
            if (!isAdded() || getView() == null) return;
            if (!isMoving) {
                safeRunOnUiThread(() -> {
                    tvAIResponse.append("\nMove execution complete!\n");
                    tvAIResponse.append("AutoPlay is finished.\n");
                    pollHandler.removeCallbacksAndMessages(null);
                    lastRecommendedMoveSequence = null;
                    currentBoardState = null;
                });
            } else {
                safeRunOnUiThread(() -> tvAIResponse.append("."));
                pollForMoveExecution();
            }
        }, 1000);
    }

    // ------------------------------------------------------------------
    //  EXISTING FUNCTIONS (Detect, AI, Execute) – Unchanged
    // ------------------------------------------------------------------

    private void captureEmptyBoard() {
        if (imageCapture == null) {          // not ready yet
            btnCaptureEmpty.postDelayed(this::captureEmptyBoard, 250);
            return;
        }
        btnCaptureEmpty.setEnabled(false);
        Toast.makeText(requireContext(),
                "Capturing empty board...",
                Toast.LENGTH_SHORT).show();
        takePicture(true);
    }

    private void detectCurrentBoard() {
        if (imageCapture == null) {          // same guard
            btnDetectCurrent.postDelayed(this::detectCurrentBoard, 250);
            return;
        }
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
                            Toast.makeText(requireContext(),
                                    "Image captured successfully",
                                    Toast.LENGTH_SHORT).show();
                            processImage(bitmap, isEmptyBoard);
                        }
                        image.close();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Image capture failed", exception);
                        safeRunOnUiThread(() -> {
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
            Toast.makeText(requireContext(),
                    "Processing empty board...",
                    Toast.LENGTH_SHORT).show();
            detectionClient.uploadEmptyBoard(bitmap, new BoardDetectionClient.DetectionCallback() {
                @Override
                public void onSuccess(List<String> boardState) {
                    safeRunOnUiThread(() -> {
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
                    safeRunOnUiThread(() -> {
                        btnCaptureEmpty.setEnabled(true);
                        Toast.makeText(requireContext(),
                                "Error: " + error,
                                Toast.LENGTH_LONG).show();
                    });
                }
            });
        } else {
            detectionClient.detectCurrentState(bitmap, new BoardDetectionClient.DetectionCallback() {
                @Override
                public void onSuccess(List<String> boardState) {
                    safeRunOnUiThread(() -> {
                        btnDetectCurrent.setEnabled(true);
                        btnGetAIMove.setEnabled(true);
                        if (boardState != null) {
                            currentBoardState = boardState;
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
                    safeRunOnUiThread(() -> {
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
        safeRunOnUiThread(() -> tvAIResponse.setText("Requesting AI move..."));

        try {
            List<String> convertedBoard = new ArrayList<>();
            for (String row : currentBoardState) {
                convertedBoard.add(row.replace('G','O').replace('R','X'));
            }

            JSONArray boardStateJson = new JSONArray();
            for (String row : convertedBoard) {
                boardStateJson.put(row);
            }

            JSONObject jsonPayload = new JSONObject();
            jsonPayload.put("board_state", boardStateJson);
            jsonPayload.put("is_player1", false);
            jsonPayload.put("depth", 3);
            jsonPayload.put("eval_func", 1);
            jsonPayload.put("use_heuristic", true);

//            String url = "http://" + serverIp + ":" + AI_PORT + "/get_ai_move";
            String url = "https://chinesecheckerrobot-zu9g.onrender.com/get_ai_move";
            Request request = new Request.Builder()

                    .url(url)
                    .post(RequestBody.create(jsonPayload.toString(), JSON))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    safeRunOnUiThread(() -> {
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
                    safeRunOnUiThread(() -> {
                        btnGetAIMove.setEnabled(true);
                        try {
                            JSONObject jsonResponse = new JSONObject(responseData);
                            String status = jsonResponse.optString("status", "error");
                            if ("success".equals(status)) {
                                JSONArray moveSeq = jsonResponse.getJSONArray("move_sequence");
                                lastRecommendedMoveSequence = moveSeq;
                                tvAIResponse.setText("AI Move Sequence: " + moveSeq.toString());
                                btnExecuteMove.setEnabled(true);
                            } else if ("no_move_possible".equals(status)) {
                                tvAIResponse.setText("No valid moves available. Your turn!");
                                btnExecuteMove.setEnabled(false);
                            } else {
                                tvAIResponse.setText("AI Error: " + jsonResponse.optString("message", "Unknown"));
                            }
                        } catch (JSONException e) {
                            tvAIResponse.setText("Error parsing AI response: " + e.getMessage());
                        }
                    });
                }
            });
        } catch (JSONException e) {
            btnGetAIMove.setEnabled(true);
            safeRunOnUiThread(() -> tvAIResponse.setText("Error creating JSON payload: " + e.getMessage()));
        }
    }

    private void executeAIMoveSequence(JSONArray moveSequence) {
        Log.d(TAG, "executeAIMoveSequence called");
        Toast.makeText(requireContext(), "Executing AI move...", Toast.LENGTH_SHORT).show();
        if (moveSequence == null || moveSequence.length() < 2) {
            Toast.makeText(requireContext(), "Invalid move sequence", Toast.LENGTH_SHORT).show();
            return;
        }

        btnExecuteMove.setEnabled(false);
        safeRunOnUiThread(() -> tvAIResponse.append("\n\nExecuting move sequence..."));

        try {
            isMoving = true;
            updateAutoPlayButtonState();

            new Thread(() -> {
                try {
                    List<CellCoordinate> path = new ArrayList<>();
                    for (int i = 0; i < moveSequence.length(); i++) {
                        JSONObject coordObj = moveSequence.getJSONObject(i);
                        int boardX = coordObj.getInt("x");
                        int boardY = coordObj.getInt("y");
                        CellCoordinate cell = BoardCoordinatesAdapter.getInstance().getBoardCellCoordinate(boardX, boardY);
                        if (cell != null) {
                            path.add(cell);
                        } else {
                            Log.e(TAG, "No mapping for (" + boardX + "," + boardY + ")");
                        }
                    }
                    if (path.size() < 2) {
                        safeRunOnUiThread(() -> {
                            isMoving = false;
                            updateAutoPlayButtonState();
                            btnExecuteMove.setEnabled(true);
                            tvAIResponse.append("\nError: Path must have at least 2 points");
                        });
                        return;
                    }
                    safeRunOnUiThread(() -> tvAIResponse.append("\nPath created with " + path.size() + " points"));
                    robotController.reset();
                    Thread.sleep(2000);

                    boolean success = executeMove(path);
                    safeRunOnUiThread(() -> {
                        isMoving = false;
                        updateAutoPlayButtonState();
                        btnExecuteMove.setEnabled(true);
                        if (success) {
                            tvAIResponse.append("\nMove sequence completed successfully!");
                            Toast.makeText(requireContext(), "Move sequence completed successfully", Toast.LENGTH_SHORT).show();
                        } else {
                            tvAIResponse.append("\nMove sequence failed.");
                            Toast.makeText(requireContext(), "Move sequence failed", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error in move thread", e);
                    safeRunOnUiThread(() -> {
                        isMoving = false;
                        updateAutoPlayButtonState();
                        btnExecuteMove.setEnabled(true);
                        tvAIResponse.append("\nError: " + e.getMessage());
                        Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();

        } catch (Exception e) {
            Log.e(TAG, "Error executing AI move sequence", e);
            safeRunOnUiThread(() -> {
                isMoving = false;
                updateAutoPlayButtonState();
                btnExecuteMove.setEnabled(true);
                tvAIResponse.append("\nError: " + e.getMessage());
                Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
        // Clear the current board state so autoPlay can restart fresh next time
        currentBoardState = null;
    }

    /**
     * Executes the robot move along the given path.
     * Retries a step (by decrementing the loop counter) if moveToAndWait() fails.
     */
    private boolean executeMove(List<CellCoordinate> path) {
        final int GRIPPER_WAIT_MS = 3000;
        try {
            // Step 1: Move to the first coordinate (pick up)
            CellCoordinate origin = path.get(0);
            if (!moveToAndWait(origin)) {
//                return false;
                updateProgress("Warning: Failed to reach pickup position accurately. Attempting to continue anyway.");
            }


            // Step 2: Close gripper (grab)
            updateProgress("Grabbing marble...");
            robotController.controlGripper(true);
            Thread.sleep(GRIPPER_WAIT_MS);

            //step 2.5, make torque to 0
//            robotController.setTorque(1.2f);
//            Thread.sleep(2000);         robotController.setTorque(1.2f);
//            Thread.sleep(2000);

            // Step 3: Move through the remaining coordinates
            for (int i = 1; i < path.size(); i++) {
                // If a move fails, retry this step by decrementing i
                if (!moveToAndWait(path.get(i))) {
//                    i--;
                    //tentatively we dont retry
                    updateProgress("Warning: Failed to reach target position：" + i+". Attempting to continue anyway.");
                }
            }

            // Step 4: Release gripper (drop)
            updateProgress("Releasing marble...");
            robotController.controlGripper(false);
            Thread.sleep(GRIPPER_WAIT_MS);

            // Step 5: Return home
            updateProgress("Returning home...");
            robotController.reset();
            Thread.sleep(2000);

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error during executeMove()", e);
            updateProgress("Error: " + e.getMessage());
            try {
                robotController.controlGripper(false); // Release gripper if an error occurs
            } catch (Exception ex) {
                Log.e(TAG, "Error releasing gripper", ex);
            }
            return false;
        }
    }

    /**
     * Moves to the given coordinate and waits until movement is verified.
     */
    private boolean moveToAndWait(CellCoordinate coord) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);

        updateProgress(String.format("Moving to (X=%.2f, Y=%.2f, Z=%.2f)...", coord.getX(), coord.getY(), coord.getZ()));

        CompletableFuture<Boolean> fut = robotController.executeVerifiedMovement(
                coord.getX(), coord.getY(), coord.getZ(), coord.getTorque(),
                new RobotController.MovementCallback() {
                    @Override
                    public void onSuccess() {
                        updateProgress("Reached successfully.");
                        success.set(true);
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        updateProgress("Move failed: " + errorMessage);
                        latch.countDown();
                    }

                    @Override
                    public void onProgress(String status) {
                        updateProgress(status);
                    }
                }
        );

        try {
            if (!latch.await(300, TimeUnit.SECONDS)) {
                updateProgress("Timeout waiting for movement!");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            updateProgress("Movement interrupted!");
            return false;
        }
        return success.get();
    }

    /**
     * Updates the tvAIResponse text on the UI.
     * Checks that the fragment is still added and its view is not null.
     */
    private void updateProgress(String msg) {
        Log.d(TAG, msg);
        if (isAdded() && getView() != null) {
            requireActivity().runOnUiThread(() -> tvAIResponse.append("\n" + msg));
        }
    }

    /**
     * A helper method to safely run code on the UI thread.
     */
    private void safeRunOnUiThread(Runnable r) {
        if (isAdded() && getView() != null) {
            requireActivity().runOnUiThread(r);
        }
    }

    // ------------------------------------------------------------------
    //  Gripper, IP Config, Debug, etc.
    // ------------------------------------------------------------------

    private void controlGripper(boolean close) {
        isMoving = true;
        updateAutoPlayButtonState();
        robotController.controlGripper(close);
        Toast.makeText(requireContext(), close ? "Closing gripper" : "Opening gripper", Toast.LENGTH_SHORT).show();
        safeRunOnUiThread(() -> tvAIResponse.append("\n" + (close ? "Gripper closing..." : "Gripper opening...")));
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            isMoving = false;
            updateAutoPlayButtonState();
        }, 1000);
    }

    private void showServerIpConfigDialog() {
        new IpConfigDialog(requireContext(), serverIp, "Configure Server IP", newIp -> {
            serverIp = newIp;
            detectionClient = new BoardDetectionClient(serverIp);
            Toast.makeText(requireContext(), "Server IP updated to: " + newIp, Toast.LENGTH_SHORT).show();
        }).show();
    }

    private void showRobotIpConfigDialog() {
        new IpConfigDialog(requireContext(), robotIp, "Configure Robot IP", newIp -> {
            robotIp = newIp;
            robotController.setRobotIp(newIp);
            Toast.makeText(requireContext(), "Robot IP updated to: " + newIp, Toast.LENGTH_SHORT).show();
        }).show();
    }

    private void showDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Debug Info:\n");
        sb.append("Server IP: ").append(serverIp).append("\n");
        sb.append("Robot IP: ").append(robotIp).append("\n");
        sb.append("isMoving: ").append(isMoving).append("\n");
        sb.append("hasEmptyBoard: ").append(hasEmptyBoard).append("\n");
        safeRunOnUiThread(() -> tvMappedPosition.setText(sb.toString()));
        Log.d(TAG, sb.toString());
        Toast.makeText(requireContext(), "Debug info displayed", Toast.LENGTH_SHORT).show();
    }

    private void lookupAndMoveToPosition(int boardX, int boardY) {
        if (isMoving) {
            Toast.makeText(requireContext(), "Robot is currently moving. Please wait.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
//            int boardX = Integer.parseInt(etDebugBoardX.getText().toString().trim());
//            int boardY = Integer.parseInt(etDebugBoardY.getText().toString().trim());
            CellCoordinate coord = BoardCoordinatesAdapter.getInstance().getBoardCellCoordinate(boardX, boardY);
            if (coord == null) {
                safeRunOnUiThread(() -> tvMappedPosition.setText("No mapping found for board coords (" + boardX + "," + boardY + ")"));
                return;
            }
            String info = String.format(Locale.US, "Mapped Board(%d,%d) → (X=%.2f, Y=%.2f, Z=%.2f, T=%.2f)",
                    boardX, boardY, coord.getX(), coord.getY(), coord.getZ(), coord.getTorque());
            safeRunOnUiThread(() -> tvMappedPosition.setText(info));
            Log.d(TAG, info);

            // Move there
            isMoving = true;
            btnLookupCoords.setEnabled(false);
            updateAutoPlayButtonState();

            robotController.executeVerifiedMovement(
                    coord.getX(), coord.getY(), coord.getZ(), coord.getTorque(),
                    new RobotController.MovementCallback() {
                        @Override
                        public void onSuccess() {
                            safeRunOnUiThread(() -> {
                                isMoving = false;
                                updateAutoPlayButtonState();
                                btnLookupCoords.setEnabled(true);
                                Toast.makeText(requireContext(), "Movement completed", Toast.LENGTH_SHORT).show();
                            });
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            safeRunOnUiThread(() -> {
                                isMoving = false;
                                updateAutoPlayButtonState();
                                btnLookupCoords.setEnabled(true);
                                Toast.makeText(requireContext(), "Movement failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                            });
                        }

                        @Override
                        public void onProgress(String status) {
                            safeRunOnUiThread(() -> tvMappedPosition.append("\n" + status));
                        }
                    }
            );
        } catch (NumberFormatException e) {
            safeRunOnUiThread(() -> tvMappedPosition.setText("Invalid coordinates. Please enter valid numbers."));
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> fut =
                ProcessCameraProvider.getInstance(requireContext());

        fut.addListener(() -> {
            try {
                ProcessCameraProvider provider = fut.get();

                Preview preview = new Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();

                provider.unbindAll();
                provider.bindToLifecycle(
                        getViewLifecycleOwner(),
                        new CameraSelector.Builder()
                                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                                .build(),
                        preview,
                        imageCapture);

                // camera & imageCapture are ready – unlock UI
                previewView.post(() -> {
                    btnCaptureEmpty.setEnabled(true);
                    btnDetectCurrent.setEnabled(hasEmptyBoard);
                });

            } catch (Exception e) {
                Log.e(TAG, "startCamera failed", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void updateAutoPlayButtonState() {
        if (btnAutoPlay != null) {
            btnAutoPlay.setEnabled(!isMoving);
            btnAutoPlay.setAlpha(isMoving ? 0.5f : 1.0f);
        }
    }

    private void setupKeyboardListener() {
        View rootView = requireView();
        rootView.setFocusableInTouchMode(true);
        rootView.requestFocus();
        rootView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN &&
                    (keyCode == KeyEvent.KEYCODE_E || keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_F7)) {
                Toast.makeText(requireContext(), "E/e key pressed -> autoPlay()!", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "E/e key pressed -> autoPlay()!");
                if (btnAutoPlay != null && btnAutoPlay.isEnabled()) {
                    btnAutoPlay.performClick();
                    return true;
                }
            }
            return false;
        });
        Log.d(TAG, "Keyboard listener set up");
    }

    //sound


    private void playButtonSound() {
        try {
            if (soundPlayer != null) {
                soundPlayer.seekTo(0);
                // If already playing, stop and reset
                if (soundPlayer.isPlaying()) {
                    soundPlayer.stop();
                    soundPlayer.prepare();
                }
                soundPlayer.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing sound", e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (imageCapture == null) startCamera();
        // Ensure the fragment's root view regains focus when resumed
        View rootView = getView();
        if (rootView != null) {
            rootView.setFocusableInTouchMode(true);
            rootView.requestFocus();
        }
    }

//    private void setupInputFields() {
//        // Set up proper keyboard handling for the debug coordinate input fields
//        View view = getView();
//        etDebugBoardX = view.findViewById(R.id.etDebugBoardX);
//        etDebugBoardY = view.findViewById(R.id.etDebugBoardY);
//
//        // Configure each input field to show keyboard when focused
//        setupEditTextKeyboard(etDebugBoardX);
//        setupEditTextKeyboard(etDebugBoardY);
//
//        // Set up keyboard done action to trigger coordinate lookup
//        etDebugBoardY.setOnEditorActionListener((v, actionId, event) -> {
//            if (actionId == EditorInfo.IME_ACTION_DONE ||
//                    actionId == EditorInfo.IME_ACTION_GO ||
//                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
//                            event.getAction() == KeyEvent.ACTION_DOWN)) {
//
//                // Hide keyboard
//                InputMethodManager imm = (InputMethodManager) requireContext()
//                        .getSystemService(Context.INPUT_METHOD_SERVICE);
//                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
//
//                // Trigger the lookup
//                lookupAndMoveToPosition();
//                return true;
//            }
//            return false;
//        });
//    }

    private void setupEditTextKeyboard(EditText editText) {
        // Make sure the EditText has proper input type
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);

        // Set IME options
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);

        // Force the keyboard to show when this EditText is clicked
        editText.setOnClickListener(v -> {
            editText.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        });

        // Also force keyboard when focus is gained
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                InputMethodManager imm = (InputMethodManager) requireContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void showBoardCoordsDialog() {
        // Inflate the dialog layout
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_board_coords, null);

        EditText etBoardX = dialogView.findViewById(R.id.etBoardX);
        EditText etBoardY = dialogView.findViewById(R.id.etBoardY);

        // Create a dialog
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Enter Board Coordinates")
                .setView(dialogView)
                .setPositiveButton("OK", null)  // We'll override the click later
                .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                .create();

        // Show the dialog so we can override the PositiveButton right away
        dialog.show();

        // Force the keyboard to appear for the first EditText
        etBoardX.requestFocus();
        etBoardX.post(() -> {
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(etBoardX, InputMethodManager.SHOW_IMPLICIT);
        });

        // Override positive button click
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            // Validate input
            String xStr = etBoardX.getText().toString().trim();
            String yStr = etBoardY.getText().toString().trim();
            if (xStr.isEmpty() || yStr.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter both X and Y", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int boardX = Integer.parseInt(xStr);
                int boardY = Integer.parseInt(yStr);

                // Pass the coordinates to your method that does the robot move
                // or store them in your Fragment’s fields so you can use them later
                lookupAndMoveToPosition(boardX, boardY);
                dialog.dismiss();
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "Invalid numbers", Toast.LENGTH_SHORT).show();
            }
        });
    }

}