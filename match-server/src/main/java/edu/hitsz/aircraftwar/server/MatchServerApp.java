package edu.hitsz.aircraftwar.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class MatchServerApp {

    private final int port;
    private final MatchCoordinator coordinator = new MatchCoordinator();

    public MatchServerApp(int port) {
        this.port = port;
    }

    public static void main(String[] args) throws IOException {
        int port = 9999;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                // Keep default port.
            }
        }
        new MatchServerApp(port).start();
    }

    private void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Aircraft War battle server listening on " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                PlayerConnection playerConnection = new PlayerConnection(socket, coordinator);
                new Thread(playerConnection, "match-server-client").start();
            }
        }
    }

    private enum Difficulty {
        EASY,
        NORMAL,
        HARD
    }

    private static final class MatchCoordinator {
        private final Map<Difficulty, PlayerConnection> waitingPlayers = new EnumMap<>(Difficulty.class);
        private final AtomicInteger roomIds = new AtomicInteger(1);

        public synchronized void join(PlayerConnection player, Difficulty difficulty) {
            player.difficulty = difficulty;
            PlayerConnection waiting = waitingPlayers.get(difficulty);
            if (waiting == null || !waiting.isConnected()) {
                waitingPlayers.put(difficulty, player);
                player.send("WAIT|Waiting for another pilot");
                return;
            }

            waitingPlayers.remove(difficulty);
            BattleRoom room = new BattleRoom(roomIds.getAndIncrement(), waiting, player, difficulty);
            waiting.bindRoom(room, 1);
            player.bindRoom(room, 2);
            waiting.send("START|" + room.id + "|1|" + difficulty.name());
            player.send("START|" + room.id + "|2|" + difficulty.name());
            System.out.println("Room " + room.id + " started");
        }

        public synchronized void removeWaiting(PlayerConnection player) {
            if (player.difficulty != null && waitingPlayers.get(player.difficulty) == player) {
                waitingPlayers.remove(player.difficulty);
            }
        }

        public synchronized void handleDisconnect(PlayerConnection player) {
            removeWaiting(player);
            if (player.room != null) {
                BattleRoom room = player.room;
                player.room = null;
                room.handleDisconnect(player);
            }
        }
    }

    private static final class BattleRoom {
        private final int id;
        private final PlayerConnection playerOne;
        private final PlayerConnection playerTwo;
        private final Difficulty difficulty;
        private Integer playerOneScore;
        private Integer playerTwoScore;
        private Long playerOneDuration;
        private Long playerTwoDuration;
        private boolean finished;

        private BattleRoom(int id, PlayerConnection playerOne, PlayerConnection playerTwo, Difficulty difficulty) {
            this.id = id;
            this.playerOne = playerOne;
            this.playerTwo = playerTwo;
            this.difficulty = difficulty;
        }

        private void forwardScore(int fromPlayerId, int score, long durationSeconds) {
            String message = "SCORE|" + fromPlayerId + "|" + score + "|" + durationSeconds;
            if (fromPlayerId == 1) {
                playerTwo.send(message);
            } else {
                playerOne.send(message);
            }
        }

        private synchronized void submitResult(int playerId, int score, long durationSeconds) {
            if (finished) {
                return;
            }
            if (playerId == 1) {
                playerOneScore = score;
                playerOneDuration = durationSeconds;
                playerTwo.send("OPPONENT_RESULT|1|" + score + "|" + durationSeconds);
            } else {
                playerTwoScore = score;
                playerTwoDuration = durationSeconds;
                playerOne.send("OPPONENT_RESULT|2|" + score + "|" + durationSeconds);
            }

            if (playerOneScore != null && playerTwoScore != null) {
                finished = true;
                int winner = playerOneScore.equals(playerTwoScore) ? 0 : (playerOneScore > playerTwoScore ? 1 : 2);
                String message = "MATCH_RESULT|" + winner
                        + "|" + playerOneScore + "|" + playerOneDuration
                        + "|" + playerTwoScore + "|" + playerTwoDuration;
                playerOne.send(message);
                playerTwo.send(message);
                playerOne.room = null;
                playerTwo.room = null;
                System.out.println("Room " + id + " finished on " + difficulty.name());
            }
        }

        private synchronized void handleDisconnect(PlayerConnection leaver) {
            if (finished) {
                return;
            }
            finished = true;
            PlayerConnection target = leaver == playerOne ? playerTwo : playerOne;
            if (target != null && target.isConnected()) {
                target.closeSilently();
                target.room = null;
            }
        }
    }

    private static final class PlayerConnection implements Runnable {
        private final Socket socket;
        private final MatchCoordinator coordinator;
        private BufferedReader reader;
        private PrintWriter writer;
        private volatile boolean connected = true;
        private BattleRoom room;
        private int playerId;
        private Difficulty difficulty;

        private PlayerConnection(Socket socket, MatchCoordinator coordinator) {
            this.socket = socket;
            this.coordinator = coordinator;
        }

        @Override
        public void run() {
            try {
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
                send("CONNECTED|Aircraft War battle server");
                String line;
                while ((line = reader.readLine()) != null) {
                    handleClientMessage(line);
                }
            } catch (IOException exception) {
                if (connected) {
                    System.out.println("Client connection lost: " + exception.getMessage());
                }
            } finally {
                connected = false;
                coordinator.handleDisconnect(this);
                closeSilently();
            }
        }

        public boolean isConnected() {
            return connected && !socket.isClosed();
        }

        public void bindRoom(BattleRoom room, int playerId) {
            this.room = room;
            this.playerId = playerId;
        }

        public void send(String message) {
            if (writer != null) {
                writer.println(message);
            }
        }

        public void closeSilently() {
            connected = false;
            try {
                socket.close();
            } catch (IOException ignored) {
                // Best effort.
            }
        }

        private void handleClientMessage(String line) {
            String[] parts = line.split("\\|");
            if (parts.length == 0) {
                return;
            }
            switch (parts[0]) {
                case "JOIN":
                    if (parts.length >= 2) {
                        coordinator.join(this, parseDifficulty(parts[1]));
                    }
                    return;
                case "SCORE":
                    if (parts.length >= 3 && room != null) {
                        room.forwardScore(playerId, parseInt(parts[1]), parseLong(parts[2]));
                    }
                    return;
                case "RESULT":
                    if (parts.length >= 3 && room != null) {
                        room.submitResult(playerId, parseInt(parts[1]), parseLong(parts[2]));
                    }
                    return;
                case "BYE":
                    closeSilently();
                    return;
                default:
            }
        }

        private Difficulty parseDifficulty(String raw) {
            try {
                return Difficulty.valueOf(raw);
            } catch (IllegalArgumentException exception) {
                return Difficulty.NORMAL;
            }
        }

        private int parseInt(String raw) {
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException exception) {
                return 0;
            }
        }

        private long parseLong(String raw) {
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException exception) {
                return 0L;
            }
        }
    }
}
