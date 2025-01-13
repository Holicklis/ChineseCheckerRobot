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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * A simple {@link Fragment} subclass that controls a robot arm using Wi-Fi (HTTP) or wired mode.
 */
public class RobotControlFragment extends Fragment {

    private Switch switchCommMode;
    private Button btnUp, btnDown, btnLeft, btnRight;
    private Button btnZUp, btnZDown;
    private Button btnTorqueLeft, btnTorqueRight;
    private Button btnReset;

    // Initial coordinates
    private static final float INT_X = 304.24f;
    private static final float INT_Y = -6.53f;
    private static final float INT_Z = 240.92f;
    private static final float INT_T = 3.14f;

    // Coordinates and orientation
    private float x = INT_X;
    private float y = INT_Y;
    private float z = INT_Z;
    private float t = INT_T;  // wrist angle

    // Robot default IP in AP mode. Adjust if needed
    private String robotIp = "192.168.4.1";

    // Movement increments
    private final float MOVE_STEP_X = 5.0f;  // For X direction (Up/Down buttons)
    private final float MOVE_STEP_Y = 5.0f;  // For Y direction (Left/Right buttons)
    private final float MOVE_STEP_Z = 5.0f;  // For Z up/down
    private final float TORQUE_STEP = 0.5f;   // For wrist angle

    // Interval (ms) between repeated moves while a button is held
    private static final long REPEAT_INTERVAL_MS = 10;

    public RobotControlFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_robot_control, container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        // Bind views
        switchCommMode = view.findViewById(R.id.switchCommMode);
        btnUp          = view.findViewById(R.id.btnUp);
        btnDown        = view.findViewById(R.id.btnDown);
        btnLeft        = view.findViewById(R.id.btnLeft);
        btnRight       = view.findViewById(R.id.btnRight);
        btnZUp         = view.findViewById(R.id.btnZUp);
        btnZDown       = view.findViewById(R.id.btnZDown);
        btnTorqueLeft  = view.findViewById(R.id.btnTorqueLeft);
        btnTorqueRight = view.findViewById(R.id.btnTorqueRight);
        btnReset       = view.findViewById(R.id.btnReset);

        // 1) Up button => X increases
        setButtonHoldListener(btnUp, () -> {
            x += MOVE_STEP_X;
            sendArmCommand(1041);
        });

        // 2) Down button => X decreases
        setButtonHoldListener(btnDown, () -> {
            x -= MOVE_STEP_X;
            sendArmCommand(1041);
        });

        // 3) Left button => Y **increases** (SWITCHED!)
        setButtonHoldListener(btnLeft, () -> {
            y += MOVE_STEP_Y;
            sendArmCommand(1041);
        });

        // 4) Right button => Y **decreases** (SWITCHED!)
        setButtonHoldListener(btnRight, () -> {
            y -= MOVE_STEP_Y;
            sendArmCommand(1041);
        });

        // 5) Z Up => z increases
        setButtonHoldListener(btnZUp, () -> {
            z += MOVE_STEP_Z;
            sendArmCommand(1041);
        });

        // 6) Z Down => z decreases
        setButtonHoldListener(btnZDown, () -> {
            z -= MOVE_STEP_Z;
            sendArmCommand(1041);
        });

        // 7) Torque Left => t decreases
        setButtonHoldListener(btnTorqueLeft, () -> {
            t -= TORQUE_STEP;
            sendArmCommand(1041);
        });

        // 8) Torque Right => t increases
        setButtonHoldListener(btnTorqueRight, () -> {
            t += TORQUE_STEP;
            sendArmCommand(1041);
        });

        // 9) Reset => CMD_MOVE_INIT
        btnReset.setOnClickListener(v -> {
            // Reset all coordinates to initial
            x = INT_X;
            y = INT_Y;
            z = INT_Z;
            t = INT_T;
            sendArmCommand(100);  // CMD_MOVE_INIT
        });
    }

    /**
     * Helper to set a press-and-hold listener on a button. Repeats the given action
     * every REPEAT_INTERVAL_MS while the button is pressed.
     */
    private void setButtonHoldListener(Button button, Runnable action) {
        button.setOnTouchListener(new View.OnTouchListener() {
            private boolean isHeld = false;
            private final Handler handler = new Handler();

            private final Runnable repeatAction = new Runnable() {
                @Override
                public void run() {
                    if (isHeld) {
                        // Execute the movement action
                        action.run();
                        // Schedule next repeat
                        handler.postDelayed(this, REPEAT_INTERVAL_MS);
                    }
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Start repeating
                        isHeld = true;
                        handler.post(repeatAction);
                        // Return true indicates we've handled this event
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // Stop repeating
                        isHeld = false;
                        return true;
                }
                return false;
            }
        });
    }

    /**
     * Sends a JSON command to the robot arm.
     *
     * @param cmdType The "T" field in the JSON; for example:
     *                - 100 for CMD_MOVE_INIT (reset to initial position)
     *                - 1041 for CMD_XYZT_DIRECT_CTRL (move to specified x,y,z,t)
     */
    private void sendArmCommand(int cmdType) {
        boolean useWiFi = switchCommMode.isChecked();

        if (!useWiFi) {
            // Placeholder logic for future wired mode
            Toast.makeText(getActivity(),
                    "Wired mode (not implemented). Current coords => x=" + x
                            + ", y=" + y + ", z=" + z + ", t=" + t,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Build JSON command
        String jsonCmd;
        if (cmdType == 100) {
            // CMD_MOVE_INIT => reset
            jsonCmd = "{\"T\":100}";
        } else if (cmdType == 1041) {
            // CMD_XYZT_DIRECT_CTRL => move to (x, y, z, t)
            jsonCmd = String.format(
                    "{\"T\":1041,\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"t\":%.2f}",
                    x, y, z, t
            );

            // Optional Toast for debugging
            Toast.makeText(getActivity(),
                    String.format("Moving to x=%.2f, y=%.2f, z=%.2f, t=%.2f", x, y, z, t),
                    Toast.LENGTH_SHORT).show();
        } else {
            // Other command types
            jsonCmd = String.format("{\"T\":%d}", cmdType);
        }

        // Construct the HTTP GET request
        final String requestUrl = "http://" + robotIp + "/js?json=" + jsonCmd;

        // Run network operation in a separate thread
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
                    String response = sb.toString();  // The response from the robot

                    // Show the response in a Toast on the main thread
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(getActivity(),
                                        "Response: " + response,
                                        Toast.LENGTH_SHORT).show());
                    }
                } else {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(getActivity(),
                                        "HTTP Error: " + responseCode,
                                        Toast.LENGTH_SHORT).show());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getActivity(),
                                    "Exception: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
                }
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }
}