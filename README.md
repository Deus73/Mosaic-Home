# Mosaic Home

Mosaic Home is a fast native Android launcher for phones, tablets, foldables, and Android TV boxes.

## Built-in features

- Home-screen launcher intent, including leanback launcher support for TV boxes.
- Metro-style tile board with responsive columns for phones, tablets, TVs, and foldables.
- Horizontal page movement and vertical page scrolling.
- Smart app drawer sorted by app usage.
- Global search across apps, app groups, and launcher actions.
- Live notification badges through Android notification-listener access.
- Automatic app grouping with TV, entertainment, settings, productivity, media, games, tools, browser, communication, shopping, system, and other categories.
- Twelve home-screen presets, including Google TV style, Android TV standard, Entertainment first, TV leanback, Monitor dashboard, Work focus, Gaming, Media studio, and System admin.
- Android TV standard layout with continue-watching placeholder, streaming apps, Live TV/IPTV apps, TV tools, and settings/system rows.
- Original installed app icons are used as logo buttons on tiles, app lists, search results, and group pages.
- Mouse and air-mouse hover animation enlarges app logos and highlights focused rows.
- Hidden apps menu for removing apps from the home screen, groups, drawer, and search.
- Official install-links menu for common TV-box apps: Netflix, KPN TV+, M3U IPTV, Appteka, and Send files to TV.
- Backup/import via clipboard, theme presets, live-info tiles, and optional hidden-app PIN protection.
- TV remote / keyboard navigation with DPAD and enter.
- Persistent tile layout, hidden apps, preset, colors, and sizes.
- Fast system actions: notifications, quick settings, recents, power dialog, settings, and screen lock.
- Device-admin based lock shortcut for older devices where needed.

## Build

Use Android Studio or run:

```powershell
.\gradlew.bat assembleDebug --project-cache-dir work\.gradle-project
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
