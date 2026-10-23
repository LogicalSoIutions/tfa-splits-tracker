# TFA Splits Tracker

An external RuneLite plugin for The Fractured Archive. It has no dependency on a custom client or the Fractured Archive encounter-helper plugin.

## Features

- A teal stopwatch sidebar with live total and room clocks.
- Exact server-reported room splits, cumulative times, total raid time and party members.
- Expandable raid history with combat/transition times, team-size filters and party-member search.
- Collapsible day sections with saved raid counts, newest days first. Raids are grouped by their local start date; daily counts follow your team-size and member filters. Hover the day header for completed versus other attempt counts.
- Separate saved history for each RuneScape profile, including existing history from the former built-in version.
- Right-click a history card to **Delete raid**, or a room row to **Delete room time**. **Undo last deletion** restores the most recently deleted saved record, even after restarting.
- Right-click the live raid card to **Reset current tracker**. That discards the current attempt and waits for the next Archive Guardian start; it cannot be undone.

Deleting a room preserves the server total but marks the raid's room history partial and excludes it from the best-total statistic. The event journal prevents deleted records from returning when retained messages are replayed.

## Build

Requires JDK 11 or newer. Open this folder as its own Gradle project, or run:

```powershell
.\gradlew.bat test jar
```

The plugin-only JAR is `build/libs/tfa-splits-tracker.jar`. It contains no RuneLite client classes or bundled dependencies. Builds use the latest public RuneLite release; a specific release can be selected with `-PruneLiteVersion=1.13.3`.

To run a development client with the plugin loaded:

```powershell
.\gradlew.bat run
```

For a Jagex account, follow RuneLite's [development login instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

## Sideload while awaiting Plugin Hub approval

1. Close your development client.
2. Copy `build/libs/tfa-splits-tracker.jar` into `%USERPROFILE%\.runelite\sideloaded-plugins\`.
3. Start a sideload-capable development client with `-ea` and `--developer-mode`, then enable **TFA Splits Tracker**.

The normal installed launcher does not enable developer mode. Alternatively, use the `run` task above. If using an older custom client that still includes the built-in tracker, disable its built-in copy before enabling this external version. Both versions use the same saved-history configuration group, so only one should run at a time.

## Plugin Hub submission

This is a standalone Gradle project with `runelite-plugin.properties`, a BSD 2-Clause license and `build=standard`. Its production code uses only public RuneLite APIs and RuneLite's existing dependencies. Mockito is used only by local tests.

1. Publish **the contents of this folder** as a public GitHub repository. Check the author in `runelite-plugin.properties` before publishing.
2. Commit the sources and Gradle wrapper; exclude `build/` and `.gradle/`.
3. Fork [runelite/plugin-hub](https://github.com/runelite/plugin-hub), then add `plugins/tfa-splits-tracker` containing your actual repository URL and the full commit hash:

```properties
repository=https://github.com/YOUR_USERNAME/tfa-splits-tracker.git
commit=YOUR_FULL_40_CHARACTER_COMMIT_HASH
```

4. Open a pull request on Plugin Hub and follow its CI/review feedback. Updates use a new commit hash in that same manifest file.

See the [official submission instructions](https://github.com/runelite/plugin-hub#submitting-a-plugin). This project has not been submitted or approved yet.

## Timing coverage

See [TRACKING.md](TRACKING.md) for event formats, recovery limits and the recorded-raid fixtures. Ezekiel, Dagon'hai Trio and Moia are included in room recognition; Dagon'hai Trio and Moia still need live verification. The existing replay fixtures cover eight complete raids across 3-, 4-, 5- and 6-player teams.
