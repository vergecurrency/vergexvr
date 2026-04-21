package com.vergepay.wallet.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.vergepay.wallet.R;

public class TetrisMatrixView extends View {
    private static final int[] COLOR_RES_IDS = new int[]{
            R.color.progress_bar_color_2,
            R.color.progress_bar_color_3,
            R.color.progress_bar_color_4,
            R.color.accent_alt,
            R.color.receive_normal,
            R.color.accent,
            R.color.send_normal
    };

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint[] blockPaints = new Paint[COLOR_RES_IDS.length];

    private int rows = 20;
    private int columns = 10;
    private int[][] matrix = new int[rows][columns];

    public TetrisMatrixView(Context context) {
        super(context);
        init();
    }

    public TetrisMatrixView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TetrisMatrixView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setGridSize(int rows, int columns) {
        this.rows = rows;
        this.columns = columns;
        this.matrix = new int[rows][columns];
        invalidate();
    }

    public void setMatrix(int[][] matrix) {
        if (matrix == null || matrix.length == 0 || matrix[0].length == 0) {
            return;
        }
        rows = matrix.length;
        columns = matrix[0].length;
        this.matrix = new int[rows][columns];
        for (int row = 0; row < rows; row++) {
            System.arraycopy(matrix[row], 0, this.matrix[row], 0, columns);
        }
        invalidate();
    }

    private void init() {
        backgroundPaint.setColor(ContextCompat.getColor(getContext(), R.color.surface_card));
        linePaint.setColor(ContextCompat.getColor(getContext(), R.color.stroke_soft));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(getResources().getDisplayMetrics().density);

        for (int index = 0; index < blockPaints.length; index++) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(ContextCompat.getColor(getContext(), COLOR_RES_IDS[index]));
            paint.setStyle(Paint.Style.FILL);
            blockPaints[index] = paint;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cellSize = Math.min(getWidth() / (float) columns, getHeight() / (float) rows);
        float boardWidth = cellSize * columns;
        float boardHeight = cellSize * rows;
        float left = (getWidth() - boardWidth) / 2f;
        float top = (getHeight() - boardHeight) / 2f;
        float radius = cellSize * 0.18f;

        canvas.drawRoundRect(left, top, left + boardWidth, top + boardHeight, radius, radius,
                backgroundPaint);

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                float cellLeft = left + (column * cellSize);
                float cellTop = top + (row * cellSize);
                float cellRight = cellLeft + cellSize;
                float cellBottom = cellTop + cellSize;

                canvas.drawRect(cellLeft, cellTop, cellRight, cellBottom, linePaint);

                int value = matrix[row][column];
                if (value <= 0 || value > blockPaints.length) {
                    continue;
                }

                float inset = cellSize * 0.12f;
                canvas.drawRoundRect(
                        cellLeft + inset,
                        cellTop + inset,
                        cellRight - inset,
                        cellBottom - inset,
                        radius * 0.75f,
                        radius * 0.75f,
                        blockPaints[value - 1]);
            }
        }
    }
}
