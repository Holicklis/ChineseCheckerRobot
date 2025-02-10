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

public class BoardFragment extends Fragment {
    private static final String TAG = "BoardFragment";
    private CheckerboardView checkerboardView;
    private CellCoordinate[][] coordinates;
    private RobotController robotController;
    private Button btnConfigureIp;
    private Button btnReset;
    private boolean isMoving = false;

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

            // Reset isMoving after movement delay
            requireView().postDelayed(() -> {
                isMoving = false;
            }, 3500);
        });

        checkerboardView.setOnCellClickListener(coordinate -> {
            if (isMoving) {
                Toast.makeText(requireContext(),
                        "Please wait for current movement to complete",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            isMoving = true;
            Log.d(TAG, String.format("Starting movement sequence to cell (%d,%d) at XYZ: (%.2f, %.2f, %.2f)",
                    coordinate.getGridX(), coordinate.getGridY(),
                    coordinate.getX(), coordinate.getY(), coordinate.getZ()));

            Toast.makeText(requireContext(),
                    "Moving to position: " + coordinate.getGridX() + "," + coordinate.getGridY(),
                    Toast.LENGTH_SHORT).show();

            robotController.moveToWithZSequence(coordinate.getX(), coordinate.getY(), coordinate.getZ());

            requireView().postDelayed(() -> {
                isMoving = false;
            }, 3500);
        });
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

        // Top triangle (Player 1)
        coordinates[0][12] = new CellCoordinate(12, 0, 484.18f, -6.68f, -40.31f, true);
        coordinates[1][11] = new CellCoordinate(11, 1, 450.88f, 8.99f, -40.31f, true);
        coordinates[1][13] = new CellCoordinate(13, 1, 452.77f, -22.94f, -40.31f, true);
        coordinates[2][10] = new CellCoordinate(10, 2, 296.06f, -14.53f, 240.92f, true);
        coordinates[2][12] = new CellCoordinate(12, 2, 304.24f, -14.53f, 240.92f, true);
        coordinates[2][14] = new CellCoordinate(14, 2, 312.42f, -14.53f, 240.92f, true);
        coordinates[3][9] = new CellCoordinate(9, 3, 291.97f, -18.53f, 240.92f, true);
        coordinates[3][11] = new CellCoordinate(11, 3, 300.15f, -18.53f, 240.92f, true);
        coordinates[3][13] = new CellCoordinate(13, 3, 308.33f, -18.53f, 240.92f, true);
        coordinates[3][15] = new CellCoordinate(15, 3, 316.51f, -18.53f, 240.92f, true);

        // Bottom triangle (Player 4)
        coordinates[16][12] = new CellCoordinate(12, 16, 304.24f, -70.53f, 240.92f, true);
        coordinates[15][11] = new CellCoordinate(11, 15, 300.15f, -66.53f, 240.92f, true);
        coordinates[15][13] = new CellCoordinate(13, 15, 308.33f, -66.53f, 240.92f, true);
        coordinates[14][10] = new CellCoordinate(10, 14, 296.06f, -62.53f, 240.92f, true);
        coordinates[14][12] = new CellCoordinate(12, 14, 304.24f, -62.53f, 240.92f, true);
        coordinates[14][14] = new CellCoordinate(14, 14, 312.42f, -62.53f, 240.92f, true);
        coordinates[13][9] = new CellCoordinate(9, 13, 291.97f, -58.53f, 240.92f, true);
        coordinates[13][11] = new CellCoordinate(11, 13, 300.15f, -58.53f, 240.92f, true);
        coordinates[13][13] = new CellCoordinate(13, 13, 308.33f, -58.53f, 240.92f, true);
        coordinates[13][15] = new CellCoordinate(15, 13, 316.51f, -58.53f, 240.92f, true);

        // Some middle positions for testing
        coordinates[8][8] = new CellCoordinate(8, 8, 288.0f, -38.53f, 240.92f, true);
        coordinates[8][12] = new CellCoordinate(12, 8, 304.24f, -38.53f, 240.92f, true);
        coordinates[8][16] = new CellCoordinate(16, 8, 320.48f, -38.53f, 240.92f, true);

        // Add a few more test positions along the edges
        coordinates[4][0] = new CellCoordinate(0, 4, 256.24f, -26.53f, 240.92f, true);
        coordinates[12][0] = new CellCoordinate(0, 12, 256.24f, -50.53f, 240.92f, true);
        coordinates[4][24] = new CellCoordinate(24, 4, 352.24f, -26.53f, 240.92f, true);
        coordinates[12][24] = new CellCoordinate(24, 12, 352.24f, -50.53f, 240.92f, true);
    }
}