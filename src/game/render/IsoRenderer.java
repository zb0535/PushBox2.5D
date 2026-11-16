package game.render;

import game.model.GameView;
import game.model.TileType;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;

/**
 * GameView를 읽어 아이소메트릭(2.5D)으로 그리는 렌더러.
 * 지금은 도형(플레이스홀더)으로 그리며, 텍스처가 오면 draw~ 메서드 내부만 이미지로 교체한다.
 *
 * 주의: EDT(paintComponent)에서만 호출한다. 그리는 중 객체를 새로 만들지 않도록
 * 폴리곤 좌표 배열·색·Stroke·Composite를 필드/상수로 재사용하므로 스레드 안전하지 않다.
 */
public class IsoRenderer {

    public static final int TILE_W = 64;   // 바닥 마름모 가로
    public static final int TILE_H = 32;   // 바닥 마름모 세로
    public static final int BOX_H  = 28;   // 박스 높이(px)
    public static final int WALL_H = 40;   // 벽 높이(px)

    /** 시야를 가리는 앞쪽 벽의 불투명도 (0.3 ~ 0.5 권장). 1.0이면 반투명 처리 없음. */
    public static final float WALL_FADE_ALPHA = 0.4f;

    /** true면 박스가 벽 뒤에 가려질 때도 그 벽을 반투명으로 그린다. false면 플레이어 기준으로만. */
    public static final boolean FADE_FOR_BOXES = true;

    /**
     * true면 목표 칸이 벽 뒤에 가려질 때도 그 벽을 반투명으로 그린다.
     * 벽(40px)이 바로 앞 칸의 바닥을 통째로 덮기 때문에, 맨 앞줄의 목표는 이 옵션이 없으면 보이지 않는다.
     */
    public static final boolean FADE_FOR_GOALS = true;

    private static final int HW = TILE_W / 2;
    private static final int HH = TILE_H / 2;

    /**
     * 반투명 후보 위치: "대상(플레이어/박스)에서 (dx, dy)만큼 앞쪽"에 있는 벽.
     * 아이소메트릭에서 x+y가 큰 쪽이 화면 앞쪽이다. 대상 바로 앞 3칸
     * (오른쪽 앞 (1,0), 왼쪽 앞 (0,1), 정면 (1,1))만 대상을 실제로 가릴 수 있다.
     * 벽 높이/스프라이트 크기가 바뀌면 이 목록을 조정한다.
     */
    private static final int[] FADE_DX = { 1, 0, 1 };
    private static final int[] FADE_DY = { 0, 1, 1 };

    // 색상·Stroke·Composite는 매번 만들지 않고 재사용
    private static final Color FLOOR_COLOR   = new Color(70, 75, 90);
    private static final Color FLOOR_LINE    = new Color(50, 55, 70);
    private static final Color GOAL_COLOR    = new Color(230, 200, 70);
    private static final Color SHADOW_COLOR  = new Color(0, 0, 0, 70);
    private static final Color OUTLINE_COLOR = new Color(25, 20, 15);
    private static final Color PLAYER_BODY   = new Color(70, 130, 220);
    private static final Color PLAYER_HEAD   = new Color(245, 215, 175);

    private static final Color[] WALL_COLORS     = { new Color(150, 155, 170), new Color(110, 115, 130), new Color(80, 85, 100) };
    private static final Color[] BOX_COLORS      = { new Color(214, 160, 90),  new Color(170, 120, 60),  new Color(130, 90, 45) };
    private static final Color[] BOX_GOAL_COLORS = { new Color(120, 210, 120), new Color(80, 165, 85),  new Color(55, 120, 60) };

    private static final BasicStroke OUTLINE_STROKE = new BasicStroke(1.2f);
    private static final Composite FADE_COMPOSITE =
            AlphaComposite.getInstance(AlphaComposite.SRC_OVER, WALL_FADE_ALPHA);

    // 폴리곤 좌표 버퍼 (윗면 / 왼쪽 면 / 오른쪽 면). 바닥 그리기도 윗면 버퍼를 같이 쓴다.
    private final int[] topX = new int[4], topY = new int[4];
    private final int[] leftX = new int[4], leftY = new int[4];
    private final int[] rightX = new int[4], rightY = new int[4];

    // ---------------------------------------------------------------
    // 이동 애니메이션 상태 (EDT에서만 접근한다)
    // GameView는 항상 "이동이 끝난 최종 상태"를 돌려주므로, 렌더러는 출발 칸 -> 도착 칸을
    // 진행률만큼 보간해서 그린다. 상태를 GamePanel이 넣어 주고(start/setProgress/clear),
    // 시간 흐름(Timer)은 GamePanel이 관리한다.
    // ---------------------------------------------------------------
    private boolean moveActive = false;
    private float moveEase = 0f;                       // ease-out이 적용된 진행률 (0~1)
    private int pFromX, pFromY, pToX, pToY;            // 플레이어 출발/도착 칸
    private int pKeyX, pKeyY;                          // 플레이어를 그릴 차례의 칸 (출발/도착 중 앞쪽)
    private boolean boxMoves = false;                  // 박스도 함께 미끄러지는지
    private int bFromX, bFromY, bToX, bToY;            // 박스 출발/도착 칸
    private int bKeyX, bKeyY;                          // 박스를 그릴 차례의 칸 (출발/도착 중 앞쪽)

    /**
     * 이동 애니메이션을 시작한다. 진행률은 0에서 시작한다.
     * 이미 진행 중이던 애니메이션은 덮어쓴다. (GamePanel이 먼저 clearMoveAnimation으로 마무리한다)
     *
     * @param boxMoves true면 박스 (bFrom -> bTo)가 플레이어와 같은 방향으로 함께 미끄러진다
     */
    public void startMoveAnimation(int playerFromX, int playerFromY, int playerToX, int playerToY,
                                   boolean boxMoves, int boxFromX, int boxFromY, int boxToX, int boxToY) {
        this.pFromX = playerFromX; this.pFromY = playerFromY;
        this.pToX = playerToX;     this.pToY = playerToY;
        this.boxMoves = boxMoves;
        this.bFromX = boxFromX;    this.bFromY = boxFromY;
        this.bToX = boxToX;        this.bToY = boxToY;

        // 깊이 정렬 기준 칸: 출발/도착 중 x+y가 큰(화면 앞쪽) 칸. 뒤쪽 칸 기준으로 그리면 앞쪽 벽에 파묻힌다.
        if (pFromX + pFromY >= pToX + pToY) { pKeyX = pFromX; pKeyY = pFromY; } else { pKeyX = pToX; pKeyY = pToY; }
        if (bFromX + bFromY >= bToX + bToY) { bKeyX = bFromX; bKeyY = bFromY; } else { bKeyX = bToX; bKeyY = bToY; }

        this.moveEase = 0f;
        this.moveActive = true;
    }

    /** 진행률 t(0.0~1.0, 선형)를 받아 ease-out을 적용해 저장한다. */
    public void setMoveProgress(float t) {
        float c = Math.max(0f, Math.min(1f, t));
        moveEase = 1f - (1f - c) * (1f - c);
    }

    /** 애니메이션을 끝내고 최종 위치(GameView 상태)로 즉시 그리게 한다. */
    public void clearMoveAnimation() {
        moveActive = false;
        boxMoves = false;
        moveEase = 0f;
    }

    public boolean hasMoveAnimation() {
        return moveActive;
    }

    private static float lerp(int from, int to, float t) {
        return from + (to - from) * t;
    }

    /** 화면 한가운데에 맵이 오도록 배치해서 전체 장면을 그린다. */
    public void render(Graphics2D g2, GameView view, int panelW, int panelH) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(OUTLINE_STROKE);

        int w = view.getWidth();
        int h = view.getHeight();

        // 맵 중앙 정렬: 맵의 화면상 가로/세로 범위를 계산해 원점을 구한다.
        int originX = panelW / 2 - (w - h) * TILE_W / 4;
        int totalH = (w + h - 2) * TILE_H / 2 + TILE_H + WALL_H;
        int originY = (panelH - totalH) / 2 + WALL_H;

        Point player = view.getPlayerPos();   // 프레임당 1회만 조회
        int playerX = player.x;
        int playerY = player.y;

        // 애니메이션 유효성 검사: GameView가 애니메이션의 도착 상태와 다르면(undo/restart/레벨 교체 등)
        // 오래된 애니메이션이므로 무시하고 현재 상태 그대로 그린다.
        boolean animating = moveActive && playerX == pToX && playerY == pToY;
        boolean boxAnimating = animating && boxMoves && view.hasBox(bToX, bToY);

        // 보간된 격자 좌표(소수) -> 화면 좌표. 프레임당 한 번만 계산한다.
        int movingPlayerSx = 0, movingPlayerSy = 0, movingBoxSx = 0, movingBoxSy = 0;
        boolean movingBoxOnGoal = false;
        if (animating) {
            float fx = lerp(pFromX, pToX, moveEase);
            float fy = lerp(pFromY, pToY, moveEase);
            movingPlayerSx = originX + Math.round((fx - fy) * HW);
            movingPlayerSy = originY + Math.round((fx + fy) * HH);
        }
        if (boxAnimating) {
            float bx = lerp(bFromX, bToX, moveEase);
            float by = lerp(bFromY, bToY, moveEase);
            movingBoxSx = originX + Math.round((bx - by) * HW);
            movingBoxSy = originY + Math.round((bx + by) * HH);
            // 목표 위 색(초록)은 절반 지점에서 출발 칸 -> 도착 칸 기준으로 바뀐다
            movingBoxOnGoal = (moveEase < 0.5f ? view.getTile(bFromX, bFromY) : view.getTile(bToX, bToY)) == TileType.GOAL;
        }

        // 깊이 정렬: x+y가 작은 대각선(뒤쪽)부터 큰 대각선(앞쪽) 순으로 그린다.
        for (int d = 0; d <= w + h - 2; d++) {
            for (int gx = 0; gx < w; gx++) {
                int gy = d - gx;
                if (gy < 0 || gy >= h) {
                    continue;
                }
                TileType tile = view.getTile(gx, gy);
                if (tile == TileType.EMPTY) {
                    continue;
                }
                int sx = screenX(gx, gy, originX);
                int sy = screenY(gx, gy, originY);

                // 같은 칸 안에서의 순서: 바닥 -> 벽/박스 -> 플레이어
                if (tile == TileType.WALL) {
                    boolean faded = coversTarget(view, gx, gy, playerX, playerY);
                    drawBlock(g2, sx, sy, WALL_H, WALL_COLORS, faded);
                    continue;
                }
                drawFloor(g2, sx, sy, tile == TileType.GOAL);
                // 이동 중인 박스의 도착 칸은 여기서 그리지 않고, 아래에서 보간 위치로 따로 그린다
                boolean boxInTransit = boxAnimating && gx == bToX && gy == bToY;
                if (view.hasBox(gx, gy) && !boxInTransit) {
                    drawBlock(g2, sx, sy, BOX_H, tile == TileType.GOAL ? BOX_GOAL_COLORS : BOX_COLORS, false);
                }
                if (!animating && playerX == gx && playerY == gy) {
                    drawPlayer(g2, sx, sy);
                }

                // 이동 중인 오브젝트는 출발/도착 중 앞쪽 칸의 차례에 그린다. (박스 -> 플레이어 순)
                if (boxAnimating && gx == bKeyX && gy == bKeyY) {
                    drawBlock(g2, movingBoxSx, movingBoxSy, BOX_H, movingBoxOnGoal ? BOX_GOAL_COLORS : BOX_COLORS, false);
                }
                if (animating && gx == pKeyX && gy == pKeyY) {
                    drawPlayer(g2, movingPlayerSx, movingPlayerSy);
                }
            }
        }
    }

    /**
     * (wx, wy)의 벽이 플레이어(또는 박스, 목표 칸)를 가리는 위치에 있는지 판단한다.
     * 벽 기준으로 FADE_DX/DY만큼 "뒤쪽"에 대상이 있으면 true.
     */
    private boolean coversTarget(GameView view, int wx, int wy, int playerX, int playerY) {
        for (int i = 0; i < FADE_DX.length; i++) {
            int tx = wx - FADE_DX[i];
            int ty = wy - FADE_DY[i];
            if (tx == playerX && ty == playerY) {
                return true;
            }
            if (FADE_FOR_BOXES && view.hasBox(tx, ty)) {
                return true;
            }
            if (FADE_FOR_GOALS && view.getTile(tx, ty) == TileType.GOAL) {
                return true;
            }
        }
        return false;
    }

    /** 격자 좌표 -> 화면 X (해당 칸 마름모의 맨 위 꼭짓점 기준) */
    private static int screenX(int gx, int gy, int originX) {
        return originX + (gx - gy) * HW;
    }

    /** 격자 좌표 -> 화면 Y (해당 칸 마름모의 맨 위 꼭짓점 기준) */
    private static int screenY(int gx, int gy, int originY) {
        return originY + (gx + gy) * HH;
    }

    /**
     * 격자 좌표 -> 화면 좌표 (반환값은 해당 칸 마름모의 맨 위 꼭짓점).
     * 마우스 클릭 좌표 변환 등 paint 바깥에서 쓰라고 남겨 둔 편의 메서드. 그리는 루프에서는 쓰지 않는다.
     */
    public Point toScreen(int gx, int gy, int originX, int originY) {
        return new Point(screenX(gx, gy, originX), screenY(gx, gy, originY));
    }

    /** 바닥 타일. 목표 칸이면 안쪽에 작은 마름모를 추가로 그린다. */
    private void drawFloor(Graphics2D g2, int sx, int sy, boolean goal) {
        setDiamond(topX, topY, sx, sy);
        g2.setColor(FLOOR_COLOR);
        g2.fillPolygon(topX, topY, 4);
        g2.setColor(FLOOR_LINE);
        g2.drawPolygon(topX, topY, 4);

        if (goal) {
            // 목표 표시: 타일 중심에 절반 크기 마름모
            topX[0] = sx;          topY[0] = sy + HH / 2;
            topX[1] = sx + HW / 2; topY[1] = sy + HH;
            topX[2] = sx;          topY[2] = sy + HH + HH / 2;
            topX[3] = sx - HW / 2; topY[3] = sy + HH;
            g2.setColor(GOAL_COLOR);
            g2.fillPolygon(topX, topY, 4);
        }
    }

    /**
     * 직육면체(벽, 박스) 그리기. colors = {윗면, 왼쪽 면, 오른쪽 면}
     * faded가 true면 WALL_FADE_ALPHA로 반투명하게 그려 뒤쪽 오브젝트가 비치게 한다.
     */
    private void drawBlock(Graphics2D g2, int sx, int sy, int height, Color[] colors, boolean faded) {
        int top = sy - height;

        setDiamond(topX, topY, sx, top);

        leftX[0] = sx - HW; leftY[0] = top + HH;
        leftX[1] = sx;      leftY[1] = top + TILE_H;
        leftX[2] = sx;      leftY[2] = sy + TILE_H;
        leftX[3] = sx - HW; leftY[3] = sy + HH;

        rightX[0] = sx;      rightY[0] = top + TILE_H;
        rightX[1] = sx + HW; rightY[1] = top + HH;
        rightX[2] = sx + HW; rightY[2] = sy + HH;
        rightX[3] = sx;      rightY[3] = sy + TILE_H;

        Composite saved = null;
        if (faded) {
            saved = g2.getComposite();
            g2.setComposite(FADE_COMPOSITE);
        }

        g2.setColor(colors[1]);
        g2.fillPolygon(leftX, leftY, 4);
        g2.setColor(colors[2]);
        g2.fillPolygon(rightX, rightY, 4);
        g2.setColor(colors[0]);
        g2.fillPolygon(topX, topY, 4);

        g2.setColor(OUTLINE_COLOR);
        g2.drawPolygon(topX, topY, 4);
        g2.drawPolygon(leftX, leftY, 4);
        g2.drawPolygon(rightX, rightY, 4);

        if (faded) {
            g2.setComposite(saved);
        }
    }

    /** 플레이어(임시 캐릭터): 그림자 + 몸통 + 머리 */
    private void drawPlayer(Graphics2D g2, int sx, int sy) {
        int cx = sx;
        int cy = sy + HH;   // 타일 중심

        g2.setColor(SHADOW_COLOR);
        g2.fillOval(cx - 13, cy - 6, 26, 12);

        g2.setColor(PLAYER_BODY);
        g2.fillOval(cx - 10, cy - 30, 20, 32);
        g2.setColor(PLAYER_HEAD);
        g2.fillOval(cx - 8, cy - 44, 16, 16);

        g2.setColor(OUTLINE_COLOR);
        g2.drawOval(cx - 10, cy - 30, 20, 32);
        g2.drawOval(cx - 8, cy - 44, 16, 16);
    }

    /** (sx, sy)를 맨 위 꼭짓점으로 하는 타일 크기 마름모 좌표를 배열에 채운다. */
    private static void setDiamond(int[] xs, int[] ys, int sx, int sy) {
        xs[0] = sx;      ys[0] = sy;
        xs[1] = sx + HW; ys[1] = sy + HH;
        xs[2] = sx;      ys[2] = sy + TILE_H;
        xs[3] = sx - HW; ys[3] = sy + HH;
    }
}
