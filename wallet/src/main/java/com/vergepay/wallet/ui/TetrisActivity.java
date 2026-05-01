package com.vergepay.wallet.ui;

import android.graphics.Color;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.vergepay.wallet.R;
import com.vergepay.wallet.game.TetrisGame;
import com.vergepay.wallet.ui.widget.TetrisMatrixView;

public class TetrisActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TetrisGame game = new TetrisGame();

    private TetrisMatrixView boardView;
    private TetrisMatrixView previewView;
    private TextView scoreView;
    private TextView linesView;
    private TextView levelView;
    private TextView statusView;

    private boolean metaVariant;
    private boolean resumed;
    private boolean showingGameOver;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!resumed || showingGameOver || isFinishing()) {
                return;
            }

            game.step();
            refreshUi();

            if (game.isGameOver()) {
                showGameOverDialog();
                return;
            }

            scheduleNextTick();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        metaVariant = getResources().getBoolean(R.bool.wallet_meta_variant);
        if (metaVariant) {
            configureMetaWindow();
        }
        setContentView(R.layout.activity_tetris);

        View root = findViewById(R.id.tetris_root);
        WindowInsetsHelper.applyPaddingInsets(root, true, true);

        boardView = findViewById(R.id.tetris_board);
        previewView = findViewById(R.id.tetris_next_board);
        scoreView = findViewById(R.id.tetris_score_value);
        linesView = findViewById(R.id.tetris_lines_value);
        levelView = findViewById(R.id.tetris_level_value);
        statusView = findViewById(R.id.tetris_status_value);

        boardView.setGridSize(TetrisGame.BOARD_HEIGHT, TetrisGame.BOARD_WIDTH);
        previewView.setGridSize(TetrisGame.PREVIEW_SIZE, TetrisGame.PREVIEW_SIZE);

        if (getSupportActionBar() != null) {
            if (metaVariant) {
                getSupportActionBar().hide();
            } else {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle(R.string.title_activity_tetris);
            }
        }

        findViewById(R.id.tetris_move_left).setOnClickListener(v -> {
            game.moveLeft();
            refreshUi();
        });
        findViewById(R.id.tetris_move_right).setOnClickListener(v -> {
            game.moveRight();
            refreshUi();
        });
        findViewById(R.id.tetris_move_down).setOnClickListener(v -> {
            game.moveDown();
            refreshUi();
            if (game.isGameOver()) showGameOverDialog();
        });
        findViewById(R.id.tetris_hard_drop).setOnClickListener(v -> {
            game.hardDrop();
            refreshUi();
            if (game.isGameOver()) showGameOverDialog();
            else rescheduleTick();
        });
        findViewById(R.id.tetris_rotate).setOnClickListener(v -> {
            game.rotate();
            refreshUi();
        });
        findViewById(R.id.tetris_restart).setOnClickListener(v -> restartGame());
        View exitView = findViewById(R.id.tetris_exit);
        if (exitView != null) {
            exitView.setOnClickListener(v -> finish());
        }

        restartGame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (metaVariant) {
            applyMetaImmersiveMode();
        }
        resumed = true;
        rescheduleTick();
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(tickRunnable);
        super.onPause();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (metaVariant && hasFocus) {
            applyMetaImmersiveMode();
        }
    }

    private void restartGame() {
        showingGameOver = false;
        game.reset();
        refreshUi();
        rescheduleTick();
    }

    private void refreshUi() {
        boardView.setMatrix(game.getBoardWithPiece());
        previewView.setMatrix(game.getNextPiecePreview());
        scoreView.setText(String.valueOf(game.getScore()));
        linesView.setText(String.valueOf(game.getLinesCleared()));
        levelView.setText(String.valueOf(game.getLevel()));
        statusView.setText(game.isGameOver()
                ? R.string.tetris_status_game_over
                : R.string.tetris_status_live);
    }

    private void rescheduleTick() {
        handler.removeCallbacks(tickRunnable);
        scheduleNextTick();
    }

    private void scheduleNextTick() {
        if (!resumed || showingGameOver || game.isGameOver()) {
            return;
        }
        handler.postDelayed(tickRunnable, game.getDropDelayMs());
    }

    private void showGameOverDialog() {
        if (showingGameOver || isFinishing()) {
            return;
        }
        showingGameOver = true;
        handler.removeCallbacks(tickRunnable);

        new AlertDialog.Builder(this)
                .setTitle(R.string.tetris_game_over_title)
                .setMessage(getString(R.string.tetris_game_over_message, game.getScore()))
                .setPositiveButton(R.string.tetris_restart_button,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                restartGame();
                            }
                        })
                .setNegativeButton(R.string.button_back,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                .setCancelable(false)
                .show();
    }

    private void configureMetaWindow() {
        Window window = getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                    | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
    }

    private void applyMetaImmersiveMode() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
