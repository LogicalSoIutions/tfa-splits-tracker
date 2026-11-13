# TFA Splits Tracker

Enable **TFA Splits Tracker** in the plugin list and open the teal stopwatch sidebar button. It works independently of the Fractured Archive encounter helper.

The sidebar shows a live raid, server room splits, cumulative times (hover a split), total raid time, party members, completed-run count, and a best total. Expand a history card to see its rooms, combat time, time between rooms, and roster. Filter history by team size or search for a member/status. The most recent 200 raids are saved separately for each RuneScape profile.

## Timing and recovery

- `Challenge started: <room>.` starts a room. A new Archive Guardian start separates consecutive attempts.
- `Challenge complete: <room>. Duration: <time>. Total: <time>` supplies authoritative room and cumulative times, including both personal-best suffix formats. The cumulative total includes time between rooms; summing room times does not equal the total raid time.
- Ocellus completion and `The Fractured Archive: Total completion time:` independently confirm completion and update one raid record. A missing start can be reconstructed from a completion. A missing room duration remains visibly unrecorded rather than being estimated as a finished split.
- Local deaths and respawn/spectating messages do not end a raid. The explicit Fractured Archive failure and abandonment messages save failed/abandoned attempts.
- On a logged-in game tick with a local player and a non-instanced scene, an unfinished run is saved as `Left raid`. Loading/reconnecting states and HUD disappearance alone do not close an attempt. The live card then shows the game's current party status instead of the old room and roster.
- Once a raid session is known, hiding the HUD between encounters keeps the live card, room progress, and PB deltas visible while still in the instance. Loading also preserves the card. When the HUD clock is unavailable, the sidebar shows the last server-recorded cumulative split with an explanatory tooltip, rather than estimating elapsed time.
- The game message buffer is reconciled in chronological order on startup and every game tick, in addition to the chat subscription. A persisted, bounded event journal prevents replayed messages from creating duplicate raids when the plugin is re-enabled. Current progress is persisted at every accepted event or roster change.
- Party names come from the game's eight `TFA_CLIENT_NAME0..7` slots while the TFA HUD is visible, rather than nearby players or RuneLite Party membership. A deferred read after slot updates avoids partially refreshed rosters. Names seen during the run are retained if a member leaves; readiness messages supply the server's team size. Unavailable rosters are labelled explicitly.

The live raid total reads `InterfaceID.TfaHud.RAID_TIMER` on client ticks and passes a snapshot to the sidebar. It does not extrapolate wall-clock time. The current room displays `In progress` until its server completion supplies a duration; an unavailable HUD timer falls back to the last recorded cumulative split. `RAID_STATE` and `STATUS` supply the game's raid/party status, and an open party HUD outside the instance is not treated as a live raid. Saved finished splits remain exact server values. Enabling late can recover messages still held by the client. Messages no longer retained, events never received by the client, and earlier rosters cannot be reconstructed by this plugin. Incomplete room history is labelled and excluded from the best-total statistic. This plugin does not import past client logs into user history.

## Recorded PB comparisons

The sidebar and infobox tooltips compare against the fastest fully recorded completed raid at the same scale. The visible party slots supply scale immediately, and readiness messages take priority when the server confirms it. Partial roster refreshes and departures do not reduce the observed scale. Until either source is available, the sidebar waits for party information. With no eligible run, it shows `No recorded PB`.

The reference is a saved snapshot selected when scale becomes known, using only runs finished before this attempt started. It stays fixed even if the attempt beats it, history is edited, or the plugin restarts. If the observed party grows or the server reports a different scale, the reference is selected again for that scale. Confirmation of the same scale preserves the existing reference. Only server-completed splits receive deltas: negative is ahead, positive is behind. Cumulative deltas compare the same raid stage and include transitions. A different boss variant is labelled, and no direct room-time delta is shown for it. This is the best run retained in plugin history, not an imported account PB or a sum of individually fastest rooms. The current records do not identify raid mode, so references are separated by scale only. Sidebar PB comparisons are always shown when a reference is available. In settings, **Chat messages** contains **Show room times**, **Show raid total**, **Include PB comparison**, and optional **Show Ocellus phases**. PB comparisons in chat are off by default; enabling the option appends deltas to enabled timing messages without printing extra messages. **Room infoboxes** contains timer and tooltip options. **Timing overlay** contains **Show overlay**, **Include raid splits**, and **Show Ocellus phases**. The overlay is optional (off by default) and never shows PB comparisons.

## Ocellus phases

New live observations record four sub-splits under Ocellus: **P1 Vines**, **P2 Portals**, **P3 Quadrants**, and **P4 Prayer switches**. P1 starts at the live Ocellus challenge-start callback. NPC changes to 16675 and 16677 end P1 and P2; the third quadrant-destruction animation (14859 on NPC 16677) ends P3. These are the same transitions used by the installed raid helper's phase-based points tracking, but this plugin observes game events independently.

Phase boundaries use game ticks (600 ms), not log timestamps or wall-clock estimates. P4 ends with the server Ocellus duration, so a fully observed fight's four phase durations sum to its room duration. Missing starts, missed transitions, or a mid-fight restart leave unavailable phases unrecorded. Repeated events do not create extra splits. Existing completed runs are not backfilled. The sidebar and expanded history show recorded phases; phase PB comparisons appear only when the selected PB contains that phase. Phase chat messages are optional and use the chat PB toggle. The timing overlay can show phases but never PB deltas.

## Validation

The replay fixture contains unaltered timing/readiness messages from eight completed raids in local `client_2026-10-20.*.log` through `client_2026-10-22.*.log` files, with 3-, 4-, 5-, and 6-player teams. Timestamps retain server-message ordering; the fixture contains no chat or player names. Tests check every recorded room and total, both finish signals, missing callbacks, replay after persistence, consecutive raids, death/failure/abandonment, all eight roster slots, and sidebar layout at 225px.

With JDK 11:

```
gradlew.bat test jar
```

The panel test renders `build/tfa-splits-panel.png` for visual inspection. Live raid verification remains necessary, particularly for alternative paths without captured completion messages. Deletion tests cover persistent removal, undo, reset suppression and the sidebar context-menu callbacks.
