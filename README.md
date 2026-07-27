<h1 align="center">BDIX-CloudStream</h1>

<p align="center">
  <b>CloudStream plugin collection for streaming movies, TV series, live TV, and more from BDIX network servers</b>
</p>

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-quick-install">Install</a> •
  <a href="#-provider-guide">Providers</a> •
  <a href="#-building-from-source">Build</a> •
  <a href="#-faq--troubleshooting">FAQ</a>
</p>

---

## Quick Install

### Prerequisites

- [CloudStream](https://github.com/recloudstream/cloudstream) Android app installed on your device
- You must be connected to a **BDIX-compatible ISP** in Bangladesh

### Add Repository

1. Open CloudStream
2. Go to **Settings** → **Extensions**
3. Tap **Add repository** and enter:

```
https://raw.githubusercontent.com/r-shafi/BDIX-CloudStream/master/repo.json
```

4. The available plugins will appear — tap **Install** on each one you want

### Direct APK Install

Alternative: manually download the `.cs3` plugin files from the [builds branch](https://github.com/r-shafi/BDIX-CloudStream/tree/builds) and sideload them via **Settings → Extensions → Install from storage**.

---

## Provider Guide

Seven plugin modules provide access to different BDIX media servers:

### 1. DhakaFlix (v4)

**Most comprehensive provider** — 5 sub-providers covering 4 servers.

| Provider      | Server IP      | Content                                                |
| ------------- | -------------- | ------------------------------------------------------ |
| DHAKA-FLIX-7  | `172.16.50.7`  | Movies sorted by year/language (50+ categories)        |
| DHAKA-FLIX-9  | `172.16.50.9`  | Anime, Korean series, documentaries, wrestling, awards |
| DHAKA-FLIX-12 | `172.16.50.12` | TV series (A-Z organized)                              |
| DHAKA-FLIX-14 | `172.16.50.14` | General movies & series                                |
| Combined      | All 4 servers  | Unified browsing across all servers                    |

- **Type:** Movies, TV Series, Anime, Asian Drama, Cartoons, Documentary
- **Source:** Apache directory listings with JSON search API
- **Quality:** Extracted from filenames (4K, 1080p, 720p, BluRay, WebRip, etc.)
- **Download:** Supported

### 2. CircleFTP

- **Type:** Movies, TV Series, Anime, OVA, Cartoons, Asian Drama, Documentary
- **Source:** REST API at `new.circleftp.net:5000`
- **Features:** 15 content categories, fallback BDIX IP resolution, search
- **Download:** Supported
- **Note:** Works even during internet shutdowns (purely local BDIX)

### 3. ICC FTP

- **Type:** Movies, TV Series, Anime, Asian Drama, Cartoons, Documentary
- **Source:** HTML/JSON at `10.16.100.244`
- **Features:** 10 categories, search support
- **Download:** Supported

### 4. Dflix - Discovery FTP

- **Type:** Movies, TV Series, Anime
- **Source:** `dflix.discoveryftp.net` with demo login
- **Features:** Movie details with actors, recommendations, quality detection; season/episode parsing for series
- **Download:** Supported

### 5. BDIP TV

- **Type:** Live TV
- **Source:** `tv.bdiptv.net`
- **Features:** Live channels by category, HLS (.m3u8) streaming with token auth, fuzzy search
- **Also includes:** IpTvIDN sub-provider (`iptvidn.com`)

### 6. ArrowNet Live TV

- **Type:** Live TV
- **Source:** `10.10.230.182:8080`
- **Channels:** 5 hardcoded Bangladeshi live TV channels
- **Search:** By channel name

### 7. ArrowNet Movies & TV

- **Type:** Movies, TV Series
- **Source:** REST API at `103.142.80.21/api/v1`
- **Features:** 15+ categories, TMDb poster integration, grouped episodes
- **Download:** Supported

---

## Building from Source

### Requirements

- JDK 17+
- Android SDK (API 35+)
- Git

### Build

```bash
git clone https://github.com/r-shafi/BDIX-CloudStream.git
cd BDIX-CloudStream
./gradlew make
./gradlew makePluginsJson
```

Output `.cs3` plugin files are in each module's build directory. The `plugins.json` manifest is generated at the project root.

### CI/CD

Every push to `master` automatically builds via GitHub Actions and publishes artifacts to the `builds` branch.

---

## FAQ & Troubleshooting

### "No results" or "Provider not working"

- Ensure your device is connected to a BDIX-compatible ISP.
- Some servers use internal IPs (`10.x.x.x`, `172.16.x.x`, `15.x.x.x`) — these are only reachable from within Bangladesh amd from specific ISPs.

### Streams won't load

- Live TV providers (BDIP TV, ArrowNet Live TV) require an active BDIX connection to the streaming server.

### How do I update plugins?

CloudStream checks for updates automatically. You can also manually refresh: **Settings → Extensions → Refresh**.

### Can I contribute?

Yes! Fork the repo, add or improve a provider, and submit a pull request. See [building from source](#-building-from-source).

---

## Credits

- **[Redowan](https://github.com/redowan99)** — Original BDIX CloudStream plugin repository

---

<p align="center">
  <sub>BDIX-CloudStream is not affiliated with any content provider. All content is served directly from publicly accessible BDIX FTP/HTTP servers.</sub>
</p>
