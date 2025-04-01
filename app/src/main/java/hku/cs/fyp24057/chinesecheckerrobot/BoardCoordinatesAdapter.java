package hku.cs.fyp24057.chinesecheckerrobot;

import android.util.Log;
import java.util.HashMap;
import java.util.Map;

/**
 * This class provides mapping between the AI's logical board coordinates
 * and the physical robot coordinates for movement.
 */
public class BoardCoordinatesAdapter {
    private static final String TAG = "BoardCoordinatesAdapter";

    private static final int centralX  = 4;
    private static final int centralY  = 8;

    private static final float centralCoordinateX = 240f;
    private static final float centralCoordinateY = 4f;
    private static final float centralCoordinateZ = -110f;
    private static final float centralCoordinateTorque = 1.9f;
    private static final float xDis = 26f;
    // Singleton instance
    private static BoardCoordinatesAdapter instance;

    // Maps from board coordinates (x,y) to CellCoordinate objects
    private final Map<String, CellCoordinate> coordsMap;

    private BoardCoordinatesAdapter() {
        coordsMap = new HashMap<>();
        initializeCoordinateMapping();
    }

    public static BoardCoordinatesAdapter getInstance() {
        if (instance == null) {
            instance = new BoardCoordinatesAdapter();
        }
        return instance;
    }

    /**
     * Convert logical board coordinates to a robot-compatible CellCoordinate
     * @param boardX x-coordinate in the AI's board representation
     * @param boardY y-coordinate in the AI's board representation
     * @return CellCoordinate for robot movement or null if not found
     */
    public CellCoordinate getBoardCellCoordinate(int boardX, int boardY) {
        String key = getKey(boardX, boardY);
        CellCoordinate result = coordsMap.get(key);

        if (result == null) {
            Log.w(TAG, "No mapping found for board coordinates: " + key);
        }

        return result;
    }

    /**
     * Generate a lookup key from x,y coordinates
     */
    private String getKey(int x, int y) {
        return x + "," + y;
    }

    /**
     * Initialize the coordinate mapping between logical board and physical robot positions
     */
    private void initializeCoordinateMapping() {
        // You can define different torques for top/center/bottom rows if desired:
        float topTorque = 3.00f;
        float centerTorque = 3.14f;
        float bottomTorque = 1.07f;

        // ---------------------------
        // Row 0 (1 cell) => offset=6 => boardX=6
        // TILES_PER_ROW[0] = 1
        // ---------------------------
        addMapping(0, centralY-8,
                /*gridX*/ 12, /*gridY*/ 0,
                /*x*/ centralCoordinateX+xDis*8+20f, /*y*/ centralCoordinateY+3f, /*z*/ centralCoordinateZ-5f,
                centralCoordinateTorque+1.2f);
//        addMapping(6, 0,
//                /*gridX*/ 0, /*gridY*/ 0,
//                /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                topTorque);

        // ---------------------------
        // Row 1 (2 cells) => offset=5 => boardX=5..6
        // TILES_PER_ROW[1] = 2
        // ---------------------------
        addMapping(0, centralY-7,
                /*gridX*/ 11, /*gridY*/ 1,
                /*x*/ centralCoordinateX+xDis*7, /*y*/ centralCoordinateY+12.5f, /*z*/ centralCoordinateZ-20f,
                centralCoordinateTorque+0.3f);

        addMapping(1, centralY-7,
                /*gridX*/ 13, /*gridY*/ 1,
                /*x*/ centralCoordinateX+xDis*7, /*y*/ centralCoordinateY-12.5f, /*z*/ centralCoordinateZ-20f,
                centralCoordinateTorque+0.3f);
//        addMapping(5, 1,
//                /*gridX*/ 0, /*gridY*/ 1,
//                /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                topTorque);
//        addMapping(6, 1,
//                /*gridX*/ 0, /*gridY*/ 1,
//                /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                topTorque);

        // ---------------------------
        // Row 2 (3 cells) => offset=4 => boardX=4..6
        // TILES_PER_ROW[2] = 3
        // ---------------------------
        addMapping(0, centralY-6,
                /*gridX*/ 10, /*gridY*/ 2,
                /*x*/ centralCoordinateX+xDis*6, /*y*/ centralCoordinateY+25.0f, /*z*/ centralCoordinateZ-15f,
                centralCoordinateTorque+0.25f);

        addMapping(1, centralY-6,
                /*gridX*/ 12, /*gridY*/ 2,
                /*x*/ centralCoordinateX+xDis*6, /*y*/ centralCoordinateY, /*z*/ centralCoordinateZ-15f,
                centralCoordinateTorque+0.25f);

        addMapping(2, centralY-6,
                /*gridX*/ 14, /*gridY*/ 2,
                /*x*/ centralCoordinateX+xDis*6, /*y*/ centralCoordinateY-25.0f, /*z*/ centralCoordinateZ-15f,
                centralCoordinateTorque+0.25f);
//        addMapping(4, 2,
//                /*gridX*/ 0, /*gridY*/ 2,
//                /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                topTorque);
//        addMapping(5, 2,
//                /*gridX*/ 0, /*gridY*/ 2,
//                /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                topTorque);
//        addMapping(6, 2,
//                /*gridX*/ 0, /*gridY*/ 2,
//                /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                topTorque);

        // ---------------------------
        // Row 3 (4 cells) => offset=3 => boardX=3..6
        // TILES_PER_ROW[3] = 4
        // ---------------------------
        for (int bx = 0; bx <= 1; bx++) {
            addMapping(bx, 3,
                    /*gridX*/ 9+bx*2, /*gridY*/ 3,
                    /*x*/ centralCoordinateX+xDis*5.5f, /*y*/ centralCoordinateY+14f+27.0f *(1-bx), /*z*/ centralCoordinateZ-10f,
                    centralCoordinateTorque+0.2f);
        }

        for (int bx = 2; bx <= 3; bx++) {
            addMapping(bx, 3,
                    /*gridX*/ 13+(bx-2)*2, /*gridY*/ 3,
                    /*x*/ centralCoordinateX+xDis*5.5f, /*y*/ centralCoordinateY-14f-27.0f *(bx-2), /*z*/ centralCoordinateZ-10f,
                    centralCoordinateTorque+0.2f);
        }
//        addMapping(3, 3,
//                /*gridX*/ 0, /*gridY*/ 3,
//                /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                topTorque);
//        addMapping(4, 3,
//                /*gridX*/ 0, /*gridY*/ 3,
//                /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                topTorque);
//        addMapping(5, 3,
//                /*gridX*/ 0, /*gridY*/ 3,
//                /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                topTorque);
//        addMapping(6, 3,
//                /*gridX*/ 0, /*gridY*/ 3,
//                /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                topTorque);

        // --------------------------------------------------
        // Row 4 (13 cells) => offset=0 => boardX=0..12
        // TILES_PER_ROW[4] = 13
        // --------------------------------------------------
        for (int bx = 0; bx <= 5; bx++) {
            addMapping(bx, 4,
                    /*gridX*/ 0+bx*2, /*gridY*/ 12,
                    /*x*/ centralCoordinateX+xDis*4, /*y*/ centralCoordinateY+25.0f *(6-bx), /*z*/ centralCoordinateZ-10f,
                    centralCoordinateTorque+0.2f);
        }

        addMapping(centralX+2, centralY-4,
                /*gridX*/ 12, /*gridY*/ 4,
                /*x*/ centralCoordinateX+xDis*4, /*y*/ centralCoordinateY, /*z*/ centralCoordinateZ-10f,
                centralCoordinateTorque+0.2f);

        for (int bx = 7; bx <= 12; bx++) {
            addMapping(bx, 4,
                    /*gridX*/ 12+(bx-6)*2, /*gridY*/ 4,
                    /*x*/ centralCoordinateX+xDis*4, /*y*/ centralCoordinateY-25.0f *(bx-6), /*z*/ centralCoordinateZ-10f,
                    centralCoordinateTorque+0.2f);
        }
//        for (int bx = 0; bx <= 12; bx++) {
//            addMapping(bx, 4,
//                    /*gridX*/ 0, /*gridY*/ 4,
//                    /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                    centerTorque);
//        }

        // --------------------------------------------------
        // Row 5 (12 cells) => offset=0 => boardX=0..11
        // TILES_PER_ROW[5] = 12
        // --------------------------------------------------
        for (int bx = 0; bx <= 5; bx++) {
            addMapping(bx, 5,
                    /*gridX*/ 1+bx*2, /*gridY*/ 5,
                    /*x*/ centralCoordinateX+xDis*4f, /*y*/ centralCoordinateY+12.5f+25.0f *(5-bx), /*z*/ centralCoordinateZ-5f,
                    centralCoordinateTorque+0.15f);
        }

        for (int bx = 6; bx <= 11; bx++) {
            addMapping(bx, 5,
                    /*gridX*/ 11+(bx-5)*2, /*gridY*/ 5,
                    /*x*/ centralCoordinateX+xDis*4f, /*y*/ centralCoordinateY-12.5f-25.0f *(bx-5), /*z*/ centralCoordinateZ-5f,
                    centralCoordinateTorque+0.15f);
        }
//        for (int bx = 0; bx <= 11; bx++) {
//            addMapping(bx, 5,
//                    /*gridX*/ 0, /*gridY*/ 5,
//                    /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                    centerTorque);
//        }

        // --------------------------------------------------
        // Row 6 (11 cells) => offset=0 => boardX=0..10
        // TILES_PER_ROW[6] = 11
        // --------------------------------------------------
//        for (int bx = 0; bx <= 4; bx++) {
//            addMapping(bx, 6,
//                    /*gridX*/ 2+bx*2, /*gridY*/ 6,
//                    /*x*/ centralCoordinateX+xDis*2, /*y*/ centralCoordinateY+25.0f *(5-bx), /*z*/ centralCoordinateZ,
//                    centralCoordinateTorque+0.1f);
//        }
//
//        addMapping(centralX+1, centralY-2, //5,10
//                /*gridX*/ 12, /*gridY*/ 6,
//                /*x*/ centralCoordinateX+xDis*2, /*y*/ centralCoordinateY, /*z*/ centralCoordinateZ,
//                centralCoordinateTorque+0.1f);
//
//        for (int bx = 6; bx <= 10; bx++) {
//            addMapping(bx, 6,
//                    /*gridX*/ 12+(bx-5)*2, /*gridY*/ 6,
//                    /*x*/ centralCoordinateX+xDis*2, /*y*/ centralCoordinateY-25.0f *(bx-5), /*z*/ centralCoordinateZ,
//                    centralCoordinateTorque+0.1f);
//        }

        addMapping(centralX-4, centralY-2,
                /*gridX*/ 2, /*gridY*/ 6,
                /*x*/ centralCoordinateX+xDis*2+7f, /*y*/ centralCoordinateY+123f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque+0.1f);

        addMapping(centralX-3, centralY-2,
                /*gridX*/ 4, /*gridY*/ 6,
                /*x*/ centralCoordinateX+xDis*2+6f, /*y*/ centralCoordinateY+98f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque+0.1f);

        addMapping(centralX-2, centralY-2,
                /*gridX*/ 6, /*gridY*/ 6,
                /*x*/ centralCoordinateX+xDis*2+4f, /*y*/ centralCoordinateY+72f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque+0.1f);

        addMapping(centralX-1, centralY-2,
                /*gridX*/ 8, /*gridY*/ 6,
                /*x*/ centralCoordinateX+xDis*2+3f, /*y*/ centralCoordinateY+48f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque+0.1f);

        addMapping(centralX+0, centralY-2,
                /*gridX*/ 10, /*gridY*/ 6,
                /*x*/ centralCoordinateX+xDis*2+1f, /*y*/ centralCoordinateY+24f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque+0.1f);

        addMapping(centralX+1, centralY-2,
                /*gridX*/ 12, /*gridY*/ 6,
                /*x*/ centralCoordinateX+xDis*2+1f, /*y*/ centralCoordinateY, /*z*/ centralCoordinateZ,
                centralCoordinateTorque+0.1f);

        addMapping(centralX+2, centralY-2,
                /*gridX*/ 14, /*gridY*/ 6,
                /*x*/ centralCoordinateX+xDis*2+2f, /*y*/ centralCoordinateY-22f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque+0.1f);

        addMapping(centralX+3, centralY-2,
                /*gridX*/ 16, /*gridY*/ 6,
                /*x*/ centralCoordinateX+xDis*2+5f, /*y*/ centralCoordinateY-48f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque+0.1f);

        addMapping(centralX+4, centralY-2,
                /*gridX*/ 18, /*gridY*/ 6,
                /*x*/ centralCoordinateX+xDis*2+6f, /*y*/ centralCoordinateY-72f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque+0.1f);

        addMapping(centralX+5, centralY-2,
                /*gridX*/ 20, /*gridY*/ 6,
                /*x*/ centralCoordinateX+xDis*2+7f, /*y*/ centralCoordinateY-96f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque+0.1f);

        addMapping(centralX+6, centralY-2,
                /*gridX*/ 22, /*gridY*/ 6,
                /*x*/ centralCoordinateX+xDis*2+10f, /*y*/ centralCoordinateY-118f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque+0.1f);

//        for (int bx = 0; bx <= 10; bx++) {
//            addMapping(bx, 6,
//                    /*gridX*/ 0, /*gridY*/ 6,
//                    /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                    centerTorque);
//        }

        // --------------------------------------------------
        // Row 7 (10 cells) => offset=0 => boardX=0..9
        // TILES_PER_ROW[7] = 10
        // --------------------------------------------------
//        for (int bx = 0; bx <= 4; bx++) {
//            addMapping(bx, 7,
//                    /*gridX*/ 3+bx*2, /*gridY*/ 7,
//                    /*x*/ centralCoordinateX+xDis, /*y*/ centralCoordinateY+12.5f+25.0f *(4-bx), /*z*/ centralCoordinateZ,
//                    centralCoordinateTorque);
//        }
//
//        for (int bx = 5; bx <= 9; bx++) {
//            addMapping(bx, 7,
//                    /*gridX*/ 11+(bx-4)*2, /*gridY*/ 7,
//                    /*x*/ centralCoordinateX+xDis, /*y*/ centralCoordinateY-12.5f-25.0f *(bx-4), /*z*/ centralCoordinateZ,
//                    centralCoordinateTorque);
//        }

        addMapping(centralX-4, centralY-1,
                /*gridX*/ 3, /*gridY*/ 7,
                /*x*/ centralCoordinateX+xDis+7f, /*y*/ centralCoordinateY+107f, /*z*/ centralCoordinateZ-1f,
                centralCoordinateTorque);

        addMapping(centralX-3, centralY-1,
                /*gridX*/ 5, /*gridY*/ 7,
                /*x*/ centralCoordinateX+xDis+5f, /*y*/ centralCoordinateY+83f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque);

        addMapping(centralX-2, centralY-1,
                /*gridX*/ 7, /*gridY*/ 7,
                /*x*/ centralCoordinateX+xDis+2f, /*y*/ centralCoordinateY+59f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque);

        addMapping(centralX-1, centralY-1,
                /*gridX*/ 9, /*gridY*/ 7,
                /*x*/ centralCoordinateX+xDis, /*y*/ centralCoordinateY+36f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque);

        addMapping(centralX+0, centralY-1,
                /*gridX*/ 11, /*gridY*/ 7,
                /*x*/ centralCoordinateX+xDis-1f, /*y*/ centralCoordinateY+14f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque);

        addMapping(centralX+1, centralY-1,
                /*gridX*/ 13, /*gridY*/ 7,
                /*x*/ centralCoordinateX+xDis, /*y*/ centralCoordinateY-10f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque);

        addMapping(centralX+2, centralY-1,
                /*gridX*/ 15, /*gridY*/ 7,
                /*x*/ centralCoordinateX+xDis+1f, /*y*/ centralCoordinateY-32f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque);

        addMapping(centralX+3, centralY-1,
                /*gridX*/ 17, /*gridY*/ 7,
                /*x*/ centralCoordinateX+xDis+1f, /*y*/ centralCoordinateY-55f, /*z*/ centralCoordinateZ+1f,
                centralCoordinateTorque);

        addMapping(centralX+4, centralY-1,
                /*gridX*/ 19, /*gridY*/ 7,
                /*x*/ centralCoordinateX+xDis+6f, /*y*/ centralCoordinateY-78f, /*z*/ centralCoordinateZ-1f,
                centralCoordinateTorque);

        addMapping(centralX+5, centralY-1,
                /*gridX*/ 21, /*gridY*/ 7,
                /*x*/ centralCoordinateX+xDis+8.5f, /*y*/ centralCoordinateY-104f, /*z*/ centralCoordinateZ-2f,
                centralCoordinateTorque);

//        for (int bx = 0; bx <= 9; bx++) {
//            addMapping(bx, 7,
//                    /*gridX*/ 0, /*gridY*/ 7,
//                    /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                    centerTorque);
//        }

        // --------------------------------------------------
        // Row 8 (9 cells) => offset=0 => boardX=0..8
        // TILES_PER_ROW[8] = 9
        // --------------------------------------------------

        //for each x smaller than 4, it plus 25f in y
        for (int bx = 0; bx <= 3; bx++) {
            addMapping(bx, 8,
                    /*gridX*/ 4+bx*2, /*gridY*/ 8,
                    /*x*/ centralCoordinateX, /*y*/ centralCoordinateY+23.0f *(4-bx), /*z*/ centralCoordinateZ,
                    centralCoordinateTorque);
        }

        addMapping(centralX, centralY, //4,8
                /*gridX*/ 12, /*gridY*/ 8,
                /*x*/ centralCoordinateX, /*y*/ centralCoordinateY, /*z*/ centralCoordinateZ,
                centralCoordinateTorque);

        //for each x larger than 4, it minus 25f in y
        for (int bx = 5; bx <= 8; bx++) {
            addMapping(bx, 8,
                    /*gridX*/ 4+(bx-4)*2, /*gridY*/ 8,
                    /*x*/ centralCoordinateX+10f, /*y*/ centralCoordinateY-23.0f *(bx-4), /*z*/ centralCoordinateZ,
                    centralCoordinateTorque);
        }

        // --------------------------------------------------
        // Row 9 (10 cells) => offset=0 => boardX=0..9
        // TILES_PER_ROW[9] = 10
        // --------------------------------------------------
//        for (int bx = 0; bx <= 4; bx++) {
//            addMapping(bx, 9,
//                    /*gridX*/ 3+bx*2, /*gridY*/ 9,
//                    /*x*/ centralCoordinateX-xDis, /*y*/ centralCoordinateY+12.5f+25.0f *(4-bx), /*z*/ centralCoordinateZ,
//                    centralCoordinateTorque);
//        }

        addMapping(centralX-4, centralY+1,
                /*gridX*/ 3, /*gridY*/ 9,
                /*x*/ centralCoordinateX-xDis+7f, /*y*/ centralCoordinateY+105f, /*z*/ centralCoordinateZ-1f,
                centralCoordinateTorque);

        addMapping(centralX-3, centralY+1,
                /*gridX*/ 5, /*gridY*/ 9,
                /*x*/ centralCoordinateX-xDis+5f, /*y*/ centralCoordinateY+81f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque);

        addMapping(centralX-2, centralY+1,
                /*gridX*/ 7, /*gridY*/ 9,
                /*x*/ centralCoordinateX-xDis+2f, /*y*/ centralCoordinateY+59f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque);

        addMapping(centralX-1, centralY+1,
                /*gridX*/ 9, /*gridY*/ 9,
                /*x*/ centralCoordinateX-xDis-1f, /*y*/ centralCoordinateY+35f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque);

        addMapping(centralX+0, centralY+1,
                /*gridX*/ 11, /*gridY*/ 9,
                /*x*/ centralCoordinateX-xDis-1f, /*y*/ centralCoordinateY+14f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque);

        addMapping(centralX+1, centralY+1,
                /*gridX*/ 13, /*gridY*/ 9,
                /*x*/ centralCoordinateX-xDis-1f, /*y*/ centralCoordinateY-9f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque);

        addMapping(centralX+2, centralY+1,
                /*gridX*/ 15, /*gridY*/ 9,
                /*x*/ centralCoordinateX-xDis+1f, /*y*/ centralCoordinateY-32f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque);

        addMapping(centralX+3, centralY+1,
                /*gridX*/ 17, /*gridY*/ 9,
                /*x*/ centralCoordinateX-xDis-1f, /*y*/ centralCoordinateY-52f, /*z*/ centralCoordinateZ+1f,
                centralCoordinateTorque);

        addMapping(centralX+4, centralY+1,
                /*gridX*/ 19, /*gridY*/ 9,
                /*x*/ centralCoordinateX-xDis+6f, /*y*/ centralCoordinateY-78f, /*z*/ centralCoordinateZ-1f,
                centralCoordinateTorque);

        addMapping(centralX+5, centralY+1,
                /*gridX*/ 21, /*gridY*/ 9,
                /*x*/ centralCoordinateX-xDis+8.5f, /*y*/ centralCoordinateY-104f, /*z*/ centralCoordinateZ-2f,
                centralCoordinateTorque);

//        for (int bx = 5; bx <= 9; bx++) {
//            addMapping(bx, 9,
//                    /*gridX*/ 11+(bx-4)*2, /*gridY*/ 9,
//                    /*x*/ centralCoordinateX-xDis, /*y*/ centralCoordinateY-12.5f-25.0f *(bx-4), /*z*/ centralCoordinateZ,
//                    centralCoordinateTorque);
//        }

//        for (int bx = 0; bx <= 9; bx++) {
//            addMapping(bx, 9,
//                    /*gridX*/ 0, /*gridY*/ 9,
//                    /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                    centerTorque);
//        }

        // --------------------------------------------------
        // Row 10 (11 cells) => offset=0 => boardX=0..10
        // TILES_PER_ROW[10] = 11
        // --------------------------------------------------
//        for (int bx = 0; bx <= 4; bx++) {
//            addMapping(bx, 10,
//                    /*gridX*/ 2+bx*2, /*gridY*/ 10,
//                    /*x*/ centralCoordinateX-xDis*2, /*y*/ centralCoordinateY+25.0f *(5-bx), /*z*/ centralCoordinateZ,
//                    centralCoordinateTorque-0.1f);
//        }
        addMapping(centralX-4, centralY+2,
                /*gridX*/ 2, /*gridY*/ 10,
                /*x*/ centralCoordinateX-xDis*2+5f, /*y*/ centralCoordinateY+114f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque-0.1f);

        addMapping(centralX-3, centralY+2,
                /*gridX*/ 4, /*gridY*/ 10,
                /*x*/ centralCoordinateX-xDis*2+2f, /*y*/ centralCoordinateY+89f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque-0.1f);

        addMapping(centralX-2, centralY+2,
                /*gridX*/ 6, /*gridY*/ 10,
                /*x*/ centralCoordinateX-xDis*2-1f, /*y*/ centralCoordinateY+65f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque-0.1f);

        addMapping(centralX-1, centralY+2,
                /*gridX*/ 8, /*gridY*/ 10,
                /*x*/ centralCoordinateX-xDis*2-1f, /*y*/ centralCoordinateY+44f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque-0.1f);

        addMapping(centralX+0, centralY+2,
                /*gridX*/ 10, /*gridY*/ 10,
                /*x*/ centralCoordinateX-xDis*2-1f, /*y*/ centralCoordinateY+20f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque-0.1f);

        addMapping(centralX+1, centralY+2, //5,10
                /*gridX*/ 12, /*gridY*/ 10,
                /*x*/ centralCoordinateX-xDis*2-2f, /*y*/ centralCoordinateY, /*z*/ centralCoordinateZ,
                centralCoordinateTorque-0.1f);

        addMapping(centralX+2, centralY+2,
                /*gridX*/ 14, /*gridY*/ 10,
                /*x*/ centralCoordinateX-xDis*2-1f, /*y*/ centralCoordinateY-20f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque-0.1f);

        addMapping(centralX+3, centralY+2,
                /*gridX*/ 16, /*gridY*/ 10,
                /*x*/ centralCoordinateX-xDis*2-1f, /*y*/ centralCoordinateY-44f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque-0.1f);

        addMapping(centralX+4, centralY+2,
                /*gridX*/ 18, /*gridY*/ 10,
                /*x*/ centralCoordinateX-xDis*2-1f, /*y*/ centralCoordinateY-65f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque-0.1f);

        addMapping(centralX+5, centralY+2,
                /*gridX*/ 20, /*gridY*/ 10,
                /*x*/ centralCoordinateX-xDis*2+4f, /*y*/ centralCoordinateY-87f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque-0.1f);

        addMapping(centralX+6, centralY+2,
                /*gridX*/ 22, /*gridY*/ 10,
                /*x*/ centralCoordinateX-xDis*2+9f, /*y*/ centralCoordinateY-109f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque-0.1f);

//        for (int bx = 6; bx <= 10; bx++) {
//            addMapping(bx, 10,
//                    /*gridX*/ 12+(bx-5)*2, /*gridY*/ 10,
//                    /*x*/ centralCoordinateX-xDis*2, /*y*/ centralCoordinateY-25.0f *(bx-5), /*z*/ centralCoordinateZ,
//                    centralCoordinateTorque-0.1f);
//        }

//        for (int bx = 0; bx <= 10; bx++) {
//            addMapping(bx, 10,
//                    /*gridX*/ 0, /*gridY*/ 10,
//                    /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                    centerTorque);
//        }
//
//        addMapping(centralX+1, centralY+2, // 5, 10
//                /*gridX*/ 12, /*gridY*/ 10,
//                /*x*/ centralCoordinateX-50f, /*y*/ centralCoordinateY, /*z*/ centralCoordinateZ,
//                centralCoordinateTorque);

        // --------------------------------------------------
        // Row 11 (12 cells) => offset=0 => boardX=0..11
        // TILES_PER_ROW[11] = 12
        // --------------------------------------------------
//        for (int bx = 0; bx <= 5; bx++) {
//            addMapping(bx, 11,
//                    /*gridX*/ 1+bx*2, /*gridY*/ 11,
//                    /*x*/ centralCoordinateX-xDis*3, /*y*/ centralCoordinateY+12.5f+25.0f *(5-bx), /*z*/ centralCoordinateZ+5f,
//                    centralCoordinateTorque-0.15f);
//        }

        addMapping(centralX-4, centralY+3,
                /*gridX*/ 1, /*gridY*/ 11,
                /*x*/ centralCoordinateX-xDis*3+12f, /*y*/ centralCoordinateY+120f, /*z*/ centralCoordinateZ+3f,
                centralCoordinateTorque-0.15f);

        addMapping(centralX-3, centralY+3,
                /*gridX*/ 3, /*gridY*/ 11,
                /*x*/ centralCoordinateX-xDis*3+7f, /*y*/ centralCoordinateY+95f, /*z*/ centralCoordinateZ+4f,
                centralCoordinateTorque-0.15f);

        addMapping(centralX-2, centralY+3,
                /*gridX*/ 5, /*gridY*/ 11,
                /*x*/ centralCoordinateX-xDis*3+5f, /*y*/ centralCoordinateY+71f, /*z*/ centralCoordinateZ+5f,
                centralCoordinateTorque-0.15f);

        addMapping(centralX-1, centralY+3,
                /*gridX*/ 7, /*gridY*/ 11,
                /*x*/ centralCoordinateX-xDis*3+2f, /*y*/ centralCoordinateY+49f, /*z*/ centralCoordinateZ+5f,
                centralCoordinateTorque-0.15f);

        addMapping(centralX-0, centralY+3,
                /*gridX*/ 9, /*gridY*/ 11,
                /*x*/ centralCoordinateX-xDis*3-1f, /*y*/ centralCoordinateY+28f, /*z*/ centralCoordinateZ+5f,
                centralCoordinateTorque-0.15f);

        addMapping(centralX+1, centralY+3,
                /*gridX*/ 11, /*gridY*/ 11,
                /*x*/ centralCoordinateX-xDis*3-3f, /*y*/ centralCoordinateY+7f, /*z*/ centralCoordinateZ+5f,
                centralCoordinateTorque-0.15f);

        addMapping(centralX+2, centralY+3,
                /*gridX*/ 13, /*gridY*/ 11,
                /*x*/ centralCoordinateX-xDis*3-3f, /*y*/ centralCoordinateY-12f, /*z*/ centralCoordinateZ+5f,
                centralCoordinateTorque-0.15f);

        addMapping(centralX+3, centralY+3,
                /*gridX*/ 15, /*gridY*/ 11,
                /*x*/ centralCoordinateX-xDis*3-4f, /*y*/ centralCoordinateY-33f, /*z*/ centralCoordinateZ+5f,
                centralCoordinateTorque-0.15f);

        addMapping(centralX+4, centralY+3,
                /*gridX*/ 17, /*gridY*/ 11,
                /*x*/ centralCoordinateX-xDis*3-1f, /*y*/ centralCoordinateY-54f, /*z*/ centralCoordinateZ+5f,
                centralCoordinateTorque-0.15f);

        addMapping(centralX+5, centralY+3,
                /*gridX*/ 19, /*gridY*/ 11,
                /*x*/ centralCoordinateX-xDis*3+1f, /*y*/ centralCoordinateY-76f, /*z*/ centralCoordinateZ+4f,
                centralCoordinateTorque-0.15f);

        addMapping(centralX+6, centralY+3,
                /*gridX*/ 21, /*gridY*/ 11,
                /*x*/ centralCoordinateX-xDis*3+6.5f, /*y*/ centralCoordinateY-98f, /*z*/ centralCoordinateZ+3f,
                centralCoordinateTorque-0.15f);

        addMapping(centralX+7, centralY+3,
                /*gridX*/ 23, /*gridY*/ 11,
                /*x*/ centralCoordinateX-xDis*3+9.5f, /*y*/ centralCoordinateY-123f, /*z*/ centralCoordinateZ+3f,
                centralCoordinateTorque-0.15f);

//        for (int bx = 6; bx <= 11; bx++) {
//            addMapping(bx, 11,
//                    /*gridX*/ 11+(bx-5)*2, /*gridY*/ 11,
//                    /*x*/ centralCoordinateX-xDis*3, /*y*/ centralCoordinateY-12.5f-25.0f *(bx-5), /*z*/ centralCoordinateZ+5f,
//                    centralCoordinateTorque-0.15f);
//        }

//        for (int bx = 0; bx <= 11; bx++) {
//            addMapping(bx, 11,
//                    /*gridX*/ 0, /*gridY*/ 11,
//                    /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                    centerTorque);
//        }

        // --------------------------------------------------
        // Row 12 (13 cells) => offset=0 => boardX=0..12
        // TILES_PER_ROW[12] = 13
        // --------------------------------------------------
//        for (int bx = 0; bx <= 5; bx++) {
//            addMapping(bx, 12,
//                    /*gridX*/ 0+bx*2, /*gridY*/ 12,
//                    /*x*/ centralCoordinateX-xDis*4, /*y*/ centralCoordinateY+25.0f *(6-bx), /*z*/ centralCoordinateZ+10f,
//                    centralCoordinateTorque-0.2f);
//        }
        addMapping(centralX-4, centralY+4,
                /*gridX*/ 0, /*gridY*/ 12,
                /*x*/ centralCoordinateX-xDis*4+14f, /*y*/ centralCoordinateY+126f, /*z*/ centralCoordinateZ,
                centralCoordinateTorque-0.2f);

        addMapping(centralX-3, centralY+4,
                /*gridX*/ 2, /*gridY*/ 12,
                /*x*/ centralCoordinateX-xDis*4+10f, /*y*/ centralCoordinateY+101f, /*z*/ centralCoordinateZ+2f,
                centralCoordinateTorque-0.2f);

        addMapping(centralX-2, centralY+4,
                /*gridX*/ 4, /*gridY*/ 12,
                /*x*/ centralCoordinateX-xDis*4+3f, /*y*/ centralCoordinateY+78f, /*z*/ centralCoordinateZ+3f,
                centralCoordinateTorque-0.2f);

        addMapping(centralX-1, centralY+4,
                /*gridX*/ 6, /*gridY*/ 12,
                /*x*/ centralCoordinateX-xDis*4+0.5f, /*y*/ centralCoordinateY+56.5f, /*z*/ centralCoordinateZ+6f,
                centralCoordinateTorque-0.2f);

        addMapping(centralX+0, centralY+4,
                /*gridX*/ 8, /*gridY*/ 12,
                /*x*/ centralCoordinateX-xDis*4-2f, /*y*/ centralCoordinateY+36f, /*z*/ centralCoordinateZ+7f,
                centralCoordinateTorque-0.2f);

        addMapping(centralX+1, centralY+4,
                /*gridX*/ 10, /*gridY*/ 12,
                /*x*/ centralCoordinateX-xDis*4-2f, /*y*/ centralCoordinateY+17.5f, /*z*/ centralCoordinateZ+9f,
                centralCoordinateTorque-0.2f);

        addMapping(centralX+2, centralY+4, //6,12
                /*gridX*/ 12, /*gridY*/ 12,
                /*x*/ centralCoordinateX-xDis*4-2f, /*y*/ centralCoordinateY, /*z*/ centralCoordinateZ+10f,
                centralCoordinateTorque-0.2f);

        addMapping(centralX+3, centralY+4,
                /*gridX*/ 14, /*gridY*/ 12,
                /*x*/ centralCoordinateX-xDis*4, /*y*/ centralCoordinateY-22.5f, /*z*/ centralCoordinateZ+9f,
                centralCoordinateTorque-0.2f);

        addMapping(centralX+4, centralY+4,
                /*gridX*/ 16, /*gridY*/ 12,
                /*x*/ centralCoordinateX-xDis*4-2f, /*y*/ centralCoordinateY-41f, /*z*/ centralCoordinateZ+9f,
                centralCoordinateTorque-0.2f);

        addMapping(centralX+5, centralY+4,
                /*gridX*/ 18, /*gridY*/ 12,
                /*x*/ centralCoordinateX-xDis*4+1f, /*y*/ centralCoordinateY-61f, /*z*/ centralCoordinateZ+9f,
                centralCoordinateTorque-0.2f);

        addMapping(centralX+6, centralY+4,
                /*gridX*/ 20, /*gridY*/ 12,
                /*x*/ centralCoordinateX-xDis*4+5f, /*y*/ centralCoordinateY-84f, /*z*/ centralCoordinateZ+8f,
                centralCoordinateTorque-0.2f);

        addMapping(centralX+7, centralY+4,
                /*gridX*/ 22, /*gridY*/ 12,
                /*x*/ centralCoordinateX-xDis*4+11f, /*y*/ centralCoordinateY-107f, /*z*/ centralCoordinateZ+6f,
                centralCoordinateTorque-0.2f);

        addMapping(centralX+8, centralY+4,
                /*gridX*/ 24, /*gridY*/ 12,
                /*x*/ centralCoordinateX-xDis*4+14f, /*y*/ centralCoordinateY-132f, /*z*/ centralCoordinateZ+5f,
                centralCoordinateTorque-0.2f);

//        for (int bx = 7; bx <= 12; bx++) {
//            addMapping(bx, 12,
//                    /*gridX*/ 12+(bx-6)*2, /*gridY*/ 12,
//                    /*x*/ centralCoordinateX-xDis*4, /*y*/ centralCoordinateY-22.5f *(bx-6), /*z*/ centralCoordinateZ+9f,
//                    centralCoordinateTorque-0.2f);
//        }

//        for (int bx = 0; bx <= 12; bx++) {
//            addMapping(bx, 12,
//                    /*gridX*/ 0, /*gridY*/ 12,
//                    /*x*/ 0.0f, /*y*/ 0.0f, /*z*/ 0.0f,
//                    centerTorque);
//        }
//        addMapping(4, 12,
//                /*gridX*/ 4, /*gridY*/ 12,
//                /*x*/ 130.16f, /*y*/ 40.36f, /*z*/ -100.51f,
//                1.7f);
//        addMapping(5, 12,
//                /*gridX*/ 5, /*gridY*/ 12,
//                /*x*/ 128.37f, /*y*/ 20.67f, /*z*/ -101.4f,
//                1.7f);
//        addMapping(6, 12,
//                /*gridX*/ 6, /*gridY*/ 12,
//                /*x*/ 127.61f, /*y*/ 4.31f, /*z*/ -100.4f,
//                1.7f);
//        addMapping(7, 12,
//                /*gridX*/ 7, /*gridY*/ 12,
//                /*x*/ 128.37f, /*y*/ -16.67f, /*z*/ -101.4f,
//                1.7f);
//        addMapping(8, 12,
//                /*gridX*/ 8, /*gridY*/ 12,
//                /*x*/ 128.71f, /*y*/ -34.14f, /*z*/ -102.2f,
//                1.7f);

        // --------------------------------------------------
        // Row 13 (4 cells) => offset=3 => boardX=3..6
        // TILES_PER_ROW[13] = 4
        // --------------------------------------------------
//        for (int bx = 0; bx <= 1; bx++) {
//            addMapping(bx, 13,
//                    /*gridX*/ 9+bx*2, /*gridY*/ 13,
//                    /*x*/ centralCoordinateX-xDis*5, /*y*/ centralCoordinateY+25.0f *(1-bx), /*z*/ centralCoordinateZ+10f,
//                    centralCoordinateTorque-0.2f);
//        }

        addMapping(0, 13,
                /*gridX*/ 9, /*gridY*/ 13,
                /*x*/ centralCoordinateX-xDis*5, /*y*/ centralCoordinateY+25.0f, /*z*/ centralCoordinateZ+10f,
                centralCoordinateTorque-0.2f);

        addMapping(1, 13,
                /*gridX*/ 11, /*gridY*/ 13,
                /*x*/ centralCoordinateX-xDis*5-2f, /*y*/ centralCoordinateY+7.0f, /*z*/ centralCoordinateZ+10f,
                centralCoordinateTorque-0.2f);

//        for (int bx = 2; bx <= 3; bx++) {
//            addMapping(bx, 13,
//                    /*gridX*/ 13+(bx-2)*2, /*gridY*/ 13,
//                    /*x*/ centralCoordinateX-xDis*5, /*y*/ centralCoordinateY-12.5f-25.0f *(bx-2), /*z*/ centralCoordinateZ+10f,
//                    centralCoordinateTorque-0.2f);
//        }

        addMapping(2, 13,
                /*gridX*/ 13, /*gridY*/ 13,
                /*x*/ centralCoordinateX-xDis*5, /*y*/ centralCoordinateY-12.5f, /*z*/ centralCoordinateZ+10f,
                centralCoordinateTorque-0.2f);

        addMapping(3, 13,
                /*gridX*/ 15, /*gridY*/ 13,
                /*x*/ centralCoordinateX-xDis*5-1f, /*y*/ centralCoordinateY-31f, /*z*/ centralCoordinateZ+10f,
                centralCoordinateTorque-0.2f);

//        addMapping(3, 13,
//                /*gridX*/ 0, /*gridY*/ 13,
//                /*x*/ 89.8f, /*y*/ 23.67f, /*z*/ -102.4f,
//                1.39f);
//        addMapping(4, 13,
//                /*gridX*/ 1, /*gridY*/ 13,
//                /*x*/ 87.78f, /*y*/ 8.92f, /*z*/ -101f,
//                1.39f);
//        addMapping(5, 13,
//                /*gridX*/ 2, /*gridY*/ 13,
//                /*x*/ 87.93f, /*y*/ -5.13f, /*z*/ -100.78f,
//                1.39f);
//        addMapping(6, 13,
//                /*gridX*/ 3, /*gridY*/ 13,
//                /*x*/ 89.8f, /*y*/ -19.33f, /*z*/ -101f,
//                1.39f);

        // --------------------------------------------------
        // Row 14 (3 cells) => offset=4 => boardX=4..6
        // TILES_PER_ROW[14] = 3
        // --------------------------------------------------
        addMapping(0, centralY+6,
                /*gridX*/ 10, /*gridY*/ 14,

                /*x*/ centralCoordinateX-xDis*6-3f, /*y*/ centralCoordinateY+15f, /*z*/ centralCoordinateZ+15f,
                centralCoordinateTorque-0.25f);

        addMapping(1, centralY+6,
                /*gridX*/ 12, /*gridY*/ 14,

                /*x*/ centralCoordinateX-xDis*6-5f, /*y*/ centralCoordinateY-3f, /*z*/ centralCoordinateZ+15f,

                centralCoordinateTorque-0.25f);

        addMapping(2, centralY+6,
                /*gridX*/ 14, /*gridY*/ 14,

                /*x*/ centralCoordinateX-xDis*6-5f, /*y*/ centralCoordinateY-18.0f, /*z*/ centralCoordinateZ+15f,

                centralCoordinateTorque-0.25f);
//        addMapping(4, 14,
//                /*gridX*/ 0, /*gridY*/ 14,
//                /*x*/ 49.71f, /*y*/ 10.13f, /*z*/ -97.44f,
//                bottomTorque);
//        addMapping(5, 14,
//                /*gridX*/ 1, /*gridY*/ 14,
//                /*x*/ 48.10f, /*y*/ 1.11f, /*z*/ -95.15f,
//                bottomTorque);
//        addMapping(6, 14,
//                /*gridX*/ 2, /*gridY*/ 14,
//                /*x*/ 49.83f, /*y*/ -8.25f, /*z*/ -97f,
//                bottomTorque);

        // --------------------------------------------------
        // Row 15 (2 cells) => offset=5 => boardX=5..6
        // TILES_PER_ROW[15] = 2
        // --------------------------------------------------
        addMapping(0, centralY+7,
                /*gridX*/ 11, /*gridY*/ 15,
                /*x*/ centralCoordinateX-xDis*7-3f, /*y*/ centralCoordinateY+2.5f, /*z*/ centralCoordinateZ+22f,
                centralCoordinateTorque-0.3f);

        addMapping(1, centralY+7,
                /*gridX*/ 13, /*gridY*/ 15,
                /*x*/ centralCoordinateX-xDis*7-3f, /*y*/ centralCoordinateY-11f, /*z*/ centralCoordinateZ+22f,
                centralCoordinateTorque-0.3f);

//        addMapping(5, 15,
//                /*gridX*/ 0, /*gridY*/ 15,
//                /*x*/ 27.51f, /*y*/ 3.87f, /*z*/ -84.45f,
//                1.08f);
//        addMapping(6, 15,
//                /*gridX*/ 1, /*gridY*/ 15,
//                /*x*/ 27.51f, /*y*/ 3.87f, /*z*/ -84.45f,
//                1.08f);

        // --------------------------------------------------
        // Row 16 (1 cell) => offset=6 => boardX=6
        // TILES_PER_ROW[16] = 1
        // --------------------------------------------------
        addMapping(0, centralY+8,
                /*gridX*/ 12, /*gridY*/ 16,
                /*x*/ centralCoordinateX-xDis*8-15f , /*y*/ 0, /*z*/ centralCoordinateZ+30f,
                centralCoordinateTorque-0.45f);

//        addMapping(6, 16,
//                /*gridX*/ 0, /*gridY*/ 16,
//                /*x*/ 4.27f, /*y*/ 0.06f, /*z*/ -71.68f,
//                1.07f);
    }

    /**
     * Add a mapping between board coordinates and robot coordinates
     */
    private void addMapping(int boardX, int boardY, int gridX, int gridY,
                            float x, float y, float z, float torque) {
        String key = getKey(boardX, boardY);
        CellCoordinate cellCoord = new CellCoordinate(gridX, gridY, x, y, z, torque, true);
        coordsMap.put(key, cellCoord);

        Log.d(TAG, String.format("Mapped (%d,%d) to (%d,%d) at (%.2f,%.2f,%.2f,%.2f)",
                boardX, boardY, gridX, gridY, x, y, z, torque));
    }
}