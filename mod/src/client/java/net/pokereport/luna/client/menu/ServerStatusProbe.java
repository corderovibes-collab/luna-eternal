package net.pokereport.luna.client.menu;

import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.ServerMetadata;
import net.minecraft.util.Util;

import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicReference;

public final class ServerStatusProbe {
    private static final long REFRESH_INTERVAL_MS = 10000L;
    private static final long RETRY_INTERVAL_MS = 5000L;
    private static final long TIMEOUT_MS = 8000L;
    private static final long PING_GOOD_MS = 150L;
    private static final int FAILURE_THRESHOLD = 3;

    private static final ServerStatusProbe INSTANCE = new ServerStatusProbe();

    public enum Status {
        CONNECTING,
        ONLINE_GOOD,
        ONLINE_OK,
        OFFLINE
    }

    public record ProbeResult(long id, boolean online, int playersOnline, int playersMax, long latencyMs) {}

    private final MultiplayerServerListPinger pinger = new MultiplayerServerListPinger();
    private final AtomicReference<ProbeResult> pending = new AtomicReference<>();
    private long probeSequence;
    private boolean everResolved;
    private boolean online;
    private boolean probeInFlight;
    private int playersOnline;
    private int playersMax;
    private long latencyMs = -1L;
    private int consecutiveFailures;
    private volatile long probeStartMillis;

    private ServerStatusProbe() {}

    public static ServerStatusProbe getInstance() {
        return INSTANCE;
    }

    public void tick(String address, long timeMillis) {
        this.pinger.tick();
        this.drainPending();

        if (this.probeInFlight && timeMillis - this.probeStartMillis >= TIMEOUT_MS) {
            this.probeSequence++;
            this.probeInFlight = false;
            this.consecutiveFailures++;
            if (this.consecutiveFailures >= FAILURE_THRESHOLD) {
                this.everResolved = true;
                this.online = false;
            }
        }

        long interval = (this.online && this.consecutiveFailures == 0) ? REFRESH_INTERVAL_MS : RETRY_INTERVAL_MS;
        if (!this.probeInFlight && timeMillis - this.probeStartMillis >= interval) {
            this.startProbe(address, timeMillis);
        }
    }

    private void drainPending() {
        ProbeResult res = this.pending.getAndSet(null);
        if (res == null || res.id() != this.probeSequence) {
            return;
        }

        if (res.online()) {
            this.playersOnline = res.playersOnline();
            this.playersMax = res.playersMax();
            if (res.latencyMs() >= 0) {
                this.latencyMs = res.latencyMs();
            }
            this.online = true;
            this.everResolved = true;
            this.probeInFlight = false;
            this.consecutiveFailures = 0;
        } else {
            this.online = false;
            this.everResolved = true;
            this.probeInFlight = false;
        }
    }

    public Status status() {
        if (!this.everResolved) {
            return Status.CONNECTING;
        }
        if (!this.online) {
            return Status.OFFLINE;
        }
        if (this.latencyMs < 0 || this.latencyMs <= PING_GOOD_MS) {
            return Status.ONLINE_GOOD;
        }
        return Status.ONLINE_OK;
    }

    public int playersOnline() {
        return this.playersOnline;
    }

    public int playersMax() {
        return this.playersMax;
    }

    private void startProbe(String address, long time) {
        long seq = ++this.probeSequence;
        this.probeStartMillis = time;
        this.probeInFlight = true;

        ServerInfo serverInfo = new ServerInfo("pokereport-status-probe", address, ServerInfo.ServerType.OTHER);
        Util.getIoWorkerExecutor().execute(() -> {
            this.probeStartMillis = Util.getMeasuringTimeMs();
            try {
                this.pinger.add(serverInfo, () -> this.publishStatus(seq, serverInfo), () -> this.publishLatency(seq, serverInfo));
            } catch (UnknownHostException e) {
                this.publishFailure(seq);
            }
        });
    }

    private void publishStatus(long seq, ServerInfo serverInfo) {
        ServerMetadata.Players players = serverInfo.players;
        int onlineCount = players != null ? players.online() : 0;
        int maxCount = players != null ? players.max() : 0;
        this.pending.set(new ProbeResult(seq, true, onlineCount, maxCount, -1L));
    }

    private void publishLatency(long seq, ServerInfo serverInfo) {
        ServerMetadata.Players players = serverInfo.players;
        int onlineCount = players != null ? players.online() : 0;
        int maxCount = players != null ? players.max() : 0;
        this.pending.set(new ProbeResult(seq, true, onlineCount, maxCount, serverInfo.ping));
    }

    private void publishFailure(long seq) {
        this.pending.set(new ProbeResult(seq, false, 0, 0, -1L));
    }
}
