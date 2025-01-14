package hku.cs.fyp24057.chinesecheckerrobot;

import java.util.Arrays;

public class BoardState {
    // Constants for board representation
    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;
    public static final int RED = 3;
    public static final int BLUE = 4;
    public static final int GREEN = 5;
    public static final int YELLOW = 6;

    // Board dimensions
    private static final int BOARD_SIZE = 121;

    // Row structure for star pattern (spaces in each row)
    private static final int[] ROW_LENGTHS = {1,2,3,4,13,12,11,10,9,10,11,12,13,4,3,2,1};
    private static final int[] ROW_OFFSETS = {6,5,4,3,0,0,0,0,0,0,0,0,0,3,4,5,6};

    private int[] board;

    public BoardState() {
        board = new int[BOARD_SIZE];
        resetBoard();
    }

    public void resetBoard() {
        Arrays.fill(board, EMPTY);
    }

    public void setPosition(int row, int col, int value) {
        int index = getIndex(row, col);
        if (index >= 0 && index < BOARD_SIZE) {
            board[index] = value;
        }
    }

    public int getPosition(int row, int col) {
        int index = getIndex(row, col);
        return (index >= 0 && index < BOARD_SIZE) ? board[index] : EMPTY;
    }

    private int getIndex(int row, int col) {
        if (row < 0 || row >= ROW_LENGTHS.length ||
                col < 0 || col >= ROW_LENGTHS[row]) {
            return -1;
        }

        int index = 0;
        for (int i = 0; i < row; i++) {
            index += ROW_LENGTHS[i];
        }
        return index + col;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int index = 0;

        for (int row = 0; row < ROW_LENGTHS.length; row++) {
            // Add proper spacing at the start of each row
            sb.append(" ".repeat(ROW_OFFSETS[row] * 2));

            // Add the positions for this row
            for (int col = 0; col < ROW_LENGTHS[row]; col++) {
                char piece = switch (board[index++]) {
                    case EMPTY -> '·';
                    case BLACK -> '⚫';
                    case WHITE -> '⚪';
                    case RED -> 'R';
                    case BLUE -> 'B';
                    case GREEN -> 'G';
                    case YELLOW -> 'Y';
                    default -> 'X';
                };
                sb.append(piece).append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    // Helper method to check if position exists
    public boolean isValidPosition(int row, int col) {
        return getIndex(row, col) >= 0;
    }

    // Get number of pieces on the board
    public int getPieceCount() {
        int count = 0;
        for (int value : board) {
            if (value != EMPTY) count++;
        }
        return count;
    }
}