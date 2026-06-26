package com.codex.mosaichome;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int[] COLORS = {
            Color.rgb(0, 132, 205), Color.rgb(0, 158, 146), Color.rgb(221, 73, 73),
            Color.rgb(132, 91, 178), Color.rgb(235, 126, 32), Color.rgb(47, 65, 85),
            Color.rgb(35, 155, 86), Color.rgb(183, 62, 119), Color.rgb(238, 194, 62),
            Color.rgb(72, 143, 224)
    };
    private static final String[] CATEGORY_ORDER = {
            "Google TV", "For You", "Continue Watching", "Android TV", "Live TV / IPTV",
            "Entertainment", "TV Tools", "Music", "Games", "Settings", "Communication",
            "Productivity", "Media", "Browser", "Tools", "Shopping", "System", "Other"
    };
    private static final String[] PRESETS = {
            "Google TV style", "Android TV standard", "Entertainment first", "TV leanback",
            "Monitor dashboard", "Work focus", "Minimal essentials", "Gaming", "Media studio",
            "Family clean", "Travel mode", "System admin"
    };
    private static final String[] THEMES = {
            "Metro", "Cinema", "Ocean", "High contrast", "Warm monitor", "Night TV"
    };
    private static final List<MainActivity> LIVE = new ArrayList<>();
    private static Map<String, Integer> latestBadges = new HashMap<>();

    private FrameLayout root;
    private TileBoard board;
    private SharedPreferences prefs;
    private final ArrayList<AppEntry> apps = new ArrayList<>();
    private final ArrayList<Tile> tiles = new ArrayList<>();
    private final HashSet<String> hidden = new HashSet<>();
    private final HashSet<String> defaultHidden = new HashSet<>();
    private boolean hiddenUnlocked;
    private int selected = 0;

    public static void updateBadges(Map<String, Integer> badges) {
        latestBadges = badges;
        for (MainActivity activity : new ArrayList<>(LIVE)) {
            activity.runOnUiThread(() -> activity.board.invalidate());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(9, 11, 16));
        getWindow().setNavigationBarColor(Color.rgb(9, 11, 16));
        prefs = getSharedPreferences("mosaic", MODE_PRIVATE);
        root = new FrameLayout(this);
        board = new TileBoard(this);
        root.addView(board, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LIVE.add(this);
        if (apps.isEmpty()) reload();
        else board.invalidate();
    }

    @Override
    protected void onPause() {
        LIVE.remove(this);
        super.onPause();
    }

    private void reload() {
        loadHidden();
        loadApps();
        seedDefaultHiddenApps();
        loadTiles();
        board.invalidate();
    }

    private void loadHidden() {
        hidden.clear();
        defaultHidden.clear();
        String raw = prefs.getString("hidden", "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) hidden.add(array.getString(i));
        } catch (JSONException ignored) {
        }
        String defaults = prefs.getString("defaultHidden", "[]");
        try {
            JSONArray array = new JSONArray(defaults);
            for (int i = 0; i < array.length(); i++) defaultHidden.add(array.getString(i));
        } catch (JSONException ignored) {
        }
    }

    private void saveHidden() {
        JSONArray array = new JSONArray();
        for (String item : hidden) array.put(item);
        JSONArray defaults = new JSONArray();
        for (String item : defaultHidden) defaults.put(item);
        prefs.edit()
                .putString("hidden", array.toString())
                .putString("defaultHidden", defaults.toString())
                .putBoolean("defaultHiddenSeeded", true)
                .apply();
    }

    private void loadApps() {
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = getPackageManager().queryIntentActivities(intent, 0);
        Map<String, Integer> usage = readUsage();
        Collator collator = Collator.getInstance(Locale.getDefault());
        apps.clear();
        for (ResolveInfo info : resolved) {
            AppEntry app = new AppEntry();
            app.label = info.loadLabel(getPackageManager()).toString();
            app.packageName = info.activityInfo.packageName;
            app.className = info.activityInfo.name;
            app.usage = usage.getOrDefault(app.packageName, 0);
            app.category = categorize(app);
            app.icon = info.loadIcon(getPackageManager());
            app.hidden = hidden.contains(app.key());
            apps.add(app);
        }
        Collections.sort(apps, (a, b) -> {
            int group = Integer.compare(categoryRank(a.category), categoryRank(b.category));
            if (group != 0) return group;
            int byUsage = Integer.compare(b.usage, a.usage);
            return byUsage != 0 ? byUsage : collator.compare(a.label, b.label);
        });
    }

    private void seedDefaultHiddenApps() {
        if (prefs.getBoolean("defaultHiddenSeeded", false)) return;
        for (AppEntry app : apps) {
            String hay = (app.label + " " + app.packageName).toLowerCase(Locale.US);
            boolean quietSystem = "System".equals(app.category) || has(hay, "migration", "assistant", "setup", "carrier", "companion");
            boolean duplicateCommunication = has(hay, "messages", "bericht", "gmail") && countVisible("Communication") > 1;
            if (quietSystem || duplicateCommunication) {
                hidden.add(app.key());
                defaultHidden.add(app.key());
                app.hidden = true;
            }
        }
        saveHidden();
    }

    private String categorize(AppEntry app) {
        String hay = (app.label + " " + app.packageName).toLowerCase(Locale.US);
        if (has(hay, "tivimate", "iptv", "m3u", "kpnandroidtv", "live tv", "livetv", "channels", "zapping")) return "Live TV / IPTV";
        if (has(hay, "google tv", "googletv", "tv launcher", "com.google.android.apps.tv")) return "Google TV";
        if (has(hay, "netflix.ninja", "youtube.tv", "youtube", "prime", "disney", "plex", "kodi", "hulu", "stream")) return "Android TV";
        if (has(hay, "sendfilestotv", "send files", "appteka", "downloader", "leanback", "androidtv", "launcherx")) return "TV Tools";
        if (has(hay, "netflix", "video", "tv")) return "Entertainment";
        if (has(hay, "spotify", "music", "audio", "radio", "podcast", "yt music")) return "Music";
        if (has(hay, "game", "xbox", "play games", "steam", "geforce")) return "Games";
        if (has(hay, "settings", "instelling", "setup", "config", "migration", "device", "admin")) return "Settings";
        if (has(hay, "phone", "bericht", "message", "mail", "gmail", "outlook", "telegram", "whatsapp", "signal")) return "Communication";
        if (has(hay, "docs", "drive", "calendar", "agenda", "office", "keep", "notion", "sheet", "work")) return "Productivity";
        if (has(hay, "camera", "foto", "photo", "gallery", "media", "vlc", "player")) return "Media";
        if (has(hay, "chrome", "browser", "firefox", "edge", "internet")) return "Browser";
        if (has(hay, "files", "bestanden", "clock", "klok", "calculator", "tools", "utility")) return "Tools";
        if (has(hay, "store", "shop", "amazon", "market", "play store")) return "Shopping";
        if (app.packageName.startsWith("com.android.") || app.packageName.startsWith("com.google.android.")) return "System";
        return "Other";
    }

    private boolean has(String hay, String... needles) {
        for (String needle : needles) if (hay.contains(needle)) return true;
        return false;
    }

    private int categoryRank(String category) {
        for (int i = 0; i < CATEGORY_ORDER.length; i++) if (CATEGORY_ORDER[i].equals(category)) return i;
        return CATEGORY_ORDER.length;
    }

    private void loadTiles() {
        tiles.clear();
        String raw = prefs.getString("tiles", "");
        if (!raw.isEmpty()) {
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) tiles.add(Tile.fromJson(array.getJSONObject(i)));
            } catch (JSONException ignored) {
                tiles.clear();
            }
        }
        if (tiles.isEmpty()) applyPreset(prefs.getString("preset", "Google TV style"), false);
        syncInstalledState();
    }

    private void applyPreset(String preset, boolean persist) {
        tiles.clear();
        prefs.edit().putString("preset", preset).apply();

        if ("Google TV style".equals(preset)) {
            addAction("Search", "search", 2, 1, 0, Color.rgb(0, 168, 168));
            addAction("For you", "group:For You", 2, 1, 0, Color.rgb(183, 62, 119));
            addAction("Profile", "profile", 1, 1, 0, Color.rgb(132, 91, 178));
            addAction("Settings", "group:Settings", 1, 1, 0, Color.rgb(94, 110, 130));
            addAction("Continue watching", "live:continue", 3, 1, 0, Color.rgb(72, 143, 224));
            addAction("Spotlight", "group:Spotlight", 2, 1, 0, Color.rgb(221, 73, 73));
            addAction("Top picks", "group:Android TV", 2, 1, 0, Color.rgb(235, 126, 32));
            addAction("Live", "group:Live TV / IPTV", 1, 1, 0, Color.rgb(221, 73, 73));
            addAction("Apps", "drawer", 1, 1, 0, Color.rgb(238, 194, 62));
            addSpotlightApps(0, 6);
            addApps("Android TV", 0, 10);
            addApps("Live TV / IPTV", 0, 6);
            addApps("TV Tools", 1, 8);
            addGroups(new String[]{"Android TV", "Live TV / IPTV", "Entertainment", "TV Tools", "Settings", "System"}, 1);
        } else if ("Android TV standard".equals(preset)) {
            addBaseTiles();
            addAction("Continue", "live:continue", 2, 1, 0, Color.rgb(72, 143, 224));
            addAction("Spotlight", "group:Spotlight", 2, 1, 0, Color.rgb(221, 73, 73));
            addSpotlightApps(0, 6);
            addApps("Android TV", 0, 12);
            addApps("Live TV / IPTV", 0, 8);
            addApps("TV Tools", 0, 8);
            addGroups(new String[]{"Android TV", "Live TV / IPTV", "TV Tools", "Settings", "System"}, 1);
        } else if ("TV leanback".equals(preset)) {
            addGroups(new String[]{"Entertainment", "TV", "Music", "Games", "Settings"}, 0);
            addGroups(new String[]{"Android TV", "Live TV / IPTV", "TV Tools"}, 0);
            addApps("Entertainment", 0, 10);
            addApps("Android TV", 0, 10);
        } else if ("Monitor dashboard".equals(preset)) {
            addBaseTiles();
            addGroups(CATEGORY_ORDER, 0);
            addApps("Productivity", 1, 12);
            addApps("Communication", 1, 8);
        } else if ("Work focus".equals(preset)) {
            addBaseTiles();
            addApps("Productivity", 0, 12);
            addApps("Communication", 0, 8);
            addGroups(new String[]{"Settings", "Browser", "Tools"}, 1);
        } else if ("Minimal essentials".equals(preset)) {
            addBaseTiles();
            addTopUsed(0, 10);
            addGroups(new String[]{"Settings", "Tools"}, 1);
        } else if ("Gaming".equals(preset)) {
            addBaseTiles();
            addApps("Games", 0, 18);
            addGroups(new String[]{"Entertainment", "Music", "Settings"}, 1);
        } else if ("Media studio".equals(preset)) {
            addBaseTiles();
            addApps("Media", 0, 12);
            addApps("Music", 0, 8);
            addGroups(new String[]{"Entertainment", "Browser", "Settings"}, 1);
        } else if ("Family clean".equals(preset)) {
            addBaseTiles();
            addApps("Entertainment", 0, 8);
            addApps("Games", 0, 8);
            addGroups(new String[]{"Settings"}, 1);
        } else if ("Travel mode".equals(preset)) {
            addBaseTiles();
            addApps("Tools", 0, 8);
            addApps("Communication", 0, 8);
            addApps("Browser", 0, 4);
            addGroups(new String[]{"Settings", "Productivity"}, 1);
        } else if ("System admin".equals(preset)) {
            addBaseTiles();
            addApps("Settings", 0, 20);
            addApps("Tools", 0, 12);
            addGroups(new String[]{"System", "Browser"}, 1);
        } else {
            addBaseTiles();
            addApps("Entertainment", 0, 14);
            addApps("Android TV", 0, 12);
            addApps("Live TV / IPTV", 0, 8);
            addApps("TV", 0, 8);
            addApps("Music", 0, 6);
            addGroups(new String[]{"Settings", "Games", "Media", "Communication", "Productivity", "Tools"}, 1);
        }
        if (tiles.size() < 12) addTopUsed(0, 24);
        syncInstalledState();
        if (persist) saveTiles();
        board.invalidate();
    }

    private void addBaseTiles() {
        addAction("Search", "search", 2, 1, 0, Color.rgb(0, 168, 168));
        addAction("Apps", "drawer", 1, 1, 0, Color.rgb(238, 194, 62));
        addAction("Presets", "presets", 1, 1, 0, Color.rgb(132, 91, 178));
        addAction("Hide apps", "hide", 2, 1, 0, Color.rgb(47, 65, 85));
        addAction("Settings", "group:Settings", 1, 1, 0, Color.rgb(94, 110, 130));
        addAction("Themes", "themes", 1, 1, 0, Color.rgb(0, 158, 146));
        addAction("Backup", "backup", 1, 1, 0, Color.rgb(72, 143, 224));
        addAction("Live info", "live", 2, 1, 0, Color.rgb(183, 62, 119));
        addAction("Security", "security", 1, 1, 0, Color.rgb(221, 73, 73));
        addAction("Install TV apps", "install_apps", 2, 1, 0, Color.rgb(35, 155, 86));
    }

    private void addGroups(String[] groups, int page) {
        for (String group : groups) {
            if (countVisible(group) == 0) continue;
            addAction(group, "group:" + group, group.length() > 9 ? 2 : 1, 1, page, colorFor(group));
        }
    }

    private void addApps(String category, int page, int limit) {
        int count = 0;
        for (AppEntry app : apps) {
            if (app.hidden || !category.equals(app.category)) continue;
            if (containsTile(app) && isSpotlightApp(app)) continue;
            Tile tile = appTile(app);
            tile.page = page;
            tile.w = shouldBeWide(app) ? 2 : 1;
            tile.h = shouldBeTall(app) ? 2 : 1;
            tiles.add(tile);
            if (++count >= limit) return;
        }
    }

    private void addSpotlightApps(int page, int limit) {
        int count = 0;
        for (AppEntry app : spotlightApps()) {
            if (containsTile(app)) continue;
            Tile tile = appTile(app);
            tile.page = page;
            tile.w = 2;
            tile.h = shouldBeTall(app) ? 2 : 1;
            tile.spotlight = true;
            tiles.add(tile);
            if (++count >= limit) return;
        }
    }

    private void addTopUsed(int page, int limit) {
        ArrayList<AppEntry> visible = new ArrayList<>();
        for (AppEntry app : apps) if (!app.hidden) visible.add(app);
        Collections.sort(visible, (a, b) -> Integer.compare(b.usage, a.usage));
        int count = 0;
        for (AppEntry app : visible) {
            if (containsTile(app)) continue;
            Tile tile = appTile(app);
            tile.page = page;
            tiles.add(tile);
            if (++count >= limit) return;
        }
    }

    private boolean containsTile(AppEntry app) {
        for (Tile tile : tiles) if (app.packageName.equals(tile.packageName) && app.className.equals(tile.className)) return true;
        return false;
    }

    private boolean shouldBeWide(AppEntry app) {
        return isSpotlightApp(app) || "Entertainment".equals(app.category) || "Productivity".equals(app.category) || app.usage > 6;
    }

    private boolean shouldBeTall(AppEntry app) {
        return isSpotlightApp(app) || "TV".equals(app.category) || "Media".equals(app.category);
    }

    private ArrayList<AppEntry> spotlightApps() {
        ArrayList<AppEntry> result = new ArrayList<>();
        for (AppEntry app : apps) if (!app.hidden && isSpotlightApp(app)) result.add(app);
        Collections.sort(result, (a, b) -> Integer.compare(spotlightScore(b), spotlightScore(a)));
        return result;
    }

    private boolean isSpotlightApp(AppEntry app) {
        return spotlightScore(app) >= 70;
    }

    private int spotlightScore(AppEntry app) {
        String hay = (app.label + " " + app.packageName + " " + app.category).toLowerCase(Locale.US);
        int score = app.usage * 10 + latestBadges.getOrDefault(app.packageName, 0) * 20;
        if ("Live TV / IPTV".equals(app.category)) score += 75;
        if ("Android TV".equals(app.category) || "Entertainment".equals(app.category)) score += 35;
        if (has(hay, "kpn", "kpnandroidtv", "iptv", "m3u", "tivimate", "live tv", "livetv", "zapping", "channels")) score += 95;
        if (has(hay, "netflix", "youtube", "prime", "disney", "plex", "kodi", "stream")) score += 45;
        return score;
    }

    private int countVisible(String category) {
        if ("Spotlight".equals(category)) return spotlightApps().size();
        int count = 0;
        for (AppEntry app : apps) if (!app.hidden && category.equals(app.category)) count++;
        return count;
    }

    private void addAction(String label, String action, int w, int h, int page, int color) {
        Tile tile = new Tile();
        tile.type = "action";
        tile.label = label;
        tile.action = action;
        tile.w = w;
        tile.h = h;
        tile.page = page;
        tile.color = color;
        tiles.add(tile);
    }

    private void saveTiles() {
        JSONArray array = new JSONArray();
        try {
            for (Tile tile : tiles) array.put(tile.toJson());
        } catch (JSONException ignored) {
        }
        prefs.edit().putString("tiles", array.toString()).apply();
    }

    private void syncInstalledState() {
        Set<String> installed = new HashSet<>();
        for (AppEntry app : apps) installed.add(app.key());
        for (Tile tile : tiles) {
            if ("app".equals(tile.type)) tile.missing = !installed.contains(tile.packageName + "/" + tile.className);
            if ("app".equals(tile.type) && !tile.missing) {
                AppEntry app = findApp(tile);
                tile.spotlight = app != null && isSpotlightApp(app);
            }
        }
    }

    private Map<String, Integer> readUsage() {
        Map<String, Integer> usage = new HashMap<>();
        try {
            JSONObject obj = new JSONObject(prefs.getString("usage", "{}"));
            JSONArray names = obj.names();
            if (names != null) for (int i = 0; i < names.length(); i++) {
                String key = names.getString(i);
                usage.put(key, obj.optInt(key));
            }
        } catch (JSONException ignored) {
        }
        return usage;
    }

    private void bumpUsage(String packageName) {
        Map<String, Integer> usage = readUsage();
        usage.put(packageName, usage.getOrDefault(packageName, 0) + 1);
        JSONObject obj = new JSONObject();
        try {
            for (Map.Entry<String, Integer> item : usage.entrySet()) obj.put(item.getKey(), item.getValue());
        } catch (JSONException ignored) {
        }
        prefs.edit().putString("usage", obj.toString()).apply();
    }

    private void launch(Tile tile) {
        if ("app".equals(tile.type)) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(tile.packageName, tile.className));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            bumpUsage(tile.packageName);
        } else if ("action".equals(tile.type)) {
            runLauncherAction(tile.action);
        }
    }

    private void runLauncherAction(String action) {
        if ("drawer".equals(action)) showDrawer();
        else if ("search".equals(action)) showSearch();
        else if ("presets".equals(action)) showPresets();
        else if ("hide".equals(action)) showHiddenApps();
        else if ("themes".equals(action)) showThemes();
        else if ("backup".equals(action)) showBackup();
        else if ("live".equals(action)) showLiveTiles();
        else if ("security".equals(action)) showSecurity();
        else if ("install_apps".equals(action)) showInstallApps();
        else if ("profile".equals(action)) showProfile();
        else if (action.startsWith("live:")) showLiveInfo(action.substring(5));
        else if (action.startsWith("group:")) showGroup(action.substring(6));
        else if ("settings".equals(action)) startActivity(new Intent(Settings.ACTION_SETTINGS));
        else if ("notifications".equals(action) && !HomeAccessibilityService.runAction("notifications")) openAccessibilitySettings();
        else if ("quick_settings".equals(action) && !HomeAccessibilityService.runAction("quick_settings")) openAccessibilitySettings();
        else if ("recents".equals(action) && !HomeAccessibilityService.runAction("recents")) openAccessibilitySettings();
        else if ("power".equals(action) && !HomeAccessibilityService.runAction("power")) openAccessibilitySettings();
        else if ("lock".equals(action)) lockScreen();
    }

    private void lockScreen() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, HomeDeviceAdminReceiver.class);
        if (dpm != null && dpm.isAdminActive(admin)) dpm.lockNow();
        else {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
            startActivity(intent);
        }
    }

    private void openAccessibilitySettings() {
        Toast.makeText(this, "Enable Mosaic Home actions for this shortcut.", Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void showDrawer() {
        LinearLayout panel = panel("Apps by group");
        addRow(panel, "Presets", "Change the whole home layout", () -> showPresets());
        addRow(panel, "Hide apps", hidden.size() + " hidden", () -> showHiddenApps());
        addRow(panel, "Themes", prefs.getString("theme", "Metro"), () -> showThemes());
        addRow(panel, "Backup / import", "Clipboard layout tools", () -> showBackup());
        addRow(panel, "Live info tiles", "Weather, news, music and clock tiles", () -> showLiveTiles());
        addRow(panel, "Hidden-app security", prefs.getBoolean("hiddenLock", false) ? "PIN lock enabled" : "Not locked", () -> showSecurity());
        addRow(panel, "Install TV apps", "Official Play/Appteka links", () -> showInstallApps());
        for (String category : CATEGORY_ORDER) {
            if (countVisible(category) == 0) continue;
            addHeader(panel, category + "  (" + countVisible(category) + ")");
            for (AppEntry app : apps) {
                if (!app.hidden && category.equals(app.category)) addAppRow(panel, app, () -> launch(appTile(app)));
            }
        }
        showPanel(panel);
    }

    private void showSearch() {
        LinearLayout panel = panel(null);
        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search apps, groups, actions");
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.rgb(160, 170, 180));
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        panel.addView(search, new LinearLayout.LayoutParams(-1, dp(54)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        showPanel(panel);
        Runnable render = () -> renderSearch(results, search.getText().toString());
        search.addTextChangedListener(new SimpleTextWatcher(render));
        search.requestFocus();
        render.run();
    }

    private void renderSearch(LinearLayout results, String text) {
        results.removeAllViews();
        String needle = text.toLowerCase(Locale.getDefault()).trim();
        addRow(results, "Add notification shortcut", "System action", () -> addTile(actionTile("Notifications", "notifications")));
        addRow(results, "Add quick settings shortcut", "System action", () -> addTile(actionTile("Quick settings", "quick_settings")));
        addRow(results, "Add lock shortcut", "System action", () -> addTile(actionTile("Lock", "lock")));
        for (String category : CATEGORY_ORDER) {
            if (countVisible(category) == 0) continue;
            if (needle.isEmpty() || category.toLowerCase(Locale.US).contains(needle)) {
                addRow(results, "Group: " + category, countVisible(category) + " apps", () -> addTile(groupTile(category)));
            }
        }
        for (AppEntry app : apps) {
            if (app.hidden) continue;
            String hay = (app.label + " " + app.category + " " + app.packageName).toLowerCase(Locale.US);
            if (!needle.isEmpty() && !hay.contains(needle)) continue;
            addAppRow(results, app, () -> {
                Tile tile = appTile(app);
                addTile(tile);
                launch(tile);
            });
        }
    }

    private void showGroup(String category) {
        LinearLayout panel = panel(category);
        List<AppEntry> groupApps = "Spotlight".equals(category) ? spotlightApps() : apps;
        for (AppEntry app : groupApps) {
            if (!app.hidden && ("Spotlight".equals(category) || category.equals(app.category))) addAppRow(panel, app, () -> launch(appTile(app)));
        }
        showPanel(panel);
    }

    private void showPresets() {
        LinearLayout panel = panel("Presets");
        for (String preset : PRESETS) {
            addRow(panel, preset, presetDescription(preset), () -> {
                closeOverlay();
                applyPreset(preset, true);
            });
        }
        showPanel(panel);
    }

    private void showThemes() {
        LinearLayout panel = panel("Themes");
        for (String theme : THEMES) {
            addRow(panel, theme, theme.equals(prefs.getString("theme", "Metro")) ? "Active" : "Apply theme", () -> {
                prefs.edit().putString("theme", theme).apply();
                reload();
                showThemes();
            });
        }
        addRow(panel, "Tile animation", "Hover pulse and focus ring are enabled", () ->
                Toast.makeText(this, "Hover animation is already active for mouse and air-mouse.", Toast.LENGTH_SHORT).show());
        showPanel(panel);
    }

    private void showBackup() {
        LinearLayout panel = panel("Backup / import");
        addRow(panel, "Copy full backup", "Copies tiles, hidden apps, defaults, preset and theme", () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Mosaic Home backup", buildBackupJson()));
            Toast.makeText(this, "Backup copied.", Toast.LENGTH_SHORT).show();
        });
        addRow(panel, "Import from clipboard", "Paste a Mosaic Home backup from another device", () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            CharSequence text = null;
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
            }
            if (text != null && restoreBackup(text.toString())) {
                Toast.makeText(this, "Backup imported.", Toast.LENGTH_SHORT).show();
                reload();
            } else {
                Toast.makeText(this, "Clipboard has no valid backup.", Toast.LENGTH_LONG).show();
            }
        });
        addRow(panel, "Reset layout", "Rebuild current preset from installed apps", () -> applyPreset(prefs.getString("preset", "Entertainment first"), true));
        showPanel(panel);
    }

    private String buildBackupJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("version", 1);
            obj.put("tiles", new JSONArray(prefs.getString("tiles", "[]")));
            obj.put("hidden", new JSONArray(prefs.getString("hidden", "[]")));
            obj.put("defaultHidden", new JSONArray(prefs.getString("defaultHidden", "[]")));
            obj.put("preset", prefs.getString("preset", "Entertainment first"));
            obj.put("theme", prefs.getString("theme", "Metro"));
            obj.put("hiddenLock", prefs.getBoolean("hiddenLock", false));
            obj.put("hiddenPin", prefs.getString("hiddenPin", "0000"));
        } catch (JSONException ignored) {
        }
        return obj.toString();
    }

    private boolean restoreBackup(String raw) {
        try {
            JSONObject obj = new JSONObject(raw);
            prefs.edit()
                    .putString("tiles", obj.optJSONArray("tiles") == null ? "[]" : obj.optJSONArray("tiles").toString())
                    .putString("hidden", obj.optJSONArray("hidden") == null ? "[]" : obj.optJSONArray("hidden").toString())
                    .putString("defaultHidden", obj.optJSONArray("defaultHidden") == null ? "[]" : obj.optJSONArray("defaultHidden").toString())
                    .putString("preset", obj.optString("preset", "Entertainment first"))
                    .putString("theme", obj.optString("theme", "Metro"))
                    .putBoolean("hiddenLock", obj.optBoolean("hiddenLock", false))
                    .putString("hiddenPin", obj.optString("hiddenPin", "0000"))
                    .putBoolean("defaultHiddenSeeded", true)
                    .apply();
            hiddenUnlocked = false;
            return true;
        } catch (JSONException ignored) {
            return false;
        }
    }

    private void showLiveTiles() {
        LinearLayout panel = panel("Live info tiles");
        addRow(panel, "Add Clock tile", "Time and date tile", () -> addTile(liveTile("Clock", "live:clock", 2, 1)));
        addRow(panel, "Add Weather tile", "Local placeholder, ready for provider hookup", () -> addTile(liveTile("Weather", "live:weather", 2, 1)));
        addRow(panel, "Add News tile", "Headline placeholder, ready for feed hookup", () -> addTile(liveTile("News", "live:news", 2, 1)));
        addRow(panel, "Add Music tile", "Now-playing placeholder", () -> addTile(liveTile("Music", "live:music", 2, 1)));
        showPanel(panel);
    }

    private Tile liveTile(String label, String action, int w, int h) {
        Tile tile = actionTile(label, action);
        tile.w = w;
        tile.h = h;
        return tile;
    }

    private void showLiveInfo(String type) {
        LinearLayout panel = panel(type.substring(0, 1).toUpperCase(Locale.US) + type.substring(1));
        if ("clock".equals(type)) addRow(panel, new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(new Date()), () -> {});
        else if ("weather".equals(type)) addRow(panel, "Weather tile", "Provider hookup point: location, forecast and icons", () -> {});
        else if ("news".equals(type)) addRow(panel, "News tile", "Provider hookup point: RSS/API headlines", () -> {});
        else if ("music".equals(type)) addRow(panel, "Music tile", "Provider hookup point: media session now playing", () -> {});
        showPanel(panel);
    }

    private void showSecurity() {
        LinearLayout panel = panel("Security");
        boolean locked = prefs.getBoolean("hiddenLock", false);
        addRow(panel, locked ? "Disable hidden PIN" : "Enable hidden PIN", locked ? "Hidden apps are protected" : "PIN defaults to 0000", () -> {
            prefs.edit().putBoolean("hiddenLock", !locked).putString("hiddenPin", prefs.getString("hiddenPin", "0000")).apply();
            hiddenUnlocked = false;
            showSecurity();
        });
        addRow(panel, "Set PIN from clipboard", "Copy a 4+ digit PIN, then tap here", () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            CharSequence text = null;
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
            }
            String pin = text == null ? "" : text.toString().trim();
            if (pin.length() >= 4) {
                prefs.edit().putString("hiddenPin", pin).putBoolean("hiddenLock", true).apply();
                hiddenUnlocked = false;
                Toast.makeText(this, "PIN updated.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Clipboard PIN must be at least 4 characters.", Toast.LENGTH_LONG).show();
            }
        });
        showPanel(panel);
    }

    private void showInstallApps() {
        LinearLayout panel = panel("Install TV apps");
        addInstallRow(panel, "Netflix for Android TV", "Official Play Store: com.netflix.ninja", "https://play.google.com/store/apps/details?id=com.netflix.ninja");
        addInstallRow(panel, "Netflix mobile/tablet", "Official Play Store: com.netflix.mediaclient", "https://play.google.com/store/apps/details?id=com.netflix.mediaclient");
        addInstallRow(panel, "KPN TV+ Android TV", "Official Play Store: com.kpn.kpnandroidtv", "https://play.google.com/store/apps/details?id=com.kpn.kpnandroidtv");
        addInstallRow(panel, "KPN TV+ phone/tablet", "Official Play Store: com.kpn.epg", "https://play.google.com/store/apps/details?id=com.kpn.epg");
        addInstallRow(panel, "M3U IPTV player", "Official Play Store, bring your own legal playlist", "https://play.google.com/store/apps/details?id=de.herber_edevelopment.m3uiptv");
        addInstallRow(panel, "Appteka", "Official Appteka client APK/source", "https://appteka.store/");
        addInstallRow(panel, "Send files to TV", "Official Play Store: com.yablio.sendfilestotv", "https://play.google.com/store/apps/details?id=com.yablio.sendfilestotv");
        showPanel(panel);
    }

    private void showProfile() {
        LinearLayout panel = panel("Profile");
        addRow(panel, "Guest profile", "Local profile placeholder", () -> {});
        addRow(panel, "Personal recommendations", "Uses installed apps and local usage only", () -> {});
        addRow(panel, "Manage layout", "Open presets, themes and hidden apps", () -> showDrawer());
        showPanel(panel);
    }

    private void addInstallRow(LinearLayout panel, String title, String subtitle, String url) {
        addRow(panel, title, subtitle, () -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
    }

    private String presetDescription(String preset) {
        if ("Google TV style".equals(preset)) return "For You, Continue watching, Top picks, Live and Apps rows";
        if ("Android TV standard".equals(preset)) return "TV rows: continue, streaming, live TV, tools, settings";
        if ("Entertainment first".equals(preset)) return "Streaming and media on page one";
        if ("TV leanback".equals(preset)) return "Large tiles for remote control";
        if ("Monitor dashboard".equals(preset)) return "Dense groups for wide screens";
        if ("System admin".equals(preset)) return "All settings and tools up front";
        return "Rebuilds the home screen";
    }

    private void showHiddenApps() {
        if (prefs.getBoolean("hiddenLock", false) && !hiddenUnlocked) {
            showHiddenUnlock();
            return;
        }
        LinearLayout panel = panel("Hide apps");
        addRow(panel, "Show all apps", "Reset hidden list", () -> {
            hidden.clear();
            defaultHidden.clear();
            saveHidden();
            applyPreset(prefs.getString("preset", "Entertainment first"), true);
            showHiddenApps();
        });
        addHeader(panel, "Hidden by default / hidden now");
        for (String category : CATEGORY_ORDER) {
            int count = countHidden(category);
            if (count == 0) continue;
            addHeader(panel, category + "  (" + count + " hidden)");
            for (AppEntry app : apps) {
                if (!app.hidden || !category.equals(app.category)) continue;
                String state = defaultHidden.contains(app.key()) ? "Default hidden - tap to show" : "Hidden - tap to show";
                addRow(panel, app.label, state, () -> {
                    hidden.remove(app.key());
                    defaultHidden.remove(app.key());
                    saveHidden();
                    reload();
                    showHiddenApps();
                });
            }
        }
        addHeader(panel, "Visible apps");
        for (String category : CATEGORY_ORDER) {
            int count = countVisible(category);
            if (count == 0) continue;
            addHeader(panel, category + "  (" + count + " visible)");
            for (AppEntry app : apps) {
                if (app.hidden || !category.equals(app.category)) continue;
                addRow(panel, app.label, app.category + " - tap to hide", () -> {
                    hidden.add(app.key());
                    saveHidden();
                    reload();
                    showHiddenApps();
                });
            }
        }
        showPanel(panel);
    }

    private void showHiddenUnlock() {
        LinearLayout panel = panel("Unlock hidden apps");
        EditText pin = new EditText(this);
        pin.setSingleLine(true);
        pin.setHint("PIN");
        pin.setTextColor(Color.WHITE);
        pin.setHintTextColor(Color.rgb(160, 170, 180));
        pin.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        panel.addView(pin, new LinearLayout.LayoutParams(-1, dp(56)));
        addRow(panel, "Unlock", "Open hidden-app manager", () -> {
            if (pin.getText().toString().equals(prefs.getString("hiddenPin", "0000"))) {
                hiddenUnlocked = true;
                showHiddenApps();
            } else {
                Toast.makeText(this, "Wrong PIN.", Toast.LENGTH_SHORT).show();
            }
        });
        showPanel(panel);
    }

    private void addTile(Tile tile) {
        tiles.add(0, tile);
        saveTiles();
        closeOverlay();
        board.invalidate();
    }

    private Tile appTile(AppEntry app) {
        Tile tile = new Tile();
        tile.type = "app";
        tile.label = app.label;
        tile.packageName = app.packageName;
        tile.className = app.className;
        tile.color = colorFor(app.category);
        return tile;
    }

    private Tile actionTile(String label, String action) {
        Tile tile = new Tile();
        tile.type = "action";
        tile.label = label;
        tile.action = action;
        tile.color = colorFor(label);
        return tile;
    }

    private Tile groupTile(String category) {
        Tile tile = actionTile(category, "group:" + category);
        tile.w = category.length() > 9 ? 2 : 1;
        return tile;
    }

    private int colorFor(String value) {
        String theme = prefs == null ? "Metro" : prefs.getString("theme", "Metro");
        int offset = 0;
        if ("Cinema".equals(theme)) offset = 2;
        else if ("Ocean".equals(theme)) offset = 1;
        else if ("High contrast".equals(theme)) offset = 8;
        else if ("Warm monitor".equals(theme)) offset = 4;
        else if ("Night TV".equals(theme)) offset = 5;
        return COLORS[(Math.abs(value.hashCode()) + offset) % COLORS.length];
    }

    private int backgroundColor() {
        String theme = prefs == null ? "Metro" : prefs.getString("theme", "Metro");
        if ("High contrast".equals(theme)) return Color.BLACK;
        if ("Ocean".equals(theme)) return Color.rgb(4, 20, 28);
        if ("Warm monitor".equals(theme)) return Color.rgb(20, 15, 12);
        if ("Cinema".equals(theme)) return Color.rgb(10, 8, 11);
        return Color.rgb(9, 11, 16);
    }

    private int countHidden(String category) {
        int count = 0;
        for (AppEntry app : apps) if (app.hidden && category.equals(app.category)) count++;
        return count;
    }

    private AppEntry findApp(Tile tile) {
        for (AppEntry app : apps) {
            if (app.packageName.equals(tile.packageName) && app.className.equals(tile.className)) return app;
        }
        return null;
    }

    private LinearLayout panel(String title) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setPadding(dp(16), dp(16), dp(16), dp(16));
        holder.setBackgroundColor(Color.rgb(13, 17, 24));
        if (title != null) {
            TextView heading = new TextView(this);
            heading.setText(title);
            heading.setTextColor(Color.WHITE);
            heading.setTextSize(isTvLike() ? 26 : 22);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            holder.addView(heading, new LinearLayout.LayoutParams(-1, dp(58)));
        }
        return holder;
    }

    private void showPanel(LinearLayout content) {
        closeOverlay();
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        root.addView(scroll, overlayParams());
    }

    private FrameLayout.LayoutParams overlayParams() {
        int width = getResources().getDisplayMetrics().widthPixels;
        int panelWidth = isTvLike() ? Math.min(width, dp(760)) : Math.min(width, dp(560));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(panelWidth, -1);
        params.gravity = Gravity.END;
        return params;
    }

    private void addHeader(LinearLayout parent, String text) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextColor(Color.rgb(170, 184, 195));
        row.setTextSize(15);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(12), dp(8), dp(4));
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(44)));
    }

    private void addRow(LinearLayout parent, String title, String subtitle, Runnable click) {
        TextView row = new TextView(this);
        row.setText(subtitle == null || subtitle.isEmpty() ? title : title + "\n" + subtitle);
        row.setTextColor(Color.WHITE);
        row.setTextSize(isTvLike() ? 22 : 18);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setFocusable(true);
        row.setBackgroundColor(Color.rgb(22, 28, 38));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, isTvLike() ? dp(76) : dp(64));
        params.setMargins(0, 0, 0, dp(7));
        parent.addView(row, params);
        row.setOnClickListener(v -> click.run());
    }

    private void addAppRow(LinearLayout parent, AppEntry app, Runnable click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setFocusable(true);
        row.setBackgroundColor(Color.rgb(22, 28, 38));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setBackground(iconBackplate());
        icon.setPadding(dp(7), dp(7), dp(7), dp(7));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(isTvLike() ? dp(58) : dp(48), isTvLike() ? dp(58) : dp(48));
        iconParams.setMargins(0, 0, dp(14), 0);
        row.addView(icon, iconParams);

        TextView text = new TextView(this);
        text.setText(app.label + "\n" + app.category);
        text.setTextColor(Color.WHITE);
        text.setTextSize(isTvLike() ? 21 : 17);
        text.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text, new LinearLayout.LayoutParams(0, -1, 1));

        View.OnHoverListener hover = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                icon.animate().scaleX(1.12f).scaleY(1.12f).setDuration(90).start();
                row.setBackgroundColor(Color.rgb(32, 40, 54));
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                icon.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                row.setBackgroundColor(Color.rgb(22, 28, 38));
            }
            return false;
        };
        row.setOnHoverListener(hover);
        row.setOnFocusChangeListener((v, hasFocus) -> {
            icon.animate().scaleX(hasFocus ? 1.1f : 1f).scaleY(hasFocus ? 1.1f : 1f).setDuration(100).start();
            row.setBackgroundColor(hasFocus ? Color.rgb(36, 46, 62) : Color.rgb(22, 28, 38));
        });
        row.setOnClickListener(v -> click.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, isTvLike() ? dp(82) : dp(68));
        params.setMargins(0, 0, 0, dp(7));
        parent.addView(row, params);
    }

    private Drawable iconBackplate() {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(Color.argb(70, 255, 255, 255));
        shape.setCornerRadius(dp(10));
        return shape;
    }

    private void closeOverlay() {
        if (root.getChildCount() > 1) root.removeViews(1, root.getChildCount() - 1);
    }

    @Override
    public void onBackPressed() {
        if (root.getChildCount() > 1) closeOverlay();
        else super.onBackPressed();
    }

    private boolean isTvLike() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK;
        return mode == Configuration.UI_MODE_TYPE_TELEVISION || getResources().getConfiguration().smallestScreenWidthDp >= 720;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class TileBoard extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float downX, downY, scrollX, scrollY;
        private long downAt;
        private int currentPage = 0;
        private int hoverIndex = -1;
        private boolean dragging;

        TileBoard(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(backgroundColor());
            int columns = getColumns();
            int cell = Math.max(isTvLike() ? dp(108) : dp(72), (getWidth() - dp(24)) / columns);
            int gap = Math.max(dp(6), cell / 18);
            List<PlacedTile> placed = layoutTiles(columns);
            for (int i = 0; i < placed.size(); i++) {
                PlacedTile item = placed.get(i);
                Tile tile = item.tile;
                int left = dp(12) - Math.round(scrollX) + item.page * getWidth() + item.col * cell + gap;
                int top = dp(28) - Math.round(scrollY) + item.row * cell + gap;
                int right = left + item.w * cell - gap;
                int bottom = top + item.h * cell - gap;
                if (right < -cell || left > getWidth() + cell || bottom < -cell || top > getHeight() + cell) continue;
                boolean hot = i == selected || i == hoverIndex;
                rect.set(left, top, right, bottom);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(tile.missing ? Color.rgb(64, 66, 72) : tile.color);
                canvas.drawRoundRect(rect, dp(3), dp(3), paint);
                paint.setColor(Color.argb(50, 255, 255, 255));
                canvas.drawRect(left, top, right, top + Math.max(dp(4), (bottom - top) / 12), paint);
                if (hot) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(i == hoverIndex ? dp(5) : dp(4));
                    paint.setColor(Color.WHITE);
                    canvas.drawRoundRect(rect, dp(3), dp(3), paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                if ("app".equals(tile.type)) drawAppIcon(canvas, tile, left, top, right, bottom, i == hoverIndex);
                if (tile.action != null && tile.action.startsWith("live:")) drawLiveTileText(canvas, tile, left, top, right, bottom);
                if (tile.spotlight) drawSpotlightChip(canvas, left, top, right);
                paint.setColor(Color.WHITE);
                paint.setTextSize(isTvLike() ? dp(22) : (tile.w > 1 ? dp(18) : dp(14)));
                paint.setFakeBoldText(true);
                drawLabel(canvas, tile.label, left + dp(10), bottom - dp(18), right - dp(10));
                paint.setFakeBoldText(false);
                int badge = latestBadges.getOrDefault(tile.packageName, 0);
                if (badge > 0) drawBadge(canvas, badge, right - dp(24), top + dp(18));
            }
            if (hoverIndex >= 0) postInvalidateDelayed(16);
            postInvalidateDelayed(1000);
        }

        private void drawLiveTileText(Canvas canvas, Tile tile, int left, int top, int right, int bottom) {
            String type = tile.action.substring(5);
            paint.setColor(Color.WHITE);
            paint.setFakeBoldText(true);
            if ("clock".equals(type)) {
                paint.setTextSize(isTvLike() ? dp(34) : dp(28));
                canvas.drawText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), left + dp(12), top + dp(48), paint);
            } else if ("weather".equals(type)) {
                paint.setTextSize(isTvLike() ? dp(26) : dp(20));
                canvas.drawText("Weather", left + dp(12), top + dp(42), paint);
                paint.setFakeBoldText(false);
                paint.setTextSize(isTvLike() ? dp(18) : dp(14));
                canvas.drawText("provider ready", left + dp(12), top + dp(68), paint);
            } else if ("news".equals(type)) {
                paint.setTextSize(isTvLike() ? dp(24) : dp(18));
                canvas.drawText("Headlines", left + dp(12), top + dp(42), paint);
            } else if ("music".equals(type)) {
                paint.setTextSize(isTvLike() ? dp(24) : dp(18));
                canvas.drawText("Now playing", left + dp(12), top + dp(42), paint);
            } else if ("continue".equals(type)) {
                paint.setTextSize(isTvLike() ? dp(24) : dp(18));
                canvas.drawText("Continue", left + dp(12), top + dp(42), paint);
                paint.setFakeBoldText(false);
                paint.setTextSize(isTvLike() ? dp(18) : dp(14));
                canvas.drawText("TV row placeholder", left + dp(12), top + dp(68), paint);
            }
            paint.setFakeBoldText(false);
        }

        private void drawAppIcon(Canvas canvas, Tile tile, int left, int top, int right, int bottom, boolean hover) {
            AppEntry app = findApp(tile);
            if (app == null || app.icon == null) return;
            int tileW = right - left;
            int tileH = bottom - top;
            int maxSize = isTvLike() ? dp(104) : dp(72);
            int size = Math.min(Math.max(dp(42), Math.min(tileW, tileH) / 2), maxSize);
            if (tileW > dp(230) || tileH > dp(230)) size = Math.min(size + dp(10), isTvLike() ? dp(118) : dp(82));
            float pulse = hover ? (float) Math.sin(System.currentTimeMillis() / 90.0) * dp(4) : 0;
            int iconLeft = left + Math.max(dp(12), (tileW - size) / 2);
            int iconTop = top + Math.max(dp(12), (tileH - size) / 2 - dp(12)) - Math.round(pulse);
            rect.set(iconLeft - dp(9), iconTop - dp(9), iconLeft + size + dp(9), iconTop + size + dp(9));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(64, 255, 255, 255));
            canvas.drawRoundRect(rect, dp(12), dp(12), paint);
            app.icon.setBounds(iconLeft, iconTop, iconLeft + size, iconTop + size);
            app.icon.setAlpha(hover ? 255 : 232);
            app.icon.draw(canvas);
            app.icon.setAlpha(255);
        }

        private void drawSpotlightChip(Canvas canvas, int left, int top, int right) {
            String text = "UIT";
            paint.setFakeBoldText(true);
            paint.setTextSize(isTvLike() ? dp(15) : dp(12));
            float width = paint.measureText(text) + dp(18);
            rect.set(right - width - dp(10), top + dp(10), right - dp(10), top + dp(34));
            paint.setColor(Color.argb(230, 255, 255, 255));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(rect, dp(12), dp(12), paint);
            paint.setColor(Color.rgb(15, 18, 24));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(text, rect.centerX(), top + dp(28), paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setFakeBoldText(false);
        }

        private void drawLabel(Canvas canvas, String label, float x, float baseline, float maxRight) {
            if (label == null) return;
            String text = label;
            while (paint.measureText(text) > maxRight - x && text.length() > 4) text = text.substring(0, text.length() - 2);
            if (!text.equals(label)) text += ".";
            canvas.drawText(text, x, baseline, paint);
        }

        private void drawBadge(Canvas canvas, int badge, float cx, float cy) {
            paint.setColor(Color.WHITE);
            canvas.drawCircle(cx, cy, dp(13), paint);
            paint.setColor(Color.rgb(9, 11, 16));
            paint.setTextSize(dp(12));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(badge > 9 ? "9+" : String.valueOf(badge), cx, cy + dp(4), paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private List<PlacedTile> layoutTiles(int columns) {
            ArrayList<PlacedTile> placed = new ArrayList<>();
            HashMap<Integer, boolean[][]> grids = new HashMap<>();
            for (Tile tile : tiles) {
                int page = tile.page;
                boolean[][] grid = grids.get(page);
                if (grid == null) {
                    grid = new boolean[120][columns];
                    grids.put(page, grid);
                }
                int w = Math.min(Math.max(1, tile.w), columns);
                int h = Math.max(1, tile.h);
                int[] spot = findSpot(grid, columns, w, h);
                mark(grid, spot[0], spot[1], w, h);
                PlacedTile item = new PlacedTile();
                item.tile = tile;
                item.page = page;
                item.row = spot[0];
                item.col = spot[1];
                item.w = w;
                item.h = h;
                placed.add(item);
            }
            return placed;
        }

        private int[] findSpot(boolean[][] grid, int columns, int w, int h) {
            for (int row = 0; row < grid.length - h; row++) {
                for (int col = 0; col <= columns - w; col++) {
                    boolean free = true;
                    for (int dy = 0; dy < h; dy++) for (int dx = 0; dx < w; dx++) if (grid[row + dy][col + dx]) free = false;
                    if (free) return new int[]{row, col};
                }
            }
            return new int[]{0, 0};
        }

        private void mark(boolean[][] grid, int row, int col, int w, int h) {
            for (int dy = 0; dy < h; dy++) for (int dx = 0; dx < w; dx++) grid[row + dy][col + dx] = true;
        }

        private int getColumns() {
            int sw = getResources().getConfiguration().smallestScreenWidthDp;
            boolean landscape = getWidth() > getHeight();
            if (isTvLike() && landscape) return 10;
            if (sw >= 900) return 10;
            if (sw >= 600) return 8;
            return 4;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                downAt = System.currentTimeMillis();
                dragging = false;
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float dx = downX - event.getX();
                float dy = downY - event.getY();
                if (Math.abs(dx) + Math.abs(dy) > dp(8)) dragging = true;
                if (Math.abs(dx) > Math.abs(dy)) scrollX = Math.max(0, currentPage * getWidth() + dx);
                else {
                    scrollY = Math.max(0, scrollY + dy / 8f);
                    downY = event.getY();
                }
                invalidate();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!dragging) hit(event.getX(), event.getY(), System.currentTimeMillis() - downAt > 520);
                else {
                    currentPage = Math.max(0, Math.round(scrollX / Math.max(1, getWidth())));
                    scrollX = currentPage * getWidth();
                }
                invalidate();
                return true;
            }
            return super.onTouchEvent(event);
        }

        private void hit(float px, float py, boolean edit) {
            int index = hitIndex(px, py);
            if (index >= 0) {
                selected = index;
                Tile tile = layoutTiles(getColumns()).get(index).tile;
                if (edit) editTile(tile);
                else launch(tile);
                invalidate();
            }
        }

        private int hitIndex(float px, float py) {
            List<PlacedTile> placed = layoutTiles(getColumns());
            int cell = Math.max(isTvLike() ? dp(108) : dp(72), (getWidth() - dp(24)) / getColumns());
            int gap = Math.max(dp(6), cell / 18);
            for (int i = placed.size() - 1; i >= 0; i--) {
                PlacedTile item = placed.get(i);
                int left = dp(12) - Math.round(scrollX) + item.page * getWidth() + item.col * cell + gap;
                int top = dp(28) - Math.round(scrollY) + item.row * cell + gap;
                int right = left + item.w * cell - gap;
                int bottom = top + item.h * cell - gap;
                if (px >= left && px <= right && py >= top && py <= bottom) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public boolean onHoverEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                hoverIndex = -1;
                invalidate();
                return true;
            }
            int hit = hitIndex(event.getX(), event.getY());
            if (hit != hoverIndex) {
                hoverIndex = hit;
                if (hit >= 0) selected = hit;
                invalidate();
            }
            return true;
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_MENU) {
                showSearch();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (!tiles.isEmpty()) launch(tiles.get(Math.min(selected, tiles.size() - 1)));
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) selected = Math.min(tiles.size() - 1, selected + 1);
            else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) selected = Math.max(0, selected - 1);
            else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) selected = Math.min(tiles.size() - 1, selected + getColumns());
            else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) selected = Math.max(0, selected - getColumns());
            else return super.onKeyDown(keyCode, event);
            invalidate();
            return true;
        }
    }

    private void editTile(Tile tile) {
        tile.w = tile.w == 1 ? 2 : 1;
        tile.h = tile.h == 1 ? 2 : 1;
        tile.color = COLORS[(indexOfColor(tile.color) + 1) % COLORS.length];
        saveTiles();
        board.invalidate();
    }

    private int indexOfColor(int color) {
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == color) return i;
        return 0;
    }

    private static final class SimpleTextWatcher implements android.text.TextWatcher {
        private final Runnable after;
        SimpleTextWatcher(Runnable after) { this.after = after; }
        public void beforeTextChanged(CharSequence s, int start, int count, int afterCount) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
        public void afterTextChanged(android.text.Editable s) { after.run(); }
    }

    private static final class AppEntry {
        String label;
        String packageName;
        String className;
        String category;
        Drawable icon;
        int usage;
        boolean hidden;
        String key() { return packageName + "/" + className; }
    }

    private static final class PlacedTile {
        Tile tile;
        int page, row, col, w, h;
    }

    private static final class Tile {
        String type = "app";
        String label = "";
        String packageName = "";
        String className = "";
        String action = "";
        int color = Color.rgb(0, 120, 215);
        int w = 1;
        int h = 1;
        int page = 0;
        boolean missing;
        boolean spotlight;

        JSONObject toJson() throws JSONException {
            JSONObject obj = new JSONObject();
            obj.put("type", type);
            obj.put("label", label);
            obj.put("packageName", packageName);
            obj.put("className", className);
            obj.put("action", action);
            obj.put("color", color);
            obj.put("w", w);
            obj.put("h", h);
            obj.put("page", page);
            obj.put("spotlight", spotlight);
            return obj;
        }

        static Tile fromJson(JSONObject obj) {
            Tile tile = new Tile();
            tile.type = obj.optString("type", "app");
            tile.label = obj.optString("label", "");
            tile.packageName = obj.optString("packageName", "");
            tile.className = obj.optString("className", "");
            tile.action = obj.optString("action", "");
            tile.color = obj.optInt("color", Color.rgb(0, 120, 215));
            tile.w = obj.optInt("w", 1);
            tile.h = obj.optInt("h", 1);
            tile.page = obj.optInt("page", 0);
            tile.spotlight = obj.optBoolean("spotlight", false);
            return tile;
        }
    }
}
