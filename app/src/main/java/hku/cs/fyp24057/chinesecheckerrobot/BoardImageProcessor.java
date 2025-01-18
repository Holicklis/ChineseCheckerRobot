package hku.cs.fyp24057.chinesecheckerrobot;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class BoardImageProcessor {
    private static final String TAG = "BoardImageProcessor";
    private static final double THRESHOLD = 127;
    private static final Size BLUR_SIZE = new Size(5, 5);
    private static final int MIN_RADIUS = 10;
    private static final int MAX_RADIUS = 25;

    // Color detection thresholds for HSV
    private static final Scalar BLUE_LOWER = new Scalar(100, 50, 50);
    private static final Scalar BLUE_UPPER = new Scalar(130, 255, 255);
    private static final Scalar WHITE_LOWER = new Scalar(0, 0, 200);
    private static final Scalar WHITE_UPPER = new Scalar(180, 30, 255);

    // Save debug images with timestamp
    private static void saveDebugImage(Context context, Mat mat, String prefix) {
        try {
            Bitmap bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, bmp);

            // Create timestamp
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());

            // Get Pictures directory
            File picturesDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES);
            File debugDir = new File(picturesDir, "ChineseCheckers");
            if (!debugDir.exists() && !debugDir.mkdirs()) {
                Log.e(TAG, "Failed to create debug directory");
                return;
            }

            // Save file with timestamp
            File imageFile = new File(debugDir, prefix + "_" + timestamp + ".jpg");
            try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                Log.d(TAG, "Saved debug image: " + imageFile.getAbsolutePath());

                // Add file to media store so it appears in Photos app
                MediaScannerConnection.scanFile(context,
                        new String[] { imageFile.getAbsolutePath() },
                        new String[] { "image/jpeg" },
                        (path, uri) -> {
                            Log.d(TAG, "Scanned " + path);
                            Log.d(TAG, "-> uri=" + uri);
                        });
            }
        } catch (IOException e) {
            Log.e(TAG, "Error saving debug image: " + e.getMessage());
        }
    }

    public static BoardState processImage(Mat inputImage, Context context) {
        BoardState boardState = new BoardState();

        try {
            // Step 1: Create a working copy
            Mat workingCopy = inputImage.clone();
            saveDebugImage(context, workingCopy, "1_original");

            // Step 2: Convert to HSV for better color detection
            Mat hsv = new Mat();
            Imgproc.cvtColor(workingCopy, hsv, Imgproc.COLOR_BGR2HSV);
            saveDebugImage(context, hsv, "2_hsv");

            // Step 3: Create binary masks for blue and white marbles
            Mat blueMask = new Mat();
            Mat whiteMask = new Mat();
            Core.inRange(hsv, BLUE_LOWER, BLUE_UPPER, blueMask);
            Core.inRange(hsv, WHITE_LOWER, WHITE_UPPER, whiteMask);
            saveDebugImage(context, blueMask, "3_blue_mask");
            saveDebugImage(context, whiteMask, "4_white_mask");

            // Step 4: Detect board grid
            Mat edges = new Mat();
            Imgproc.Canny(workingCopy, edges, 50, 150);
            saveDebugImage(context, edges, "5_edges");

            // Step 5: Find hexagonal board
            MatOfPoint2f approxBoard = detectHexagonalBoard(edges);
            if (approxBoard == null) {
                Log.e(TAG, "Failed to detect hexagonal board");
                return boardState;
            }

            // Draw detected board outline
            Imgproc.polylines(workingCopy, Arrays.asList(new MatOfPoint(approxBoard.toArray())),
                    true, new Scalar(0, 255, 0), 2);
            saveDebugImage(context, workingCopy, "6_detected_board");

            // Step 6: Detect circles (marble positions)
            List<Point> marblePositions = findMarblePositions(workingCopy);

            // Step 7: Classify marbles
            for (Point pos : marblePositions) {
                // Get small region around position
                Rect roi = getRoiAroundPoint(pos, workingCopy.size());

                // Check blue mask
                Mat blueRoi = blueMask.submat(roi);
                Mat whiteRoi = whiteMask.submat(roi);

                int blueCount = Core.countNonZero(blueRoi);
                int whiteCount = Core.countNonZero(whiteRoi);

                // Classify based on majority color
                int marbleType = BoardState.EMPTY;
                if (blueCount > 100) marbleType = BoardState.BLUE;
                else if (whiteCount > 100) marbleType = BoardState.WHITE;

                // Convert position to board coordinates
                int[] boardCoords = convertPositionToBoardCoordinates(pos, approxBoard);
                if (boardCoords != null) {
                    boardState.setPosition(boardCoords[0], boardCoords[1], marbleType);

                    // Draw detection result
                    Scalar color = marbleType == BoardState.BLUE ?
                            new Scalar(255, 0, 0) :
                            (marbleType == BoardState.WHITE ?
                                    new Scalar(255, 255, 255) :
                                    new Scalar(128, 128, 128));
                    Imgproc.circle(workingCopy, pos, 5, color, -1);
                }
            }

            saveDebugImage(context, workingCopy, "7_final_result");

            // Cleanup
            workingCopy.release();
            hsv.release();
            blueMask.release();
            whiteMask.release();
            edges.release();

        } catch (Exception e) {
            Log.e(TAG, "Error processing image: " + e.getMessage());
            e.printStackTrace();
        }

        return boardState;
    }

    private static MatOfPoint2f detectHexagonalBoard(Mat edges) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(edges, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        MatOfPoint2f bestHex = null;
        double maxArea = 0;

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area > 1000) { // Minimum area threshold
                MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
                MatOfPoint2f approx = new MatOfPoint2f();
                double epsilon = 0.02 * Imgproc.arcLength(contour2f, true);
                Imgproc.approxPolyDP(contour2f, approx, epsilon, true);

                if (approx.total() == 6) { // Hexagon has 6 vertices
                    if (area > maxArea) {
                        maxArea = area;
                        bestHex = approx;
                    }
                }
            }
        }

        return bestHex;
    }

    private static List<Point> findMarblePositions(Mat image) {
        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(gray, gray, new Size(9, 9), 2);

        Mat circles = new Mat();
        Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1,
                20, // Minimum distance between centers
                100, // Upper threshold for Canny edge detector
                30,  // Threshold for center detection
                MIN_RADIUS, MAX_RADIUS);

        List<Point> positions = new ArrayList<>();
        if (!circles.empty()) {
            for (int i = 0; i < circles.cols(); i++) {
                double[] circle = circles.get(0, i);
                positions.add(new Point(circle[0], circle[1]));
            }
        }

        gray.release();
        circles.release();
        return positions;
    }

    private static Rect getRoiAroundPoint(Point center, Size imageSize) {
        int roiSize = MAX_RADIUS * 2;
        int x = Math.max(0, (int)center.x - MAX_RADIUS);
        int y = Math.max(0, (int)center.y - MAX_RADIUS);
        x = Math.min(x, (int)imageSize.width - roiSize);
        y = Math.min(y, (int)imageSize.height - roiSize);
        return new Rect(x, y, roiSize, roiSize);
    }

    private static int[] convertPositionToBoardCoordinates(Point pos, MatOfPoint2f board) {
        // TODO: Implement coordinate conversion based on board perspective
        // This is a placeholder implementation
        return new int[]{0, 0};
    }
}