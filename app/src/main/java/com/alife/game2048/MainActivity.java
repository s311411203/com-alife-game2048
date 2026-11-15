

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import java.util.Random;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        setTheme(android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        super.onCreate(b);
        setContentView(new GameView(this));
    }

    static class GameView extends View {

        static final int N = 4;

        int[][] board = new int[N][N];
        float[][] anim = new float[N][N];
        int[][] animType = new int[N][N];

        int score = 0;
        int best = 0;
        boolean over = false;
        boolean won = false;
        boolean wonDismissed = false;

        Random rnd = new Random();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        SharedPreferences sp;

        float density;
        float boardL, boardT, boardS, cell, gap;
        RectF btnNew = new RectF();
        RectF btnOverlay = new RectF();
        float downX, downY;

        GameView(Context c) {
            super(c);
            density = c.getResources().getDisplayMetrics().density;
            sp = c.getSharedPreferences("g2048", Context.MODE_PRIVATE);
            best = sp.getInt("best", 0);
            txt.setTypeface(Typeface.DEFAULT_BOLD);
            txt.setTextAlign(Paint.Align.CENTER);
            setKeepScreenOn(true);
            newGame();
        }

        void newGame() {
            for (int r = 0; r < N; r++) {
                for (int c = 0; c < N; c++) {
                    board[r][c] = 0;
                    anim[r][c] = 1f;
                    animType[r][c] = 0;
                }
            }
            score = 0;
            over = false;
            won = false;
            wonDismissed = false;
            addRandom();
            addRandom();
            invalidate();
        }

        void addRandom() {
            int empty = 0;
            for (int r = 0; r < N; r++)
                for (int c = 0; c < N; c++)
                    if (board[r][c] == 0) empty++;
            if (empty == 0) return;
            int pick = rnd.nextInt(empty);
            for (int r = 0; r < N; r++) {
                for (int c = 0; c < N; c++) {
                    if (board[r][c] == 0) {
                        if (pick == 0) {
                            board[r][c] = rnd.nextInt(10) == 0 ? 4 : 2;
                            anim[r][c] = 0f;
                            animType[r][c] = 2;
                            return;
                        }
                        pick--;
                    }
                }
            }
        }

        boolean slide(int dir) {
            boolean moved = false;
            int gained = 0;
            int[][] nb = new int[N][N];
            int[][] nt = new int[N][N];
            float[][] na = new float[N][N];
            for (int r = 0; r < N; r++)
                for (int c = 0; c < N; c++) na[r][c] = 1f;

            for (int line = 0; line < N; line++) {
                int[] vals = new int[N];
                for (int k = 0; k < N; k++) {
                    int r, c;
                    if (dir == 0) { r = k; c = line; }
                    else if (dir == 2) { r = N - 1 - k; c = line; }
                    else if (dir == 3) { r = line; c = k; }
                    else { r = line; c = N - 1 - k; }
                    vals[k] = board[r][c];
                }

                int[] comp = new int[N];
                int n = 0;
                for (int k = 0; k < N; k++) if (vals[k] != 0) comp[n++] = vals[k];

                int[] out = new int[N];
                boolean[] merged = new boolean[N];
                int oi = 0, i = 0;
                while (i < n) {
                    if (i + 1 < n && comp[i] == comp[i + 1]) {
                        out[oi] = comp[i] * 2;
                        merged[oi] = true;
                        gained += out[oi];
                        i += 2;
                    } else {
                        out[oi] = comp[i];
                        i++;
                    }
                    oi++;
                }

                for (int k = 0; k < N; k++) {
                    int r, c;
                    if (dir == 0) { r = k; c = line; }
                    else if (dir == 2) { r = N - 1 - k; c = line; }
                    else if (dir == 3) { r = line; c = k; }
                    else { r = line; c = N - 1 - k; }
                    nb[r][c] = out[k];
                    if (out[k] != board[r][c]) moved = true;
                    if (out[k] != 0 && merged[k]) {
                        nt[r][c] = 1;
                        na[r][c] = 0f;
                    }
                }
            }

            if (!moved) return false;

            board = nb;
            animType = nt;
            anim = na;
            score += gained;
            if (score > best) {
                best = score;
                sp.edit().putInt("best", best).apply();
            }
            return true;
        }

        void checkState() {
            for (int r = 0; r < N; r++)
                for (int c = 0; c < N; c++)
                    if (board[r][c] >= 2048) won = true;

            for (int r = 0; r < N; r++)
                for (int c = 0; c < N; c++)
                    if (board[r][c] == 0) { over = false; return; }

            for (int r = 0; r < N; r++) {
                for (int c = 0; c < N; c++) {
                    int v = board[r][c];
                    if (c + 1 < N && board[r][c + 1] == v) { over = false; return; }
                    if (r + 1 < N && board[r + 1][c] == v) { over = false; return; }
                }
            }
            over = true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    return true;
                case MotionEvent.ACTION_UP: {
                    float x = e.getX(), y = e.getY();
                    float dx = x - downX, dy = y - downY;
                    float thr = 26 * density;

                    if (over || (won && !wonDismissed)) {
                        if (Math.abs(dx) < thr && Math.abs(dy) < thr && btnOverlay.contains(x, y)) {
                            if (over) newGame();
                            else { wonDismissed = true; invalidate(); }
                        }
                        return true;
                    }

                    if (Math.abs(dx) < thr && Math.abs(dy) < thr) {
                        if (btnNew.contains(x, y)) newGame();
                        return true;
                    }

                    int dir;
                    if (Math.abs(dx) > Math.abs(dy)) dir = dx > 0 ? 1 : 3;
                    else dir = dy > 0 ? 2 : 0;

                    if (slide(dir)) {
                        addRandom();
                        checkState();
                    }
                    invalidate();
                    return true;
                }
            }
            return super.onTouchEvent(e);
        }

        int tileColor(int v) {
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

        float textSizeFor(int v, float cw) {
            int len = String.valueOf(v).length();
            float ts = cw * 0.46f;
            if (len == 3) ts = cw * 0.37f;
            else if (len == 4) ts = cw * 0.29f;
            else if (len >= 5) ts = cw * 0.23f;
            return ts;
        }

        @Override
        protected void onDraw(Canvas cv) {
            boolean needAnim = false;
            for (int r = 0; r < N; r++) {
                for (int c = 0; c < N; c++) {
                    if (anim[r][c] < 1f) {
                        anim[r][c] = Math.min(1f, anim[r][c] + 0.13f);
                        needAnim = true;
                    }
                }
            }

            cv.drawColor(0xFFFAF8EF);

            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            float pad = w * 0.055f;
            float headerH = w * 0.135f;
            float rowH = w * 0.115f;
            float gapY = w * 0.045f;

            float fixedH = pad + headerH + gapY + rowH + gapY + pad;
            float bs = Math.min(w - 2 * pad, h - fixedH);
            if (bs < w * 0.45f) bs = w - 2 * pad;
            float totalH = fixedH + bs;
            float top = (h - totalH) / 2f;
            if (top < pad * 0.5f) top = pad * 0.5f;

            txt.setTextAlign(Paint.Align.LEFT);
            txt.setColor(0xFF776E65);
            txt.setTextSize(w * 0.135f);
            float titleCy = top + pad + headerH / 2f;
            cv.drawText("2048", pad, titleCy - (txt.ascent() + txt.descent()) / 2f, txt);

            float bw = w * 0.205f;
            float gapB = w * 0.025f;
            float bestL = w - pad - bw;
            float scoreL = bestL - gapB - bw;
            float boxT = top + pad;
            float boxB = boxT + headerH;
            float rad = headerH * 0.18f;

            p.setColor(0xFFBBADA0);
            RectF r1 = new RectF(scoreL, boxT, scoreL + bw, boxB);
            RectF r2 = new RectF(bestL, boxT, bestL + bw, boxB);
            cv.drawRoundRect(r1, rad, rad, p);
            cv.drawRoundRect(r2, rad, rad, p);

            txt.setTextAlign(Paint.Align.CENTER);
            txt.setColor(0xFFEEE4DA);
            txt.setTextSize(bw * 0.30f);
            float l1 = boxT + headerH * 0.34f;
            cv.drawText("得分", scoreL + bw / 2f, l1, txt);
            cv.drawText("最高", bestL + bw / 2f, l1, txt);

            txt.setColor(Color.WHITE);
            txt.setTextSize(bw * 0.42f);
            float l2 = boxT + headerH * 0.75f;
            cv.drawText(String.valueOf(score), scoreL + bw / 2f, l2, txt);
            cv.drawText(String.valueOf(best), bestL + bw / 2f, l2, txt);

            float rowT = top + pad + headerH + gapY;
            float btnW = w * 0.30f;
            btnNew.set(w - pad - btnW, rowT, w - pad, rowT + rowH);

            txt.setTextAlign(Paint.Align.LEFT);
            txt.setColor(0xFF8F7A66);
            txt.setTextSize(w * 0.040f);
            cv.drawText("滑动方块，合并相同数字", pad, rowT + rowH / 2f - (txt.ascent() + txt.descent()) / 2f, txt);

            p.setColor(0xFF8F7A66);
            cv.drawRoundRect(btnNew, rowH * 0.28f, rowH * 0.28f, p);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setColor(Color.WHITE);
            txt.setTextSize(w * 0.048f);
            cv.drawText("新游戏", btnNew.centerX(), btnNew.centerY() - (txt.ascent() + txt.descent()) / 2f, txt);

            boardL = (w - bs) / 2f;
            boardT = rowT + rowH + gapY;
            boardS = bs;
            gap = bs * 0.028f;
            cell = (bs - gap * (N + 1)) / N;

            p.setColor(0xFFBBADA0);
            RectF br = new RectF(boardL, boardT, boardL + bs, boardT + bs);
            float brRad = bs * 0.025f;
            cv.drawRoundRect(br, brRad, brRad, p);

            float cRad = cell * 0.09f;

            for (int r = 0; r < N; r++) {
                for (int c = 0; c < N; c++) {
                    float x = boardL + gap + c * (cell + gap);
                    float y = boardT + gap + r * (cell + gap);
                    int v = board[r][c];

                    if (v == 0) {
                        p.setColor(0xFFCDC1B4);
                        cv.drawRoundRect(new RectF(x, y, x + cell, y + cell), cRad, cRad, p);
                        continue;
                    }

                    float a = anim[r][c];
                    float sc = 1f;
                    if (a < 1f) {
                        sc = animType[r][c] == 1 ? (1.22f - 0.22f * a) : (0.30f + 0.70f * a);
                    }
                    float cw = cell * sc;
                    float ch = cell * sc;
                    float tx = x + (cell - cw) / 2f;
                    float ty = y + (cell - ch) / 2f;

                    p.setColor(tileColor(v));
                    cv.drawRoundRect(new RectF(tx, ty, tx + cw, ty + ch), cRad, cRad, p);

                    txt.setColor(v <= 4 ? 0xFF776E65 : Color.WHITE);
                    txt.setTextSize(textSizeFor(v, cw));
                    cv.drawText(String.valueOf(v), tx + cw / 2f,
                            ty + ch / 2f - (txt.ascent() + txt.descent()) / 2f, txt);
                }
            }

            if (over || (won && !wonDismissed)) {
                p.setColor(0xB8FAF8EF);
                cv.drawRoundRect(new RectF(boardL, boardT, boardL + bs, boardT + bs), brRad, brRad, p);

                txt.setTextAlign(Paint.Align.CENTER);
                txt.setColor(0xFF776E65);
                txt.setTextSize(bs * 0.125f);
                cv.drawText(over ? "游戏结束" : "你赢了！", boardL + bs / 2f,
                        boardT + bs * 0.36f - (txt.ascent() + txt.descent()) / 2f, txt);

                txt.setColor(0xFF8F7A66);
                txt.setTextSize(bs * 0.062f);
                cv.drawText("得分 " + score, boardL + bs / 2f,
                        boardT + bs * 0.47f - (txt.ascent() + txt.descent()) / 2f, txt);

                float bw2 = bs * 0.52f;
                float bh2 = bs * 0.155f;
                float bx = boardL + (bs - bw2) / 2f;
                float by = boardT + bs * 0.60f;
                btnOverlay.set(bx, by, bx + bw2, by + bh2);

                p.setColor(0xFF8F7A66);
                cv.drawRoundRect(btnOverlay, bh2 * 0.3f, bh2 * 0.3f, p);

                txt.setColor(Color.WHITE);
                txt.setTextSize(bs * 0.068f);
                cv.drawText(over ? "再来一局" : "继续游戏", btnOverlay.centerX(),
                        btnOverlay.centerY() - (txt.ascent() + txt.descent()) / 2f, txt);
            } else {
                btnOverlay.setEmpty();
            }

            if (needAnim) postInvalidateDelayed(16);
        }
    }
}