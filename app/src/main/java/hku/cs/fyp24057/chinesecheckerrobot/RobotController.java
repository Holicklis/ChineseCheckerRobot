package hku.cs.fyp24057.chinesecheckerrobot;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RobotController coordinates queued robot commands over HTTP,
 * plus high-level convenience methods for picking up / placing marbles.
 */
public class RobotController {
    private static final String TAG = "RobotController";

    // Queue & executor settings
    private static final int COMMAND_QUEUE_SIZE = 10;
    private static final int COMMAND_INTERVAL_MS = 20;   // Min gap between commands
    private static final int HTTP_TIMEOUT_MS = 1000;     // HTTP connect/read timeouts
    private static final float DEFAULT_SPEED = 0.3f;     // default move speed

    // Movement delays
    private static final int MOVEMENT_DELAY_MS = 5000;   // Wait after each multi-step movement
    // "Safe" overhead height to avoid collisions
    private static final float SAFE_Z = -60f;
    // Slight offset to go near the surface for pick/place
    private static final float BOARD_OFFSET = 5f;

    private String robotIp = "192.168.11.172";

    // Single-threaded executor for commands
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private final BlockingQueue<Command> commandQueue = new ArrayBlockingQueue<>(COMMAND_QUEUE_SIZE);

    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private volatile long lastCommandTime = 0;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // The current state of the robot (x,y,z,t)
    private final AtomicReference<Float> x = new AtomicReference<>(304.24f);
    private final AtomicReference<Float> y = new AtomicReference<>(6.53f);
    private final AtomicReference<Float> z = new AtomicReference<>(240.92f);
    private final AtomicReference<Float> t = new AtomicReference<>(3.14f);

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

    // Command structure for queued commands
    private static class Command {
        final int cmdType;
        final float x, y, z, t;  // coords & torque
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

    // --------------------------
    // Basic config & utilities
    // --------------------------

    public void setRobotIp(String ip) {
        this.robotIp = ip;
    }

    public String getRobotIp() {
        return robotIp;
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

    /**
     * Launches the background thread that processes commands from the queue.
     */
    private void startCommandProcessor() {
        commandExecutor.submit(() -> {
            while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    long currentTime = System.currentTimeMillis();
                    long timeSinceLastCommand = currentTime - lastCommandTime;

                    // If enough time has passed & we're not currently processing
                    if (timeSinceLastCommand >= COMMAND_INTERVAL_MS && !isProcessing.get()) {
                        Command cmd = commandQueue.poll();
                        if (cmd != null) {
                            processCommand(cmd);
                            lastCommandTime = currentTime;
                        }
                    }

                    // Sleep to avoid busy loop
                    long sleepTime = Math.max(1, COMMAND_INTERVAL_MS - timeSinceLastCommand);
                    Thread.sleep(sleepTime);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    /**
     * Puts a command in the queue for processing.
     */
    private void queueCommand(int cmdType) {
        Command cmd = new Command(cmdType,
                x.get(), y.get(), z.get(), t.get(), DEFAULT_SPEED);
        commandQueue.offer(cmd);
    }

    /**
     * Actually executes a command by sending an HTTP request to the robot.
     */
    private void processCommand(Command cmd) {
        isProcessing.set(true);
        try {
            String jsonCmd;
            if (cmd.cmdType == 100) {
                // e.g. reset command
                jsonCmd = "{\"T\":100}";
            } else {
                // e.g. move or torque command
                jsonCmd = String.format("{\"T\":%d,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f,\"spd\":%.2f}",
                        cmd.cmdType, cmd.x, cmd.y, cmd.z, cmd.t, cmd.spd);
            }

            String urlString = "http://" + robotIp + "/js?json=" + jsonCmd;
            sendHttpRequest(urlString);

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

    // --------------------------
    // Movement & Torque
    // --------------------------

    /**
     * Move the robot to "home" position quickly.
     */
    public void reset() {
        float homeX = 313.76f;
        float homeY = 6.35f;
        float homeZ = 233.32f;
        float homeTorque = 3.13f;

        // Update internal references
        x.set(homeX);
        y.set(homeY);
        z.set(homeZ);
        t.set(homeTorque);

        // Enqueue a special command for reset
        queueCommand(100);
        Log.d(TAG, "Reset: Moving directly to home position");
    }

    /**
     * 4-step "precision" sequence:
     * 1) Move Z up to a safe height, keep XY
     * 2) Adjust torque
     * 3) Move XY over the target, keep safe Z
     * 4) Lower Z to final
     */
    public void moveToWithPrecisionSequence(float targetX, float targetY, float targetZ, float targetTorque) {
        float currentX = x.get();
        float currentY = y.get();
        float currentZ = z.get();
        float currentTorque = t.get();

        float safeZ = SAFE_Z;

        // Step 1: rise to safe Z higher before adjusting torque
        x.set(currentX);
        y.set(currentY);
        z.set(safeZ +20f);
        t.set(currentTorque);
        queueCommand(104);
        Log.d(TAG, String.format("Step1: Move to safe Z=%.2f from (%.2f,%.2f,%.2f,%.2f)",
                safeZ, currentX, currentY, currentZ, currentTorque));

        // Step 2: adjust torque
        mainHandler.postDelayed(() -> {
            x.set(currentX);
            y.set(currentY);
            z.set(safeZ+20f);
            t.set(targetTorque);
            queueCommand(104);
            Log.d(TAG, String.format("Step2: Adjust torque to %.2f at safeZ=%.2f", targetTorque, safeZ));

            // Step 3: hover above final XY
            mainHandler.postDelayed(() -> {
                x.set(targetX);
                y.set(targetY);
                z.set(safeZ);
                t.set(targetTorque);
                queueCommand(104);
                Log.d(TAG, String.format("Step3: Hover above (%.2f,%.2f) at Z=%.2f", targetX, targetY, safeZ));

                // Step 4: go down to targetZ
                mainHandler.postDelayed(() -> {
                    x.set(targetX);
                    y.set(targetY);
                    z.set(targetZ);
                    t.set(targetTorque);
                    queueCommand(104);
                    Log.d(TAG, String.format("Step4: Descend to (%.2f,%.2f,%.2f)", targetX, targetY, targetZ));
                }, MOVEMENT_DELAY_MS);

            }, MOVEMENT_DELAY_MS);
        }, MOVEMENT_DELAY_MS);
    }

    // --------------------------
    // Gripper control
    // --------------------------

    /**
     * Controls the motorized gripper.
     * @param close true = close, false = open
     */
    public void controlGripper(boolean close) {
        String jsonCmd = close ? "{\"T\":116,\"cmd\":1}" : "{\"T\":116,\"cmd\":0}";
        try {
            URL url = new URL("http://" + robotIp + "/js?json=" + jsonCmd);
            new Thread(() -> {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(HTTP_TIMEOUT_MS);
                    connection.setReadTimeout(HTTP_TIMEOUT_MS);

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Log.d(TAG, close ? "Gripper closed" : "Gripper opened");
                    } else {
                        Log.e(TAG, "Failed to control gripper: code=" + responseCode);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error controlling gripper", e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }).start();
        } catch (MalformedURLException e) {
            Log.e(TAG, "Malformed URL", e);
        }
    }

    // --------------------------
    // High-Level "Pick and Place" Helpers
    // --------------------------

    /**
     * Move above the cell at safe Z (hover).
     */
    public void moveAboveCell(CellCoordinate cell) {
        // same torque, x,y from cell, z=SAFE_Z
        float tx = cell.getX();
        float ty = cell.getY();
        float tt = cell.getTorque();
        moveToWithPrecisionSequence(tx, ty, SAFE_Z, tt);
    }

    /**
     * Move down close to the board for picking.
     */
    public void moveDownToPick(CellCoordinate cell) {
        float tx = cell.getX();
        float ty = cell.getY();
        float tz = cell.getZ() + BOARD_OFFSET; // slightly above the board
        float tt = cell.getTorque();
        moveToWithPrecisionSequence(tx, ty, tz, tt);
    }

    /**
     * Move down close to the board for placing a marble.
     * (Could be the same as moveDownToPick, but a separate method for clarity.)
     */
    public void moveDownToPlace(CellCoordinate cell) {
        float tx = cell.getX();
        float ty = cell.getY();
        float tz = cell.getZ() + BOARD_OFFSET;
        float tt = cell.getTorque();
        moveToWithPrecisionSequence(tx, ty, tz, tt);
    }

    /**
     * Complete "pick up" sequence:
     * 1) Move overhead
     * 2) Move down
     * 3) Close gripper
     * 4) Move back up
     */
    public void pickUpMarble(CellCoordinate cell) {
        new Thread(() -> {
            try {
                // Move overhead
                moveAboveCell(cell);
                Thread.sleep(MOVEMENT_DELAY_MS * 4); // Enough time for the 4-step sequence to finish

                // Move down
                moveDownToPick(cell);
                Thread.sleep(MOVEMENT_DELAY_MS * 4);

                // Close gripper
                controlGripper(true);
                Thread.sleep(1000);

                // Move back up
                moveAboveCell(cell);
                Thread.sleep(MOVEMENT_DELAY_MS * 4);

            } catch (InterruptedException e) {
                Log.e(TAG, "pickUpMarble interrupted", e);
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Complete "place" sequence:
     * 1) Move overhead
     * 2) Move down
     * 3) Open gripper
     * 4) Move back up
     */
    public void placeMarble(CellCoordinate cell) {
        new Thread(() -> {
            try {
                // Move overhead
                moveAboveCell(cell);
                Thread.sleep(MOVEMENT_DELAY_MS * 4);

                // Move down
                moveDownToPlace(cell);
                Thread.sleep(MOVEMENT_DELAY_MS * 4);

                // Open gripper
                controlGripper(false);
                Thread.sleep(1000);

                // Move back up
                moveAboveCell(cell);
                Thread.sleep(MOVEMENT_DELAY_MS * 4);

            } catch (InterruptedException e) {
                Log.e(TAG, "placeMarble interrupted", e);
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    // --------------------------
    // Multi-step Move Example
    // --------------------------

    /**
     * Demonstrates picking up from origin, optionally passing through intermediate
     * jump points, then placing at destination.
     */
    public void executeCheckerMove(CellCoordinate origin,
                                   CellCoordinate destination,
                                   List<CellCoordinate> intermediatePoints) {

        new Thread(() -> {
            try {
                // 1) Move above origin -> go down -> close
                moveAboveCell(origin);
                Thread.sleep(MOVEMENT_DELAY_MS * 4);

                moveDownToPick(origin);
                Thread.sleep(MOVEMENT_DELAY_MS * 4);

                controlGripper(true);
                Thread.sleep(1500);

                // 2) Go back up
                moveAboveCell(origin);
                Thread.sleep(MOVEMENT_DELAY_MS * 4);

                // 3) Intermediate points (chain jumps)
                if (intermediatePoints != null && !intermediatePoints.isEmpty()) {
                    for (CellCoordinate point : intermediatePoints) {
                        moveAboveCell(point);
                        Thread.sleep(MOVEMENT_DELAY_MS * 4);
                    }
                }

                // 4) Move above destination -> go down -> open
                moveAboveCell(destination);
                Thread.sleep(MOVEMENT_DELAY_MS * 4);

                moveDownToPlace(destination);
                Thread.sleep(MOVEMENT_DELAY_MS * 4);

                controlGripper(false);
                Thread.sleep(1500);

                // 5) Go back up from final
                moveAboveCell(destination);
                Thread.sleep(MOVEMENT_DELAY_MS * 4);

            } catch (InterruptedException e) {
                Log.e(TAG, "executeCheckerMove interrupted: " + e.getMessage(), e);
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}