package moe.xWey10.tiling;

import android.content.ContentProvider;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;

/** Settings/preview bridge. Restart requests are app-only and consumed only by SystemUI. */
public final class SettingsProvider extends ContentProvider {
    static final String BASE_URI = "content://" + BuildConfig.APPLICATION_ID + ".settings";
    public static final Uri URI = Uri.parse(BASE_URI + "/config");
    public static final String[] COLUMNS = {"enabled", "scale", "gap", "radius", "battery", "revision"};
    private Bundle previewRequest, previewResult;
    private long previewSequence;
    private RestartControl restartControl;
    private final LayoutControl layoutControl = new LayoutControl();
    private final TileControl tileControl = new TileControl();

    @Override public boolean onCreate() { return true; }

    private void checkCaller() {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) return;
        String[] packages = requireContext().getPackageManager().getPackagesForUid(uid);
        if (packages != null && Arrays.asList(packages).contains("com.android.systemui")) return;
        throw new SecurityException("Only SystemUI may read QS settings");
    }

    private void checkApp() {
        if (Binder.getCallingUid() != Process.myUid()) throw new SecurityException("Settings app only");
    }

    private void checkSystemUi() {
        String[] packages = requireContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (packages == null || !Arrays.asList(packages).contains("com.android.systemui")) {
            throw new SecurityException("SystemUI only");
        }
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        checkCaller();
        if (!URI.equals(uri)) throw new IllegalArgumentException("Unknown URI");
        Config c = SettingsStore.read(requireContext());
        MatrixCursor cursor = new MatrixCursor(COLUMNS, 1);
        cursor.addRow(new Object[]{c.enabled ? 1 : 0, c.scalePercent, c.gapDp, c.radiusDp,
                c.battery ? 1 : 0, c.revision});
        return cursor;
    }

    @SuppressLint("ApplySharedPref") // Binder thread: persist the report before acknowledging the caller.
    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (method.startsWith("restart.")) return restartCall(method, extras);
        if (method.startsWith("preview.")) return previewCall(method, extras);
        if (method.startsWith("layout.")) return layoutCall(method, extras);
        if (method.startsWith("tiles.")) return tileCall(method, extras);
        checkCaller();
        if (!"report".equals(method) || extras == null) return null;
        String summary = extras.getString("summary", "");
        if (summary.length() > 2000) summary = summary.substring(0, 2000);
        requireContext().createDeviceProtectedStorageContext()
                .getSharedPreferences("runtime", Context.MODE_PRIVATE).edit()
                .putString("summary", summary)
                .putLong("loadedRevision", extras.getLong("revision", -1))
                .putLong("time", System.currentTimeMillis()).commit();
        return Bundle.EMPTY;
    }
    private Bundle tileCall(String method, Bundle extras) {
        if (method.equals("tiles.request") || method.equals("tiles.status") || method.equals("tiles.read") || method.equals("tiles.cancel")) checkApp(); else checkSystemUi();
        long now = SystemClock.elapsedRealtime();
        switch (method) {
            case "tiles.request":
                if (extras == null) throw new IllegalArgumentException("Missing tile request");
                synchronized (this) {
                    if (layoutControl.busy(now)) throw new IllegalStateException("Дождитесь сохранения раскладки.");
                    Bundle b = tileControl.request(extras, now);
                    requireContext().getContentResolver().notifyChange(TileControl.REQUEST, null); return b;
                }
            case "tiles.claim": return tileControl.claim(now);
            case "tiles.publish": tileControl.page(extras); return Bundle.EMPTY;
            case "tiles.complete": tileControl.complete(extras); return Bundle.EMPTY;
            case "tiles.cancel": tileControl.cancel(extras == null ? 0 : extras.getLong("id")); return Bundle.EMPTY;
            case "tiles.status": return tileControl.status(extras == null ? 0 : extras.getLong("id"), now);
            case "tiles.read":
                if (extras == null) throw new IllegalArgumentException("Missing catalogue page");
                return tileControl.read(extras.getLong("id"), extras.getInt("offset"), now);
            default: throw new IllegalArgumentException("Unknown tile operation");
        }
    }

    private synchronized Bundle restartCall(String method, Bundle extras) {
        boolean app = "restart.request".equals(method) || "restart.status".equals(method);
        if (app) checkApp(); else checkSystemUi();
        if (restartControl == null) restartControl = new RestartControl(requireContext()
                .createDeviceProtectedStorageContext().getSharedPreferences("systemui_control", Context.MODE_PRIVATE));
        long now = SystemClock.elapsedRealtime();
        switch (method) {
            case "restart.register":
                if (extras == null) throw new IllegalArgumentException("Missing registration");
                return restartControl.register(extras.getString("session"), extras.getInt("protocol"), Binder.getCallingPid(), now);
            case "restart.request":
                Bundle request = restartControl.request(now);
                if ("pending".equals(request.getString("state"))) {
                    requireContext().getContentResolver().notifyChange(RestartControl.REQUEST, null);
                }
                return request;
            case "restart.claim":
                return extras == null ? null : restartControl.claim(extras.getString("session", ""), Binder.getCallingPid(), now);
            case "restart.status":
                return restartControl.status(extras == null ? null : extras.getString("id"), now);
            default: throw new IllegalArgumentException("Unknown restart operation");
        }
    }

    private synchronized Bundle previewCall(String method, Bundle extras) {
        if ("preview.request".equals(method)) {
            checkApp();
            if (extras == null) throw new IllegalArgumentException("Missing draft");
            previewRequest = PreviewProtocol.request(PreviewProtocol.config(extras), extras.getInt("page"), extras.getString("layout", ""), extras.getString("focus", ""));
            previewRequest.putBoolean("refreshStates", extras.getBoolean("refreshStates", true));
            previewSequence = Math.max(System.currentTimeMillis(), previewSequence + 1);
            previewRequest.putLong("id", previewSequence);
            previewResult = null;
            File[] old = previewDirectory().listFiles();
            if (old != null) for (File file : old) file.delete();
            requireContext().getContentResolver().notifyChange(PreviewProtocol.REQUEST, null);
            return new Bundle(previewRequest);
        }
        if ("preview.pending".equals(method)) {
            checkSystemUi();
            return previewRequest == null ? null : new Bundle(previewRequest);
        }
        if ("preview.complete".equals(method)) {
            checkSystemUi();
            if (extras == null || previewRequest == null || extras.getLong("id") != previewSequence) return Bundle.EMPTY;
            if (extras.getString("error", "").isEmpty() && !previewFile(previewSequence).isFile()) {
                throw new IllegalStateException("Preview image missing");
            }
            previewResult = new Bundle(extras);
            requireContext().getContentResolver().notifyChange(PreviewProtocol.RESULT, null);
            return Bundle.EMPTY;
        }
        if ("preview.result".equals(method)) {
            checkApp(); return previewResult == null ? null : new Bundle(previewResult);
        }
        if ("preview.source".equals(method)) {
            checkSystemUi();
            requireContext().getContentResolver().notifyChange(PreviewProtocol.SOURCE, null);
            return Bundle.EMPTY;
        }
        throw new IllegalArgumentException("Unknown preview operation");
    }

    private Bundle layoutCall(String method, Bundle extras) {
        if ("layout.request".equals(method) || "layout.status".equals(method)) checkApp(); else checkSystemUi();
        long now = SystemClock.elapsedRealtime();
        switch (method) {
            case "layout.request":
                if (extras == null) throw new IllegalArgumentException("Missing layout");
                synchronized (this) {
                    if (tileControl.changing(now)) throw new IllegalStateException("Дождитесь операции с плитками.");
                    Bundle b = layoutControl.request(extras.getString("layout"), now);
                    requireContext().getContentResolver().notifyChange(LayoutControl.REQUEST, null); return b;
                }
            case "layout.claim": return layoutControl.claim(now);
            case "layout.complete":
                if (extras != null) layoutControl.complete(extras.getLong("id"), extras.getString("error"), now);
                return Bundle.EMPTY;
            case "layout.status": return layoutControl.status(extras == null ? 0 : extras.getLong("id"), now);
            default: throw new IllegalArgumentException("Unknown layout operation");
        }
    }

    private File previewDirectory() {
        File dir = new File(requireContext().getCacheDir(), "native-qs-preview");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Preview cache unavailable");
        return dir;
    }

    private File previewFile(long id) { return new File(previewDirectory(), id + ".png"); }

    @Override public synchronized ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!uri.toString().startsWith(BASE_URI + "/preview/image/")
                || uri.getPathSegments().size() != 3) throw new FileNotFoundException("Unknown image URI");
        long id;
        try { id = Long.parseLong(uri.getLastPathSegment()); }
        catch (NumberFormatException e) { throw new FileNotFoundException("Invalid image ID"); }
        if (previewRequest == null || id != previewSequence) throw new FileNotFoundException("Expired preview");
        if ("w".equals(mode)) {
            checkSystemUi();
            return ParcelFileDescriptor.open(previewFile(id), ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        if (!"r".equals(mode)) throw new FileNotFoundException("Unsupported mode");
        checkApp();
        if (previewResult == null || !previewResult.getString("error", "").isEmpty()) {
            throw new FileNotFoundException("Preview not ready");
        }
        return ParcelFileDescriptor.open(previewFile(id), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) {
        return uri.getPath() != null && uri.getPath().startsWith("/preview/image/")
                ? previewResult != null && PreviewBitmapIO.FORMAT.equals(previewResult.getString("imageFormat")) ? "application/octet-stream" : "image/png"
                : "vnd.android.cursor.item/vnd.essentialtiling.config";
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
