package edu.hitsz.aircraftwar.network;

public final class MultiplayerSessionStore {

    private static SocketMatchClient activeClient;

    private MultiplayerSessionStore() {
    }

    public static synchronized void setActiveClient(SocketMatchClient client) {
        activeClient = client;
    }

    public static synchronized SocketMatchClient getActiveClient() {
        return activeClient;
    }

    public static synchronized void clear() {
        activeClient = null;
    }
}
