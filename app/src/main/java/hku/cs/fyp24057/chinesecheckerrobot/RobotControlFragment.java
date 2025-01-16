package hku.cs.fyp24057.chinesecheckerrobot;

import android.os.Bundle;
import android.os.Handler;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class RobotControlFragment extends Fragment {
    private static final String TAG = "RobotControlFragment";

    // UI Elements
    private Switch switchCommMode;
    private Button btnUp, btnDown, btnLeft, btnRight;
    private Button btnZUp, btnZDown;
    private Button btnTorqueLeft, btnTorqueRight;
    private Button btnReset;

    // Lock and synchronization
    private final ReentrantLock commandLock = new ReentrantLock();
    private final AtomicBoolean isCommandInProgress = new AtomicBoolean(false);
    private long lastCommandTime = 0;
    private static final long MIN_COMMAND_INTERVAL = 20; // Minimum time between commands in ms

    // Movement state tracking
    private boolean isMoving = false;
    private MovementDirection currentDirection = MovementDirection.NONE;

    private enum MovementDirection {
        NONE, UP, DOWN, LEFT, RIGHT, Z_UP, Z_DOWN, T_LEFT, T_RIGHT
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
    private float t = INT_T;  // wrist angle

    // Robot configuration
    private String robotIp = "192.168.4.1";

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
        setupButtonListeners();
    }

    private void initializeViews(View view) {
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
    }

    private void setupButtonListeners() {
        // Up button (X+)
        setButtonListener(btnUp, () -> {
            synchronized(commandLock) {
                if (canSendCommand()) {
                    x += MOVE_STEP_X;
                    currentDirection = MovementDirection.UP;
                    sendArmCommand(1041);
                    updateLastCommandTime();
                }
            }
        });

        // Down button (X-)
        setButtonListener(btnDown, () -> {
            synchronized(commandLock) {
                if (canSendCommand()) {
                    x -= MOVE_STEP_X;
                    currentDirection = MovementDirection.DOWN;
                    sendArmCommand(1041);
                    updateLastCommandTime();
                }
            }
        });

        // Left button (Y+)
        setButtonListener(btnLeft, () -> {
            synchronized(commandLock) {
                if (canSendCommand()) {
                    y += MOVE_STEP_Y;
                    currentDirection = MovementDirection.LEFT;
                    sendArmCommand(1041);
                    updateLastCommandTime();
                }
            }
        });

        // Right button (Y-)
        setButtonListener(btnRight, () -> {
            synchronized(commandLock) {
                if (canSendCommand()) {
                    y -= MOVE_STEP_Y;
                    currentDirection = MovementDirection.RIGHT;
                    sendArmCommand(1041);
                    updateLastCommandTime();
                }
            }
        });

        // Z Up
        setButtonListener(btnZUp, () -> {
            synchronized(commandLock) {
                if (canSendCommand()) {
                    z += MOVE_STEP_Z;
                    currentDirection = MovementDirection.Z_UP;
                    sendArmCommand(1041);
                    updateLastCommandTime();
                }
            }
        });

        // Z Down
        setButtonListener(btnZDown, () -> {
            synchronized(commandLock) {
                if (canSendCommand()) {
                    z -= MOVE_STEP_Z;
                    currentDirection = MovementDirection.Z_DOWN;
                    sendArmCommand(1041);
                    updateLastCommandTime();
                }
            }
        });

        // Torque Left
        setButtonListener(btnTorqueLeft, () -> {
            synchronized(commandLock) {
                if (canSendCommand()) {
                    t -= TORQUE_STEP;
                    currentDirection = MovementDirection.T_LEFT;
                    sendArmCommand(1041);
                    updateLastCommandTime();
                }
            }
        });

        // Torque Right
        setButtonListener(btnTorqueRight, () -> {
            synchronized(commandLock) {
                if (canSendCommand()) {
                    t += TORQUE_STEP;
                    currentDirection = MovementDirection.T_RIGHT;
                    sendArmCommand(1041);
                    updateLastCommandTime();
                }
            }
        });

        // Reset button
        btnReset.setOnClickListener(v -> {
            synchronized(commandLock) {
                if (canSendCommand()) {
                    x = INT_X;
                    y = INT_Y;
                    z = INT_Z;
                    t = INT_T;
                    currentDirection = MovementDirection.NONE;
                    sendArmCommand(100);
                    updateLastCommandTime();
                }
            }
        });
    }

    private boolean canSendCommand() {
        long currentTime = System.currentTimeMillis();
        return !isCommandInProgress.get() &&
                (currentTime - lastCommandTime) >= MIN_COMMAND_INTERVAL;
    }

    private void updateLastCommandTime() {
        lastCommandTime = System.currentTimeMillis();
    }

    private void setButtonListener(Button button, Runnable action) {
        button.setOnTouchListener(new View.OnTouchListener() {
            private boolean isHeld = false;
            private final Handler handler = new Handler();

            private final Runnable repeatAction = new Runnable() {
                @Override
                public void run() {
                    if (isHeld) {
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

    private void sendArmCommand(int cmdType) {
        if (!switchCommMode.isChecked()) {
            showToast(String.format("Wired mode (not implemented). Coords: x=%.2f, y=%.2f, z=%.2f, t=%.2f",
                    x, y, z, t));
            return;
        }

        isCommandInProgress.set(true);

        String jsonCmd;
        if (cmdType == 100) {
            jsonCmd = "{\"T\":100}";
        } else if (cmdType == 1041) {
            jsonCmd = String.format(
                    "{\"T\":1041,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f}",
                    x, y, z, t
            );
            Log.d(TAG, String.format("Sending command: x=%.2f, y=%.2f, z=%.2f, t=%.2f", x, y, z, t));
        } else {
            jsonCmd = String.format("{\"T\":%d}", cmdType);
        }

        final String requestUrl = "http://" + robotIp + "/js?json=" + jsonCmd;

        new Thread(() -> {
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            try {
                URL url = new URL(requestUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    String response = sb.toString();
                    Log.d(TAG, "Response: " + response);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending command", e);
                showToast("Error: " + e.getMessage());
            } finally {
                try {
                    if (reader != null) reader.close();
                    if (conn != null) conn.disconnect();
                } catch (Exception ignored) {}
                isCommandInProgress.set(false);
            }
        }).start();
    }

    private void showToast(String message) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() ->
                    Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show()
            );
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        synchronized(commandLock) {
            isMoving = false;
            currentDirection = MovementDirection.NONE;
            isCommandInProgress.set(false);
        }
    }
}