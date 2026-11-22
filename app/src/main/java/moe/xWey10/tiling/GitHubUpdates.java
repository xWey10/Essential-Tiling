package moe.xWey10.tiling;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;

/** Checks stable releases while the settings app is open. State is shared across recreations. */
final class GitHubUpdates {
  private static final String REPOSITORY = "xWey10/Essential-Tiling";
  private static final String LATEST = "https://api.github.com/repos/" + REPOSITORY + "/releases/latest";
  private static final long DAY = TimeUnit.DAYS.toMillis(1);
  private static final long HOUR = TimeUnit.HOURS.toMillis(1);
  private static final int RESPONSE_LIMIT = 256 * 1024;
  private static final Pattern VERSION = Pattern.compile("[vV]?([0-9]{1,9})\\.([0-9]{1,9})\\.([0-9]{1,9})");
  private static GitHubUpdates instance;

  private final SharedPreferences preferences;
  private final Handler main = new Handler(Looper.getMainLooper());
  private Runnable observer;
  private boolean checking;
  private boolean manualFailure;

  static GitHubUpdates get(Context context) {
    if (instance == null) instance = new GitHubUpdates(context.getApplicationContext());
    return instance;
  }

  private GitHubUpdates(Context context) {
    preferences = context.getSharedPreferences("github_updates", Context.MODE_PRIVATE);
  }

  void observe(Runnable listener) {
    observer = listener;
    listener.run();
  }

  void forget(Runnable listener) {
    if (observer == listener) observer = null;
  }

  boolean isAutomatic() {
    return preferences.getBoolean("automatic", true);
  }

  void setAutomatic(boolean enabled) {
    preferences.edit().putBoolean("automatic", enabled).apply();
    if (enabled) check(false);
  }

  boolean isChecking() {
    return checking;
  }

  boolean hasManualFailure() {
    return manualFailure;
  }

  String availableTag() {
    String latest = preferences.getString("release_tag", "");
    return isNewer(latest, BuildConfig.VERSION_NAME) ? latest : null;
  }

  boolean hasChecked() {
    return preferences.contains("release_tag");
  }

  void check(boolean manual) {
    long now = System.currentTimeMillis();
    long last = preferences.getLong("last_attempt", 0);
    long delay = preferences.getBoolean("last_success", false) ? DAY : HOUR;
    if (checking || (!manual && (!isAutomatic() || !due(now, last, delay)))) return;
    checking = true;
    manualFailure = false;
    preferences.edit().putLong("last_attempt", now).putBoolean("last_success", false).apply();
    notifyObserver();
    Thread worker = new Thread(() -> {
      String tag = null;
      try {
        tag = fetchLatest();
      } catch (IOException | JSONException ignored) {
        // Retain the last successful tag so an intermittent failure does not hide updates.
      }
      String result = tag;
      main.post(() -> finish(manual, result));
    }, "EssentialTiling-update-check");
    worker.setDaemon(true);
    worker.start();
  }

  private void finish(boolean manual, String tag) {
    checking = false;
    manualFailure = manual && tag == null;
    if (tag != null) {
      preferences.edit().putString("release_tag", tag).putBoolean("last_success", true).apply();
    }
    notifyObserver();
  }

  private void notifyObserver() {
    if (observer != null) observer.run();
  }

  static boolean due(long now, long last, long delay) {
    return last <= 0 || now < last || now - last >= delay;
  }

  static String releaseUrl(String tag) {
    return "https://github.com/" + REPOSITORY + "/releases/tags/" + tag;
  }

  static boolean isNewer(String candidate, String installed) {
    Matcher latest = VERSION.matcher(candidate);
    Matcher current = VERSION.matcher(installed);
    if (!latest.matches() || !current.matches()) return false;
    for (int part = 1; part <= 3; part++) {
      int difference = Integer.compare(Integer.parseInt(latest.group(part)),
          Integer.parseInt(current.group(part)));
      if (difference != 0) return difference > 0;
    }
    return false;
  }

  private static String fetchLatest() throws IOException, JSONException {
    HttpsURLConnection request = (HttpsURLConnection) URI.create(LATEST).toURL().openConnection();
    try {
      request.setConnectTimeout(6000);
      request.setReadTimeout(6000);
      request.setInstanceFollowRedirects(false);
      request.setRequestProperty("Accept", "application/vnd.github+json");
      request.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
      request.setRequestProperty("User-Agent", "Essential-Tiling");
      int status = request.getResponseCode();
      if (status != HttpsURLConnection.HTTP_OK) throw new IOException("GitHub HTTP " + status);
      try (InputStreamReader stream =
          new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8)) {
        StringBuilder json = new StringBuilder();
        char[] buffer = new char[4096];
        for (int read; (read = stream.read(buffer)) != -1; ) {
          if (json.length() + read > RESPONSE_LIMIT) throw new IOException("Release response too large");
          json.append(buffer, 0, read);
        }
        JSONObject release = new JSONObject(json.toString());
        String tag = release.getString("tag_name");
        if (!VERSION.matcher(tag).matches() || release.optBoolean("prerelease")
            || release.optBoolean("draft")) {
          throw new IOException("Expected a stable release tag");
        }
        return tag;
      }
    } finally {
      request.disconnect();
    }
  }
}
