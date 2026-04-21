package com.vergepay.wallet.game;

import java.util.Random;

public class TetrisGame {
    public static final int BOARD_WIDTH = 10;
    public static final int BOARD_HEIGHT = 20;
    public static final int PREVIEW_SIZE = 4;

    private static final int[][][][] SHAPES = new int[][][][]{
            { // I
                    {{0, 1}, {1, 1}, {2, 1}, {3, 1}},
                    {{2, 0}, {2, 1}, {2, 2}, {2, 3}},
                    {{0, 2}, {1, 2}, {2, 2}, {3, 2}},
                    {{1, 0}, {1, 1}, {1, 2}, {1, 3}}
            },
            { // J
                    {{0, 0}, {0, 1}, {1, 1}, {2, 1}},
                    {{1, 0}, {2, 0}, {1, 1}, {1, 2}},
                    {{0, 1}, {1, 1}, {2, 1}, {2, 2}},
                    {{1, 0}, {1, 1}, {0, 2}, {1, 2}}
            },
            { // L
                    {{2, 0}, {0, 1}, {1, 1}, {2, 1}},
                    {{1, 0}, {1, 1}, {1, 2}, {2, 2}},
                    {{0, 1}, {1, 1}, {2, 1}, {0, 2}},
                    {{0, 0}, {1, 0}, {1, 1}, {1, 2}}
            },
            { // O
                    {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
                    {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
                    {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
                    {{1, 0}, {2, 0}, {1, 1}, {2, 1}}
            },
            { // S
                    {{1, 0}, {2, 0}, {0, 1}, {1, 1}},
                    {{1, 0}, {1, 1}, {2, 1}, {2, 2}},
                    {{1, 1}, {2, 1}, {0, 2}, {1, 2}},
                    {{0, 0}, {0, 1}, {1, 1}, {1, 2}}
            },
            { // T
                    {{1, 0}, {0, 1}, {1, 1}, {2, 1}},
                    {{1, 0}, {1, 1}, {2, 1}, {1, 2}},
                    {{0, 1}, {1, 1}, {2, 1}, {1, 2}},
                    {{1, 0}, {0, 1}, {1, 1}, {1, 2}}
            },
            { // Z
                    {{0, 0}, {1, 0}, {1, 1}, {2, 1}},
                    {{2, 0}, {1, 1}, {2, 1}, {1, 2}},
                    {{0, 1}, {1, 1}, {1, 2}, {2, 2}},
                    {{1, 0}, {0, 1}, {1, 1}, {0, 2}}
            }
    };

    private final Random random = new Random();
    private final int[][] board = new int[BOARD_HEIGHT][BOARD_WIDTH];
    private Piece currentPiece;
    private int nextShapeIndex = random.nextInt(SHAPES.length);
    private int score;
    private int linesCleared;
    private int level = 1;
    private boolean gameOver;

    public void reset() {
        for (int row = 0; row < BOARD_HEIGHT; row++) {
            for (int column = 0; column < BOARD_WIDTH; column++) {
                board[row][column] = 0;
            }
        }
        score = 0;
        linesCleared = 0;
        level = 1;
        gameOver = false;
        currentPiece = null;
        nextShapeIndex = random.nextInt(SHAPES.length);
        spawnNextPiece();
    }

    public boolean step() {
        if (gameOver) {
            return false;
        }
        if (tryMove(0, 1, currentPiece.rotation)) {
            return true;
        }
        lockPiece();
        clearLines();
        spawnNextPiece();
        return !gameOver;
    }

    public boolean moveLeft() {
        return tryMove(-1, 0, currentPiece.rotation);
    }

    public boolean moveRight() {
        return tryMove(1, 0, currentPiece.rotation);
    }

    public boolean moveDown() {
        if (!tryMove(0, 1, currentPiece.rotation)) {
            return step();
        }
        return true;
    }

    public void hardDrop() {
        if (gameOver) {
            return;
        }
        while (tryMove(0, 1, currentPiece.rotation)) {
            // Keep moving until the piece settles.
        }
        step();
    }

    public boolean rotate() {
        int newRotation = (currentPiece.rotation + 1) % 4;
        if (tryMove(0, 0, newRotation)) {
            return true;
        }
        if (tryMove(-1, 0, newRotation)) {
            return true;
        }
        return tryMove(1, 0, newRotation);
    }

    public int[][] getBoardWithPiece() {
        int[][] snapshot = new int[BOARD_HEIGHT][BOARD_WIDTH];
        for (int row = 0; row < BOARD_HEIGHT; row++) {
            System.arraycopy(board[row], 0, snapshot[row], 0, BOARD_WIDTH);
        }
        if (currentPiece == null) {
            return snapshot;
        }

        for (int[] cell : SHAPES[currentPiece.shapeIndex][currentPiece.rotation]) {
            int x = currentPiece.x + cell[0];
            int y = currentPiece.y + cell[1];
            if (y >= 0 && y < BOARD_HEIGHT && x >= 0 && x < BOARD_WIDTH) {
                snapshot[y][x] = currentPiece.shapeIndex + 1;
            }
        }
        return snapshot;
    }

    public int[][] getNextPiecePreview() {
        int[][] preview = new int[PREVIEW_SIZE][PREVIEW_SIZE];
        for (int[] cell : SHAPES[nextShapeIndex][0]) {
            if (cell[1] >= 0 && cell[1] < PREVIEW_SIZE && cell[0] >= 0 && cell[0] < PREVIEW_SIZE) {
                preview[cell[1]][cell[0]] = nextShapeIndex + 1;
            }
        }
        return preview;
    }

    public int getScore() {
        return score;
    }

    public int getLinesCleared() {
        return linesCleared;
    }

    public int getLevel() {
        return level;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public long getDropDelayMs() {
        return Math.max(150L, 1000L - ((long) (level - 1) * 50L));
    }

    private void spawnNextPiece() {
        currentPiece = new Piece(nextShapeIndex, 0, 3, 0);
        nextShapeIndex = random.nextInt(SHAPES.length);
        if (collides(currentPiece.x, currentPiece.y, currentPiece.rotation)) {
            gameOver = true;
        }
    }

    private boolean tryMove(int dx, int dy, int rotation) {
        if (gameOver || currentPiece == null) {
            return false;
        }
        int targetX = currentPiece.x + dx;
        int targetY = currentPiece.y + dy;
        if (collides(targetX, targetY, rotation)) {
            return false;
        }
        currentPiece.x = targetX;
        currentPiece.y = targetY;
        currentPiece.rotation = rotation;
        return true;
    }

    private boolean collides(int targetX, int targetY, int rotation) {
        for (int[] cell : SHAPES[currentPiece.shapeIndex][rotation]) {
            int x = targetX + cell[0];
            int y = targetY + cell[1];
            if (x < 0 || x >= BOARD_WIDTH || y < 0 || y >= BOARD_HEIGHT) {
                return true;
            }
            if (board[y][x] != 0) {
                return true;
            }
        }
        return false;
    }

    private void lockPiece() {
        for (int[] cell : SHAPES[currentPiece.shapeIndex][currentPiece.rotation]) {
            int x = currentPiece.x + cell[0];
            int y = currentPiece.y + cell[1];
            if (y >= 0 && y < BOARD_HEIGHT && x >= 0 && x < BOARD_WIDTH) {
                board[y][x] = currentPiece.shapeIndex + 1;
            }
        }
    }

    private void clearLines() {
        int cleared = 0;
        for (int row = BOARD_HEIGHT - 1; row >= 0; row--) {
            boolean full = true;
            for (int column = 0; column < BOARD_WIDTH; column++) {
                if (board[row][column] == 0) {
                    full = false;
                    break;
                }
            }
            if (!full) {
                continue;
            }

            cleared++;
            for (int moveRow = row; moveRow > 0; moveRow--) {
                System.arraycopy(board[moveRow - 1], 0, board[moveRow], 0, BOARD_WIDTH);
            }
            for (int column = 0; column < BOARD_WIDTH; column++) {
                board[0][column] = 0;
            }
            row++;
        }

        if (cleared == 0) {
            return;
        }

        linesCleared += cleared;
        switch (cleared) {
            case 1:
                score += 100;
                break;
            case 2:
                score += 200;
                break;
            case 3:
                score += 400;
                break;
            default:
                score += 800;
                break;
        }
        level = Math.max(1, (linesCleared / 20) + 1);
    }

    private static final class Piece {
        private final int shapeIndex;
        private int rotation;
        private int x;
        private int y;

        private Piece(int shapeIndex, int rotation, int x, int y) {
            this.shapeIndex = shapeIndex;
            this.rotation = rotation;
            this.x = x;
            this.y = y;
        }
    }
}
