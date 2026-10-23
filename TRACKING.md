# TFA Splits Tracker

Enable **TFA Splits Tracker** in the plugin list and open the teal stopwatch sidebar button. It works independently of the Fractured Archive encounter helper.

The sidebar shows a live raid, server room splits, cumulative times (hover a split), total raid time, party members, completed-run count, and a best total. Expand a history card to see its rooms, combat time, time between rooms, and roster. Filter history by team size or search for a member/status. The most recent 200 raids are saved separately for each RuneScape profile.

## Timing and recovery

- `Challenge started: <room>.` starts a room. A new Archive Guardian start separates consecutive attempts.
- `Challenge complete: <room>. Duration: <time>. Total: <time>` supplies authoritative room and cumulative times, including both personal-best suffix formats. The cumulative total includes time between rooms; summing room times does not equal the total raid time.
- Ocellus completion and `The Fractured Archive: Total completion time:` independently confirm completion and update one raid record. A missing start can be reconstructed from a completion. A missing room duration remains visibly unrecorded rather than being estimated as a finished split.
- Local deaths and respawn/spectating messages do not end a raid. The explicit Fractured Archive failure and abandonment messages save failed/abandoned attempts.
- The game message buffer is reconciled in chronological order on startup and every game tick, in addition to the chat subscription. A persisted, bounded event journal prevents replayed messages from creating duplicate raids when the plugin is re-enabled. Current progress is persisted at every accepted event or roster change.
- Party names come from the game's eight `TFA_CLIENT_NAME0..7` slots while the TFA HUD is visible, rather than nearby players or RuneLite Party membership. A deferred read after slot updates avoids partially refreshed rosters. Names seen during the run are retained if a member leaves; readiness messages supply the server's team size. Unavailable rosters are labelled explicitly.

The live clocks are estimates between server updates; saved finished splits are exact server values. Enabling late can recover messages still held by the client. Messages no longer retained, events never received by the client, and earlier rosters cannot be reconstructed by this plugin. Incomplete room history is labelled and excluded from the best-total statistic. This plugin does not import past client logs into user history.

## Validation

The replay fixture contains unaltered timing/readiness messages from eight completed raids in local `client_2026-10-20.*.log` through `client_2026-10-22.*.log` files, with 3-, 4-, 5-, and 6-player teams. Timestamps retain server-message ordering; the fixture contains no chat or player names. Tests check every recorded room and total, both finish signals, missing callbacks, replay after persistence, consecutive raids, death/failure/abandonment, all eight roster slots, and sidebar layout at 225px.

With JDK 11:

```
gradlew.bat test jar
```

The panel test renders `build/tfa-splits-panel.png` for visual inspection. Live raid verification remains necessary, particularly for alternative paths without captured completion messages. Deletion tests cover persistent removal, undo, reset suppression and the sidebar context-menu callbacks.
