package hku.cs.fyp24057.chinesecheckerrobot;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RobotControlFragment extends Fragment {
    private static final String TAG = "RobotControlFragment";
    private static final String PREFS_NAME = "RobotSettings";
    private static final String PREF_ROBOT_IP = "robot_ip";

    // UI Elements
    private Switch switchCommMode;
    private EditText editTextIpAddress;
    private Button btnConnect;
    private Button btnUp, btnDown, btnLeft, btnRight;
    private Button btnZUp, btnZDown;
    private Button btnTorqueLeft, btnTorqueRight;
    private Button btnReset;

    // Command processing
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final BlockingQueue<Command> commandQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean isCommandInProgress = new AtomicBoolean(false);
    private long lastCommandTime = 0;
    private static final long MIN_COMMAND_INTERVAL = 20;

    // Movement state tracking
    private boolean isMoving = false;
    private MovementDirection currentDirection = MovementDirection.NONE;

    private enum MovementDirection {
        NONE, UP, DOWN, LEFT, RIGHT, Z_UP, Z_DOWN, T_LEFT, T_RIGHT
    }

    // Command class to encapsulate command data
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

    // Initial coordinates
    private static final float INT_X = 304.24f;
    private static final float INT_Y = -6.53f;
    private static final float INT_Z = 240.92f;
    private static final float INT_T = 3.14f;

    // Current coordinates
    private float x = INT_X;
    private float y = INT_Y;
    private float z = INT_Z;
    private float t = INT_T;

    // Robot configuration
    private String robotIp;
    private boolean isConnected = false;

    // Movement increments
    private final float MOVE_STEP_X = 5.0f;
    private final float MOVE_STEP_Y = 5.0f;
    private final float MOVE_STEP_Z = 5.0f;
    private final float TORQUE_STEP = 0.5f;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_robot_control, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        loadRobotIp();
        setupButtonListeners();
        startCommandProcessor();
    }

    private void initializeViews(View view) {
        // Original views
        switchCommMode = view.findViewById(R.id.switchCommMode);
        btnUp = view.findViewById(R.id.btnUp);
        btnDown = view.findViewById(R.id.btnDown);
        btnLeft = view.findViewById(R.id.btnLeft);
        btnRight = view.findViewById(R.id.btnRight);
        btnZUp = view.findViewById(R.id.btnZUp);
        btnZDown = view.findViewById(R.id.btnZDown);
        btnTorqueLeft = view.findViewById(R.id.btnTorqueLeft);
        btnTorqueRight = view.findViewById(R.id.btnTorqueRight);
        btnReset = view.findViewById(R.id.btnReset);

        // New views for IP configuration
        editTextIpAddress = view.findViewById(R.id.editTextIpAddress);
        btnConnect = view.findViewById(R.id.btnConnect);

        // Set up the connect button
        btnConnect.setOnClickListener(v -> {
            String newIp = editTextIpAddress.getText().toString().trim();
            if (isValidIpAddress(newIp)) {
                saveRobotIp(newIp);
                testConnection();
            } else {
                showToast("Invalid IP address format");
            }
        });

        // Initially disable control buttons
        setControlButtonsEnabled(false);
    }

    private void loadRobotIp() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        robotIp = prefs.getString(PREF_ROBOT_IP, "192.168.1.100"); // Default IP for router mode
        editTextIpAddress.setText(robotIp);
    }

    private void saveRobotIp(String ip) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_ROBOT_IP, ip).apply();
        robotIp = ip;
    }

    private boolean isValidIpAddress(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;

        try {
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void testConnection() {
        executorService.submit(() -> {
            try {
                String testUrl = "http://" + robotIp + "/js?json={\"T\":0}";  // Simple test command
                URL url = new URL(testUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                isConnected = (responseCode == HttpURLConnection.HTTP_OK);

                requireActivity().runOnUiThread(() -> {
                    if (isConnected) {
                        showToast("Connected to robot successfully");
                        setControlButtonsEnabled(true);
                    } else {
                        showToast("Failed to connect to robot");
                        setControlButtonsEnabled(false);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Connection test failed", e);
                requireActivity().runOnUiThread(() -> {
                    showToast("Connection failed: " + e.getMessage());
                    setControlButtonsEnabled(false);
                    isConnected = false;
                });
            }
        });
    }

    private void setControlButtonsEnabled(boolean enabled) {
        btnUp.setEnabled(enabled);
        btnDown.setEnabled(enabled);
        btnLeft.setEnabled(enabled);
        btnRight.setEnabled(enabled);
        btnZUp.setEnabled(enabled);
        btnZDown.setEnabled(enabled);
        btnTorqueLeft.setEnabled(enabled);
        btnTorqueRight.setEnabled(enabled);
        btnReset.setEnabled(enabled);
    }

    private void startCommandProcessor() {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Command cmd = commandQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (cmd != null && isConnected) {
                        processCommand(cmd);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void setupButtonListeners() {
        // Up button (X+)
        setButtonListener(btnUp, () -> {
            if (canSendCommand()) {
                x += MOVE_STEP_X;
                currentDirection = MovementDirection.UP;
                sendCommand(1041);
            }
        });

        // Down button (X-)
        setButtonListener(btnDown, () -> {
            if (canSendCommand()) {
                x -= MOVE_STEP_X;
                currentDirection = MovementDirection.DOWN;
                sendCommand(1041);
            }
        });

        // Left button (Y+)
        setButtonListener(btnLeft, () -> {
            if (canSendCommand()) {
                y += MOVE_STEP_Y;
                currentDirection = MovementDirection.LEFT;
                sendCommand(1041);
            }
        });

        // Right button (Y-)
        setButtonListener(btnRight, () -> {
            if (canSendCommand()) {
                y -= MOVE_STEP_Y;
                currentDirection = MovementDirection.RIGHT;
                sendCommand(1041);
            }
        });

        // Z Up
        setButtonListener(btnZUp, () -> {
            if (canSendCommand()) {
                z += MOVE_STEP_Z;
                currentDirection = MovementDirection.Z_UP;
                sendCommand(1041);
            }
        });

        // Z Down
        setButtonListener(btnZDown, () -> {
            if (canSendCommand()) {
                z -= MOVE_STEP_Z;
                currentDirection = MovementDirection.Z_DOWN;
                sendCommand(1041);
            }
        });

        // Torque Left
        setButtonListener(btnTorqueLeft, () -> {
            if (canSendCommand()) {
                t -= TORQUE_STEP;
                currentDirection = MovementDirection.T_LEFT;
                sendCommand(1041);
            }
        });

        // Torque Right
        setButtonListener(btnTorqueRight, () -> {
            if (canSendCommand()) {
                t += TORQUE_STEP;
                currentDirection = MovementDirection.T_RIGHT;
                sendCommand(1041);
            }
        });

        // Reset button
        btnReset.setOnClickListener(v -> {
            if (canSendCommand()) {
                x = INT_X;
                y = INT_Y;
                z = INT_Z;
                t = INT_T;
                currentDirection = MovementDirection.NONE;
                sendCommand(100);
            }
        });
    }

    private void setButtonListener(Button button, Runnable action) {
        button.setOnTouchListener(new View.OnTouchListener() {
            private boolean isHeld = false;
            private final Handler handler = new Handler(Looper.getMainLooper());

            private final Runnable repeatAction = new Runnable() {
                @Override
                public void run() {
                    if (isHeld && isConnected) {
                        action.run();
                        handler.postDelayed(this, MIN_COMMAND_INTERVAL);
                    }
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isHeld = true;
                        isMoving = true;
                        handler.post(repeatAction);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isHeld = false;
                        isMoving = false;
                        currentDirection = MovementDirection.NONE;
                        handler.removeCallbacks(repeatAction);
                        return true;
                }
                return false;
            }
        });
    }

    private boolean canSendCommand() {
        if (!isConnected) {
            showToast("Not connected to robot");
            return false;
        }
        long currentTime = System.currentTimeMillis();
        return !isCommandInProgress.get() &&
                (currentTime - lastCommandTime) >= MIN_COMMAND_INTERVAL;
    }

    private void updateLastCommandTime() {
        lastCommandTime = System.currentTimeMillis();
    }

    private void sendCommand(int cmdType) {
        if (canSendCommand()) {
            commandQueue.offer(new Command(cmdType, x, y, z, t));
            updateLastCommandTime();
        }
    }

    private void processCommand(Command cmd) {
        if (!switchCommMode.isChecked()) {
            showToast(String.format("Wired mode (not implemented). Coords: x=%.2f, y=%.2f, z=%.2f, t=%.2f",
                    x, y, z, t));
            return;
        }

        isCommandInProgress.set(true);
        String jsonCmd;
        if (cmd.cmdType == 100) {
            jsonCmd = "{\"T\":100}";
        } else {
            jsonCmd = String.format(
                    "{\"T\":%d,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f}",
                    cmd.cmdType, cmd.x, cmd.y, cmd.z, cmd.t
            );
            Log.d(TAG, String.format("Sending command: x=%.2f, y=%.2f, z=%.2f, t=%.2f",
                    cmd.x, cmd.y, cmd.z, cmd.t));
        }

        String requestUrl = "http://" + robotIp + "/js?json=" + jsonCmd;

        try {
            URL url = new URL(requestUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                Log.d(TAG, "Response: " + response);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending command", e);
            showToast("Error: " + e.getMessage());
        } finally {
            isCommandInProgress.set(false);
        }
    }

    private void showToast(String message) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            if (getActivity() != null && !getActivity().isFinishing()) {
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdownNow();
        try {
            if (!executorService.awaitTermination(100, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        isMoving = false;
        currentDirection = MovementDirection.NONE;
        isCommandInProgress.set(false);
    }
}