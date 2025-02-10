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
    private static final int MOVEMENT_DELAY_MS = 3000; // 3 seconds delay between movements

    private String robotIp = "192.168.11.172";
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private final BlockingQueue<Command> commandQueue = new ArrayBlockingQueue<>(COMMAND_QUEUE_SIZE);
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private volatile long lastCommandTime = 0;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Thread-safe coordinates
    private final AtomicReference<Float> x = new AtomicReference<>(484.18f);
    private final AtomicReference<Float> y = new AtomicReference<>(-6.68f);
    private final AtomicReference<Float> z = new AtomicReference<>(-40.31f);
    private final AtomicReference<Float> t = new AtomicReference<>(3.14f);

    private static class Command {
        final int cmdType;
        final float x, y, z, t;

        Command(int cmdType, float x, float y, float z, float t) {
            this.cmdType = cmdType;
            this.x = x;
            this.y = y;
            this.z = z;
            this.t = t;
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

    public void moveToWithZSequence(float targetX, float targetY, float targetZ) {
        // First move Z to 0
        float currentX = x.get();
        float currentY = y.get();

        // Step 1: Move Z to 0
        x.set(currentX);
        y.set(currentY);
        z.set(0f);
        queueCommand(1041);

        // Step 2: Wait and then move to target position
        mainHandler.postDelayed(() -> {
            x.set(targetX);
            y.set(targetY);
            z.set(targetZ);
            queueCommand(1041);
        }, MOVEMENT_DELAY_MS);
    }

    public void reset() {
        x.set(484.18f);
        y.set(-6.68f);
        z.set(-40.31f);
        t.set(3.14f);
        queueCommand(100);
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
                x.get(), y.get(), z.get(), t.get());
        commandQueue.offer(cmd);
    }

    private void processCommand(Command cmd) {
        isProcessing.set(true);
        try {
            String jsonCmd = cmd.cmdType == 100 ?
                    "{\"T\":100}" :
                    String.format("{\"T\":%d,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f}",
                            cmd.cmdType, cmd.x, cmd.y, cmd.z, cmd.t);

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