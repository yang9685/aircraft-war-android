package edu.hitsz.aircraftwar.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

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
            System.out.println("Match server started on port " + port);
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

        public synchronized void join(PlayerConnection player, String playerName, Difficulty difficulty) {
            player.playerName = playerName;
            player.difficulty = difficulty;

            PlayerConnection waiting = waitingPlayers.get(difficulty);
            if (waiting == null || !waiting.isConnected()) {
                waitingPlayers.put(difficulty, player);
                player.send("WAITING");
                return;
            }

            waitingPlayers.remove(difficulty);
            MatchRoom room = new MatchRoom(waiting, player);
            waiting.room = room;
            player.room = room;
            waiting.send("MATCHED\t" + encode(playerName) + "\t" + difficulty.name());
            player.send("MATCHED\t" + encode(waiting.playerName) + "\t" + difficulty.name());
        }

        public synchronized void updateScore(PlayerConnection player, int score) {
            if (player.room != null) {
                player.currentScore = score;
                player.room.forwardScore(player, score);
            }
        }

        public synchronized void markDead(PlayerConnection player, int score, long durationSeconds) {
            if (player.room == null) {
                return;
            }
            player.currentScore = score;
            player.currentDurationSeconds = durationSeconds;
            player.dead = true;
            player.room.forwardDeath(player, score, durationSeconds);
            if (player.room.isFinished()) {
                player.room.finish();
            }
        }

        public synchronized void handleDisconnect(PlayerConnection player) {
            if (player.difficulty != null && waitingPlayers.get(player.difficulty) == player) {
                waitingPlayers.remove(player.difficulty);
            }
            if (player.room != null) {
                MatchRoom room = player.room;
                player.room = null;
                room.handleDisconnect(player);
            }
        }
    }

    private static final class MatchRoom {
        private final PlayerConnection first;
        private final PlayerConnection second;
        private boolean finished;

        private MatchRoom(PlayerConnection first, PlayerConnection second) {
            this.first = first;
            this.second = second;
        }

        public void forwardScore(PlayerConnection source, int score) {
            PlayerConnection target = other(source);
            if (target != null) {
                target.send("OPPONENT_SCORE\t" + score);
            }
        }

        public void forwardDeath(PlayerConnection source, int score, long durationSeconds) {
            PlayerConnection target = other(source);
            if (target != null) {
                target.send("OPPONENT_DEAD\t" + score + "\t" + durationSeconds);
            }
        }

        public boolean isFinished() {
            return first.dead && second.dead;
        }

        public void finish() {
            if (finished) {
                return;
            }
            finished = true;
            first.send(buildMatchEndMessage(first, second));
            second.send(buildMatchEndMessage(second, first));
            first.room = null;
            second.room = null;
        }

        public void handleDisconnect(PlayerConnection leaver) {
            if (finished) {
                return;
            }
            finished = true;
            PlayerConnection target = other(leaver);
            if (target != null && target.isConnected()) {
                target.send("OPPONENT_LEFT\t" + encode("\u5BF9\u624B\u5DF2\u79BB\u5F00\u5BF9\u5C40"));
                target.closeSilently();
                target.room = null;
            }
        }

        private String buildMatchEndMessage(PlayerConnection local, PlayerConnection opponent) {
            return "MATCH_END\t"
                    + local.currentScore
                    + "\t"
                    + local.currentDurationSeconds
                    + "\t"
                    + opponent.currentScore
                    + "\t"
                    + opponent.currentDurationSeconds;
        }

        private PlayerConnection other(PlayerConnection source) {
            return source == first ? second : first;
        }
    }

    private static final class PlayerConnection implements Runnable {
        private final Socket socket;
        private final MatchCoordinator coordinator;
        private BufferedReader reader;
        private PrintWriter writer;
        private boolean connected = true;
        private MatchRoom room;
        private String playerName = "";
        private Difficulty difficulty;
        private int currentScore;
        private long currentDurationSeconds;
        private boolean dead;

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
            String[] parts = line.split("\t", -1);
            if (parts.length == 0) {
                return;
            }
            switch (parts[0]) {
                case "JOIN":
                    if (parts.length >= 3) {
                        coordinator.join(this, decode(parts[1]), parseDifficulty(parts[2]));
                    }
                    return;
                case "SCORE":
                    if (parts.length >= 2) {
                        coordinator.updateScore(this, parseInt(parts[1]));
                    }
                    return;
                case "DEAD":
                    if (parts.length >= 3) {
                        coordinator.markDead(this, parseInt(parts[1]), parseLong(parts[2]));
                    }
                    return;
                case "LEAVE":
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

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
