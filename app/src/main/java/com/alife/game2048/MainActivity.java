import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new GameView(this));
    }

    static class GameView extends View {
        static final int N = 4;

        int[][] board = new int[N][N];
        int score = 0;
        int best = 0;
        boolean over = false;
        boolean won = false;

        final Random rnd = new Random();
        final SharedPreferences sp;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        int W = 0;
        int H = 0;
        float pad;
        float boardSize;
        float cellSize;
        float gap;
        float boardLeft;
        float boardTop;
        final RectF btnRect = new RectF();

        float downX = 0;
        float downY = 0;
        boolean btnDown = false;

        GameView(Context c) {
            super(c);
            sp = c.getSharedPreferences("game2048", Context.MODE_PRIVATE);
            best = sp.getInt("best", 0);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            newGame();
            setFocusable(true);
        }

        float dp(float v) {
            return v * getResources().getDisplayMetrics().density;
        }

        void newGame() {
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < N; j++) {
                    board[i][j] = 0;
                }
            }
            score = 0;
            over = false;
            won = false;
            addRandom();
            addRandom();
            invalidate();
        }

        boolean addRandom() {
            int count = 0;
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < N; j++) {
                    if (board[i][j] == 0) count++;
                }
            }
            if (count == 0) return false;
            int idx = rnd.nextInt(count);
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < N; j++) {
                    if (board[i][j] == 0) {
                        if (idx == 0) {
                            board[i][j] = (rnd.nextInt(10) == 0) ? 4 : 2;
                            return true;
                        }
                        idx--;
                    }
                }
            }
            return false;
        }

        int[] compress(int[] src) {
            int[] tmp = new int[N];
            int k = 0;
            for (int i = 0; i < N; i++) {
                if (src[i] != 0) tmp[k++] = src[i];
            }
            for (int i = 0; i < N - 1; i++) {
                if (tmp[i] != 0 && tmp[i] == tmp[i + 1]) {
                    tmp[i] = tmp[i] * 2;
                    tmp[i + 1] = 0;
                    score += tmp[i];
                    if (tmp[i] >= 2048) won = true;
                }
            }
            int[] res = new int[N];
            k = 0;
            for (int i = 0; i < N; i++) {
                if (tmp[i] != 0) res[k++] = tmp[i];
            }
            return res;
        }

        boolean move(int dir) {
            boolean changed = false;
            for (int i = 0; i < N; i++) {
                int[] src = new int[N];
                for (int j = 0; j < N; j++) {
                    switch (dir) {
                        case 0: src[j] = board[j][i]; break;
                        case 2: src[j] = board[N - 1 - j][i]; break;
                        case 3: src[j] = board[i][j]; break;
                        default: src[j] = board[i][N - 1 - j]; break;
                    }
                }
                int[] dst = compress(src);
                for (int j = 0; j < N; j++) {
                    if (dst[j] != src[j]) changed = true;
                }
                for (int j = 0; j < N; j++) {
                    switch (dir) {
                        case 0: board[j][i] = dst[j]; break;
                        case 2: board[N - 1 - j][i] = dst[j]; break;
                        case 3: board[i][j] = dst[j]; break;
                        default: board[i][N - 1 - j] = dst[j]; break;
                    }
                }
            }
            return changed;
        }

        boolean canMove() {
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < N; j++) {
                    if (board[i][j] == 0) return true;
                    if (j + 1 < N && board[i][j] == board[i][j + 1]) return true;
                    if (i + 1 < N && board[i][j] == board[i + 1][j]) return true;
                }
            }
            return false;
        }

        void doMove(int dir) {
            if (over) return;
            if (move(dir)) {
                if (score > best) {
                    best = score;
                    sp.edit().putInt("best", best).apply();
                }
                addRandom();
                if (!canMove()) over = true;
            }
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            W = w;
            H = h;
            pad = dp(14);
            float header = dp(88);
            float footer = dp(70);
            boardSize = Math.min(w - pad * 2, h - header - footer);
            if (boardSize < dp(120)) boardSize = Math.min(w - pad * 2, h - header - footer);
            gap = boardSize * 0.035f;
            cellSize = (boardSize - gap * (N + 1)) / N;
            boardLeft = (w - boardSize) / 2f;
            boardTop = header;
            float btnW = dp(150);
            float btnTop = boardTop + boardSize + dp(14);
            btnRect.set((w - btnW) / 2f, btnTop, (w + btnW) / 2f, btnTop + dp(44));
        }

        int cellColor(int v) {
            switch (v) {
                case 0: return 0xFFCDC1B4;
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

        float fitText(int v, float box) {
            String s = String.valueOf(v);
            float size = box * 0.46f;
            paint.setTextSize(size);
            while (paint.measureText(s) > box * 0.84f && size > dp(9)) {
                size -= dp(1);
                paint.setTextSize(size);
            }
            return size;
        }

        void drawScoreCard(Canvas cv, float x, float y, float w, float h, String label, int value) {
            paint.setColor(0xFFBBADA0);
            RectF r = new RectF(x, y, x + w, y + h);
            cv.drawRoundRect(r, dp(5), dp(5), paint);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(0xFFEEE4DA);
            paint.setTextSize(dp(12));
            cv.drawText(label, r.centerX(), y + dp(18), paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(dp(19));
            cv.drawText(String.valueOf(value), r.centerX(), y + dp(40), paint);
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            cv.drawColor(0xFFFAF8EF);

            paint.setTypeface(Typeface.DEFAULT_BOLD);

            paint.setColor(0xFF776E65);
            paint.setTextSize(dp(36));
            paint.setTextAlign(Paint.Align.LEFT);
            cv.drawText("2048", pad, dp(50), paint);

            if (won) {
                paint.setColor(0xFFF67C5F);
                paint.setTextSize(dp(13));
                cv.drawText("已达成 2048！继续挑战", pad, dp(72), paint);
            } else {
                paint.setColor(0xFF9C8F84);
                paint.setTextSize(dp(12));
                cv.drawText("滑动合并相同数字", pad, dp(72), paint);
            }

            float cardW = dp(78);
            drawScoreCard(cv, W - pad - cardW * 2 - dp(8), pad + dp(4), cardW, dp(52), "分数", score);
            drawScoreCard(cv, W - pad - cardW, pad + dp(4), cardW, dp(52), "最高", best);

            RectF boardRect = new RectF(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize);
            paint.setColor(0xFFBBADA0);
            cv.drawRoundRect(boardRect, dp(6), dp(6), paint);

            for (int i = 0; i < N; i++) {
                for (int j = 0; j < N; j++) {
                    float x = boardLeft + gap + j * (cellSize + gap);
                    float y = boardTop + gap + i * (cellSize + gap);
                    RectF r = new RectF(x, y, x + cellSize, y + cellSize);
                    int v = board[i][j];
                    paint.setColor(cellColor(v));
                    cv.drawRoundRect(r, dp(5), dp(5), paint);
                    if (v != 0) {
                        paint.setColor(v <= 4 ? 0xFF776E65 : 0xFFF9F6F2);
                        paint.setTextAlign(Paint.Align.CENTER);
                        paint.setTextSize(fitText(v, cellSize));
                        Paint.FontMetrics fm = paint.getFontMetrics();
                        float ty = r.centerY() - (fm.ascent + fm.descent) / 2f;
                        cv.drawText(String.valueOf(v), r.centerX(), ty, paint);
                    }
                }
            }

            if (over) {
                paint.setColor(0xE6FAF8EF);
                cv.drawRoundRect(boardRect, dp(6), dp(6), paint);
                paint.setColor(0xFF776E65);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(dp(30));
                Paint.FontMetrics fm = paint.getFontMetrics();
                cv.drawText("游戏结束", boardRect.centerX(), boardRect.centerY() - dp(6), paint);
                paint.setColor(0xFF8F7A66);
                paint.setTextSize(dp(15));
                cv.drawText("点击棋盘重新开始", boardRect.centerX(), boardRect.centerY() + dp(28), paint);
            }

            paint.setColor(0xFF8F7A66);
            cv.drawRoundRect(btnRect, dp(6), dp(6), paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(17));
            Paint.FontMetrics bfm = paint.getFontMetrics();
            cv.drawText("重新开始", btnRect.centerX(), btnRect.centerY() - (bfm.ascent + bfm.descent) / 2f, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    btnDown = btnRect.contains(downX, downY);
                    return true;
                case MotionEvent.ACTION_UP: {
                    float dx = e.getX() - downX;
                    float dy = e.getY() - downY;
                    float th = dp(26);
                    if (btnDown && btnRect.contains(e.getX(), e.getY())) {
                        newGame();
                    } else if (over) {
                        if (Math.abs(dx) < th && Math.abs(dy) < th) newGame();
                    } else if (Math.abs(dx) > Math.abs(dy)) {
                        if (dx > th) doMove(3);
                        else if (dx < -th) doMove(1);
                    } else {
                        if (dy > th) doMove(2);
                        else if (dy < -th) doMove(0);
                    }
                    btnDown = false;
                    return true;
                }
                default:
                    return super.onTouchEvent(e);
            }
        }
    }
}
