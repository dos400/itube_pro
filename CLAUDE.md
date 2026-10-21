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
:core:player  (Phase 3) PlaybackService : MediaSessionService, ExoPlayer, MediaSource building
```

Dependency direction: `app → domain, core:player, data` · `data → domain` · `core:player → domain`.

### Module rules
- **No NewPipe types outside `:data`.** Map them to `:domain` models in `data/mapper`.
  Exceptions are converted to `AppException` in `data/error/ExceptionMapper.kt`.
- `:domain` has no Android, DI or library dependencies. Use cases are plain classes and
  get constructed in `app/di/UseCaseModule.kt`.
- `:app` depends on `:data` only so Hilt can see its modules. The one allowed import is
  `NewPipeInitializer` in `ItubeApp`.
- The UI talks to the player only through `MediaController` (Phase 3).
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
