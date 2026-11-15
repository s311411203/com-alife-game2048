package com.alife.game2048;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        gameView = new GameView(this);
        setContentView(gameView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) {
            gameView.saveBest();
        }
    }

    class GameView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF scoreRect = new RectF();
        private final RectF bestRect = new RectF();
        private final RectF undoRect = new RectF();
        private final RectF restartRect = new RectF();

        private int[][] grid = new int[4][4];
        private long[][] spawnAt = new long[4][4];
        private int[][] undoGrid = null;

        private int score = 0;
        private int best = 0;
        private int undoScore = 0;
        private boolean hasUndo = false;
        private boolean keepPlaying = false;
        private boolean winShown = false;
        private boolean overShown = false;
        private boolean winJustReached = false;

        private float padding = 0f;
        private float headerTop = 0f;
        private float headerH = 0f;
        private float boardLeft = 0f;
        private float boardTop = 0f;
        private float boardSize = 0f;
        private float cell = 0f;
        private float gap = 0f;
        private float btnY = 0f;
        private float btnH = 0f;

        private float downX = 0f;
        private float downY = 0f;
        private boolean touchMoved = false;
        private boolean animating = false;

        private final SharedPreferences sp;

        GameView(Context c) {
            super(c);
            sp = c.getSharedPreferences("g2048", Context.MODE_PRIVATE);
            best = sp.getInt("best", 0);
            setFocusable(true);
            newGame();
        }

        private float dp(float v) {
            return v * getResources().getDisplayMetrics().density;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            padding = w * 0.06f;
            headerTop = h * 0.045f;
            headerH = Math.min(h * 0.15f, w * 0.30f);

            float scoreBoxH = headerH * 0.75f;
            float scoreBoxW = (w - padding * 2f) * 0.20f;
            bestRect.set(w - padding - scoreBoxW, headerTop + headerH - scoreBoxH,
                    w - padding, headerTop + headerH);
            scoreRect.set(bestRect.left - padding * 0.35f - scoreBoxW, bestRect.top,
                    bestRect.left - padding * 0.35f, bestRect.bottom);

            float boardTopTmp = headerTop + headerH + padding * 0.55f;
            float btnAreaH = Math.max(h * 0.10f, dp(52));
            float availH = h - boardTopTmp - btnAreaH - padding * 1.8f;
            boardSize = Math.min(w - padding * 2f, availH);
            if (boardSize < dp(80)) {
                boardSize = dp(80);
            }
            boardLeft = (w - boardSize) / 2f;
            boardTop = boardTopTmp;
            gap = boardSize * 0.03f;
            cell = (boardSize - gap * 5f) / 4f;

            btnH = Math.max(dp(44), btnAreaH * 0.62f);
            btnY = boardTop + boardSize + padding * 0.8f;
            float btnW = (w - padding * 2f - gap) / 2f;
            undoRect.set(padding, btnY, padding + btnW, btnY + btnH);
            restartRect.set(w - padding - btnW, btnY, w - padding, btnY + btnH);
        }

        private void newGame() {
            grid = new int[4][4];
            spawnAt = new long[4][4];
            undoGrid = null;
            hasUndo = false;
            score = 0;
            keepPlaying = false;
            winShown = false;
            overShown = false;
            winJustReached = false;
            animating = true;
            spawn();
            spawn();
            invalidate();
        }

        private void spawn() {
            int[] empty = new int[16];
            int n = 0;
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    if (grid[y][x] == 0) {
                        empty[n] = y * 4 + x;
                        n++;
                    }
                }
            }
            if (n == 0) {
                return;
            }
            int idx = empty[(int) (Math.random() * n)];
            int y = idx / 4;
            int x = idx % 4;
            grid[y][x] = Math.random() < 0.9 ? 2 : 4;
            spawnAt[y][x] = System.currentTimeMillis();
        }

        private int getCell(int dir, int i, int j) {
            if (dir == 0) {
                return grid[j][i];
            } else if (dir == 2) {
                return grid[3 - j][i];
            } else if (dir == 3) {
                return grid[i][j];
            }
            return grid[i][3 - j];
        }

        private void setCell(int dir, int i, int j, int v) {
            if (dir == 0) {
                grid[j][i] = v;
            } else if (dir == 2) {
                grid[3 - j][i] = v;
            } else if (dir == 3) {
                grid[i][j] = v;
            } else {
                grid[i][3 - j] = v;
            }
        }

        private boolean slide(int dir) {
            boolean changed = false;
            int addScore = 0;
            for (int i = 0; i < 4; i++) {
                int[] line = new int[4];
                for (int j = 0; j < 4; j++) {
                    line[j] = getCell(dir, i, j);
                }
                int[] res = new int[4];
                int cnt = 0;
                for (int j = 0; j < 4; j++) {
                    if (line[j] != 0) {
                        res[cnt] = line[j];
                        cnt++;
                    }
                }
                int[] out = new int[4];
                int oc = 0;
                for (int j = 0; j < cnt; j++) {
                    if (j + 1 < cnt && res[j] == res[j + 1]) {
                        int v = res[j] * 2;
                        out[oc] = v;
                        oc++;
                        addScore += v;
                        if (v >= 2048) {
                            winJustReached = true;
                        }
                        j++;
                    } else {
                        out[oc] = res[j];
                        oc++;
                    }
                }
                for (int j = 0; j < 4; j++) {
                    if (line[j] != out[j]) {
                        changed = true;
                    }
                }
                if (changed) {
                    for (int j = 0; j < 4; j++) {
                        setCell(dir, i, j, out[j]);
                    }
                }
            }
            if (changed) {
                score += addScore;
                updateBest();
            }
            return changed;
        }

        private int[][] copyGrid(int[][] src) {
            int[][] dst = new int[4][4];
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    dst[y][x] = src[y][x];
                }
            }
            return dst;
        }

        private void doMove(int dir) {
            int[][] before = copyGrid(grid);
            int beforeScore = score;
            winJustReached = false;
            if (slide(dir)) {
                undoGrid = before;
                undoScore = beforeScore;
                hasUndo = true;
                spawn();
                animating = true;
                invalidate();
                if (winJustReached && !keepPlaying && !winShown) {
                    winShown = true;
                    showWinDialog();
                }
                checkGameOver();
            }
        }

        private void doUndo() {
            if (!hasUndo || undoGrid == null) {
                return;
            }
            grid = copyGrid(undoGrid);
            score = undoScore;
            undoGrid = null;
            hasUndo = false;
            overShown = false;
            spawnAt = new long[4][4];
            updateBest();
            invalidate();
        }

        private void updateBest() {
            if (score > best) {
                best = score;
                sp.edit().putInt("best", best).apply();
            }
        }

        void saveBest() {
            updateBest();
        }

        private boolean canMove() {
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    if (grid[y][x] == 0) {
                        return true;
                    }
                    if (x < 3 && grid[y][x] == grid[y][x + 1]) {
                        return true;
                    }
                    if (y < 3 && grid[y][x] == grid[y + 1][x]) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void checkGameOver() {
            if (!canMove() && !overShown) {
                overShown = true;
                showOverDialog();
            }
        }

        private void showWinDialog() {
            AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
            b.setTitle("恭喜达成 2048！");
            b.setMessage("当前得分：" + score + "\n可以继续挑战更高分。");
            b.setCancelable(false);
            b.setPositiveButton("继续游戏", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    keepPlaying = true;
                }
            });
            b.setNegativeButton("重新开始", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    newGame();
                }
            });
            b.show();
        }

        private void showOverDialog() {
            AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
            b.setTitle("游戏结束");
            b.setMessage("本局得分：" + score + "\n最高分：" + best + "\n\n点“撤销”可以退回上一步再想想。");
            b.setCancelable(false);
            b.setPositiveButton("重新开始", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    newGame();
                }
            });
            b.setNegativeButton("关闭", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                }
            });
            b.show();
        }

        private void confirmRestart() {
            AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
            b.setTitle("重新开始");
            b.setMessage("本局得分将清零，确定重开？");
            b.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    newGame();
                }
            });
            b.setNegativeButton("取消", null);
            b.show();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    touchMoved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!touchMoved) {
                        float dx = e.getX() - downX;
                        float dy = e.getY() - downY;
                        float th = Math.max(dp(16), boardSize * 0.07f);
                        if (Math.abs(dx) > th || Math.abs(dy) > th) {
                            touchMoved = true;
                            if (Math.abs(dx) > Math.abs(dy)) {
                                doMove(dx > 0 ? 1 : 3);
                            } else {
                                doMove(dy > 0 ? 2 : 0);
                            }
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!touchMoved) {
                        float x = e.getX();
                        float y = e.getY();
                        if (undoRect.contains(x, y)) {
                            doUndo();
                        } else if (restartRect.contains(x, y)) {
                            confirmRestart();
                        }
                    }
                    return true;
            }
            return super.onTouchEvent(e);
        }

        private void drawTextCentered(Canvas c, String s, float cx, float cy, float size,
                                      int color, boolean bold) {
            textPaint.setColor(color);
            textPaint.setTextSize(size);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float off = (fm.ascent + fm.descent) / 2f;
            c.drawText(s, cx, cy - off, textPaint);
        }

        private void drawTextLeft(Canvas c, String s, float left, float cy, float size,
                                  int color, boolean bold) {
            textPaint.setColor(color);
            textPaint.setTextSize(size);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float off = (fm.ascent + fm.descent) / 2f;
            c.drawText(s, left, cy - off, textPaint);
        }

        private int tileColor(int v) {
            switch (v) {
                case 2:
                    return 0xFFEEE4DA;
                case 4:
                    return 0xFFEDE0C8;
                case 8:
                    return 0xFFF2B179;
                case 16:
                    return 0xFFF59563;
                case 32:
                    return 0xFFF67C5F;
                case 64:
                    return 0xFFF65E3B;
                case 128:
                    return 0xFFEDCF72;
                case 256:
                    return 0xFFEDCC61;
                case 512:
                    return 0xFFEDC850;
                case 1024:
                    return 0xFFEDC53F;
                case 2048:
                    return 0xFFEDC22E;
                default:
                    return 0xFF3C3A32;
            }
        }

        private int tileTextColor(int v) {
            return v >= 8 ? 0xFFF9F6F2 : 0xFF776E65;
        }

        private float tileTextSize(int v) {
            int len = String.valueOf(v).length();
            if (len <= 2) {
                return cell * 0.44f;
            } else if (len == 3) {
                return cell * 0.36f;
            } else if (len == 4) {
                return cell * 0.28f;
            }
            return cell * 0.23f;
        }

        private void drawScoreBox(Canvas c, RectF r, String label, int val) {
            paint.setColor(0xFFBBADA0);
            c.drawRoundRect(r, r.height() * 0.16f, r.height() * 0.16f, paint);
            drawTextCentered(c, label, r.centerX(), r.top + r.height() * 0.30f,
                    r.height() * 0.26f, 0xFFEDE4DA, true);
            drawTextCentered(c, String.valueOf(val), r.centerX(), r.top + r.height() * 0.68f,
                    r.height() * 0.36f, 0xFFFFFFFF, true);
        }

        private void drawButton(Canvas c, RectF r, String label, boolean enabled) {
            paint.setColor(enabled ? 0xFF8F7A66 : 0xFFCFC4B8);
            c.drawRoundRect(r, r.height() * 0.28f, r.height() * 0.28f, paint);
            drawTextCentered(c, label, r.centerX(), r.centerY(), r.height() * 0.38f,
                    enabled ? 0xFFF9F6F2 : 0xFFF1ECE4, true);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.drawColor(0xFFFAF8EF);

            float titleSize = Math.min(headerH * 0.62f, dp(46));
            drawTextLeft(c, "2048", padding, headerTop + headerH * 0.55f, titleSize,
                    0xFF776E65, true);
            drawScoreBox(c, scoreRect, "得分", score);
            drawScoreBox(c, bestRect, "最高", best);

            paint.setColor(0xFFBBADA0);
            rect.set(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize);
            c.drawRoundRect(rect, boardSize * 0.03f, boardSize * 0.03f, paint);

            long now = System.currentTimeMillis();
            for (int y = 0; y < 4; y++) {
                for (int x = 0; x < 4; x++) {
                    float left = boardLeft + gap + x * (cell + gap);
                    float top = boardTop + gap + y * (cell + gap);
                    int v = grid[y][x];
                    if (v == 0) {
                        paint.setColor(0xFFCDC1B4);
                        rect.set(left, top, left + cell, top + cell);
                        c.drawRoundRect(rect, cell * 0.12f, cell * 0.12f, paint);
                    } else {
                        float scale = 1f;
                        long st = spawnAt[y][x];
                        if (st > 0) {
                            float t = (now - st) / 110f;
                            if (t < 1f) {
                                scale = 0.6f + 0.4f * t;
                                animating = true;
                            } else {
                                spawnAt[y][x] = 0;
                            }
                        }
                        float cx = left + cell / 2f;
                        float cy = top + cell / 2f;
                        float half = cell / 2f * scale;
                        paint.setColor(tileColor(v));
                        rect.set(cx - half, cy - half, cx + half, cy + half);
                        c.drawRoundRect(rect, cell * 0.12f * scale, cell * 0.12f * scale, paint);
                        drawTextCentered(c, String.valueOf(v), cx, cy,
                                tileTextSize(v) * scale, tileTextColor(v), true);
                    }
                }
            }

            drawButton(c, undoRect, "撤销一步", hasUndo);
            drawButton(c, restartRect, "重新开始", true);

            if (animating) {
                animating = false;
                postInvalidateOnAnimation();
            }
        }
    }
}