package hku.cs.fyp24057.chinesecheckerrobot;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class RobotControlFragment extends Fragment {
    private static final String TAG = "RobotControlFragment";
    private static String ROBOT_IP = "192.168.4.1";
    private static final int COMMAND_QUEUE_SIZE = 10;
    private static final int COMMAND_INTERVAL_MS = 20;
    private static final int HTTP_TIMEOUT_MS = 1000;

    // UI Elements
//    private Switch switchCommMode;
    private Button btnConfigureIp;
    private Button btnUp, btnDown, btnLeft, btnRight;
    private Button btnZUp, btnZDown;
    private Button btnTorqueLeft, btnTorqueRight;
    private Button btnReset;

    // Thread-safe command processing
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private final BlockingQueue<Command> commandQueue = new ArrayBlockingQueue<>(COMMAND_QUEUE_SIZE);
    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private volatile long lastCommandTime = 0;

    // Thread-safe coordinates
    private final AtomicReference<Float> x = new AtomicReference<>(304.24f);
    private final AtomicReference<Float> y = new AtomicReference<>(-6.53f);
    private final AtomicReference<Float> z = new AtomicReference<>(240.92f);
    private final AtomicReference<Float> t = new AtomicReference<>(3.14f);

    // Movement constants
    private static final float MOVE_STEP = 5.0f;
    private static final float TORQUE_STEP = 0.5f;

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

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_robot_control, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        setupButtonListeners();
        startCommandProcessor();
    }

    private void initializeViews(View view) {
//        switchCommMode = view.findViewById(R.id.switchCommMode);
        btnConfigureIp = view.findViewById(R.id.btnConfigureIp);
        btnUp = view.findViewById(R.id.btnUp);
        btnDown = view.findViewById(R.id.btnDown);
        btnLeft = view.findViewById(R.id.btnLeft);
        btnRight = view.findViewById(R.id.btnRight);
        btnZUp = view.findViewById(R.id.btnZUp);
        btnZDown = view.findViewById(R.id.btnZDown);
        btnTorqueLeft = view.findViewById(R.id.btnTorqueLeft);
        btnTorqueRight = view.findViewById(R.id.btnTorqueRight);
        btnReset = view.findViewById(R.id.btnReset);

        btnConfigureIp.setOnClickListener(v -> showIpConfigDialog());
    }

    private void showIpConfigDialog() {
        new IpConfigDialog(
                requireContext(),
                ROBOT_IP,
                "Configure Robot IP",
                newIp -> {
                    ROBOT_IP = newIp;
                    Toast.makeText(requireContext(),
                            "Robot IP updated to: " + newIp,
                            Toast.LENGTH_SHORT).show();
                }
        ).show();
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

                    // Dynamic sleep based on time until next command should be sent
                    long sleepTime = Math.max(1, COMMAND_INTERVAL_MS - timeSinceLastCommand);
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void setupButtonListeners() {
        setupDirectionalButton(btnUp, () -> updateCoordinate(x, MOVE_STEP));
        setupDirectionalButton(btnDown, () -> updateCoordinate(x, -MOVE_STEP));
        setupDirectionalButton(btnLeft, () -> updateCoordinate(y, MOVE_STEP));
        setupDirectionalButton(btnRight, () -> updateCoordinate(y, -MOVE_STEP));
        setupDirectionalButton(btnZUp, () -> updateCoordinate(z, MOVE_STEP));
        setupDirectionalButton(btnZDown, () -> updateCoordinate(z, -MOVE_STEP));
        setupDirectionalButton(btnTorqueLeft, () -> updateCoordinate(t, -TORQUE_STEP));
        setupDirectionalButton(btnTorqueRight, () -> updateCoordinate(t, TORQUE_STEP));

        btnReset.setOnClickListener(v -> resetPositions());
    }

    private void setupDirectionalButton(Button button, Runnable updateAction) {
        button.setOnTouchListener(new View.OnTouchListener() {
            private boolean isPressed = false;
            private final Handler handler = new Handler(Looper.getMainLooper());

            private final Runnable repeatAction = new Runnable() {
                @Override
                public void run() {
                    if (isPressed) {
                        updateAction.run();
                        queueCommand(1041);
                        handler.postDelayed(this, COMMAND_INTERVAL_MS);
                    }
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isPressed = true;
                        handler.post(repeatAction);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isPressed = false;
                        handler.removeCallbacks(repeatAction);
                        return true;
                }
                return false;
            }
        });
    }

    private void updateCoordinate(AtomicReference<Float> coordinate, float delta) {
        coordinate.updateAndGet(current -> current + delta);
    }

    private void resetPositions() {
        x.set(304.24f);
        y.set(-6.53f);
        z.set(240.92f);
        t.set(3.14f);
        queueCommand(100);
    }

    private void queueCommand(int cmdType) {
        Command cmd = new Command(cmdType,
                x.get(), y.get(), z.get(), t.get());

        // Don't block if queue is full, just skip the command
        commandQueue.offer(cmd);
    }

    private void processCommand(Command cmd) {
//        if (!switchCommMode.isChecked()) {
//            Log.d(TAG, "Wired mode not implemented");
//            return;
//        }

        isProcessing.set(true);
        try {
            String jsonCmd = cmd.cmdType == 100 ?
                    "{\"T\":100}" :
                    String.format("{\"T\":%d,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f}",
                            cmd.cmdType, cmd.x, cmd.y, cmd.z, cmd.t);

            String url = "http://" + ROBOT_IP + "/js?json=" + jsonCmd;
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning.set(false);
        commandExecutor.shutdownNow();
        try {
            commandExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}