package hku.cs.fyp24057.chinesecheckerrobot;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.*;

public class BoardImageProcessor {
    private static final double THRESHOLD = 127;
    private static final Size BLUR_SIZE = new Size(5, 5);
    private static final int CIRCLE_DETECTION_THRESHOLD = 30;
    private static final int MIN_RADIUS = 10;
    private static final int MAX_RADIUS = 25;

    public static BoardState processImage(Mat inputImage) {
        BoardState boardState = new BoardState();

        // Step 1: Preprocess the image
        Mat preprocessed = preprocess(inputImage);

        // Step 2: Find the board corners using Hough lines
        Point[] corners = findBoardCorners(preprocessed);
        if (corners == null) {
            System.out.println("Failed to detect board corners");
            return boardState;
        }

        // Step 3: Calculate grid positions
        List<Point> gridPositions = calculateGridPositions(corners);

        // Step 4: Detect marbles at each grid position
        for (int i = 0; i < gridPositions.size(); i++) {
            Point pos = gridPositions.get(i);
            int[] rowCol = indexToRowCol(i);

            // Check for marble presence
            Rect roi = getRoiAroundPoint(pos, inputImage.size());
            Mat region = new Mat(preprocessed, roi);

            int marbleType = detectMarbleInRegion(region, inputImage.submat(roi));
            if (marbleType != BoardState.EMPTY) {
                boardState.setPosition(rowCol[0], rowCol[1], marbleType);
            }

            // Draw debug visualization
            drawGridPoint(inputImage, pos, marbleType);
        }

        return boardState;
    }

    private static Mat preprocess(Mat input) {
        Mat gray = new Mat();
        Mat blurred = new Mat();
        Mat binary = new Mat();

        // Convert to grayscale
        Imgproc.cvtColor(input, gray, Imgproc.COLOR_BGR2GRAY);

        // Apply Gaussian blur
        Imgproc.GaussianBlur(gray, blurred, BLUR_SIZE, 0);

        // Apply adaptive thresholding
        Imgproc.adaptiveThreshold(blurred, binary, 255,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV, 11, 2);

        gray.release();
        blurred.release();
        return binary;
    }

    private static Point[] findBoardCorners(Mat binary) {
        // Find contours
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(binary, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Find the largest hexagonal contour
        MatOfPoint boardContour = null;
        double maxArea = 0;

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area > maxArea) {
                MatOfPoint2f approx = new MatOfPoint2f();
                MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
                double epsilon = 0.02 * Imgproc.arcLength(contour2f, true);
                Imgproc.approxPolyDP(contour2f, approx, epsilon, true);

                if (approx.total() == 6) {  // Hexagonal shape
                    maxArea = area;
                    boardContour = contour;
                }
            }
        }

        if (boardContour == null) return null;

        // Get the corners from the contour
        MatOfPoint2f boardContour2f = new MatOfPoint2f(boardContour.toArray());
        RotatedRect boundingBox = Imgproc.minAreaRect(boardContour2f);
        Point[] corners = new Point[4];
        boundingBox.points(corners);

        hierarchy.release();
        return corners;
    }

    private static List<Point> calculateGridPositions(Point[] corners) {
        List<Point> positions = new ArrayList<>();
        Point center = new Point(
                (corners[0].x + corners[2].x) / 2,
                (corners[0].y + corners[2].y) / 2
        );

        double cellSize = Math.min(
                Math.abs(corners[1].x - corners[0].x),
                Math.abs(corners[1].y - corners[0].y)
        ) / 8.0;  // Divide by 8 as there are typically 8 cells across

        // Calculate positions based on the standard Chinese Checkers layout
        for (int row = 0; row < 17; row++) {
            int rowLength = getRowLength(row);
            double rowOffset = (13 - rowLength) * cellSize / 2;

            for (int col = 0; col < rowLength; col++) {
                double x = center.x - (rowLength * cellSize / 2) + (col * cellSize) + rowOffset;
                double y = center.y - (8 * cellSize) + (row * cellSize * 0.866); // 0.866 = sqrt(3)/2
                positions.add(new Point(x, y));
            }
        }

        return positions;
    }

    private static int getRowLength(int row) {
        if (row < 4) return row + 1;
        else if (row < 13) return 13;
        else return 17 - row;
    }

    private static int[] indexToRowCol(int index) {
        int row = 0;
        int remaining = index;

        while (remaining >= getRowLength(row)) {
            remaining -= getRowLength(row);
            row++;
        }

        return new int[]{row, remaining};
    }

    private static Rect getRoiAroundPoint(Point center, Size imageSize) {
        int roiSize = MAX_RADIUS * 2;
        int x = Math.max(0, (int)center.x - MAX_RADIUS);
        int y = Math.max(0, (int)center.y - MAX_RADIUS);
        x = Math.min(x, (int)imageSize.width - roiSize);
        y = Math.min(y, (int)imageSize.height - roiSize);
        return new Rect(x, y, roiSize, roiSize);
    }

    private static int detectMarbleInRegion(Mat region, Mat colorRegion) {
        // Count white pixels in binary image
        int whitePixels = Core.countNonZero(region);
        if (whitePixels < (Math.PI * MIN_RADIUS * MIN_RADIUS * 0.7)) {
            return BoardState.EMPTY;
        }

        // If marble detected, determine its color
        Mat hsv = new Mat();
        Imgproc.cvtColor(colorRegion, hsv, Imgproc.COLOR_BGR2HSV);

        Scalar meanColor = Core.mean(hsv);
        int hue = (int)meanColor.val[0];
        int sat = (int)meanColor.val[1];
        int val = (int)meanColor.val[2];

        // Simple color classification
        if (sat < 50 && val > 150) return BoardState.WHITE;
        if (sat < 50 && val < 50) return BoardState.BLACK;
        if (hue < 10 || hue > 170) return BoardState.RED;
        if (hue > 100 && hue < 130) return BoardState.BLUE;
        if (hue > 35 && hue < 85) return BoardState.GREEN;
        if (hue > 20 && hue < 35) return BoardState.YELLOW;

        return BoardState.BLACK;  // Default if color unclear
    }

    private static void drawGridPoint(Mat image, Point center, int marbleType) {
        Scalar color;
        switch (marbleType) {
            case BoardState.EMPTY -> color = new Scalar(128, 128, 128);
            case BoardState.BLACK -> color = new Scalar(0, 0, 0);
            case BoardState.WHITE -> color = new Scalar(255, 255, 255);
            case BoardState.RED -> color = new Scalar(0, 0, 255);
            case BoardState.BLUE -> color = new Scalar(255, 0, 0);
            case BoardState.GREEN -> color = new Scalar(0, 255, 0);
            case BoardState.YELLOW -> color = new Scalar(0, 255, 255);
            default -> color = new Scalar(128, 128, 128);
        }

        // Draw grid point
        Imgproc.circle(image, center, 2, color, -1);
        if (marbleType != BoardState.EMPTY) {
            Imgproc.circle(image, center, MIN_RADIUS, color, 2);
        }
    }
}