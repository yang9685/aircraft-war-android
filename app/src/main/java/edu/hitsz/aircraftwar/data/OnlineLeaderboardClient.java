package edu.hitsz.aircraftwar.data;

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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import edu.hitsz.aircraftwar.game.Difficulty;

public class OnlineLeaderboardClient {

    public interface LoadCallback {
        void onSuccess(List<ScoreRecord> records);

        void onFailure(String reason);
    }

    public interface UploadCallback {
        void onSuccess();

        void onFailure(String reason);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final String host;
    private final int port;

    public OnlineLeaderboardClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void loadLeaderboard(Difficulty difficulty, LoadCallback callback) {
        executor.execute(() -> {
            try (Socket socket = openSocket();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(
                         new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)),
                         true)) {
                writer.println("GET_LEADERBOARD|" + difficulty.name());
                List<ScoreRecord> records = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("CONNECTED|")) {
                        continue;
                    }
                    if ("LEADERBOARD_END".equals(line)) {
                        break;
                    }
                    if (line.startsWith("LEADERBOARD_ITEM|")) {
                        ScoreRecord record = parseRecord(line);
                        if (record != null) {
                            records.add(record);
                        }
                    } else if (line.startsWith("ERROR|")) {
                        postLoadFailure(callback, line.substring("ERROR|".length()));
                        return;
                    }
                }
                mainHandler.post(() -> callback.onSuccess(records));
            } catch (IOException exception) {
                postLoadFailure(callback, "在线排行榜连接失败：" + exception.getMessage());
            }
        });
    }

    public void uploadScore(ScoreRecord record, UploadCallback callback) {
        executor.execute(() -> {
            try (Socket socket = openSocket();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(
                         new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)),
                         true)) {
                writer.println("UPLOAD_SCORE|"
                        + encode(record.getPlayerName()) + "|"
                        + record.getScore() + "|"
                        + record.getDurationSeconds() + "|"
                        + record.getDifficulty().name() + "|"
                        + record.getCreatedAt());
                String response;
                while ((response = reader.readLine()) != null) {
                    if (response.startsWith("CONNECTED|")) {
                        continue;
                    }
                    if ("UPLOAD_OK".equals(response)) {
                        mainHandler.post(callback::onSuccess);
                        return;
                    }
                    if (response.startsWith("ERROR|")) {
                        postUploadFailure(callback, response.substring("ERROR|".length()));
                        return;
                    }
                }
                postUploadFailure(callback, "在线排行榜返回了未知结果");
            } catch (IOException exception) {
                postUploadFailure(callback, "在线排行榜上传失败：" + exception.getMessage());
            }
        });
    }

    private Socket openSocket() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        return socket;
    }

    private ScoreRecord parseRecord(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 6) {
            return null;
        }
        Difficulty difficulty;
        try {
            difficulty = Difficulty.valueOf(parts[4]);
        } catch (IllegalArgumentException exception) {
            difficulty = Difficulty.NORMAL;
        }
        try {
            return new ScoreRecord(
                    decode(parts[1]),
                    Integer.parseInt(parts[2]),
                    Long.parseLong(parts[3]),
                    difficulty,
                    Long.parseLong(parts[5]));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void postLoadFailure(LoadCallback callback, String reason) {
        mainHandler.post(() -> callback.onFailure(reason));
    }

    private void postUploadFailure(UploadCallback callback, String reason) {
        mainHandler.post(() -> callback.onFailure(reason));
    }

    private String encode(String raw) {
        return raw == null ? "" : raw.replace("|", "%7C").replace("\n", " ");
    }

    private String decode(String raw) {
        return raw == null ? "" : raw.replace("%7C", "|");
    }
}
