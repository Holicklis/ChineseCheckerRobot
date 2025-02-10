package hku.cs.fyp24057.chinesecheckerrobot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class CheckerboardView extends View {
    private static final String TAG = "CheckerboardView";
    private static final int ROWS = 17;
    private static final int COLS = 25;
    private static final int CELL_PADDING = 1;
    private static final float VERTICAL_SPACING_FACTOR = 1.2f; // Increase vertical spacing

    private float cellSize;
    private float startX;  // For centering the board horizontally
    private float startY;  // For centering the board vertically
    private Paint gridPaint;
    private Paint cellPaint;
    private Paint validCellPaint;
    private Paint highlightPaint;
    private OnCellClickListener listener;

    private CellCoordinate[][] coordinates;
    private int lastTouchedRow = -1;
    private int lastTouchedCol = -1;

    public interface OnCellClickListener {
        void onCellClick(CellCoordinate coordinate);
    }

    public CheckerboardView(Context context) {
        super(context);
        init();
    }

    public CheckerboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        gridPaint = new Paint();
        gridPaint.setColor(Color.GRAY);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);

        cellPaint = new Paint();
        cellPaint.setColor(Color.WHITE);
        cellPaint.setStyle(Paint.Style.FILL);

        validCellPaint = new Paint();
        validCellPaint.setColor(Color.LTGRAY);
        validCellPaint.setStyle(Paint.Style.FILL);

        highlightPaint = new Paint();
        highlightPaint.setColor(Color.BLUE);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(2f);
    }

    public void setCoordinates(CellCoordinate[][] coordinates) {
        this.coordinates = coordinates;
        invalidate();
    }

    public void setOnCellClickListener(OnCellClickListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Calculate the base cell size using the width
        cellSize = (w * 0.9f) / COLS; // Use 90% of width

        // Calculate total board width and height
        float boardWidth = cellSize * COLS;
        float boardHeight = cellSize * ROWS * VERTICAL_SPACING_FACTOR;

        // Center the board
        startX = (w - boardWidth) / 2;
        startY = (h - boardHeight) / 2;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (coordinates == null) return;

        // Draw grid and cells
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                float left = startX + (col * cellSize);
                float top = startY + (row * cellSize * VERTICAL_SPACING_FACTOR);

                // Draw grid cells
                canvas.drawRect(
                        left + CELL_PADDING,
                        top + CELL_PADDING,
                        left + cellSize - CELL_PADDING,
                        top + cellSize - CELL_PADDING,
                        gridPaint
                );

                if (coordinates[row][col] != null && coordinates[row][col].isValidCell()) {
                    // Draw valid cell background
                    canvas.drawRect(
                            left + CELL_PADDING,
                            top + CELL_PADDING,
                            left + cellSize - CELL_PADDING,
                            top + cellSize - CELL_PADDING,
                            validCellPaint
                    );

                    // Draw circle for valid positions
                    canvas.drawCircle(
                            left + cellSize/2,
                            top + cellSize/2,
                            (cellSize/2) - CELL_PADDING * 2,
                            cellPaint
                    );
                }

                // Highlight touched cell
                if (row == lastTouchedRow && col == lastTouchedCol) {
                    canvas.drawRect(
                            left + CELL_PADDING,
                            top + CELL_PADDING,
                            left + cellSize - CELL_PADDING,
                            top + cellSize - CELL_PADDING,
                            highlightPaint
                    );
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX() - startX;
        float y = event.getY() - startY;

        // Convert touch coordinates to grid positions, accounting for vertical spacing
        int col = (int) (x / cellSize);
        int row = (int) (y / (cellSize * VERTICAL_SPACING_FACTOR));

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (isValidPosition(row, col)) {
                    lastTouchedRow = row;
                    lastTouchedCol = col;
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
                if (row == lastTouchedRow && col == lastTouchedCol && isValidPosition(row, col)) {
                    CellCoordinate coordinate = coordinates[row][col];
                    if (coordinate != null && coordinate.isValidCell() && listener != null) {
                        Log.d(TAG, "Cell clicked: (" + row + "," + col + ")");
                        listener.onCellClick(coordinate);
                    }
                }
                lastTouchedRow = -1;
                lastTouchedCol = -1;
                invalidate();
                return true;
        }

        return super.onTouchEvent(event);
    }

    private boolean isValidPosition(int row, int col) {
        return row >= 0 && row < ROWS && col >= 0 && col < COLS &&
                coordinates != null && coordinates[row][col] != null &&
                coordinates[row][col].isValidCell();
    }
}