# itube pro

Personal, ad-free YouTube client for Android. Learning project, sideloaded APK only.

## Build

```
./gradlew assembleDebug        # must pass with zero errors before any phase is "done"
```

Toolchain: AGP 9.3 (built-in Kotlin, so no `kotlin-android` plugin), Gradle 9.5, Kotlin 2.4.21,
KSP 2.3.12, JDK 21 to run Gradle, bytecode target Java 17, minSdk 26, compile/targetSdk 37.
All versions live in `gradle/libs.versions.toml`.

The root `build.gradle.kts` puts `kotlin-gradle-plugin` on the buildscript classpath.
AGP 9 would otherwise use its own bundled (older) Kotlin.

## Modules

```
:app          UI (Compose + M3), Navigation (type-safe routes), ViewModels, Hilt entry points
:domain       pure Kotlin/JVM: models, repository interfaces, use cases, AppException
:data         NewPipeExtractor, OkHttpDownloader, repository impls, (later) Room, DataStore
:core:player  PlaybackService : MediaSessionService, ExoPlayer, MediaSource building,
              PlayerConnection (the UI's MediaController wrapper)
```

Dependency direction: `app → domain, core:player, data` · `data → domain` · `core:player → domain`.

### Module rules
- **No NewPipe types outside `:data`.** Map them to `:domain` models in `data/mapper`.
  Exceptions are converted to `AppException` in `data/error/ExceptionMapper.kt`.
- `:domain` has no Android, DI or library dependencies. Use cases are plain classes and
  get constructed in `app/di/UseCaseModule.kt`.
- `:app` depends on `:data` only so Hilt can see its modules. The one allowed import is
  `NewPipeInitializer` in `ItubeApp`.
- The UI talks to the player only through `MediaController`, wrapped by `PlayerConnection`
  (connect in Activity.onStart, disconnect in onStop). The UI sends light MediaItems
  (mediaId = video URL). The service resolves streams in `MediaSession.Callback.onAddMediaItems`.
- Stream *selection* rules live in `:domain` (`StreamSelector`). `:data` only maps the result back
  to NewPipe streams to generate DASH manifests and per-client HTTP headers.
- Media3 `@UnstableApi` needs `@OptIn(UnstableApi::class)` (androidx.annotation). Kotlin doesn't
  check it, only lint does, so run `./gradlew lintDebug` before committing.
- No business logic in composables. A screen is a stateful `XScreen(viewModel)` wrapper
  plus a stateless `XContent(state, callbacks)`.
- User-visible text lives in `strings.xml`. UI state carries `@StringRes` IDs, never a Context.
- Process death: anything the user typed or selected goes in `SavedStateHandle`.
  Network results are re-fetched, never stored in the Bundle.
- Blocking calls (extractor) run in repositories via the injected `@IoDispatcher`.
  Never swallow `CancellationException`.

### Reference code
`ref/NewPipe` and `ref/Tubular` are read-only GPLv3 references, git-ignored, and never part
of the build. Learn from them and write our own code. Cite the ref file in a comment when
borrowing an idea.

## Updating NewPipeExtractor when YouTube breaks

Symptoms: extraction fails with the "Couldn't read YouTube's response" error
(`AppException.Parsing`) on videos that play fine on youtube.com, or playback gets HTTP 403
on every video.

1. Check which commit NewPipe uses now:
   `git -C ref/NewPipe pull && grep newpipe-extractor ref/NewPipe/gradle/libs.versions.toml`.
   Or check the NewPipeExtractor GitHub releases and commits on `dev`.
2. Put the tag (e.g. `v0.26.6`) or the full commit hash into `newpipeExtractor` in
   `gradle/libs.versions.toml`.
3. `./gradlew assembleDebug --refresh-dependencies`.
   JitPack builds the artifact on first request. The first build may time out; just rerun it.
   If JitPack says the artifact is missing, use the commit hash instead of the tag.
4. If the API changed, only `:data` should need edits (mappers, repositories).
5. Also compare `OkHttpDownloader.USER_AGENT` with `DownloaderImpl.USER_AGENT` in ref/NewPipe.

## Playback pipeline (Phase 3)

```
UI ─MediaItem(mediaId=url)→ MediaController ══IPC══ MediaSession (PlaybackService)
  onAddMediaItems → MediaItemResolver → PlaybackRepository (:data: extract, select, manifests)
  → MediaItem(tag = PlaybackSource) → YoutubeMediaSourceFactory
  → MergingMediaSource(DashMediaSource(video), DashMediaSource(audio))
  → YoutubeDataSourceFactory (rn, range=, POST) → DefaultHttpDataSource
Errors: 403 → PlaybackRecovery re-resolves (forceRefresh) + seeks back; network → re-prepare.
```

## Local database (Phase 5)

Room, file `itubepro.db`, schema JSON in `data/schemas/` (commit it). Tables: `videos` (shared
display data) ← `watch_history`, `favorites`, `playlist_items` → `playlists`;
`subscriptions` ← `feed_items`; `feed_state`. Changing an entity means bumping
`AppDatabase.version` and adding a Migration. **Never** use destructive fallback.
Watch history + resume positions are written by `PlaybackHistoryTracker` inside the service.
Resume happens in `PlaybackService.onSetMediaItems`.

## Lessons learned

### Phase 1 – Foundation
- NewPipeExtractor is network-agnostic. It calls our `Downloader.execute()` for every HTTP
  request, so we control the client, headers, cookies and timeouts.
- The extractor uses `Stream.toList()` (API 34). Without **core library desugaring** it
  crashes on Android 8–13 even though it compiles. We checked by disassembling the jar
  with `javap -c`. Lint doesn't check library bytecode.
- The extractor never sets `Accept-Encoding`, so OkHttp's transparent gzip works.
  (If a caller sets that header itself, OkHttp stops decompressing.)
- Java/Kotlin interop trap: `VideoStream` has a deprecated public field `isVideoOnly` and a
  method `isVideoOnly()`. Kotlin property syntax binds to the **field**. Call the method.
- NewPipe exceptions almost all extend `ParsingException`. Check subclasses first in `when`.
- Validate URLs with `streamLHFactory.acceptUrl()` up front. Otherwise a bad link shows up as a
  confusing parsing error.
- YouTube currently returns **no muxed streams and no DASH manifest** for normal videos, only
  separate video-only + audio progressive streams. Phase 3 must merge them (or generate a DASH
  manifest the way ref/NewPipe's `PlaybackResolver` does).

### Phase 2 – Search & video details
- NewPipe's paging cursor (`Page`) is a NewPipe type, so it can't reach the UI. It's
  `Serializable`, so `PageTokenCodec` turns it into a Base64 string inside a domain
  `PageToken`. The repositories stay stateless. Tokens are never persisted, because they
  break when the extractor version changes.
- TextField state must update synchronously. Feed the field from
  `SavedStateHandle.getStateFlow()` directly, not through `combine().stateIn()`, or fast
  typing drops characters.
- Suggestions: `debounce` → `distinctUntilChanged` → `mapLatest`. `mapLatest` cancels a
  suggestion request that's still running when the next keystroke arrives.
- Infinite scroll: `snapshotFlow { lastVisibleIndex to totalItemsCount }`. Including the
  total re-triggers after each append. All guards (already loading, no next page, last
  attempt failed) live in the ViewModel.
- YouTube can repeat a video across pages. Duplicate `LazyColumn` keys crash, so results
  are de-duplicated by URL.
- `StreamInfoCache` (LRU 30 entries / 1 h, idea from ref/NewPipe InfoCache): detail screen +
  player share one extraction (~1.5 s → 1 ms).
- `AnnotatedString.fromHtml` gives clickable links in descriptions without WebView or
  ClickableText.
- Search items for **live streams report `VIDEO_STREAM`** with duration -1. Only the full
  `StreamInfo` says `LIVE_STREAM` (HLS URL, no DASH). So lists can't show a LIVE badge,
  and the player must check the full info's stream type.
- Coil shares the app's OkHttpClient through `SingletonImageLoader.Factory` on the Application.

### Phase 3 – Background playback
- YouTube stream URLs contain `ip=<device IP>` and expire after ~6 h. A Wi-Fi ↔ mobile switch
  or a long pause therefore ends in **HTTP 403**. The fix is re-extraction, not retrying
  (`NoRetryOnForbiddenPolicy` + `PlaybackRecovery`). Recovery was tested on an emulator with a
  fake repository: 403 → `resolve(forceRefresh = true)` → playback resumed.
- The extractor currently hands out `c=VISIONOS` URLs, which need the visionOS User-Agent.
  `:data` puts per-track headers on `PlaybackTrack`, so the player never needs NewPipe.
- NewPipe's 900-line forked HTTP data source comes down to a `ResolvingDataSource` that rewrites
  each `DataSpec` (add `rn`, move the range into `&range=`, POST `x\0`) in front of the stock
  `DefaultHttpDataSource`.
- **Media3 `isPlaybackOngoing()` only means "still in the foreground"**, and Media3 stays in the
  foreground for a while after pausing. In `onTaskRemoved`, check the player instead
  (`playWhenReady`, items, not ENDED).
- In `onTaskRemoved`, use plain `stopSelf()` when already paused. `pauseAllPlayersAndStopSelf()`
  posted a notification update that outlived the process (stuck, undismissable notification).
- Audio only = `setTrackTypeDisabled(TRACK_TYPE_VIDEO)`. With separate sources, ExoPlayer stops
  loading video segments. It switches instantly, without re-buffering.
- Release the MediaController in `onStop`. A bound controller keeps the service from ever being
  destroyed.
- `asPaddingValues()` ignores consumed insets. Use `Spacer(Modifier.windowInsetsBottomHeight(...))`
  inside lists when a parent (the now-playing bar) consumes the navigation bar inset.
- Live streams: only HLS is offered and `media3-exoplayer-hls` isn't a dependency yet →
  `AppException.LiveNotSupported`.
- Verified on the emulator (API 37): 720p H.264 + Opus merged, seek, audio-only, foreground
  service + MediaStyle notification, Home, screen off, swipe while playing (keeps going), swipe
  while paused (service stops, notification removed), media keys, battery exemption dialog.

### Phase 4 – Quality, fullscreen, gestures, mini-player, PiP (+ HLS live)
- **Quality = one AdaptationSet.** `DashManifestMerger` combines NewPipe's one-stream-per-MPD
  manifests into a single AdaptationSet (one Representation per quality). ExoPlayer then
  does ABR "Auto", and a manual pick is just a `TrackSelectionOverride`: verified with 0
  re-prepares. ref/NewPipe rebuilds the whole source to change quality.
- One codec family per AdaptationSet (H.264 preferred if it reaches 1080p). ExoPlayer won't
  adapt smoothly across codecs.
- **Android's XML parser ≠ desktop JDK.** `setFeature(".../disallow-doctype-decl")` works on the
  JVM (unit test passed) but throws on Android, and the silent fallback hid it. Lesson: log
  every fallback, and run XML/regex/charset code on a device once.
- **Media3 1.11 MediaController did not receive `COMMAND_SET_DEVICE_VOLUME*`**, even though the
  service player had it (`setDeviceVolumeControlEnabled(true)`) and our controller was
  trusted (overriding `onConnect` didn't help). The volume gesture uses `AudioManager`
  (STREAM_MUSIC) instead, like NewPipe's AudioReactor.
- With no surface attached, `videoSize` stays 0. In tests, check the *selected track*.
- Gestures live in a View (`GesturePlayerLayout.dispatchTouchEvent`), not a Compose overlay,
  so PlayerView's own controls keep receiving taps. We only take over (with a CANCEL to the
  child) once a vertical drag or a double-tap is detected.
- Only one video surface at a time: the detail screen hides its PlayerView in fullscreen/PiP,
  and the mini-player shows artwork on the playing video's detail screen.
  `PlayerView.setPlayer(null)` only clears its own surface, so swapping views is safe.
- PiP: `setAutoEnterEnabled` (12+) / `onUserLeaveHint` (8–11), `setSourceRectHint` from the
  main player's bounds, `configChanges` on the activity so PiP/rotation don't recreate it.
  NewPipe/Tubular have no PiP (they use a SYSTEM_ALERT_WINDOW popup).
- Live: YouTube live = HLS only (`media3-exoplayer-hls`). NewPipe disables `rn`/`range` for HLS.
  `ERROR_CODE_BEHIND_LIVE_WINDOW` → `seekToDefaultPosition()` + `prepare()`.
- **Testing:** don't run the emulator on the dev Mac (overheats). Verify on a real phone.

### Phase 5 – Room library
- Video URL as primary key everywhere; ForeignKey + CASCADE does the cleanup (delete playlist →
  items, unsubscribe → feed). Index foreign-key columns, or every parent delete scans the table.
- Flow-returning DAO queries re-emit after any write to their tables, so the detail screen's
  heart and the Favorites list stay in sync with no manual refresh.
- Multi-step writes (`videos` row + history/favorite/playlist row) go in `db.withTransaction`.
- History is written in the service (works with screen off). Failures are logged, never thrown:
  a database problem must not stop playback.
- Resume = `onSetMediaItems` returning `MediaItemsWithStartPosition`. Skip resuming the first 5 s,
  and restart from 0 if the saved position is within 10 s of the end.
- Feed: RSS `FeedInfo` per channel (ref/NewPipe "fast mode"), max 4 in parallel with `Semaphore`.
  Errors are *returned* per channel instead of thrown, so one bad channel can't cancel the rest.
- `flatMapLatest` turns "the channel URL isn't known until details load" into a clean
  subscribed-state Flow.
- Subscriptions are local only. Nothing is sent to YouTube (no account).
