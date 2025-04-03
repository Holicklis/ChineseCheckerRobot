package hku.cs.fyp24057.chinesecheckerrobot;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RobotController {
    private static final String TAG = "RobotController";

    // Settings
    private static final int HTTP_TIMEOUT_MS = 1000;
    private static final float DEFAULT_SPEED = 2f;  // Speed setting
    private static final int MOVEMENT_DELAY_MS = 2500;
    private static final int SHORT_DELAY_MS = 2500;
    private static final float DEFAULT_POSITION_TOLERANCE = 2.3f;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int VERIFICATION_DELAY_MS = 500;
    private static final float SAFE_Z = -60f;

    private String robotIp = "192.168.11.172";
    private OkHttpClient httpClient;
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface MovementCallback {
        void onSuccess();
        void onFailure(String errorMessage);
        void onProgress(String status);
    }

    private static RobotController instance;

    public static RobotController getInstance() {
        if (instance == null) {
            instance = new RobotController();
        }
        return instance;
    }

    private RobotController() {
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build();
    }

    public void setRobotIp(String ip) {
        this.robotIp = ip;
    }

    public String getRobotIp() {
        return robotIp;
    }

    public void shutdown() {
        commandExecutor.shutdownNow();
        try {
            commandExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void reset() {
        String jsonCmd = "{\"T\":100}";
        sendHttpCommand(jsonCmd);
        Log.d(TAG, "Reset command sent");
    }

    public void moveTo(float x, float y, float z, float torque) {
        String jsonCmd = String.format("{\"T\":104,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f,\"spd\":%.2f}",
                x, y, z, torque, DEFAULT_SPEED);
        sendHttpCommand(jsonCmd);
        Log.d(TAG, String.format("Moving to (%.2f, %.2f, %.2f, %.2f) with speed %.2f",
                x, y, z, torque, DEFAULT_SPEED));
    }

    public void moveToWithPrecisionSequence(float targetX, float targetY, float targetZ, float targetTorque) {
        float safeZ = SAFE_Z;

        // Get current position feedback to keep original XY in first step
        JSONObject currentPos = getPositionFeedback();
        float currentX = targetX;
        float currentY = targetY;
        float currentTorque = targetTorque;

        // Try to use current position if available
        if (currentPos != null) {
            try {
                currentX = (float) currentPos.getDouble("x");
                currentY = (float) currentPos.getDouble("y");
                currentTorque = (float) currentPos.getDouble("t");
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing current position, using target coords instead: " + e.getMessage());
            }
        }

        // Step 1: rise to safe Z first (keep XY unchanged)
        String jsonCmd1 = String.format("{\"T\":104,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f,\"spd\":%.2f}",
                currentX, currentY, safeZ, currentTorque, DEFAULT_SPEED);
        sendHttpCommand(jsonCmd1);
        Log.d(TAG, "Step1: Move to safe Z (keeping current XY)");

        try {
            Thread.sleep(SHORT_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Step 2: adjust torque
        String jsonCmd2 = String.format("{\"T\":104,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f,\"spd\":%.2f}",
                currentX, currentY, safeZ, targetTorque, DEFAULT_SPEED);
        sendHttpCommand(jsonCmd2);
        Log.d(TAG, "Step2: Adjust torque");

        try {
            Thread.sleep(SHORT_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Step 3: move to target XY at safe Z
        String jsonCmd3 = String.format("{\"T\":104,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f,\"spd\":%.2f}",
                targetX, targetY, safeZ, targetTorque, DEFAULT_SPEED);
        sendHttpCommand(jsonCmd3);
        Log.d(TAG, "Step3: Hover above target");

        try {
            Thread.sleep(SHORT_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Step 4: go down to targetZ
        String jsonCmd4 = String.format("{\"T\":104,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f,\"spd\":%.2f}",
                targetX, targetY, targetZ, targetTorque, DEFAULT_SPEED);
        sendHttpCommand(jsonCmd4);
        Log.d(TAG, "Step4: Descend to final position");
    }

    public JSONObject getPositionFeedback() {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<JSONObject> resultRef = new AtomicReference<>();

        String jsonCmd = "{\"T\":105}";
        String url = "http://" + robotIp + "/js?json=" + jsonCmd;

        Request request = new Request.Builder()
                .url(url)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to get position feedback: " + e.getMessage());
                latch.countDown();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        resultRef.set(jsonResponse);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing position feedback: " + e.getMessage());
                    }
                }
                latch.countDown();
            }
        });

        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return resultRef.get();
    }

    public boolean verifyPosition(float targetX, float targetY, float targetZ, float tolerance) {
        JSONObject feedback = getPositionFeedback();
        if (feedback == null) {
            Log.e(TAG, "Position verification failed: Could not get feedback");
            return false;
        }

        try {
            float currentX = (float) feedback.getDouble("x");
            float currentY = (float) feedback.getDouble("y");
            float currentZ = (float) feedback.getDouble("z");

            boolean isPositionCorrect =
                    Math.abs(currentX - targetX) <= tolerance &&
                            Math.abs(currentY - targetY) <= tolerance &&
                            Math.abs(currentZ - targetZ) <= tolerance;

            Log.d(TAG, String.format("Position verification: Target(%.2f, %.2f,%.2f) Current(%.2f,%.2f,%.2f) Result: %s",
                    targetX, targetY, targetZ, currentX, currentY, currentZ, isPositionCorrect));

            return isPositionCorrect;

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing position data: " + e.getMessage());
            return false;
        }
    }

    public CompletableFuture<Boolean> executeVerifiedMovement(
            float targetX, float targetY, float targetZ, float targetTorque,
            MovementCallback callback) {

        return executeVerifiedMovement(targetX, targetY, targetZ, targetTorque,
                DEFAULT_POSITION_TOLERANCE, DEFAULT_MAX_RETRIES, callback);
    }

    public CompletableFuture<Boolean> executeVerifiedMovement(
            float targetX, float targetY, float targetZ, float targetTorque,
            float tolerance, int maxRetries, MovementCallback callback) {

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        commandExecutor.submit(() -> {
            // Use the provided tolerance
            final float POSITION_TOLERANCE = tolerance;
            // Use the provided max retries
            final int MAX_TRIES = maxRetries;
            // Step size of 1mm for each adjustment
            final float CORRECTION_STEP = 1.0f;

            // Store original target values - these never change
            final float originalX = targetX;
            final float originalY = targetY;
            final float originalZ = targetZ;

            int attemptCount = 0;
            boolean success = false;

            while (attemptCount < MAX_TRIES && !success) {
                attemptCount++;

                // Calculate adjusted target for this attempt
                float adjustedX = originalX;
                float adjustedY = originalY;
                float adjustedZ = originalZ;

                // Only apply corrections on retry attempts
                if (attemptCount > 1) {
                    // Get current position to determine error direction
                    JSONObject currentPos = getPositionFeedback();
                    if (currentPos != null) {
                        try {
                            float currentX = (float) currentPos.getDouble("x");
                            float currentY = (float) currentPos.getDouble("y");

                            // Calculate errors from original target
                            float diffX = originalX - currentX;
                            float diffY = originalY - currentY;

                            if (attemptCount >= 2) {
                                adjustedX = originalX
                                        + diffX;
                                adjustedY = originalY + diffY;

                                if (callback != null) {
                                    callback.onProgress(String.format(
                                            "First correction (attempt %d):\n" +
                                                    "Original target: (%.2f, %.2f, %.2f)\n" +
                                                    "Current position: (%.2f, %.2f)\n" +
                                                    "Error: (X=%.2f, Y=%.2f)\n" +
                                                    "Applying exact error correction\n" +
                                                    "Adjusted target: (%.2f, %.2f, %.2f)",
                                            attemptCount,
                                            originalX, originalY, originalZ,
                                            currentX, currentY,
                                            diffX, diffY,
                                            adjustedX, adjustedY, adjustedZ));
                                }
                            }
                            // For subsequent corrections, add increasing step size
                            else {
                                float additionalCorrection = CORRECTION_STEP * (attemptCount - 2);
                                adjustedX = originalX + diffX*0 + (diffX >= 0 ? additionalCorrection : -additionalCorrection);
                                adjustedY = originalY + diffY* 0 + (diffY >= 0 ? additionalCorrection : -additionalCorrection);

                                if (callback != null) {
                                    callback.onProgress(String.format(
                                            "Correction attempt %d:\n" +
                                                    "Original target: (%.2f, %.2f, %.2f)\n" +
                                                    "Current position: (%.2f, %.2f)\n" +
                                                    "Error: (X=%.2f, Y=%.2f)\n" +
                                                    "Additional correction: %.2f mm\n" +
                                                    "Adjusted target: (%.2f, %.2f, %.2f)",
                                            attemptCount,
                                            originalX, originalY, originalZ,
                                            currentX, currentY,
                                            diffX, diffY,
                                            additionalCorrection,
                                            adjustedX, adjustedY, adjustedZ));
                                }
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing position data for adjustment", e);
                            if (callback != null) {
                                callback.onProgress("Error calculating adjustment: " + e.getMessage());
                            }
                        }
                    }
                }

                if (callback != null) {
                    String statusMsg = String.format("Moving to (%.2f,%.2f,%.2f) - Attempt %d of %d",
                            adjustedX, adjustedY, adjustedZ, attemptCount, MAX_TRIES);
                    callback.onProgress(statusMsg);
                }

                // Use sequential movement for better precision
                moveToWithPrecisionSequence(adjustedX, adjustedY, adjustedZ, targetTorque);

                try {
                    Thread.sleep(MOVEMENT_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (callback != null) callback.onFailure("Movement interrupted");
                    future.complete(false);
                    return;
                }

                try {
                    Thread.sleep(VERIFICATION_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Get position feedback to check result - always compare against original target
                JSONObject feedback = getPositionFeedback();
                if (feedback == null) {
                    if (callback != null) {
                        callback.onProgress("Could not get position feedback");
                    }
                    continue;
                }

                try {
                    float currentX = (float) feedback.getDouble("x");
                    float currentY = (float) feedback.getDouble("y");
                    float currentZ = (float) feedback.getDouble("z");
                    float currentT = (float) feedback.getDouble("t");

                    // Calculate differences between ORIGINAL target and actual positions
                    float diffX = originalX - currentX;
                    float diffY = originalY - currentY;

                    // Only check X and Y coordinates (exclude Z)
                    success = Math.abs(diffX) <= POSITION_TOLERANCE &&
                            Math.abs(diffY) <= POSITION_TOLERANCE;

                    if (success) {
                        if (callback != null) {
                            callback.onProgress(String.format(
                                    "Position reached within tolerance of %.2fmm\n" +
                                            "Original target: (%.2f, %.2f, %.2f)\n" +
                                            "Final position: (%.2f, %.2f, %.2f)",
                                    POSITION_TOLERANCE,
                                    originalX, originalY, originalZ,
                                    currentX, currentY, currentZ));
                            callback.onSuccess();
                        }
                        future.complete(true);
                        return;
                    } else {
                        Log.w(TAG, "Position verification failed, attempt: " + attemptCount);

                        String posInfo = String.format(
                                "Position verification failed (attempt %d/%d)\n" +
                                        "Original target: (%.2f, %.2f, %.2f, %.2f)\n" +
                                        "Adjusted target: (%.2f, %.2f, %.2f, %.2f)\n" +
                                        "Actual position: (%.2f, %.2f, %.2f, %.2f)\n" +
                                        "Error: (X=%.2f, Y=%.2f)\n" +
                                        "Tolerance: %.2f",
                                attemptCount, MAX_TRIES,
                                originalX, originalY, originalZ, targetTorque,
                                adjustedX, adjustedY, adjustedZ, targetTorque,
                                currentX, currentY, currentZ, currentT,
                                diffX, diffY,
                                POSITION_TOLERANCE);

                        if (callback != null) {
                            callback.onProgress(posInfo);
                            callback.onProgress("Raw feedback: " + feedback.toString());
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing position data", e);
                    if (callback != null && attemptCount < MAX_TRIES) {
                        callback.onProgress("Error parsing position data: " + e.getMessage());
                    }
                }
            }

            if (callback != null) {
                callback.onFailure("Failed to reach position after " + MAX_TRIES + " attempts");
            }
            future.complete(false);
        });

        return future;
    }

    public void controlGripper(boolean close) {
        String jsonCmd = close ? "{\"T\":116,\"cmd\":1}" : "{\"T\":116,\"cmd\":0}";
        sendHttpCommand(jsonCmd);
        Log.d(TAG, close ? "Gripper closing" : "Gripper opening");
    }

    public void pickUpMarbleWithVerification(CellCoordinate cell, MovementCallback callback) {
        commandExecutor.submit(() -> {
            try {
                if (callback != null) callback.onProgress("Starting pickup");

                // Move overhead
                CompletableFuture<Boolean> moveAboveFuture = executeVerifiedMovement(
                        cell.getX(), cell.getY(), SAFE_Z, cell.getTorque(),
                        new MovementCallback() {
                            @Override public void onSuccess() { /* handled in main sequence */ }
                            @Override public void onFailure(String errorMessage) {
                                if (callback != null) callback.onFailure("Failed to move above: " + errorMessage);
                            }
                            @Override public void onProgress(String status) {
                                if (callback != null) callback.onProgress(status);
                            }
                        });

                if (!moveAboveFuture.get()) {
                    if (callback != null) callback.onFailure("Failed to move above");
                    return;
                }

                // Move down
                CompletableFuture<Boolean> moveDownFuture = executeVerifiedMovement(
                        cell.getX(), cell.getY(), cell.getZ(), cell.getTorque(),
                        new MovementCallback() {
                            @Override public void onSuccess() { /* handled in main sequence */ }
                            @Override public void onFailure(String errorMessage) {
                                if (callback != null) callback.onFailure("Failed to move down: " + errorMessage);
                            }
                            @Override public void onProgress(String status) {
                                if (callback != null) callback.onProgress(status);
                            }
                        });

                if (!moveDownFuture.get()) {
                    if (callback != null) callback.onFailure("Failed to move down");
                    return;
                }

                // Close gripper
                if (callback != null) callback.onProgress("Closing gripper");
                controlGripper(true);
                Thread.sleep(1000);

                // Move back up
                CompletableFuture<Boolean> moveUpFuture = executeVerifiedMovement(
                        cell.getX(), cell.getY(), SAFE_Z, cell.getTorque(),
                        new MovementCallback() {
                            @Override public void onSuccess() { /* handled in main sequence */ }
                            @Override public void onFailure(String errorMessage) {
                                if (callback != null) callback.onFailure("Failed to move up: " + errorMessage);
                            }
                            @Override public void onProgress(String status) {
                                if (callback != null) callback.onProgress(status);
                            }
                        });

                if (!moveUpFuture.get()) {
                    if (callback != null) callback.onFailure("Failed to move up");
                    controlGripper(false);
                    return;
                }

                if (callback != null) callback.onSuccess();

            } catch (Exception e) {
                Log.e(TAG, "Error in pickUpMarbleWithVerification: " + e.getMessage(), e);
                if (callback != null) callback.onFailure("Error: " + e.getMessage());
            }
        });
    }

    public void placeMarbleWithVerification(CellCoordinate cell, MovementCallback callback) {
        commandExecutor.submit(() -> {
            try {
                if (callback != null) callback.onProgress("Starting placement");

                // Move overhead
                CompletableFuture<Boolean> moveAboveFuture = executeVerifiedMovement(
                        cell.getX(), cell.getY(), SAFE_Z, cell.getTorque(),
                        new MovementCallback() {
                            @Override public void onSuccess() { /* handled in main sequence */ }
                            @Override public void onFailure(String errorMessage) {
                                if (callback != null) callback.onFailure("Failed to move above: " + errorMessage);
                            }
                            @Override public void onProgress(String status) {
                                if (callback != null) callback.onProgress(status);
                            }
                        });

                if (!moveAboveFuture.get()) {
                    if (callback != null) callback.onFailure("Failed to move above");
                    return;
                }

                // Move down
                CompletableFuture<Boolean> moveDownFuture = executeVerifiedMovement(
                        cell.getX(), cell.getY(), cell.getZ(), cell.getTorque(),
                        new MovementCallback() {
                            @Override public void onSuccess() { /* handled in main sequence */ }
                            @Override public void onFailure(String errorMessage) {
                                if (callback != null) callback.onFailure("Failed to move down: " + errorMessage);
                            }
                            @Override public void onProgress(String status) {
                                if (callback != null) callback.onProgress(status);
                            }
                        });

                if (!moveDownFuture.get()) {
                    if (callback != null) callback.onFailure("Failed to move down");
                    controlGripper(false);
                    return;
                }

                // Open gripper
                if (callback != null) callback.onProgress("Opening gripper");
                controlGripper(false);
                Thread.sleep(1000);

                // Move back up
                CompletableFuture<Boolean> moveUpFuture = executeVerifiedMovement(
                        cell.getX(), cell.getY(), SAFE_Z, cell.getTorque(),
                        new MovementCallback() {
                            @Override public void onSuccess() { /* handled in main sequence */ }
                            @Override public void onFailure(String errorMessage) {
                                if (callback != null) callback.onFailure("Failed to move up: " + errorMessage);
                            }
                            @Override public void onProgress(String status) {
                                if (callback != null) callback.onProgress(status);
                            }
                        });

                if (!moveUpFuture.get()) {
                    if (callback != null) callback.onProgress("Warning: Failed to move up, but marble placed");
                }

                if (callback != null) callback.onSuccess();

            } catch (Exception e) {
                Log.e(TAG, "Error in placeMarbleWithVerification: " + e.getMessage(), e);
                if (callback != null) callback.onFailure("Error: " + e.getMessage());
            }
        });
    }

    public void executeCheckerMoveWithVerification(
            CellCoordinate origin, CellCoordinate destination,
            List<CellCoordinate> intermediatePoints, MovementCallback callback) {

        commandExecutor.submit(() -> {
            try {
                // Reset arm
                if (callback != null) callback.onProgress("Resetting arm");
                reset();
                Thread.sleep(2000);

                // Pick up marble
                if (callback != null) callback.onProgress("Picking up marble");

                AtomicBoolean pickupSuccess = new AtomicBoolean(false);
                CountDownLatch pickupLatch = new CountDownLatch(1);

                pickUpMarbleWithVerification(origin, new MovementCallback() {
                    @Override
                    public void onSuccess() {
                        pickupSuccess.set(true);
                        pickupLatch.countDown();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (callback != null) callback.onFailure("Pickup failed: " + errorMessage);
                        pickupLatch.countDown();
                    }

                    @Override
                    public void onProgress(String status) {
                        if (callback != null) callback.onProgress(status);
                    }
                });

                pickupLatch.await();

                if (!pickupSuccess.get()) {
                    return;
                }

                // Move through intermediate points
                if (intermediatePoints != null && !intermediatePoints.isEmpty()) {
                    if (callback != null) callback.onProgress("Moving through jump path");

                    for (int i = 0; i < intermediatePoints.size(); i++) {
                        CellCoordinate point = intermediatePoints.get(i);

                        if (callback != null) {
                            callback.onProgress("Moving to intermediate point " + (i+1) +
                                    " of " + intermediatePoints.size());
                        }

                        CompletableFuture<Boolean> moveFuture = executeVerifiedMovement(
                                point.getX(), point.getY(), SAFE_Z, point.getTorque(),
                                new MovementCallback() {
                                    @Override public void onSuccess() { /* handled in main sequence */ }
                                    @Override public void onFailure(String errorMessage) {
                                        if (callback != null) callback.onFailure("Failed at intermediate: " + errorMessage);
                                    }
                                    @Override public void onProgress(String status) {
                                        if (callback != null) callback.onProgress(status);
                                    }
                                });

                        if (!moveFuture.get()) {
                            if (callback != null) callback.onFailure("Failed at intermediate point");
                            controlGripper(false);
                            return;
                        }
                    }
                }

                // Place marble at destination
                if (callback != null) callback.onProgress("Placing marble");

                AtomicBoolean placeSuccess = new AtomicBoolean(false);
                CountDownLatch placeLatch = new CountDownLatch(1);

                placeMarbleWithVerification(destination, new MovementCallback() {
                    @Override
                    public void onSuccess() {
                        placeSuccess.set(true);
                        placeLatch.countDown();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (callback != null) callback.onFailure("Placement failed: " + errorMessage);
                        placeLatch.countDown();
                    }

                    @Override
                    public void onProgress(String status) {
                        if (callback != null) callback.onProgress(status);
                    }
                });

                placeLatch.await();

                if (!placeSuccess.get()) {
                    return;
                }

                if (callback != null) callback.onSuccess();

            } catch (Exception e) {
                Log.e(TAG, "Error in executeCheckerMoveWithVerification: " + e.getMessage(), e);
                if (callback != null) callback.onFailure("Error: " + e.getMessage());
            }
        });
    }

    private void sendHttpCommand(String jsonCommand) {
        try {
            URL url = new URL("http://" + robotIp + "/js?json=" + jsonCommand);
            new Thread(() -> {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(HTTP_TIMEOUT_MS);
                    connection.setReadTimeout(HTTP_TIMEOUT_MS);

                    int responseCode = connection.getResponseCode();
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        Log.w(TAG, "HTTP request failed with code: " + responseCode);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error sending command: " + e.getMessage(), e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }).start();
        } catch (MalformedURLException e) {
            Log.e(TAG, "Malformed URL: " + e.getMessage(), e);
        }
    }
}