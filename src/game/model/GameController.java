package game.model;

/**
 * 프론트엔드가 로직에 "명령"을 내리는 인터페이스.
 * 규칙 판단(벽 충돌, 박스 밀기 가능 여부)은 전부 구현체 쪽 책임이다.
 */
public interface GameController {

    /**
     * 플레이어를 해당 방향으로 한 칸 이동시킨다. (박스가 있으면 함께 민다)
     * @return 이동에 성공하면 true, 벽 등에 막혀 실패하면 false
     */
    boolean move(Direction dir);

    /** 직전 이동을 되돌린다. 되돌릴 기록이 없으면 아무 일도 하지 않는다. */
    void undo();

    /** 현재 레벨을 처음 상태로 되돌린다. */
    void restart();
}