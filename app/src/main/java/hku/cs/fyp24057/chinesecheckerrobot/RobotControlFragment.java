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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RobotControlFragment extends Fragment {
    private static final String TAG = "RobotControlFragment";

    // UI Elements
    private Switch switchCommMode;
    private Button btnUp, btnDown, btnLeft, btnRight;
    private Button btnZUp, btnZDown;
    private Button btnTorqueLeft, btnTorqueRight;
    private Button btnReset;

    // Command processing
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final BlockingQueue<Command> commandQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean isCommandInProgress = new AtomicBoolean(false);
    private long lastCommandTime = 0;
    private static final long MIN_COMMAND_INTERVAL = 20; // Minimum time between commands in ms

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
        startCommandProcessor();
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

    private void startCommandProcessor() {
        executorService.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Command cmd = commandQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (cmd != null) {
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

    private boolean canSendCommand() {
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