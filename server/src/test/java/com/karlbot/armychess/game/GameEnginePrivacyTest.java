package com.karlbot.armychess.game;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameEnginePrivacyTest {
    @Test
    void masksEveryOpponentIdentityAndUsesOpaqueIds() {
        GameEngine engine = startedGame();

        GameSnapshot snapshot = engine.snapshotFor(0);

        assertThat(snapshot.pieces()).filteredOn(piece -> piece.owner() != 0)
                .allSatisfy(piece -> {
                    assertThat(piece.visibleType()).isNull();
                    assertThat(piece.revealed()).isFalse();
                    assertThat(piece.id().toLowerCase(Locale.ROOT))
                            .doesNotContain(Arrays.stream(PieceType.values())
                                    .map(type -> type.name().toLowerCase(Locale.ROOT)).toArray(String[]::new));
                });
    }

    @Test
    void publicEventSchemaCannotRevealBattlePieceIdentities() throws Exception {
        String json = new ObjectMapper().writeValueAsString(PublicGameEvent.clash(0, new Position(8, 8)));

        assertThat(json).contains("CLASH_OCCURRED");
        assertThat(json.toLowerCase(Locale.ROOT))
                .doesNotContain("attacker", "defender", "winnerpiece", "loserpiece", "piecetype");
        assertThat(PublicGameEvent.class.getRecordComponents())
                .extracting(component -> component.getName().toLowerCase(Locale.ROOT))
                .doesNotContain("attacker", "defender", "winnerpiece", "loserpiece", "piecetype");
    }

    @Test
    void rejectsInvalidLayoutBeforeMutatingBoard() {
        GameEngine engine = new GameEngine();

        assertThatThrownBy(() -> engine.submitLayout(0, TestLayouts.valid(0).subList(0, 24)))
                .isInstanceOf(GameRuleException.class)
                .hasMessageContaining("25");
        assertThat(engine.snapshotFor(0).pieces()).isEmpty();
    }

    @Test
    void enforcesFlagBombAndMinePlacementRules() {
        assertLayoutCode(swappedTypes(TestLayouts.valid(0), PieceType.FLAG,
                placement -> !BoardRules.isHeadquarters(placement.position())), "FLAG_REQUIRES_HQ");
        assertLayoutCode(swappedTypes(TestLayouts.valid(0), PieceType.BOMB,
                placement -> BoardRules.isFirstRow(0, placement.position())), "BOMB_FORBIDDEN_ON_FRONT");
        assertLayoutCode(swappedTypes(TestLayouts.valid(0), PieceType.LANDMINE,
                placement -> !BoardRules.isLastTwoRows(0, placement.position())), "LANDMINE_REQUIRES_BACK_ROWS");
    }

    @Test
    void rotatesTurnsSouthWestNorthEast() {
        GameEngine engine = startedGame();
        for (int player = 0; player < 4; player++) {
            assertThat(engine.snapshotFor(player).currentTurn()).isEqualTo(player);
            makeAnyLegalMove(engine, player);
        }
        assertThat(engine.snapshotFor(0).currentTurn()).isEqualTo(0);
    }

    @Test
    void serverDeadlineSkipsTimedOutTurnsAndEliminatesAfterFiveStrikes() {
        GameEngine engine = startedGame();

        for (int timeout = 0; timeout < 17; timeout++) {
            GameSnapshot before = engine.snapshotFor(0);
            assertThat(before.turnDeadlineEpochMs()).isNotNull();
            engine.expireTurn(before.turnDeadlineEpochMs()).orElseThrow();
        }

        GameSnapshot snapshot = engine.snapshotFor(0);
        assertThat(snapshot.alive().get(0)).isFalse();
        assertThat(snapshot.timeoutCounts().get(0)).isEqualTo(GameEngine.MAX_TIMEOUTS);
        assertThat(snapshot.phase()).isEqualTo("PLAYING");
    }

    @Test
    void allFourPlayersCanVoteToResetFinishedRoomForRematch() {
        GameEngine engine = startedGame();
        engine.surrender(0);
        engine.surrender(2);

        GameSnapshot finished = engine.snapshotFor(1);
        assertThat(finished.phase()).isEqualTo("FINISHED");
        assertThat(finished.winnerTeam()).isEqualTo("EAST_WEST");

        for (int player = 0; player < 4; player++) engine.requestRematch(player);

        GameSnapshot reset = engine.snapshotFor(0);
        assertThat(reset.phase()).isEqualTo("LAYOUT");
        assertThat(reset.pieces()).isEmpty();
        assertThat(reset.alive()).containsExactly(true, true, true, true);
        assertThat(reset.timeoutCounts()).containsExactly(0, 0, 0, 0);
        assertThat(reset.winnerTeam()).isNull();
        assertThat(reset.rematchVotes()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void marshalDeathRevealsItsFlagWithoutRevealingOtherPieces() throws Exception {
        GameEngine engine = new GameEngine();
        var boardField = GameEngine.class.getDeclaredField("board");
        boardField.setAccessible(true);
        Map<Position, PieceState> board = (Map<Position, PieceState>) boardField.get(engine);
        Position attackerPosition = new Position(8, 10);
        Position defenderPosition = new Position(8, 8);
        Position flagPosition = new Position(0, 7);
        Position mobilePosition = new Position(1, 6);
        board.put(attackerPosition, new PieceState("attacker", PieceType.BOMB, 0, attackerPosition));
        board.put(defenderPosition, new PieceState("marshal", PieceType.MARSHAL, 1, defenderPosition));
        board.put(flagPosition, new PieceState("flag", PieceType.FLAG, 1, flagPosition));
        board.put(mobilePosition, new PieceState("hidden", PieceType.CAPTAIN, 1, mobilePosition));
        var phaseField = GameEngine.class.getDeclaredField("phase");
        phaseField.setAccessible(true);
        phaseField.set(engine, GameEngine.Phase.PLAYING);

        engine.move(0, "attacker", defenderPosition);

        PieceView flag = engine.snapshotFor(2).pieces().stream()
                .filter(piece -> piece.id().equals("flag"))
                .findFirst().orElseThrow();
        assertThat(flag.visibleType()).isEqualTo(PieceType.FLAG);
        assertThat(flag.revealed()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void flagCapturePublishesTheDefeatedPlayersReason() throws Exception {
        GameEngine engine = new GameEngine();
        var boardField = GameEngine.class.getDeclaredField("board");
        boardField.setAccessible(true);
        Map<Position, PieceState> board = (Map<Position, PieceState>) boardField.get(engine);
        board.put(new Position(8, 10), new PieceState("attacker", PieceType.CAPTAIN, 0, new Position(8, 10)));
        board.put(new Position(8, 8), new PieceState("flag", PieceType.FLAG, 1, new Position(8, 8)));
        board.put(new Position(8, 5), new PieceState("north-mobile", PieceType.CAPTAIN, 2, new Position(8, 5)));
        board.put(new Position(11, 8), new PieceState("east-mobile", PieceType.CAPTAIN, 3, new Position(11, 8)));
        setPlaying(engine);

        List<PublicGameEvent> events = engine.move(0, "attacker", new Position(8, 8));

        assertThat(events).anySatisfy(event -> {
            assertThat(event.type()).isEqualTo("PLAYER_ELIMINATED");
            assertThat(event.actor()).isEqualTo(1);
            assertThat(event.reason().toString()).isEqualTo("FLAG_LOST");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void noLegalMovePublishesTheDefeatedPlayersReason() throws Exception {
        GameEngine engine = new GameEngine();
        var boardField = GameEngine.class.getDeclaredField("board");
        boardField.setAccessible(true);
        Map<Position, PieceState> board = (Map<Position, PieceState>) boardField.get(engine);
        board.put(new Position(8, 10), new PieceState("south-mobile", PieceType.CAPTAIN, 0, new Position(8, 10)));
        board.put(new Position(0, 7), new PieceState("west-flag", PieceType.FLAG, 1, new Position(0, 7)));
        board.put(new Position(8, 5), new PieceState("north-mobile", PieceType.CAPTAIN, 2, new Position(8, 5)));
        setPlaying(engine);

        List<PublicGameEvent> events = engine.move(0, "south-mobile", new Position(8, 8));

        assertThat(events).anySatisfy(event -> {
            assertThat(event.type()).isEqualTo("PLAYER_ELIMINATED");
            assertThat(event.actor()).isEqualTo(1);
            assertThat(event.reason().toString()).isEqualTo("NO_LEGAL_MOVE");
        });
        assertThat(engine.snapshotFor(0).alive().get(1)).isFalse();
    }

    private static void assertLayoutCode(List<PiecePlacement> layout, String expectedCode) {
        GameEngine engine = new GameEngine();
        assertThatThrownBy(() -> engine.submitLayout(0, layout))
                .isInstanceOfSatisfying(GameRuleException.class,
                        error -> assertThat(error.code()).isEqualTo(expectedCode));
    }

    private static List<PiecePlacement> swappedTypes(List<PiecePlacement> source, PieceType restricted,
                                                      java.util.function.Predicate<PiecePlacement> illegalTarget) {
        List<PiecePlacement> layout = new ArrayList<>(source);
        int restrictedIndex = -1;
        int targetIndex = -1;
        for (int index = 0; index < layout.size(); index++) {
            if (layout.get(index).type() == restricted && restrictedIndex < 0) restrictedIndex = index;
            if (layout.get(index).type() != restricted && illegalTarget.test(layout.get(index)) && targetIndex < 0) {
                targetIndex = index;
            }
        }
        PiecePlacement piece = layout.get(restrictedIndex);
        PiecePlacement target = layout.get(targetIndex);
        layout.set(restrictedIndex, new PiecePlacement(target.type(), piece.position()));
        layout.set(targetIndex, new PiecePlacement(piece.type(), target.position()));
        return layout;
    }

    private static void makeAnyLegalMove(GameEngine engine, int player) {
        for (PieceView piece : engine.snapshotFor(player).pieces()) {
            if (piece.owner() != player) continue;
            for (int y = 0; y < BoardRules.SIZE; y++) {
                for (int x = 0; x < BoardRules.SIZE; x++) {
                    try {
                        engine.move(player, piece.id(), new Position(x, y));
                        return;
                    } catch (GameRuleException ignored) {
                        // Try the next destination until a legal move is found.
                    }
                }
            }
        }
        throw new AssertionError("No legal move found for player " + player);
    }

    private static void setPlaying(GameEngine engine) throws Exception {
        var phaseField = GameEngine.class.getDeclaredField("phase");
        phaseField.setAccessible(true);
        phaseField.set(engine, GameEngine.Phase.PLAYING);
    }

    private static GameEngine startedGame() {
        GameEngine engine = new GameEngine();
        for (int player = 0; player < 4; player++) engine.submitLayout(player, TestLayouts.valid(player));
        return engine;
    }
}
