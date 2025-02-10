package hku.cs.fyp24057.chinesecheckerrobot;

public class CellCoordinate {
    private final float x;
    private final float y;
    private final float z;
    private final int gridX;
    private final int gridY;
    private boolean isValidCell;

    public CellCoordinate(int gridX, int gridY, float x, float y, float z, boolean isValidCell) {
        this.gridX = gridX;
        this.gridY = gridY;
        this.x = x;
        this.y = y;
        this.z = z;
        this.isValidCell = isValidCell;
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public int getGridX() { return gridX; }
    public int getGridY() { return gridY; }
    public boolean isValidCell() { return isValidCell; }
}