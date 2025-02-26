package hku.cs.fyp24057.chinesecheckerrobot;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class RobotController {
    private static final String TAG = "RobotController";
    private static final int COMMAND_QUEUE_SIZE = 10;
    private static final int COMMAND_INTERVAL_MS = 20;
    private static final int HTTP_TIMEOUT_MS = 1000;
    private static final int MOVEMENT_DELAY_MS = 1000; // Delay between movement steps
    private static final float DEFAULT_SPEED = 0.3f; // Default speed value for all commands

    private String robotIp = "192.168.11.172";
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private final BlockingQueue<Command> commandQueue = new ArrayBlockingQueue<>(COMMAND_QUEUE_SIZE);
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private volatile long lastCommandTime = 0;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Thread-safe coordinates
    private final AtomicReference<Float> x = new AtomicReference<>(304.24f);
    private final AtomicReference<Float> y = new AtomicReference<>(6.53f);
    private final AtomicReference<Float> z = new AtomicReference<>(240.92f);
    private final AtomicReference<Float> t = new AtomicReference<>(3.14f);

    private static class Command {
        final int cmdType;
        final float x, y, z, t;
        final float spd;

        Command(int cmdType, float x, float y, float z, float t, float spd) {
            this.cmdType = cmdType;
            this.x = x;
            this.y = y;
            this.z = z;
            this.t = t;
            this.spd = spd;
        }
    }

    private static RobotController instance;

    public static RobotController getInstance() {
        if (instance == null) {
            instance = new RobotController();
        }
        return instance;
    }

    private RobotController() {
        startCommandProcessor();
    }

    public void setRobotIp(String ip) {
        this.robotIp = ip;
    }

    public String getRobotIp() {
        return robotIp;
    }

    /**
     * Enhanced movement sequence with four steps:
     * 1. Lift to safe Z height first
     * 2. Adjust torque angle
     * 3. Move horizontally to position above target
     * 4. Lower to final position
     */
    public void moveToWithPrecisionSequence(float targetX, float targetY, float targetZ, float targetTorque) {
        // Step 1: Move Z to safe height while keeping current XY position
        float currentX = x.get();
        float currentY = y.get();
        float currentZ = z.get();
        float currentTorque = t.get();

        float safeZ = 30f; // Safe height

        x.set(currentX);
        y.set(currentY);
        z.set(safeZ);
        t.set(currentTorque); // Keep current torque angle during lift
        queueCommand(104);

        Log.d(TAG, String.format("Step 1: Moving to safe height at (%.2f, %.2f, %.2f, %.2f)",
                currentX, currentY, safeZ, currentTorque));

        // Step 2: Adjust torque angle at safe height
        mainHandler.postDelayed(() -> {
            x.set(currentX);
            y.set(currentY);
            z.set(safeZ);
            t.set(targetTorque); // Now adjust torque angle
            queueCommand(104);

            Log.d(TAG, String.format("Step 2: Adjusting torque angle at (%.2f, %.2f, %.2f, %.2f)",
                    currentX, currentY, safeZ, targetTorque));

            // Step 3: Move XY to position above target (hover)
            mainHandler.postDelayed(() -> {
                x.set(targetX);
                y.set(targetY);
                z.set(safeZ);  // Maintain safe Z height
                t.set(targetTorque);
                queueCommand(104);

                Log.d(TAG, String.format("Step 3: Hovering above target at (%.2f, %.2f, %.2f, %.2f)",
                        targetX, targetY, safeZ, targetTorque));

                // Step 4: Lower Z to the final target position
                mainHandler.postDelayed(() -> {
                    x.set(targetX);
                    y.set(targetY);
                    z.set(targetZ);
                    t.set(targetTorque);
                    queueCommand(104);

                    Log.d(TAG, String.format("Step 4: Final descent to target at (%.2f, %.2f, %.2f, %.2f)",
                            targetX, targetY, targetZ, targetTorque));
                }, MOVEMENT_DELAY_MS);
            }, MOVEMENT_DELAY_MS);
        }, MOVEMENT_DELAY_MS);
    }

    public void reset() {
        // Home position coordinates
        float homeX = 313.76f;
        float homeY = 6.35f;
        float homeZ = 233.32f;
        float homeTorque = 3.13f;

        // Direct reset to home position without delays
        x.set(homeX);
        y.set(homeY);
        z.set(homeZ);
        t.set(homeTorque);
        queueCommand(100); // Use command 100 which is a direct reset

        Log.d(TAG, "Reset: Moving directly to home position");
    }

    private void startCommandProcessor() {
        commandExecutor.submit(() -> {
            while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    long currentTime = System.currentTimeMillis();
                    long timeSinceLastCommand = currentTime - lastCommandTime;

                    if (timeSinceLastCommand >= COMMAND_INTERVAL_MS && !isProcessing.get()) {
                        Command cmd = commandQueue.poll();
                        if (cmd != null) {
                            processCommand(cmd);
                            lastCommandTime = currentTime;
                        }
                    }

                    long sleepTime = Math.max(1, COMMAND_INTERVAL_MS - timeSinceLastCommand);
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void queueCommand(int cmdType) {
        Command cmd = new Command(cmdType,
                x.get(), y.get(), z.get(), t.get(), DEFAULT_SPEED);
        commandQueue.offer(cmd);
    }

    private void processCommand(Command cmd) {
        isProcessing.set(true);
        try {
            String jsonCmd;
            if (cmd.cmdType == 100) {
                jsonCmd = "{\"T\":100}";
            } else {
                jsonCmd = String.format("{\"T\":%d,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f,\"spd\":%.2f}",
                        cmd.cmdType, cmd.x, cmd.y, cmd.z, cmd.t, cmd.spd);
            }

            String url = "http://" + robotIp + "/js?json=" + jsonCmd;
            sendHttpRequest(url);
        } finally {
            isProcessing.set(false);
        }
    }

    private void sendHttpRequest(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(HTTP_TIMEOUT_MS);
            connection.setReadTimeout(HTTP_TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP request failed with code: " + responseCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending command", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public void shutdown() {
        isRunning.set(false);
        commandExecutor.shutdownNow();
        try {
            commandExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}