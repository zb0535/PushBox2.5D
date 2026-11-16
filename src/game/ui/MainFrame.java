package game.ui;

import game.mock.MockGame;
import game.model.GameController;
import game.model.GameView;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 실행 진입점. CardLayout으로 화면(시작 / 레벨 선택 / 게임 / 클리어)을 전환한다.
 * 일시정지, 설정 화면은 나중에 카드로 추가하면 된다.
 */
public class MainFrame extends JFrame {

    // 화면 이름 (CardLayout 키)
    private static final String SCREEN_MENU = "MENU";
    private static final String SCREEN_LEVELS = "LEVELS";
    private static final String SCREEN_GAME = "GAME";
    private static final String SCREEN_CLEAR = "CLEAR";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel root = new JPanel(cardLayout);

    // 로직과의 접점: 읽기용(view) / 조작용(controller)
    private final GameView view;
    private final GameController controller;

    private final GamePanel gamePanel;
    private final LevelSelectPanel levelSelectPanel;
    private final JLabel clearInfoLabel = new JLabel("", SwingConstants.CENTER);
    private final Timer clearWatcher;

    private String currentScreen = SCREEN_MENU;
    private boolean clearPending = false;
    private Timer clearDelay;   // 클리어 후 화면 전환 대기 타이머 (취소할 수 있게 필드로 보관)
    private int currentLevelIndex = 0;   // 지금 플레이 중인(마지막으로 고른) 레벨 번호, 0부터

    public MainFrame() {
        super("Sokoban 2.5D");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // 실제 로직이 완성되면 이 부분(MockGame 생성)만 로직 담당의 구현체로 교체
        MockGame game = new MockGame();
        this.view = game;
        this.controller = game;
        this.gamePanel = new GamePanel(view, controller);
        this.levelSelectPanel = new LevelSelectPanel(
                createPlaceholderLevels(), this::startLevel, () -> showScreen(SCREEN_MENU));

        root.add(new MenuPanel(this::openLevelSelect, this::openSettings, this::exitGame), SCREEN_MENU);
        root.add(levelSelectPanel, SCREEN_LEVELS);
        root.add(gamePanel, SCREEN_GAME);
        root.add(createClearScreen(), SCREEN_CLEAR);
        add(root);

        installEscapeKey();

        // 게임 화면일 때만 클리어 여부를 확인한다 (임시 방식, 아래 checkClear 참고)
        clearWatcher = new Timer(100, e -> checkClear());

        pack();
        setLocationRelativeTo(null);
        showScreen(SCREEN_MENU);
    }

    // ---------------------------------------------------------------
    // 화면 전환
    // ---------------------------------------------------------------

    private void showScreen(String name) {
        currentScreen = name;
        cardLayout.show(root, name);

        if (SCREEN_GAME.equals(name)) {
            clearWatcher.start();
            // 화면이 보인 뒤에 포커스를 줘야 키 입력이 GamePanel로 들어간다
            SwingUtilities.invokeLater(gamePanel::requestFocusInWindow);
        } else {
            clearWatcher.stop();
            cancelClearDelay();   // ESC 등으로 게임 화면을 벗어나면 대기 중인 클리어 전환도 취소
            if (SCREEN_LEVELS.equals(name)) {
                // 마지막으로 고른 레벨에 포커스를 두면 키보드로 바로 이어서 고를 수 있다
                SwingUtilities.invokeLater(() -> levelSelectPanel.focusLevel(currentLevelIndex));
            }
        }
    }

    private void openLevelSelect() {
        showScreen(SCREEN_LEVELS);
    }

    /**
     * 레벨 선택 화면에서 레벨을 골랐을 때.
     * TODO: 지금은 GameController에 "N번 레벨 불러오기"가 없어서 번호를 기억만 하고 MockGame을 처음부터 시작한다.
     *       로직 담당과 레벨 로딩 방식(loadLevel(int) 또는 레벨별 GameView/GameController 팩토리)이 정해지면
     *       이 메서드 안에서 index로 해당 레벨을 불러오도록 바꾼다.
     */
    private void startLevel(int index) {
        currentLevelIndex = index;
        startGame();
    }

    /**
     * 레벨 목록 (임시). MockGame은 맵이 하나뿐이라 모든 레벨이 같은 맵이다.
     * 레벨 목록 형식과 읽는 주체가 정해지면 실제 목록으로 교체한다.
     */
    private static List<LevelSelectPanel.LevelInfo> createPlaceholderLevels() {
        List<LevelSelectPanel.LevelInfo> levels = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            levels.add(new LevelSelectPanel.LevelInfo("임시 맵"));
        }
        return levels;
    }

    /** 현재 레벨을 처음부터 시작하고 게임 화면으로 간다. (클리어 화면의 "다시 하기"도 이걸 쓴다) */
    private void startGame() {
        controller.restart();
        cancelClearDelay();
        showScreen(SCREEN_GAME);
    }

    /** 대기 중인 클리어 전환 타이머를 멈추고 클리어 감지 상태를 초기화한다. */
    private void cancelClearDelay() {
        if (clearDelay != null) {
            clearDelay.stop();
            clearDelay = null;
        }
        clearPending = false;
    }

    private void openSettings() {
        // TODO: 설정 화면(볼륨, 키 안내 등)이 정해지면 카드로 교체
        JOptionPane.showMessageDialog(this, "설정 화면은 준비 중입니다.", "설정", JOptionPane.INFORMATION_MESSAGE);
    }

    private void exitGame() {
        dispose();
        System.exit(0);
    }

    // ---------------------------------------------------------------
    // 클리어 처리
    // ---------------------------------------------------------------

    /**
     * 임시 방식: 100ms마다 view.isCleared()를 확인해 클리어 화면으로 넘긴다.
     * 로직 쪽에서 클리어 이벤트(콜백)를 주기로 합의되면 폴링을 지우고 이벤트로 바꾼다.
     */
    private void checkClear() {
        if (!SCREEN_GAME.equals(currentScreen) || clearPending || !view.isCleared()) {
            return;
        }
        clearPending = true;

        // 마지막 박스가 들어가는 모습을 잠깐 보여준 뒤 전환
        clearDelay = new Timer(700, e -> {
            clearDelay = null;
            if (!SCREEN_GAME.equals(currentScreen)) {
                return;
            }
            // 대기 중에 R(다시 시작) 등으로 클리어가 풀렸다면 전환하지 않고 감지를 다시 시작한다
            if (!view.isCleared()) {
                clearPending = false;
                return;
            }
            clearInfoLabel.setText("이동 횟수: " + view.getMoveCount());
            showScreen(SCREEN_CLEAR);
        });
        clearDelay.setRepeats(false);
        clearDelay.start();
    }

    /** 클리어 화면 (임시): 결과 문구와 버튼 2개. 다음 레벨 버튼은 레벨 목록이 생기면 추가. */
    private JPanel createClearScreen() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(30, 34, 44));
        panel.setPreferredSize(new Dimension(640, 480));

        JLabel title = new JLabel("CLEAR!", SwingConstants.CENTER);
        title.setFont(new Font("Dialog", Font.BOLD, 48));
        title.setForeground(new Color(120, 220, 120));

        clearInfoLabel.setFont(new Font("Dialog", Font.PLAIN, 20));
        clearInfoLabel.setForeground(Color.WHITE);

        JButton retry = createSimpleButton("다시 하기", this::startGame);
        JButton toMenu = createSimpleButton("메인 메뉴", () -> showScreen(SCREEN_MENU));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.insets = new Insets(8, 0, 8, 0);
        panel.add(title, c);
        panel.add(clearInfoLabel, c);
        panel.add(retry, c);
        panel.add(toMenu, c);
        return panel;
    }

    private JButton createSimpleButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(new Font("Dialog", Font.BOLD, 18));
        button.setPreferredSize(new Dimension(200, 44));
        button.setFocusPainted(false);
        button.addActionListener(e -> action.run());
        return button;
    }

    // ---------------------------------------------------------------
    // 입력
    // ---------------------------------------------------------------

    /** ESC: 게임 화면에서 메인 메뉴로 돌아간다. (일시정지 메뉴가 생기면 그쪽으로 연결) */
    private void installEscapeKey() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "backToMenu");
        getRootPane().getActionMap().put("backToMenu", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 게임 중, 레벨 선택 중 모두 ESC는 시작 화면으로
                if (SCREEN_GAME.equals(currentScreen) || SCREEN_LEVELS.equals(currentScreen)) {
                    showScreen(SCREEN_MENU);
                }
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
