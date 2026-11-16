package game.mock;

import game.model.Direction;
import game.model.GameController;
import game.model.GameView;
import game.model.TileType;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 프론트엔드 개발용 임시 게임 로직.
 * 실제 로직이 완성되면 이 클래스 대신 로직 담당의 구현체를 GamePanel에 넣으면 된다.
 *
 * 맵 문자: # 벽 / 공백 바닥 / . 목표 / $ 박스 / * 목표 위 박스 / @ 플레이어 / + 목표 위 플레이어
 */
public class MockGame implements GameView, GameController {

    private static final String[] LEVEL = {
        "#######",
        "#  .  #",
        "# $   #",
        "#  @  #",
        "#  $  #",
        "#   . #",
        "#######"
    };

    /** undo용 스냅샷 (박스 배치, 플레이어 위치, 이동 횟수) */
    private record Snapshot(boolean[][] boxes, int px, int py, int moves) { }

    private int width;
    private int height;
    private TileType[][] tiles;   // [y][x]
    private boolean[][] boxes;    // [y][x]
    private int px, py;
    private int moves;
    private final Deque<Snapshot> history = new ArrayDeque<>();

    public MockGame() {
        load();
    }

    /** LEVEL 문자열을 읽어 상태를 초기화 */
    private void load() {
        height = LEVEL.length;
        width = 0;
        for (String row : LEVEL) {
            width = Math.max(width, row.length());
        }
        tiles = new TileType[height][width];
        boxes = new boolean[height][width];
        moves = 0;
        history.clear();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                char c = x < LEVEL[y].length() ? LEVEL[y].charAt(x) : ' ';
                switch (c) {
                    case '#' -> tiles[y][x] = TileType.WALL;
                    case '.' -> tiles[y][x] = TileType.GOAL;
                    case '$' -> { tiles[y][x] = TileType.FLOOR; boxes[y][x] = true; }
                    case '*' -> { tiles[y][x] = TileType.GOAL;  boxes[y][x] = true; }
                    case '@' -> { tiles[y][x] = TileType.FLOOR; px = x; py = y; }
                    case '+' -> { tiles[y][x] = TileType.GOAL;  px = x; py = y; }
                    default  -> tiles[y][x] = TileType.FLOOR;
                }
            }
        }
    }

    // ---------------- GameController ----------------

    @Override
    public boolean move(Direction dir) {
        int nx = px + dir.dx;
        int ny = py + dir.dy;

        if (getTile(nx, ny) == TileType.WALL || getTile(nx, ny) == TileType.EMPTY) {
            return false;
        }

        // 이동할 칸에 박스가 있으면 그 너머 칸으로 밀 수 있는지 확인
        boolean pushing = hasBox(nx, ny);
        int bx = nx + dir.dx;
        int by = ny + dir.dy;
        if (pushing) {
            TileType beyond = getTile(bx, by);
            if (beyond == TileType.WALL || beyond == TileType.EMPTY || hasBox(bx, by)) {
                return false;
            }
        }

        history.push(snapshot());
        if (pushing) {
            boxes[ny][nx] = false;
            boxes[by][bx] = true;
        }
        px = nx;
        py = ny;
        moves++;
        return true;
    }

    @Override
    public void undo() {
        if (history.isEmpty()) {
            return;
        }
        Snapshot s = history.pop();
        boxes = s.boxes();
        px = s.px();
        py = s.py();
        moves = s.moves();
    }

    @Override
    public void restart() {
        load();
    }

    private Snapshot snapshot() {
        boolean[][] copy = new boolean[height][];
        for (int y = 0; y < height; y++) {
            copy[y] = boxes[y].clone();
        }
        return new Snapshot(copy, px, py, moves);
    }

    // ---------------- GameView ----------------

    @Override public int getWidth()  { return width; }
    @Override public int getHeight() { return height; }

    @Override
    public TileType getTile(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return TileType.EMPTY;
        }
        return tiles[y][x];
    }

    @Override
    public boolean hasBox(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return false;
        }
        return boxes[y][x];
    }

    @Override public Point getPlayerPos() { return new Point(px, py); }
    @Override public int getMoveCount()   { return moves; }

    @Override
    public boolean isCleared() {
        boolean hasGoal = false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (tiles[y][x] == TileType.GOAL) {
                    hasGoal = true;
                    if (!boxes[y][x]) {
                        return false;
                    }
                }
            }
        }
        return hasGoal;
    }
}