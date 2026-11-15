import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

public class MainActivity extends Activity {

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gameView = new GameView(this);
        setContentView(gameView);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int dir = -1;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_W) {
            dir = GameView.UP;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_S) {
            dir = GameView.DOWN;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_A) {
            dir = GameView.LEFT;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_D) {
            dir = GameView.RIGHT;
        } else if (keyCode == KeyEvent.KEYCODE_R) {
            if (gameView != null) gameView.restart();
            return true;
        }
        if (dir >= 0 && gameView != null) {
            gameView.keyMove(dir);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    static class GameView extends View {

        static final int UP = 0, RIGHT = 1, DOWN = 2, LEFT = 3;
        static final int N = 4;
        static final long MOVE_MS = 90L;
        static final long POP_MS = 130L;

        private final int[] board = new int[N * N];
        private final int[] pending = new int[N * N];
        private final int[] animVal = new int[N * N * 2];
        private final int[] animR0 = new int[N * N * 2];
        private final int[] animC0 = new int[N * N * 2];
        private final int[] animR1 = new int[N * N * 2];
        private final int[] animC1 = new int[N * N * 2];
        private int animCount;
        private long animStart;
        private boolean animating;
        private int pendingGain;

        private int score;
        private int best;
        private boolean gameOver;
        private boolean showWin;
        private boolean winShown;
        private int popIdx = -1;
        private long popStart;

        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        private float boardLeft, boardTop, boardSize, gap, cellSize;
        private float downX, downY;
        private final SharedPreferences prefs;

        GameView(Context c) {
            super(c);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextAlign(Paint.Align.CENTER);
            prefs = c.getSharedPreferences("game2048", Context.MODE_PRIVATE);
            best = prefs.getInt("best", 0);
            restart();
        }

        void restart() {
            for (int i = 0; i < N * N; i++) board[i] = 0;
            score = 0;
            pendingGain = 0;
            animating = false;
            animCount = 0;
            gameOver = false;
            showWin = false;
            winShown = false;
            popIdx = -1;
            spawn();
            spawn();
            invalidate();
        }

        void keyMove(int dir) {
            move(dir);
        }

        private void move(int dir) {
            if (gameOver) return;
            if (showWin) showWin = false;
            if (animating) commitAnim();
            if (!computeMove(dir)) return;
            animating = true;
            animStart = System.currentTimeMillis();
            invalidate();
        }

        private int cellIndex(int dir, int line, int k) {
            if (dir == LEFT) return line * N + k;
            if (dir == RIGHT) return line * N + (N - 1 - k);
            if (dir == UP) return k * N + line;
            return (N - 1 - k) * N + line;
        }

        private boolean computeMove(int dir) {
            for (int i = 0; i < N * N; i++) pending[i] = 0;
            animCount = 0;
            pendingGain = 0;
            int[] v = new int[N];
            int[] src = new int[N];
            int[] ov = new int[N];
            int[] os1 = new int[N];
            int[] os2 = new int[N];

            for (int line = 0; line < N; line++) {
                for (int k = 0; k < N; k++) {
                    int idx = cellIndex(dir, line, k);
                    v[k] = board[idx];
                    src[k] = idx;
                }
                int oc = 0;
                int i = 0;
                while (i < N) {
                    if (v[i] == 0) { i++; continue; }
                    if (i + 1 < N && v[i + 1] == v[i]) {
                        ov[oc] = v[i] * 2;
                        os1[oc] = src[i];
                        os2[oc] = src[i + 1];
                        pendingGain += ov[oc];
                        i += 2;
                    } else {
                        ov[oc] = v[i];
                        os1[oc] = src[i];
                        os2[oc] = -1;
                        i++;
                    }
                    oc++;
                }
                for (int j = 0; j < N; j++) {
                    if (j >= oc) continue;
                    int idx = cellIndex(dir, line, j);
                    pending[idx] = ov[j];
                    int a = os1[j];
                    addAnim(board[a], a, idx);
                    if (os2[j] >= 0) addAnim(board[os2[j]], os2[j], idx);
                }
            }

            boolean changed = false;
            for (int i = 0; i < N * N; i++) {
                if (pending[i] != board[i]) { changed = true; break; }
            }
            if (!changed) {
                animCount = 0;
                pendingGain = 0;
            }
            return changed;
        }

        private void addAnim(int val, int from, int to) {
            if (animCount >= animVal.length) return;
            animVal[animCount] = val;
            animR0[animCount] = from / N;
            animC0[animCount] = from % N;
            animR1[animCount] = to / N;
            animC1[animCount] = to % N;
            animCount++;
        }

        private void commitAnim() {
            for (int i = 0; i < N * N; i++) board[i] = pending[i];
            score += pendingGain;
            pendingGain = 0;
            animating = false;
            animCount = 0;
            spawn();
            if (score > best) {
                best = score;
                prefs.edit().putInt("best", best).apply();
            }
            if (!winShown) {
                for (int i = 0; i < N * N; i++) {
                    if (board[i] >= 2048) { winShown = true; showWin = true; break; }
                }
            }
            if (isGameOver()) gameOver = true;
            invalidate();
        }

        private void spawn() {
            int[] empty = new int[N * N];
            int n = 0;
            for (int i = 0; i < N * N; i++) if (board[i] == 0) empty[n++] = i;
            if (n == 0) return;
            int pick = empty[(int) (Math.random() * n)];
            board[pick] = Math.random() < 0.9 ? 2 : 4;
            popIdx = pick;
            popStart = System.currentTimeMillis();
        }

        private boolean isGameOver() {
            for (int i = 0; i < N * N; i++) if (board[i] == 0) return false;
            for (int r = 0; r < N; r++) {
                for (int c = 0; c < N; c++) {
                    int v = board[r * N + c];
                    if (c + 1 < N && board[r * N + c + 1] == v) return false;
                    if (r + 1 < N && board[(r + 1) * N + c] == v) return false;
                }
            }
            return true;
        }

        private float cellX(float c) {
            return boardLeft + gap + c * (cellSize + gap);
        }

        private float cellY(float r) {
            return boardTop + gap + r * (cellSize + gap);
        }

        private float cellRadius() {
            return cellSize * 0.09f;
        }

        private int tileColor(int v) {
            switch (v) {
                case 2: return 0xFFEEE4DA;
                case 4: return 0xFFEDE0C8;
                case 8: return 0xFFF2B179;
                case 16: return 0xFFF59563;
                case 32: return 0xFFF67C5F;
                case 64: return 0xFFF65E3B;
                case 128: return 0xFFEDCF72;
                case 256: return 0xFFEDCC61;
                case 512: return 0xFFEDC850;
                case 1024: return 0xFFEDC53F;
                case 2048: return 0xFFEDC22E;
                default: return 0xFF3C3A32;
            }
        }

        private void drawTileAt(Canvas cv, float r, float c, int v, float scale) {
            float x = cellX(c);
            float y = cellY(r);
            float cx = x + cellSize / 2f;
            float cy = y + cellSize / 2f;
            float half = cellSize * scale / 2f;
            fill.setColor(tileColor(v));
            rect.set(cx - half, cy - half, cx + half, cy + half);
            cv.drawRoundRect(rect, cellRadius() * scale, cellRadius() * scale, fill);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(v <= 4 ? 0xFF776E65 : 0xFFF9F6F2);
            float ts = cellSize * (v < 100 ? 0.44f : (v < 1000 ? 0.36f : 0.29f));
            text.setTextSize(ts * scale);
            float ty = cy - (text.descent() + text.ascent()) / 2f;
            cv.drawText(String.valueOf(v), cx, ty, text);
        }

        private void drawBox(Canvas cv, float x, float y, float w, float h, String label, int val) {
            fill.setColor(0xFFBBADA0);
            rect.set(x, y, x + w, y + h);
            cv.drawRoundRect(rect, h * 0.16f, h * 0.16f, fill);
            text.setTextAlign(Paint.Align.CENTER);
            float cx = x + w / 2f;
            text.setColor(0xFFEEE4DA);
            text.setTextSize(h * 0.26f);
            cv.drawText(label, cx, y + h * 0.34f, text);
            text.setColor(0xFFFFFFFF);
            text.setTextSize(h * 0.40f);
            cv.drawText(String.valueOf(val), cx, y + h * 0.82f, text);
        }

        @Override
        protected void onDraw(Canvas cv) {
            long now = System.currentTimeMillis();
            if (animating && now - animStart >= MOVE_MS) commitAnim();

            int w = getWidth();
            int h = getHeight();
            float pad = w * 0.045f;
            float avail = h - pad * 2f;
            float headerH = avail * 0.16f;
            boardSize = Math.min(w - pad * 2f, avail - headerH);
            boardLeft = (w - boardSize) / 2f;
            boardTop = pad + headerH;
            gap = boardSize * 0.028f;
            cellSize = (boardSize - gap * (N + 1)) / N;

            cv.drawColor(0xFFFAF8EF);

            float boxH = headerH * 0.62f;
            float boxW = boardSize * 0.29f;
            float boxTop = pad + (headerH - boxH) / 2f;
            float bestX = boardLeft + boardSize - boxW;
            float scoreX = bestX - boxW - boardSize * 0.02f;

            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(0xFF776E65);
            text.setTextSize(headerH * 0.62f);
            cv.drawText("2048", boardLeft, boxTop + boxH * 0.78f, text);

            drawBox(cv, scoreX, boxTop, boxW, boxH, "分数", score);
            drawBox(cv, bestX, boxTop, boxW, boxH, "最高分", best);

            fill.setColor(0xFFBBADA0);
            rect.set(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize);
            cv.drawRoundRect(rect, boardSize * 0.03f, boardSize * 0.03f, fill);

            fill.setColor(0xFFCDC1B4);
            for (int r = 0; r < N; r++) {
                for (int c = 0; c < N; c++) {
                    float x = cellX(c);
                    float y = cellY(r);
                    rect.set(x, y, x + cellSize, y + cellSize);
                    cv.drawRoundRect(rect, cellRadius(), cellRadius(), fill);
                }
            }

            if (animating) {
                float t = (now - animStart) / (float) MOVE_MS;
                if (t > 1f) t = 1f;
                float e = 1f - (1f - t) * (1f - t);
                for (int i = 0; i < animCount; i++) {
                    float rr = animR0[i] + (animR1[i] - animR0[i]) * e;
                    float cc = animC0[i] + (animC1[i] - animC0[i]) * e;
                    drawTileAt(cv, rr, cc, animVal[i], 1f);
                }
            } else {
                for (int r = 0; r < N; r++) {
                    for (int c = 0; c < N; c++) {
                        int idx = r * N + c;
                        int v = board[idx];
                        if (v == 0) continue;
                        float s = 1f;
                        if (idx == popIdx) {
                            float t = (now - popStart) / (float) POP_MS;
                            if (t < 1f) {
                                s = 0.35f + 0.75f * t - 0.10f * t * t;
                            } else {
                                popIdx = -1;
                            }
                        }
                        drawTileAt(cv, r, c, v, s);
                    }
                }
            }

            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(0xFF9C8F84);
            text.setTextSize(Math.max(h * 0.020f, 12f * getResources().getDisplayMetrics().density));
            cv.drawText("方向键 / WASD 或滑动屏幕，R 重开",
                    w / 2f, boardTop + boardSize + pad * 1.3f, text);

            if (gameOver || showWin) {
                fill.setColor(0xE6FAF8EF);
                rect.set(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize);
                cv.drawRoundRect(rect, boardSize * 0.03f, boardSize * 0.03f, fill);
                text.setTextAlign(Paint.Align.CENTER);
                text.setColor(0xFF776E65);
                text.setTextSize(boardSize * 0.105f);
                cv.drawText(gameOver ? "游戏结束" : "你赢了！",
                        w / 2f, boardTop + boardSize * 0.45f, text);
                text.setTextSize(boardSize * 0.052f);
                text.setColor(0xFF8F7F72);
                String tip = gameOver ? ("得分 " + score + "　点击重新开始") : "点击继续挑战";
                cv.drawText(tip, w / 2f, boardTop + boardSize * 0.58f, text);
            }

            postInvalidateDelayed(16);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = e.getX();
                downY = e.getY();
                return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                float th = 18f * getResources().getDisplayMetrics().density;
                if (Math.abs(dx) < th && Math.abs(dy) < th) {
                    if (gameOver) restart();
                    else if (showWin) { showWin = false; invalidate(); }
                    return true;
                }
                if (Math.abs(dx) > Math.abs(dy)) {
                    move(dx > 0 ? RIGHT : LEFT);
                } else {
                    move(dy > 0 ? DOWN : UP);
                }
                return true;
            }
            return super.onTouchEvent(e);
        }
    }
}
