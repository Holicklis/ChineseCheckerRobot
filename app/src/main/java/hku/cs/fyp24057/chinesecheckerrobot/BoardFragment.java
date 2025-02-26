package hku.cs.fyp24057.chinesecheckerrobot;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.constraintlayout.widget.ConstraintLayout;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.MalformedURLException;

public class BoardFragment extends Fragment {
    private static final String TAG = "BoardFragment";
    private static final int MOVEMENT_DELAY_MS = 2000; // Delay per movement step

    private CheckerboardView checkerboardView;
    private CellCoordinate[][] coordinates;
    private RobotController robotController;
    private Button btnConfigureIp;
    private Button btnReset;
    private Button btnGripper;  // Added gripper button
    private boolean isMoving = false;
    private boolean isGripClosed = false;  // Track gripper state

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        robotController = RobotController.getInstance();
        initializeCoordinates();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ConstraintLayout rootView = (ConstraintLayout) inflater.inflate(R.layout.fragment_board, container, false);

        checkerboardView = rootView.findViewById(R.id.checkerboardView);
        btnConfigureIp = rootView.findViewById(R.id.btnConfigureIp);
        btnReset = rootView.findViewById(R.id.btnReset);
        btnGripper = rootView.findViewById(R.id.btnGripper);  // Initialize gripper button

        checkerboardView.setCoordinates(coordinates);
        setupClickListeners();

        return rootView;
    }

    private void setupClickListeners() {
        btnConfigureIp.setOnClickListener(v -> showIpConfigDialog());

        btnReset.setOnClickListener(v -> {
            if (isMoving) {
                Toast.makeText(requireContext(),
                        "Please wait for current movement to complete",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            isMoving = true;
            Toast.makeText(requireContext(),
                    "Resetting robot position",
                    Toast.LENGTH_SHORT).show();

            robotController.reset();

            // Reset isMoving immediately after reset command is sent
            requireView().postDelayed(() -> {
                isMoving = false;
                Log.d(TAG, "Reset completed, ready for next command");
            }, 500); // Short delay to ensure command is processed
        });

        // Add gripper button click listener
        btnGripper.setOnClickListener(v -> {
            if (isMoving) {
                Toast.makeText(requireContext(),
                        "Please wait for current movement to complete",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            toggleGripper();
        });

        checkerboardView.setOnCellClickListener(coordinate -> {
            if (isMoving) {
                Toast.makeText(requireContext(),
                        "Please wait for current movement to complete",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            isMoving = true;
            Log.d(TAG, String.format("Starting precision movement sequence to cell (%d,%d) at XYZT: (%.2f, %.2f, %.2f, %.2f)",
                    coordinate.getGridX(), coordinate.getGridY(),
                    coordinate.getX(), coordinate.getY(), coordinate.getZ(), coordinate.getTorque()));

            Toast.makeText(requireContext(),
                    "Moving to position: " + coordinate.getGridX() + "," + coordinate.getGridY(),
                    Toast.LENGTH_SHORT).show();

            // Use the precision movement sequence (4 steps) with torque control from the coordinate
            float targetTorque = coordinate.getTorque(); // Get torque from coordinate
            robotController.moveToWithPrecisionSequence(coordinate.getX(), coordinate.getY(), coordinate.getZ(), targetTorque);

            // Reset isMoving after full movement sequence completes (4 movements * delay)
            requireView().postDelayed(() -> {
                isMoving = false;
                Log.d(TAG, "Movement sequence completed, ready for next command");
            }, 4 * MOVEMENT_DELAY_MS);
        });
    }

    private void toggleGripper() {
        isMoving = true;

        // Send the motorized gripper control command
        String jsonCmd;
        if (isGripClosed) {
            // Open the gripper
            jsonCmd = "{\"T\":116,\"cmd\":0}";
            btnGripper.setText("Grab Marble");
        } else {
            // Close the gripper
            jsonCmd = "{\"T\":116,\"cmd\":1}";
            btnGripper.setText("Release Marble");
        }

        isGripClosed = !isGripClosed;

        try {
            URL url = new URL("http://" + robotController.getRobotIp() + "/js?json=" + jsonCmd);
            new Thread(() -> {
                try {
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(1000);
                    connection.setReadTimeout(1000);

                    int responseCode = connection.getResponseCode();
                    requireActivity().runOnUiThread(() -> {
                        if (responseCode == HttpURLConnection.HTTP_OK) {
                            Toast.makeText(requireContext(),
                                    isGripClosed ? "Gripper closed" : "Gripper opened",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(),
                                    "Failed to control gripper",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });

                    connection.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "Error controlling gripper", e);
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
                                "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        } catch (MalformedURLException e) {
            Log.e(TAG, "Malformed URL", e);
            Toast.makeText(requireContext(),
                    "Error: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }

        // Allow new movements after a short delay
        requireView().postDelayed(() -> {
            isMoving = false;
        }, 1000);
    }

    private void showIpConfigDialog() {
        new IpConfigDialog(
                requireContext(),
                "192.168.11.172",
                "Configure Robot IP",
                newIp -> {
                    robotController.setRobotIp(newIp);
                    Toast.makeText(requireContext(),
                            "Robot IP updated to: " + newIp,
                            Toast.LENGTH_SHORT).show();
                }
        ).show();
    }

    private void initializeCoordinates() {
        coordinates = new CellCoordinate[17][25];

        // Default torque angle
        float defaultTorque = 3.14f;

        // Custom torque angles for different regions of the board
        float topTorque = 3.00f;      // Top triangle area
        float bottomTorque = 3.28f;   // Bottom triangle area
        float leftTorque = 2.85f;     // Left edge
        float rightTorque = 3.43f;    // Right edge
        float centerTorque = 3.14f;   // Center area

        // Top triangle (Player 1)
        coordinates[0][12] = new CellCoordinate(12, 0, 484.18f, -6.68f, -40.31f, topTorque, true);
        coordinates[1][11] = new CellCoordinate(11, 1, 450.88f, 8.99f, -40.31f, topTorque, true);
        coordinates[1][13] = new CellCoordinate(13, 1, 452.77f, -22.94f, -40.31f, topTorque, true);
        coordinates[2][10] = new CellCoordinate(10, 2, 296.06f, -14.53f, 240.92f, topTorque, true);
        coordinates[2][12] = new CellCoordinate(12, 2, 304.24f, -14.53f, 240.92f, topTorque, true);
        coordinates[2][14] = new CellCoordinate(14, 2, 312.42f, -14.53f, 240.92f, topTorque, true);
        coordinates[3][9] = new CellCoordinate(9, 3, 291.97f, -18.53f, 240.92f, topTorque, true);
        coordinates[3][11] = new CellCoordinate(11, 3, 300.15f, -18.53f, 240.92f, topTorque, true);
        coordinates[3][13] = new CellCoordinate(13, 3, 308.33f, -18.53f, 240.92f, topTorque, true);
        coordinates[3][15] = new CellCoordinate(15, 3, 316.51f, -18.53f, 240.92f, topTorque, true);

        // Bottom triangle (Player 4)
        coordinates[16][12] = new CellCoordinate(12, 16, 304.24f, -70.53f, 240.92f, bottomTorque, true);
        coordinates[15][11] = new CellCoordinate(11, 15, 300.15f, -66.53f, 240.92f, bottomTorque, true);
        coordinates[15][13] = new CellCoordinate(13, 15, 308.33f, -66.53f, 240.92f, bottomTorque, true);
        coordinates[14][10] = new CellCoordinate(10, 14, 296.06f, -62.53f, 240.92f, bottomTorque, true);
        coordinates[14][12] = new CellCoordinate(12, 14, 304.24f, -62.53f, 240.92f, bottomTorque, true);
        coordinates[14][14] = new CellCoordinate(14, 14, 312.42f, -62.53f, 240.92f, bottomTorque, true);
        coordinates[13][9] = new CellCoordinate(9, 13, 291.97f, -58.53f, 240.92f, bottomTorque, true);
        coordinates[13][11] = new CellCoordinate(11, 13, 300.15f, -58.53f, 240.92f, bottomTorque, true);
        coordinates[13][13] = new CellCoordinate(13, 13, 308.33f, -58.53f, 240.92f, bottomTorque, true);
        coordinates[13][15] = new CellCoordinate(15, 13, 316.51f, -58.53f, 240.92f, bottomTorque, true);

        // Some middle positions for testing
        coordinates[8][8] = new CellCoordinate(8, 8, 288.0f, -38.53f, 240.92f, centerTorque, true);
        coordinates[8][12] = new CellCoordinate(12, 8, 253.00f, 11.0f, -83.07f, 2.1f, true);
        coordinates[8][16] = new CellCoordinate(16, 8, 320.48f, -38.53f, 240.92f, centerTorque, true);

        // Add a few more test positions along the edges
        coordinates[4][0] = new CellCoordinate(0, 4, 256.24f, -26.53f, 240.92f, leftTorque, true);
        coordinates[12][0] = new CellCoordinate(0, 12, 256.24f, -50.53f, 240.92f, leftTorque, true);
        coordinates[4][24] = new CellCoordinate(24, 4, 352.24f, -26.53f, 240.92f, rightTorque, true);
        coordinates[12][24] = new CellCoordinate(24, 12, 352.24f, -50.53f, 240.92f, rightTorque, true);

        Log.d(TAG, "Coordinates initialized: " + coordinates.length + " rows");
    }
}