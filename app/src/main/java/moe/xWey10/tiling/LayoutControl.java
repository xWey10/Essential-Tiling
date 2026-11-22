package moe.xWey10.tiling;

import android.net.Uri;
import android.os.Bundle;

/** One in-memory, expiring request. An app/provider restart never replays a layout write. */
final class LayoutControl {
    static final Uri REQUEST = Uri.parse(SettingsProvider.BASE_URI + "/layout/request");
    private long sequence, started;
    private String state = "idle", layout = "", error = "";
    synchronized Bundle request(String json, long now) {
        QsLayout.parse(json);
        expire(now);
        if (state.equals("pending") || state.equals("saving")) throw new IllegalStateException("Сохранение раскладки уже выполняется.");
        sequence = Math.max(System.currentTimeMillis(), sequence + 1); started = now;
        layout = json; error = ""; state = "pending";
        return status(sequence, now);
    }
    synchronized Bundle claim(long now) {
        expire(now); if (!state.equals("pending")) return null;
        state = "saving"; Bundle b = status(sequence, now); b.putString("layout", layout); return b;
    }
    synchronized void complete(long id, String failure, long now) {
        if (id != sequence || !state.equals("saving")) return;
        error = failure == null ? "" : failure;
        if (error.length() > 1000) error = error.substring(0, 1000);
        state = error.isEmpty() ? "saved" : "error"; layout = "";
    }
    synchronized Bundle status(long id, long now) {
        expire(now); Bundle b = new Bundle(); b.putLong("id", sequence);
        b.putString("state", id == sequence ? state : "expired"); b.putString("error", error); return b;
    }
    synchronized boolean busy(long now) { expire(now); return state.equals("pending") || state.equals("saving"); }
    private void expire(long now) {
        if (state.equals("pending") && now - started > 5000) {
            state = "error"; error = "Нет ответа модуля. Перезапустите SystemUI."; layout = "";
        }
        if (state.equals("saving") && now - started > 30_000) {
            state = "error"; error = "Сохранение не подтверждено. Обновите предпросмотр перед повторной попыткой."; layout = "";
        }
    }
}
