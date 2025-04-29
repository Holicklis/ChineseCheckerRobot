package hku.cs.fyp24057.chinesecheckerrobot;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection; // Keep for potential future use? Though OkHttp replaces its direct use here
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Locale; // Import Locale
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.Callback; // Keep Callback for getPositionFeedback's async nature if desired
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RobotController {
    private static final String TAG = "RobotController";

    // Settings
    // Increase timeout slightly for potentially slower robot responses
    private static final int HTTP_TIMEOUT_MS = 5000; // Increased to 5 seconds
    private static final float DEFAULT_SPEED = 2f;  // Speed setting
    private static final int MOVEMENT_DELAY_MS = 2500; // Still used by verified movement logic initially
    private static final int SHORT_DELAY_MS = 1500; // Reduced delay between sequence steps
    private static final float DEFAULT_POSITION_TOLERANCE = 1.5f;
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int VERIFICATION_DELAY_MS = 500;
    private static final float SAFE_Z = -60f;

    private String robotIp = "192.168.11.172";
    private OkHttpClient httpClient;
    // Single thread executor ensures commands are processed one after another
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
                // Add write timeout as well
                .writeTimeout(HTTP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build();
    }

    public void setRobotIp(String ip) {
        this.robotIp = ip;
    }

    public String getRobotIp() {
        return robotIp;
    }

    public void shutdown() {
        Log.d(TAG, "Shutting down RobotController command executor.");
        commandExecutor.shutdown(); // Initiate graceful shutdown
        try {
            // Wait a certain time for existing tasks to terminate
            if (!commandExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                commandExecutor.shutdownNow(); // Cancel currently executing tasks
                // Wait a bit for tasks to respond to being cancelled
                if (!commandExecutor.awaitTermination(5, TimeUnit.SECONDS))
                    Log.e(TAG, "Executor did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            commandExecutor.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    // --- Step 1: Create the Private Blocking HTTP Method ---
    /**
     * Sends an HTTP GET command to the robot synchronously on the calling thread.
     * This should ONLY be called from within the commandExecutor thread.
     * @param jsonCommand The JSON payload for the command.
     * @return true if the command was acknowledged with HTTP 200 OK, false otherwise.
     */
    private boolean sendHttpRequestBlocking(String jsonCommand) {
        // Construct the URL (handle MalformedURLException)
        URL url;
        try {
            // Ensure proper URL encoding if jsonCommand might contain special chars, though unlikely here
            // For simplicity, assuming jsonCommand is safe for URL query param
            url = new URL("http://" + robotIp + "/js?json=" + jsonCommand);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Malformed URL for command: " + jsonCommand, e);
            return false;
        }

        Request request = new Request.Builder().url(url).get().build();

        try {
            // Use synchronous execute() - this will block the commandExecutor thread
            // until the request completes or times out.
            Log.v(TAG, "Executing HTTP (blocking): " + url.toString()); // Verbose log
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.w(TAG, "HTTP request failed: " + response.code() + " - " + response.message() + " for command: " + jsonCommand);
                    // Read body for more details if possible
                    String errorBody = response.body() != null ? response.body().string() : "No body";
                    Log.w(TAG, "Error Body: " + errorBody);
                    return false;
                }
                // Success (HTTP 2xx)
                Log.v(TAG, "HTTP request successful for command: " + jsonCommand);
                // We might want to read the response body here if the robot sends useful info back
                if (response.body() != null) {
                    response.body().close(); // Ensure body is consumed and closed
                }
                return true; // Indicate success acknowledgement
            }
        } catch (IOException e) {
            // Includes timeouts (SocketTimeoutException is a subclass of IOException)
            Log.e(TAG, "IOException during HTTP request for command: " + jsonCommand, e);
            return false; // Indicate failure
        }
    }

    // --- Step 2: Modify Public Methods to Use the Executor ---

    public void reset() {
        Log.d(TAG, "Submitting reset command to executor.");
        commandExecutor.submit(() -> {
            String jsonCmd = "{\"T\":100}";
            boolean success = sendHttpRequestBlocking(jsonCmd);
            if (success) {
                Log.i(TAG, "Reset command acknowledged by robot.");
            } else {
                Log.e(TAG, "Reset command failed or was not acknowledged.");
                // Consider adding error handling or feedback here if needed
            }
        });
    }

    public void moveTo(float x, float y, float z, float torque) {
        Log.d(TAG, String.format("Submitting moveTo command to executor: (%.2f, %.2f, %.2f, %.2f)", x, y, z, torque));
        commandExecutor.submit(() -> {
            // Use Locale.US to ensure decimal points regardless of device locale
            String jsonCmd = String.format(Locale.US, "{\"T\":104,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f,\"spd\":%.2f}",
                    x, y, z, torque, DEFAULT_SPEED);
            boolean success = sendHttpRequestBlocking(jsonCmd);
            if (success) {
                Log.i(TAG, String.format("MoveTo command acknowledged for (%.2f, %.2f, %.2f, %.2f)", x, y, z, torque));
            } else {
                Log.e(TAG, String.format("MoveTo command failed or was not acknowledged for (%.2f, %.2f, %.2f, %.2f)", x, y, z, torque));
            }
        });
    }

    public void controlGripper(boolean close) {
        Log.d(TAG, "Submitting gripper command to executor: " + (close ? "Close" : "Open"));
        commandExecutor.submit(() -> {
            String jsonCmd = close ? "{\"T\":116,\"cmd\":1}" : "{\"T\":116,\"cmd\":0}";
            boolean success = sendHttpRequestBlocking(jsonCmd);
            if (success) {
                Log.i(TAG, close ? "Gripper close command acknowledged." : "Gripper open command acknowledged.");
            } else {
                Log.e(TAG, close ? "Gripper close command failed or was not acknowledged." : "Gripper open command failed or was not acknowledged.");
            }
        });
    }

    // --- Refactor moveToWithPrecisionSequence to run entirely within the executor ---
    public void moveToWithPrecisionSequence(float targetX, float targetY, float targetZ, float targetTorque) {
        Log.d(TAG, String.format("Submitting precision sequence to executor for target (%.2f, %.2f, %.2f, %.2f)", targetX, targetY, targetZ, targetTorque));

        commandExecutor.submit(() -> {
            Log.i(TAG, "Executing precision sequence within executor thread...");
            // Get current position feedback (making this blocking for simplicity within the sequence)
            JSONObject currentPos = getPositionFeedbackBlocking(); // Use a blocking version
            float currentX = targetX; // Default to target if feedback fails
            float currentY = targetY;
            float currentTorque = targetTorque; // Default to target if feedback fails

            if (currentPos != null) {
                try {
                    currentX = (float) currentPos.getDouble("x");
                    currentY = (float) currentPos.getDouble("y");
                    // Don't necessarily use current torque, we often want to set it explicitly first
                    // currentTorque = (float) currentPos.getDouble("t");
                    Log.d(TAG, String.format("Seq: Got current pos (%.2f, %.2f)", currentX, currentY));
                } catch (JSONException e) {
                    Log.e(TAG, "Seq: Error parsing current position, using target XY for first step: " + e.getMessage());
                }
            } else {
                Log.w(TAG, "Seq: Failed to get current position feedback, using target XY for first step.");
            }

            boolean stepSuccess;
            String jsonCmd;

            // Step 1: Rise to safe Z first (keep current XY and current/target torque)
            jsonCmd = String.format(Locale.US, "{\"T\":104,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f,\"spd\":%.2f}",
                    currentX, currentY, SAFE_Z, currentTorque, DEFAULT_SPEED);
            Log.i(TAG, "Seq Step 1: Sending move to safe Z...");
            stepSuccess = sendHttpRequestBlocking(jsonCmd);
            if (!stepSuccess) { Log.e(TAG, "Seq Step 1 failed. Aborting sequence."); return; }
            safeSleep(SHORT_DELAY_MS); // Wait for robot to likely finish moving

            // Step 2: Adjust torque (at safe Z, current XY)
            jsonCmd = String.format(Locale.US, "{\"T\":104,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f,\"spd\":%.2f}",
                    currentX, currentY, SAFE_Z, targetTorque, DEFAULT_SPEED);
            Log.i(TAG, "Seq Step 2: Sending torque adjust...");
            stepSuccess = sendHttpRequestBlocking(jsonCmd);
            if (!stepSuccess) { Log.e(TAG, "Seq Step 2 failed. Aborting sequence."); return; }
            safeSleep(SHORT_DELAY_MS); // Wait for robot to likely finish moving

            // Step 3: Move to target XY (at safe Z, target torque)
            jsonCmd = String.format(Locale.US, "{\"T\":104,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f,\"spd\":%.2f}",
                    targetX, targetY, SAFE_Z, targetTorque, DEFAULT_SPEED);
            Log.i(TAG, "Seq Step 3: Sending hover move...");
            stepSuccess = sendHttpRequestBlocking(jsonCmd);
            if (!stepSuccess) { Log.e(TAG, "Seq Step 3 failed. Aborting sequence."); return; }
            safeSleep(SHORT_DELAY_MS); // Wait for robot to likely finish moving

            // Step 4: Go down to target Z (at target XY, target torque)
            jsonCmd = String.format(Locale.US, "{\"T\":104,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f,\"spd\":%.2f}",
                    targetX, targetY, targetZ, targetTorque, DEFAULT_SPEED);
            Log.i(TAG, "Seq Step 4: Sending descend move...");
            stepSuccess = sendHttpRequestBlocking(jsonCmd);
            if (!stepSuccess) { Log.e(TAG, "Seq Step 4 failed. Aborting sequence."); return; }
            // No sleep needed after the final step of this sequence block

            Log.i(TAG, "Precision sequence HTTP commands submitted successfully.");
        });
    }

    // Helper for sleeps within the executor, handling InterruptedException
    private void safeSleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Log.w(TAG, "Sleep interrupted in sequence.");
            Thread.currentThread().interrupt(); // Re-interrupt the thread
        }
    }


    // --- Position Feedback (Can remain async for UI, but needs a blocking version for internal use) ---

    // Existing async version for UI updates if needed
    public void getPositionFeedbackAsync(Callback callback) {
        String jsonCmd = "{\"T\":105}";
        String url = "http://" + robotIp + "/js?json=" + jsonCmd;
        Request request = new Request.Builder().url(url).build();
        // Use OkHttp's enqueue for async
        httpClient.newCall(request).enqueue(callback);
    }

    // New blocking version for internal use (e.g., verification, sequences)
    // Should only be called from the commandExecutor thread or another background thread
    private JSONObject getPositionFeedbackBlocking() {
        String jsonCmd = "{\"T\":105}";
        URL url;
        try {
            url = new URL("http://" + robotIp + "/js?json=" + jsonCmd);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Malformed URL for position feedback", e);
            return null;
        }

        Request request = new Request.Builder().url(url).get().build();

        try {
            Log.v(TAG, "Getting position feedback (blocking)...");
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    Log.v(TAG, "Position feedback received: " + responseBody);
                    return new JSONObject(responseBody);
                } else {
                    Log.w(TAG, "Get position feedback HTTP request failed: " + response.code());
                    return null;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException getting position feedback", e);
            return null;
        } catch (JSONException e) {
            Log.e(TAG, "JSONException parsing position feedback", e);
            return null;
        }
    }

    // Original getPositionFeedback using CountDownLatch (can be replaced by getPositionFeedbackBlocking)
    // Kept here for reference or if you prefer the latch pattern.
    public JSONObject getPositionFeedback() {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<JSONObject> resultRef = new AtomicReference<>();

        // Use the async callback method here
        getPositionFeedbackAsync(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to get position feedback (latch method): " + e.getMessage());
                latch.countDown();
            }

            @Override
            public void onResponse(Call call, Response response) {
                // Need to handle potential IOException when reading body
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            resultRef.set(jsonResponse);
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing position feedback JSON (latch method): " + e.getMessage());
                        }
                    } else {
                        Log.w(TAG, "Get position feedback unsuccessful response (latch method): " + response.code());
                    }
                } catch (IOException e) {
                    Log.e(TAG, "IOException reading position feedback body (latch method)", e);
                } finally {
                    // Ensure the response body is closed even if errors occur
                    if (response != null && response.body() != null) {
                        response.body().close();
                    }
                    latch.countDown();
                }
            }
        });

        try {
            // Wait for the callback to complete, with a timeout
            if (!latch.await(HTTP_TIMEOUT_MS + 500, TimeUnit.MILLISECONDS)) { // Wait slightly longer than HTTP timeout
                Log.w(TAG, "Timeout waiting for position feedback callback (latch method)");
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Interrupted while waiting for position feedback (latch method)");
            Thread.currentThread().interrupt();
        }

        return resultRef.get();
    }


    // --- Verification and Execution Logic (Relies on blocking methods now) ---

    public boolean verifyPosition(float targetX, float targetY, float targetZ, float tolerance) {
        // This should ideally run on a background thread (like commandExecutor)
        // as getPositionFeedbackBlocking is synchronous.
        JSONObject feedback = getPositionFeedbackBlocking(); // Use blocking version
        if (feedback == null) {
            Log.e(TAG, "Position verification failed: Could not get feedback");
            return false;
        }
        // ... rest of the verification logic remains the same ...
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
            Log.e(TAG, "Error parsing position data for verification: " + e.getMessage());
            return false;
        }
    }

    // Overload for default tolerance/retries
    public CompletableFuture<Boolean> executeVerifiedMovement(
            float targetX, float targetY, float targetZ, float targetTorque,
            MovementCallback callback) {

        return executeVerifiedMovement(targetX, targetY, targetZ, targetTorque,
                DEFAULT_POSITION_TOLERANCE, DEFAULT_MAX_RETRIES, callback);
    }


    // Main verified movement logic - runs within commandExecutor
    public CompletableFuture<Boolean> executeVerifiedMovement(
            float targetX, float targetY, float targetZ, float targetTorque,
            float tolerance, int maxRetries, MovementCallback callback) {

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        commandExecutor.submit(() -> {
            final float POSITION_TOLERANCE = tolerance;
            final int MAX_TRIES = maxRetries;
            final float CORRECTION_STEP = 1.0f; // Keep correction logic for now

            final float originalX = targetX;
            final float originalY = targetY;
            final float originalZ = targetZ;

            int attemptCount = 0;
            boolean success = false;

            while (attemptCount < MAX_TRIES && !success) {
                attemptCount++;
                Log.i(TAG, "Verified Movement Attempt " + attemptCount + "/" + MAX_TRIES);

                float adjustedX = originalX;
                float adjustedY = originalY;
                float adjustedZ = originalZ;

                // Apply correction logic on retries (attempts > 1)
                if (attemptCount > 1) {
                    JSONObject currentPos = getPositionFeedbackBlocking(); // Needs blocking feedback
                    if (currentPos != null) {
                        try {
                            float currentX = (float) currentPos.getDouble("x");
                            float currentY = (float) currentPos.getDouble("y");
                            float diffX = originalX - currentX;
                            float diffY = originalY - currentY;

                            // Simplified correction: apply difference directly on first retry
                            if (attemptCount == 2) {
                                adjustedX = originalX + diffX;
                                adjustedY = originalY + diffY;
                                final String progMsg = String.format(
                                        "First correction (attempt %d):\n" +
                                                "Original target: (%.2f, %.2f)\n" +
                                                "Current pos: (%.2f, %.2f), Error: (X=%.2f, Y=%.2f)\n" +
                                                "Adjusted target: (%.2f, %.2f)",
                                        attemptCount, originalX, originalY, currentX, currentY, diffX, diffY, adjustedX, adjustedY);
                                if (callback != null) mainHandler.post(() -> callback.onProgress(progMsg));

                            }
                            // For subsequent corrections, add increasing step size (optional, keep if desired)
                            else if (attemptCount > 2) {
                                float additionalCorrection = CORRECTION_STEP * (attemptCount - 2);
                                adjustedX = originalX + (diffX >= 0 ? additionalCorrection : -additionalCorrection);
                                adjustedY = originalY + (diffY >= 0 ? additionalCorrection : -additionalCorrection);
                                final String progMsg = String.format(
                                        "Step correction (attempt %d):\n" +
                                                "Original target: (%.2f, %.2f)\n" +
                                                "Current pos: (%.2f, %.2f), Error: (X=%.2f, Y=%.2f)\n" +
                                                "Additional correction: %.2f\n" +
                                                "Adjusted target: (%.2f, %.2f)",
                                        attemptCount, originalX, originalY, currentX, currentY, diffX, diffY, additionalCorrection, adjustedX, adjustedY);
                                if (callback != null) mainHandler.post(() -> callback.onProgress(progMsg));
                            }


                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing position for adjustment", e);
                            final String errorMsg = "Error calculating adjustment: " + e.getMessage();
                            if (callback != null) mainHandler.post(() -> callback.onProgress(errorMsg));
                        }
                    } else {
                        Log.w(TAG, "Could not get position feedback for adjustment, retrying original target.");
                        if (callback != null) mainHandler.post(() -> callback.onProgress("Could not get feedback for adjustment"));
                    }
                }

                // --- Execute the movement sequence steps synchronously within this Runnable ---
                // Step 1: Rise to safe Z
                JSONObject posBeforeSeq = getPositionFeedbackBlocking(); // Get current pos before starting
                float startX = adjustedX, startY = adjustedY, startTorque = targetTorque;
                if (posBeforeSeq != null) {
                    try {
                        startX = (float) posBeforeSeq.getDouble("x");
                        startY = (float) posBeforeSeq.getDouble("y");
                        // Use targetTorque as we likely want to set it during the sequence
                    } catch (JSONException e) { Log.w(TAG, "Error parsing posBeforeSeq"); }
                }
                String cmd1 = String.format(Locale.US, "{\"T\":104,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f,\"spd\":%.2f}", startX, startY, SAFE_Z, startTorque, DEFAULT_SPEED);
                final String progMsg1 = String.format("Attempt %d: Moving to safe Z (%.2f, %.2f, %.2f)", attemptCount, startX, startY, SAFE_Z);
                if (callback != null) mainHandler.post(() -> callback.onProgress(progMsg1));
                if (!sendHttpRequestBlocking(cmd1)) { continue; } // Retry loop if HTTP fails
                safeSleep(SHORT_DELAY_MS);

                // Step 2: Adjust Torque at safe Z
                String cmd2 = String.format(Locale.US, "{\"T\":104,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f,\"spd\":%.2f}", startX, startY, SAFE_Z, targetTorque, DEFAULT_SPEED);
                final String progMsg2 = String.format("Attempt %d: Adjusting torque to %.2f", attemptCount, targetTorque);
                if (callback != null) mainHandler.post(() -> callback.onProgress(progMsg2));
                if (!sendHttpRequestBlocking(cmd2)) { continue; } // Retry loop
                safeSleep(SHORT_DELAY_MS);

                // Step 3: Move to adjusted XY at safe Z
                String cmd3 = String.format(Locale.US, "{\"T\":104,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f,\"spd\":%.2f}", adjustedX, adjustedY, SAFE_Z, targetTorque, DEFAULT_SPEED);
                final String progMsg3 = String.format("Attempt %d: Moving to hover (%.2f, %.2f, %.2f)", attemptCount, adjustedX, adjustedY, SAFE_Z);
                if (callback != null) mainHandler.post(() -> callback.onProgress(progMsg3));
                if (!sendHttpRequestBlocking(cmd3)) { continue; } // Retry loop
                safeSleep(SHORT_DELAY_MS);

                // Step 4: Descend to adjusted Z
                String cmd4 = String.format(Locale.US, "{\"T\":104,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f,\"spd\":%.2f}", adjustedX, adjustedY, adjustedZ, targetTorque, DEFAULT_SPEED);
                final String progMsg4 = String.format("Attempt %d: Descending to final (%.2f, %.2f, %.2f)", attemptCount, adjustedX, adjustedY, adjustedZ);
                if (callback != null) mainHandler.post(() -> callback.onProgress(progMsg4));
                if (!sendHttpRequestBlocking(cmd4)) { continue; } // Retry loop
                safeSleep(MOVEMENT_DELAY_MS); // Longer sleep after final move before verification


                // --- Verification ---
                safeSleep(VERIFICATION_DELAY_MS); // Short pause before checking position

                JSONObject feedback = getPositionFeedbackBlocking(); // Use blocking feedback
                if (feedback == null) {
                    Log.w(TAG, "Verification failed: Could not get feedback on attempt " + attemptCount);
                    if (callback != null) mainHandler.post(() -> callback.onProgress("Could not get position feedback for verification"));
                    continue; // Go to next attempt
                }

                try {
                    float currentX = (float) feedback.getDouble("x");
                    float currentY = (float) feedback.getDouble("y");
                    float currentZ = (float) feedback.getDouble("z");

                    // Always compare against the ORIGINAL target
                    float diffX = Math.abs(originalX - currentX);
                    float diffY = Math.abs(originalY - currentY);
                    float diffZ = Math.abs(originalZ - currentZ); // Check Z too

                    success = diffX <= POSITION_TOLERANCE &&
                            diffY <= POSITION_TOLERANCE &&
                            diffZ <= POSITION_TOLERANCE; // Include Z in check

                    if (success) {
                        Log.i(TAG, "Position verified successfully on attempt " + attemptCount);
                        final String finalMsg = String.format(
                                "Position reached within tolerance (%.2fmm)\n" +
                                        "Original target: (%.2f, %.2f, %.2f)\n" +
                                        "Final position: (%.2f, %.2f, %.2f)",
                                POSITION_TOLERANCE,
                                originalX, originalY, originalZ,
                                currentX, currentY, currentZ);
                        if (callback != null) mainHandler.post(() -> {
                            callback.onProgress(finalMsg);
                            callback.onSuccess();
                        });
                        future.complete(true);
                        return; // Exit the Runnable and the loop
                    } else {
                        Log.w(TAG, "Position verification failed on attempt " + attemptCount + String.format(". Diff (X:%.2f, Y:%.2f, Z:%.2f)", diffX, diffY, diffZ));
                        final String failMsg = String.format(
                                "Verification failed (attempt %d/%d)\n" +
                                        "Original target: (%.2f, %.2f, %.2f)\n" +
                                        "Actual position: (%.2f, %.2f, %.2f)\n" +
                                        "Error: (X=%.2f, Y=%.2f, Z=%.2f)\n" +
                                        "Tolerance: %.2f",
                                attemptCount, MAX_TRIES,
                                originalX, originalY, originalZ,
                                currentX, currentY, currentZ,
                                diffX, diffY, diffZ,
                                POSITION_TOLERANCE);
                        if (callback != null) mainHandler.post(() -> callback.onProgress(failMsg));
                        // Loop continues to the next attempt
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing position data during verification", e);
                    if (callback != null) mainHandler.post(() -> callback.onProgress("Error parsing verification feedback: " + e.getMessage()));
                    // Consider this a failure for this attempt, continue loop
                }
            } // End of while loop

            // If loop finishes without success
            Log.e(TAG, "Failed to reach position within tolerance after " + MAX_TRIES + " attempts.");
            final String finalFailMsg = "Failed to reach position after " + MAX_TRIES + " attempts";
            if (callback != null) mainHandler.post(() -> callback.onFailure(finalFailMsg));
            future.complete(false);
        });

        return future;
    }


    // --- Complex Sequences (Pickup, Place, Full Move) ---
    // These should also be submitted to the executor and internally use
    // the CompletableFuture returned by executeVerifiedMovement to chain steps.

    // Example: pickUpMarbleWithVerification refactored
    public void pickUpMarbleWithVerification(CellCoordinate cell, MovementCallback callback) {
        commandExecutor.submit(() -> {
            try {
                if (callback != null) mainHandler.post(() -> callback.onProgress("Starting pickup sequence..."));

                // Chain steps using CompletableFuture's blocking get() within the executor thread
                boolean successStep1 = executeVerifiedMovement(
                        cell.getX(), cell.getY(), SAFE_Z, cell.getTorque(), callback).get(); // Blocks executor thread

                if (!successStep1) {
                    if (callback != null) mainHandler.post(() -> callback.onFailure("Pickup failed: Could not move above"));
                    return;
                }

                boolean successStep2 = executeVerifiedMovement(
                        cell.getX(), cell.getY(), cell.getZ(), cell.getTorque(), callback).get(); // Blocks executor thread

                if (!successStep2) {
                    if (callback != null) mainHandler.post(() -> callback.onFailure("Pickup failed: Could not move down"));
                    return;
                }

                // Close gripper (already submits to executor, but we need to wait)
                // For simplicity here, send blocking and sleep. A better way involves futures.
                if (callback != null) mainHandler.post(() -> callback.onProgress("Closing gripper..."));
                if (!sendHttpRequestBlocking("{\"T\":116,\"cmd\":1}")) {
                    if (callback != null) mainHandler.post(() -> callback.onFailure("Pickup failed: Gripper close command failed"));
                    return;
                }
                safeSleep(1500); // Allow time for gripper


                boolean successStep4 = executeVerifiedMovement(
                        cell.getX(), cell.getY(), SAFE_Z, cell.getTorque(), callback).get(); // Blocks executor thread

                if (!successStep4) {
                    // Might still have the marble, log a warning but maybe continue?
                    Log.w(TAG, "Pickup Warning: Failed to move back up after gripping.");
                    if (callback != null) mainHandler.post(() -> callback.onProgress("Warning: Failed to move back up after gripping."));
                }

                if (callback != null) mainHandler.post(callback::onSuccess);

            } catch (Exception e) { // Catches exceptions from .get() like ExecutionException or InterruptedException
                Log.e(TAG, "Error in pickUpMarble sequence", e);
                final String errorMsg = "Pickup sequence error: " + e.getMessage();
                if (callback != null) mainHandler.post(() -> callback.onFailure(errorMsg));
                // Attempt to open gripper on error
                sendHttpRequestBlocking("{\"T\":116,\"cmd\":0}");
            }
        });
    }

    // Example: placeMarbleWithVerification refactored (similar structure to pickup)
    public void placeMarbleWithVerification(CellCoordinate cell, MovementCallback callback) {
        commandExecutor.submit(() -> {
            try {
                if (callback != null) mainHandler.post(() -> callback.onProgress("Starting placement sequence..."));

                boolean successStep1 = executeVerifiedMovement(
                        cell.getX(), cell.getY(), SAFE_Z, cell.getTorque(), callback).get(); // Blocks

                if (!successStep1) {
                    if (callback != null) mainHandler.post(() -> callback.onFailure("Placement failed: Could not move above"));
                    return;
                }

                boolean successStep2 = executeVerifiedMovement(
                        cell.getX(), cell.getY(), cell.getZ(), cell.getTorque(), callback).get(); // Blocks

                if (!successStep2) {
                    if (callback != null) mainHandler.post(() -> callback.onFailure("Placement failed: Could not move down"));
                    // Attempt to open gripper anyway if move down failed but we might be close
                    sendHttpRequestBlocking("{\"T\":116,\"cmd\":0}");
                    return;
                }

                // Open gripper
                if (callback != null) mainHandler.post(() -> callback.onProgress("Opening gripper..."));
                if (!sendHttpRequestBlocking("{\"T\":116,\"cmd\":0}")) {
                    if (callback != null) mainHandler.post(() -> callback.onFailure("Placement failed: Gripper open command failed"));
                    return;
                }
                safeSleep(1500);

                boolean successStep4 = executeVerifiedMovement(
                        cell.getX(), cell.getY(), SAFE_Z, cell.getTorque(), callback).get(); // Blocks

                if (!successStep4) {
                    Log.w(TAG, "Placement Warning: Failed to move back up after releasing.");
                    if (callback != null) mainHandler.post(() -> callback.onProgress("Warning: Failed to move back up after releasing."));
                }

                if (callback != null) mainHandler.post(callback::onSuccess);

            } catch (Exception e) {
                Log.e(TAG, "Error in placeMarble sequence", e);
                final String errorMsg = "Placement sequence error: " + e.getMessage();
                if (callback != null) mainHandler.post(() -> callback.onFailure(errorMsg));
                // Attempt to open gripper on error
                sendHttpRequestBlocking("{\"T\":116,\"cmd\":0}");
            }
        });
    }

    // executeCheckerMoveWithVerification needs similar refactoring using .get()
    public void executeCheckerMoveWithVerification(
            CellCoordinate origin, CellCoordinate destination,
            List<CellCoordinate> intermediatePoints, MovementCallback callback) {

        commandExecutor.submit(() -> {
            try {
                // Reset arm (already submits, but wait for it conceptually or via feedback if possible)
                if (callback != null) mainHandler.post(() -> callback.onProgress("Resetting arm..."));
                if(!sendHttpRequestBlocking("{\"T\":100}")) {
                    Log.w(TAG, "Reset command failed, proceeding anyway...");
                    if (callback != null) mainHandler.post(() -> callback.onProgress("Warning: Reset command failed"));
                }
                safeSleep(2000); // Allow time for reset

                // --- Pick up ---
                AtomicBoolean pickupOk = new AtomicBoolean(false);
                CountDownLatch pickupLatch = new CountDownLatch(1);
                pickUpMarbleWithVerification(origin, new MovementCallback() { // This now runs in its own submission block
                    @Override public void onSuccess() { pickupOk.set(true); pickupLatch.countDown(); }
                    @Override public void onFailure(String m) { if(callback!=null)mainHandler.post(()->callback.onFailure("Pickup failed: "+m)); pickupLatch.countDown(); }
                    @Override public void onProgress(String s) { if(callback!=null)mainHandler.post(()->callback.onProgress(s)); }
                });
                pickupLatch.await(); // Wait for pickup submission to finish
                if (!pickupOk.get()) return; // Exit if pickup failed

                // --- Move through intermediate points ---
                if (intermediatePoints != null && !intermediatePoints.isEmpty()) {
                    if (callback != null) mainHandler.post(() -> callback.onProgress("Moving through jump path..."));
                    for (int i = 0; i < intermediatePoints.size(); i++) {
                        CellCoordinate point = intermediatePoints.get(i);
                        final String jumpMsg = "Moving to jump point " + (i+1) + "/" + intermediatePoints.size();
                        if (callback != null) mainHandler.post(() -> callback.onProgress(jumpMsg));

                        // Execute move to intermediate point (at SAFE_Z) and wait
                        boolean successJump = executeVerifiedMovement(
                                point.getX(), point.getY(), SAFE_Z, point.getTorque(), callback).get(); // Blocks

                        if (!successJump) {
                            final String jumpFailMsg = "Failed at intermediate point " + (i+1);
                            if (callback != null) mainHandler.post(() -> callback.onFailure(jumpFailMsg));
                            // Attempt to release gripper if move fails mid-jump
                            sendHttpRequestBlocking("{\"T\":116,\"cmd\":0}");
                            return;
                        }
                    }
                }

                // --- Place marble ---
                AtomicBoolean placeOk = new AtomicBoolean(false);
                CountDownLatch placeLatch = new CountDownLatch(1);
                if (callback != null) mainHandler.post(() -> callback.onProgress("Placing marble..."));
                placeMarbleWithVerification(destination, new MovementCallback() { // Runs in its own submission
                    @Override public void onSuccess() { placeOk.set(true); placeLatch.countDown(); }
                    @Override public void onFailure(String m) { if(callback!=null)mainHandler.post(()->callback.onFailure("Placement failed: "+m)); placeLatch.countDown(); }
                    @Override public void onProgress(String s) { if(callback!=null)mainHandler.post(()->callback.onProgress(s)); }
                });
                placeLatch.await(); // Wait for placement submission to finish

                if (!placeOk.get()) return; // Exit if placement failed

                // --- Final Reset ---
                if (callback != null) mainHandler.post(() -> callback.onProgress("Final reset..."));
                if(!sendHttpRequestBlocking("{\"T\":100}")) {
                    Log.w(TAG, "Final reset command failed.");
                }
                safeSleep(2000);

                if (callback != null) mainHandler.post(callback::onSuccess); // Final success

            } catch (Exception e) { // Catches await InterruptedException or .get() ExecutionException
                Log.e(TAG, "Error in executeCheckerMove sequence", e);
                final String errorMsg = "Checker Move sequence error: " + e.getMessage();
                if (callback != null) mainHandler.post(() -> callback.onFailure(errorMsg));
                // Attempt to open gripper on error
                sendHttpRequestBlocking("{\"T\":116,\"cmd\":0}");
            }
        });
    }

    // OLD sendHttpCommand - Keep for reference or remove
     /*
    private void sendHttpCommand(String jsonCommand) {
        try {
            URL url = new URL("http://" + robotIp + "/js?json=" + jsonCommand);
            new Thread(() -> { // <<< THE PROBLEMATIC PART
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
    */

}