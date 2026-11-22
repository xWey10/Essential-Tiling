package moe.xWey10.tiling;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import java.util.UUID;

/** Provider-side, one-shot requests tied to the currently registered SystemUI process. */
final class RestartControl {
    static final Uri REQUEST = Uri.parse(SettingsProvider.BASE_URI + "/restart/request");
    static final int PROTOCOL = 1;
    static final long REQUEST_TTL_MS = 5000;
    private final SharedPreferences registration;
    private String requestId, requestSession, state;
    private long requestedAt;

    RestartControl(SharedPreferences registration) { this.registration = registration; }

    synchronized Bundle register(String session, int protocol, int pid, long now) {
        if (session == null || session.isEmpty() || session.length() > 96 || pid <= 0 || protocol != PROTOCOL) {
            throw new IllegalArgumentException("Invalid SystemUI registration");
        }
        expire(now);
        String previous = session();
        if (!registration.edit().putString("session", session).putInt("protocol", protocol)
                .putInt("pid", pid).commit()) throw new IllegalStateException("Registration not saved");
        if (!session.equals(previous) && previous.equals(requestSession)
                && ("pending".equals(state) || "accepted".equals(state))) state = "restarted";
        return snapshot();
    }

    synchronized Bundle request(long now) {
        expire(now);
        if (session().isEmpty() || registration.getInt("protocol", 0) != PROTOCOL) {
            Bundle result = snapshot(); result.putString("state", "unavailable"); return result;
        }
        if (!"pending".equals(state) && !"accepted".equals(state)) {
            requestId = UUID.randomUUID().toString(); requestSession = session();
            requestedAt = now; state = "pending";
        }
        return snapshot();
    }

    synchronized Bundle claim(String session, int pid, long now) {
        expire(now);
        if (!"pending".equals(state) || !session().equals(session) || !session.equals(requestSession)
                || pid != registration.getInt("pid", -1)) return null;
        // Consume before returning to SystemUI; a new process can never replay this request.
        state = "accepted";
        return snapshot();
    }

    synchronized Bundle status(String id, long now) {
        expire(now);
        Bundle result = snapshot();
        if (id == null || !id.equals(requestId)) result.putString("state", "unknown");
        return result;
    }

    private void expire(long now) {
        long age = now - requestedAt;
        if (("pending".equals(state) && (age < 0 || age > REQUEST_TTL_MS))
                || ("accepted".equals(state) && (age < 0 || age > 20000))) state = "expired";
    }
    private String session() { return registration.getString("session", ""); }
    private Bundle snapshot() {
        Bundle b = new Bundle(); b.putString("id", requestId); b.putString("state", state == null ? "idle" : state);
        b.putString("session", session()); b.putInt("pid", registration.getInt("pid", -1));
        b.putInt("protocol", registration.getInt("protocol", 0)); return b;
    }
}
