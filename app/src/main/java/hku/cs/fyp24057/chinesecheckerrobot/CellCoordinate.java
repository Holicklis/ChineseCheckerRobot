package hku.cs.fyp24057.chinesecheckerrobot;

public class CellCoordinate {
    private final float x;
    private final float y;
    private final float z;
    private final float torque; // Add torque angle parameter
    private final int gridX;
    private final int gridY;
    private boolean isValidCell;

    public CellCoordinate(int gridX, int gridY, float x, float y, float z, float torque, boolean isValidCell) {
        this.gridX = gridX;
        this.gridY = gridY;
        this.x = x;
        this.y = y;
        this.z = z;
        this.torque = torque;
        this.isValidCell = isValidCell;
    }

    // Constructor for backward compatibility
    public CellCoordinate(int gridX, int gridY, float x, float y, float z, boolean isValidCell) {
        this(gridX, gridY, x, y, z, 2.1f, isValidCell); // Default torque angle to 3.14 radians
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public float getTorque() { return torque; } // Getter for torque angle
    public int getGridX() { return gridX; }
    public int getGridY() { return gridY; }
    public boolean isValidCell() { return isValidCell; }
}