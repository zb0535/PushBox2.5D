package game.ui;

import game.model.Direction;
import game.model.GameController;
import game.model.GameView;
import game.render.IsoRenderer;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;

/**
 * 게임 화면 패널: 렌더러 호출 + 키 입력 처리 + 이동 애니메이션 시간 관리 + 간단한 HUD.
 * 로직 클래스는 GameView / GameController 인터페이스로만 접근한다.
 *
 * 이동 애니메이션: 로직은 move() 즉시 최종 상태로 바뀐다. 이 패널이 move() 전후의 GameView 상태를 비교해
 * (플레이어 좌표 변화, 플레이어 앞 칸의 박스가 밀렸는지) 출발 -> 도착 보간을 렌더러에 시킨다.
 * 규칙 판단은 하지 않고 읽기만 한다. 모든 메서드는 EDT에서만 호출한다.
 */
public class GamePanel extends JPanel {

    /** 이동 애니메이션 길이(ms) */
    private static final int MOVE_DURATION_MS = 110;
    /** 애니메이션 타이머 간격(ms), 약 60fps */
    private static final int FRAME_MS = 16;
    private static final long MOVE_DURATION_NANOS = MOVE_DURATION_MS * 1_000_000L;

    private final GameView view;
    private final GameController controller;
    private final IsoRenderer renderer = new IsoRenderer();

    private final Font hudFont = new Font("SansSerif", Font.BOLD, 16);
    private final Font clearFont = new Font("SansSerif", Font.BOLD, 48);

    // paintComponent에서 매번 만들지 않도록 미리 만들어 둔 색
    private static final Color CLEAR_BOX_COLOR = new Color(0, 0, 0, 150);
    private static final Color CLEAR_TEXT_COLOR = new Color(255, 220, 80);

    // 이동 횟수 문구 캐시 (횟수가 바뀔 때만 새로 만든다)
    private int cachedMoveCount = -1;
    private String moveText = "";

    // 이동 애니메이션 타이머 (javax.swing.Timer는 EDT에서 동작한다). 필요할 때만 돌린다.
    private final Timer animTimer = new Timer(FRAME_MS, e -> onAnimationTick());
    private long animStartNanos;

    public GamePanel(GameView view, GameController controller) {
        this.view = view;
        this.controller = controller;

        setPreferredSize(new Dimension(800, 600));
        setBackground(new Color(30, 30, 40));
        setFocusable(true);

        setupKeyBindings();

        // 다른 화면(CardLayout)으로 벗어날 때 진행 중인 애니메이션을 정리한다. (다시 들어올 때 오래된 상태가 남지 않게)
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                cancelAnimation();
            }
        });
    }

    // ---------------------------------------------------------------
    // 이동 애니메이션
    // ---------------------------------------------------------------

    /**
     * 방향 이동을 요청하고, 실제로 좌표가 바뀌었으면 보간 애니메이션을 시작한다.
     * 진행 중인 애니메이션이 있으면 먼저 즉시 완료해 연타가 끊기지 않게 한다.
     */
    private void moveWithAnimation(Direction dir) {
        cancelAnimation();   // 진행 중이면 도착 위치로 스냅 (로직 상태는 이미 도착 상태다)

        Point before = view.getPlayerPos();
        int aheadX = before.x + dir.dx;     // 플레이어 앞 칸 (박스가 있으면 밀리는 칸)
        int aheadY = before.y + dir.dy;
        boolean boxWasAhead = view.hasBox(aheadX, aheadY);

        controller.move(dir);

        // 반환값 대신 실제 좌표 변화로 판단한다: 벽에 막히면 좌표가 그대로라 애니메이션을 실행하지 않는다.
        Point after = view.getPlayerPos();
        if (after.x == before.x && after.y == before.y) {
            return;
        }

        // 박스 밀기: 이동 전 앞 칸에 박스가 있었고, 이동 후 플레이어가 그 칸에 들어갔으며,
        // 박스가 한 칸 너머에 새로 놓여 있는 경우 (읽기만 한다)
        int boxToX = aheadX + dir.dx;
        int boxToY = aheadY + dir.dy;
        boolean pushed = boxWasAhead
                && after.x == aheadX && after.y == aheadY
                && view.hasBox(boxToX, boxToY);

        renderer.startMoveAnimation(before.x, before.y, after.x, after.y, pushed, aheadX, aheadY, boxToX, boxToY);
        animStartNanos = System.nanoTime();
        animTimer.restart();
    }

    /** 애니메이션 타이머 틱: 진행률을 렌더러에 넘기고 다시 그린다. 끝나면 정리한다. */
    private void onAnimationTick() {
        float t = (System.nanoTime() - animStartNanos) / (float) MOVE_DURATION_NANOS;
        if (t >= 1f) {
            cancelAnimation();   // 완료: 타이머 정지 + 최종 위치로 그리기
        } else {
            renderer.setMoveProgress(t);
        }
        repaint();
    }

    /**
     * 진행 중인 이동 애니메이션을 보간 없이 즉시 목표 위치로 스냅하고 타이머를 정리한다.
     * undo / restart / 스테이지 변경 직전에 부른다. 외부(MainFrame 등)에서 레벨을 바꿀 때도 쓸 수 있다.
     * 진행 중이 아니면 아무 일도 하지 않는다. (재생성/재등록 없음)
     */
    public void cancelAnimation() {
        animTimer.stop();
        renderer.clearMoveAnimation();
        repaint();
    }

    /** Key Bindings 등록 (KeyListener보다 포커스 문제가 적다) */
    private void setupKeyBindings() {
        bindMove(Direction.UP,    KeyEvent.VK_UP,    KeyEvent.VK_W);
        bindMove(Direction.DOWN,  KeyEvent.VK_DOWN,  KeyEvent.VK_S);
        bindMove(Direction.LEFT,  KeyEvent.VK_LEFT,  KeyEvent.VK_A);
        bindMove(Direction.RIGHT, KeyEvent.VK_RIGHT, KeyEvent.VK_D);

        // undo / restart는 보간 없이 즉시 스냅: 먼저 진행 중인 애니메이션을 정리한 뒤 로직을 호출한다.
        bindKey(KeyEvent.VK_Z, "undo",    true,  () -> { cancelAnimation(); controller.undo(); });
        bindKey(KeyEvent.VK_R, "restart", false, () -> { cancelAnimation(); controller.restart(); });
    }

    private void bindMove(Direction dir, int... keyCodes) {
        for (int code : keyCodes) {
            bindKey(code, "move_" + dir + "_" + code, true, () -> moveWithAnimation(dir));
        }
    }

    /**
     * @param lockWhenCleared true면 클리어된 뒤에는 이 키를 무시한다.
     *        (클리어 화면으로 넘어가기 전 짧은 시간에 이동/되돌리기가 먹혀 상태가 어긋나는 것을 막는 화면 쪽 처리.
     *         다시 시작(R)은 클리어 후에도 허용한다.)
     */
    private void bindKey(int keyCode, String name, boolean lockWhenCleared, Runnable action) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyCode, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (lockWhenCleared && view.isCleared()) {
                    return;
                }
                action.run();
                repaint();   // 상태가 바뀌었으니 다시 그림
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            renderer.render(g2, view, getWidth(), getHeight());
            drawHud(g2);
        } finally {
            g2.dispose();
        }
    }

    /** 임시 HUD (나중에 HudPanel로 분리 예정) */
    private void drawHud(Graphics2D g2) {
        g2.setFont(hudFont);
        g2.setColor(Color.WHITE);
        int moves = view.getMoveCount();
        if (moves != cachedMoveCount) {
            cachedMoveCount = moves;
            moveText = "이동: " + moves;
        }
        g2.drawString(moveText, 16, 28);
        g2.drawString("방향키/WASD 이동  |  Z 되돌리기  |  R 다시 시작", 16, getHeight() - 16);

        if (view.isCleared()) {
            g2.setFont(clearFont);
            String text = "CLEAR!";
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(text)) / 2;
            int y = getHeight() / 2;
            g2.setColor(CLEAR_BOX_COLOR);
            g2.fillRoundRect(x - 24, y - fm.getAscent(), fm.stringWidth(text) + 48, fm.getHeight() + 8, 24, 24);
            g2.setColor(CLEAR_TEXT_COLOR);
            g2.drawString(text, x, y);
        }
    }
}
