package edu.hitsz.aircraftwar.network;

import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import edu.hitsz.aircraftwar.game.Difficulty;

public class SocketMatchClient {

    public interface Listener {
        void onConnecting();

        void onWaitingForOpponent(Difficulty difficulty);

        void onMatched(String opponentName, Difficulty difficulty);

        void onOpponentStateChanged(String opponentName, int score, boolean defeated, long durationSeconds);

        void onMatchFinished(int localScore, long localDurationSeconds, int opponentScore, long opponentDurationSeconds);

        void onDisconnected(String reason);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object writeLock = new Object();
    private final String host;
    private final int port;
    private final String playerName;
    private final Difficulty difficulty;

    private volatile Listener listener;
    private volatile Socket socket;
    private volatile BufferedReader reader;
    private volatile PrintWriter writer;
    private volatile boolean manuallyClosed;
    private volatile boolean connected;
    private volatile boolean matched;
    private volatile boolean disconnectedNotified;
    private volatile boolean opponentDead;
    private volatile boolean matchFinished;
    private volatile String opponentName = "";
    private volatile int opponentScore;
    private volatile long opponentDurationSeconds;
    private volatile int localFinalScore;
    private volatile long localFinalDurationSeconds;
    private volatile int opponentFinalScore;
    private volatile long opponentFinalDurationSeconds;

    public SocketMatchClient(String host, int port, String playerName, Difficulty difficulty) {
        this.host = host;
        this.port = port;
        this.playerName = playerName;
        this.difficulty = difficulty;
    }

    public void connect() {
        manuallyClosed = false;
        disconnectedNotified = false;
        dispatchListener(Listener::onConnecting);
        Thread worker = new Thread(this::runConnectionLoop, "socket-match-client");
        worker.start();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        dispatchSnapshot(listener);
    }

    public void sendScore(int score) {
        sendLine("SCORE\t" + score);
    }

    public void sendDeath(int score, long durationSeconds) {
        localFinalScore = score;
        localFinalDurationSeconds = durationSeconds;
        sendLine("DEAD\t" + score + "\t" + durationSeconds);
    }

    public void disconnect() {
        manuallyClosed = true;
        Thread worker = new Thread(() -> {
            sendLine("LEAVE");
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
            sendLine("JOIN\t" + encode(playerName) + "\t" + difficulty.name());

            String line;
            while ((line = reader.readLine()) != null) {
                handleServerMessage(line);
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
        String[] parts = line.split("\t", -1);
        if (parts.length == 0) {
            return;
        }
        switch (parts[0]) {
            case "WAITING":
                dispatchListener(listener -> listener.onWaitingForOpponent(difficulty));
                return;
            case "MATCHED":
                if (parts.length >= 3) {
                    matched = true;
                    opponentName = decode(parts[1]);
                    Difficulty matchedDifficulty = parseDifficulty(parts[2]);
                    dispatchListener(listener -> listener.onMatched(opponentName, matchedDifficulty));
                }
                return;
            case "OPPONENT_SCORE":
                if (parts.length >= 2) {
                    opponentScore = parseInt(parts[1]);
                    dispatchListener(listener -> listener.onOpponentStateChanged(opponentName, opponentScore, opponentDead, opponentDurationSeconds));
                }
                return;
            case "OPPONENT_DEAD":
                if (parts.length >= 3) {
                    opponentScore = parseInt(parts[1]);
                    opponentDurationSeconds = parseLong(parts[2]);
                    opponentDead = true;
                    dispatchListener(listener -> listener.onOpponentStateChanged(opponentName, opponentScore, true, opponentDurationSeconds));
                }
                return;
            case "MATCH_END":
                if (parts.length >= 5) {
                    matchFinished = true;
                    localFinalScore = parseInt(parts[1]);
                    localFinalDurationSeconds = parseLong(parts[2]);
                    opponentFinalScore = parseInt(parts[3]);
                    opponentFinalDurationSeconds = parseLong(parts[4]);
                    opponentScore = opponentFinalScore;
                    opponentDurationSeconds = opponentFinalDurationSeconds;
                    opponentDead = true;
                    dispatchListener(listener -> listener.onMatchFinished(
                            localFinalScore,
                            localFinalDurationSeconds,
                            opponentFinalScore,
                            opponentFinalDurationSeconds));
                }
                return;
            case "OPPONENT_LEFT":
                notifyDisconnected(parts.length >= 2 ? decode(parts[1]) : "\u5BF9\u624B\u5DF2\u79BB\u5F00\u5BF9\u5C40");
                return;
            case "ERROR":
                notifyDisconnected(parts.length >= 2 ? decode(parts[1]) : "\u670D\u52A1\u5668\u62D2\u7EDD\u4E86\u8FDE\u63A5");
                return;
            default:
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
            if (!matched) {
                listener.onWaitingForOpponent(difficulty);
                return;
            }
            listener.onMatched(opponentName, difficulty);
            listener.onOpponentStateChanged(opponentName, opponentScore, opponentDead, opponentDurationSeconds);
            if (matchFinished) {
                listener.onMatchFinished(
                        localFinalScore,
                        localFinalDurationSeconds,
                        opponentFinalScore,
                        opponentFinalDurationSeconds);
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

    private void notifyDisconnected(String reason) {
        if (disconnectedNotified) {
            return;
        }
        disconnectedNotified = true;
        dispatchListener(listener -> listener.onDisconnected(reason));
    }

    private void sendLine(String message) {
        synchronized (writeLock) {
            if (writer != null) {
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

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String decode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private interface ListenerAction {
        void invoke(Listener listener);
    }
}
