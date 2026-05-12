package edu.hitsz.aircraftwar.network;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import edu.hitsz.aircraftwar.game.Difficulty;

public class SocketMatchClient implements Closeable {

    public interface Listener {
        void onStatus(String message);

        void onMatchStarted(int roomId, int playerId, Difficulty difficulty);

        void onOpponentScoreUpdate(int playerId, int score, long durationSeconds);

        void onOpponentResult(int playerId, int score, long durationSeconds);

        void onMatchResult(
                int winnerId,
                int playerOneScore,
                long playerOneDurationSeconds,
                int playerTwoScore,
                long playerTwoDurationSeconds);

        void onDisconnected(String reason);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object writeLock = new Object();
    private final String host;
    private final int port;
    private final Difficulty difficulty;

    private volatile Listener listener;
    private volatile Socket socket;
    private volatile BufferedReader reader;
    private volatile PrintWriter writer;
    private volatile boolean manuallyClosed;
    private volatile boolean connected;
    private volatile boolean disconnectedNotified;
    private volatile String statusMessage = "";
    private volatile int roomId;
    private volatile int playerId;
    private volatile int lastOpponentPlayerId;
    private volatile int lastOpponentScore;
    private volatile long lastOpponentDurationSeconds;
    private volatile boolean opponentScoreKnown;
    private volatile boolean opponentResultKnown;
    private volatile boolean matchResultKnown;
    private volatile int winnerId;
    private volatile int playerOneScore;
    private volatile long playerOneDurationSeconds;
    private volatile int playerTwoScore;
    private volatile long playerTwoDurationSeconds;

    public SocketMatchClient(String host, int port, Difficulty difficulty) {
        this.host = host;
        this.port = port;
        this.difficulty = difficulty;
    }

    public void connect() {
        manuallyClosed = false;
        disconnectedNotified = false;
        postStatus("\u6B63\u5728\u8FDE\u63A5\u670D\u52A1\u5668\u2026");
        Thread worker = new Thread(this::runConnectionLoop, "socket-match-client");
        worker.start();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        dispatchSnapshot(listener);
    }

    public void sendScore(int score, long durationSeconds) {
        sendLine("SCORE|" + score + "|" + durationSeconds);
    }

    public void sendResult(int score, long durationSeconds) {
        sendLine("RESULT|" + score + "|" + durationSeconds);
    }

    public void disconnect() {
        close();
    }

    @Override
    public void close() {
        Thread worker = new Thread(() -> {
            sendLine("BYE", true);
            manuallyClosed = true;
            closeSocket();
        }, "socket-match-disconnect");
        worker.start();
    }

    private void runConnectionLoop() {
        try {
            Socket connection = new Socket();
            connection.connect(new InetSocketAddress(host, port), 5000);
            socket = connection;
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8)), true);
            connected = true;
            sendLine("JOIN|" + difficulty.name());

            String line;
            while (!manuallyClosed && (line = reader.readLine()) != null) {
                try {
                    handleServerMessage(line);
                } catch (RuntimeException exception) {
                    notifyDisconnected("\u8054\u673A\u6570\u636E\u5904\u7406\u5931\u8D25\uff1A" + exception.getMessage());
                    return;
                }
            }
            if (!manuallyClosed) {
                notifyDisconnected("\u670D\u52A1\u5668\u5DF2\u5173\u95ED\u8FDE\u63A5");
            }
        } catch (SocketException exception) {
            if (!manuallyClosed) {
                notifyDisconnected("\u7F51\u7EDC\u8FDE\u63A5\u5DF2\u65AD\u5F00");
            }
        } catch (IOException exception) {
            if (!manuallyClosed) {
                notifyDisconnected("\u65E0\u6CD5\u8FDE\u63A5\u5230\u670D\u52A1\u5668\uff1A" + exception.getMessage());
            }
        } finally {
            closeSocket();
        }
    }

    private void handleServerMessage(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length == 0) {
            return;
        }
        switch (parts[0]) {
            case "CONNECTED":
                postStatus("\u5DF2\u8FDE\u63A5\u670D\u52A1\u5668");
                return;
            case "WAIT":
                postStatus("\u6B63\u5728\u7B49\u5F85\u53E6\u4E00\u540D\u73A9\u5BB6\u52A0\u5165\u2026");
                return;
            case "START":
                if (parts.length >= 4) {
                    roomId = parseInt(parts[1]);
                    playerId = parseInt(parts[2]);
                    Difficulty matchedDifficulty = parseDifficulty(parts[3]);
                    dispatchListener(listener -> listener.onMatchStarted(roomId, playerId, matchedDifficulty));
                }
                return;
            case "SCORE":
                if (parts.length >= 4) {
                    lastOpponentPlayerId = parseInt(parts[1]);
                    lastOpponentScore = parseInt(parts[2]);
                    lastOpponentDurationSeconds = parseLong(parts[3]);
                    opponentScoreKnown = true;
                    dispatchListener(listener -> listener.onOpponentScoreUpdate(
                            lastOpponentPlayerId,
                            lastOpponentScore,
                            lastOpponentDurationSeconds));
                }
                return;
            case "OPPONENT_RESULT":
                if (parts.length >= 4) {
                    lastOpponentPlayerId = parseInt(parts[1]);
                    lastOpponentScore = parseInt(parts[2]);
                    lastOpponentDurationSeconds = parseLong(parts[3]);
                    opponentScoreKnown = true;
                    opponentResultKnown = true;
                    dispatchListener(listener -> listener.onOpponentResult(
                            lastOpponentPlayerId,
                            lastOpponentScore,
                            lastOpponentDurationSeconds));
                }
                return;
            case "MATCH_RESULT":
                if (parts.length >= 6) {
                    winnerId = parseInt(parts[1]);
                    playerOneScore = parseInt(parts[2]);
                    playerOneDurationSeconds = parseLong(parts[3]);
                    playerTwoScore = parseInt(parts[4]);
                    playerTwoDurationSeconds = parseLong(parts[5]);
                    matchResultKnown = true;
                    dispatchListener(listener -> listener.onMatchResult(
                            winnerId,
                            playerOneScore,
                            playerOneDurationSeconds,
                            playerTwoScore,
                            playerTwoDurationSeconds));
                }
                return;
            default:
                postStatus(line);
        }
    }

    private void dispatchSnapshot(Listener listener) {
        if (listener == null || !connected) {
            return;
        }
        mainHandler.post(() -> {
            if (this.listener != listener) {
                return;
            }
            if (!statusMessage.isEmpty()) {
                listener.onStatus(statusMessage);
            }
            if (opponentScoreKnown) {
                listener.onOpponentScoreUpdate(lastOpponentPlayerId, lastOpponentScore, lastOpponentDurationSeconds);
            }
            if (opponentResultKnown) {
                listener.onOpponentResult(lastOpponentPlayerId, lastOpponentScore, lastOpponentDurationSeconds);
            }
            if (matchResultKnown) {
                listener.onMatchResult(
                        winnerId,
                        playerOneScore,
                        playerOneDurationSeconds,
                        playerTwoScore,
                        playerTwoDurationSeconds);
            }
        });
    }

    private void dispatchListener(ListenerAction action) {
        Listener currentListener = listener;
        if (currentListener == null) {
            return;
        }
        mainHandler.post(() -> {
            if (listener == currentListener) {
                action.invoke(currentListener);
            }
        });
    }

    private void postStatus(String message) {
        statusMessage = message == null ? "" : message;
        dispatchListener(listener -> listener.onStatus(statusMessage));
    }

    private void notifyDisconnected(String reason) {
        if (disconnectedNotified) {
            return;
        }
        disconnectedNotified = true;
        dispatchListener(listener -> listener.onDisconnected(reason));
    }

    private void sendLine(String message) {
        sendLine(message, false);
    }

    private void sendLine(String message, boolean forceSend) {
        synchronized (writeLock) {
            if (writer != null && (forceSend || !manuallyClosed)) {
                writer.println(message);
            }
        }
    }

    private void closeSocket() {
        connected = false;
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {
                // Best effort.
            }
            reader = null;
        }
        if (writer != null) {
            writer.close();
            writer = null;
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Best effort.
            }
            socket = null;
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

    private interface ListenerAction {
        void invoke(Listener listener);
    }
}
