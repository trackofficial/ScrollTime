<img width="128" height="128" alt="logo_scrolltime" src="https://github.com/user-attachments/assets/3f71115f-4446-4a9d-b90a-9ceb56e11adc" />

## Description
#### Scroll Time is an Android application that helps you control the time spent watching short videos (YouTube Shorts, TikTok Reels, Instagram Reels). The application works through the accessibility service and does not require root permissions.

## Technical architecture

### Components

| Component | Purpose |
|-----------|------------|
| `LogTrackingService` | Accessibility service for screen analysis and time tracking |
| `TimeManager` | Time management, storage in SharedPreferences, reset to a new day |
| `PermissionHelper` | Checking and requesting permissions |
| `MainActivity` | Main screen with status and navigation |
| `SettingsActivity` | Setting limits for each application |
| `SplashActivity` | Loading screen at startup |

### Definition of Shorts/Reels

| Application | Method of determination |
|------------|-------------------|
| YouTube Shorts | Text "Shorts", ID elements with "shorts", vertical buttons |
| TikTok Reels | ID of elements with "video", "reel", "short" |
| Instagram Reels | Text contains "reel" |

### Data storage (SharedPreferences)

| Key | Description |
|------|----------|
| `last_reset_date` | Last reset date |
| `total_time_{package}` | Total time in the app |
| `shorts_time_{package}` | Time for short videos |
| `{package}:daily_limit` | Total limit |
| `{package}:shorts_limit` | The limit of short |

### Tracking lifecycle

| Event | Action |
|---------|----------|
| Opening of Shorts/Reels | Timer start, countdown |
| View Shorts/Reels | Adding 1 second every second |
| Exceeding the short limit | Blocking (swipe down), notification |
| Exiting Shorts/Reels | Timer stop, time saving |
| Switching to normal video | Timer stop, continuation of total time |
| Exceeding the total limit | Blocking the entire application |
| New Day (00:00) | Reset all counters |

### Permissions

| Permission | Purpose |
|------------|------------|
| `POST_NOTIFICATIONS` | Limit Exceedance Notifications (Android 13+) |
| `SYSTEM_ALERT_WINDOW` | Displaying on top of other windows |
| `BIND_ACCESSIBILITY_SERVICE` | Accessibility service for screen analysis |
| `FOREGROUND_SERVICE` | Background operation of the service |

7. Blocking the entire app
``
