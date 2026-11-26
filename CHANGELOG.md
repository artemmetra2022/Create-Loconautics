# Loconautics Changelog

## Ver 0.1.16BETA

### Added
- **Driver hints**: orange, silent HUD when the driver tries to move while blocked (stop-cock, emergency brake notch, derail, safety latch, dispatcher hold, red signal, brakes applied, low pipe pressure, open doors).

### Changed
- Network protocol version 88.


## Ver 0.1.15BETA

### Added
- Safety Unit: Red signal passed emergency-brakes on SPAD.
- Dispatcher: block section list; Close/Open signal via SignalOverrides.
- Dispatcher: session event journal.
- Distant Signal (signal_repeater) without splitting block sections.

### Changed
- Network protocol version 87.


## Ver 0.1.13BETA

### Added
- A dedicated three-aspect railway signal that uses Create's native track-signal boundaries and signal-edge groups to split a route into automatically detected block sections.
- Signal aspects show red for occupied/reserved Sable-train sections and invalid/unlinked signal boundaries, yellow for restrictive cross-signal paths, and green for clear sections. Redstone power can hold a signal at danger; a wrench cycles entry/cross-signal mode.
- Ponder training for selecting a track with the signal item, placing block-section boundaries, understanding signal aspects, and using redstone and the wrench.

### Changed
- Increased the mod version for the railway-signalling feature.


## Ver 0.1.10BETA

### Fixed
- Track-switch stands now claim junctions exclusively. A second stand cannot take control of a junction already held by another stand, and the claim survives chunk unload/reload.
- Retarder track now has its own Create track material, so extended track placement and curves retain the retarder track instead of silently becoming ordinary andesite track.
- Added loot tables for the catenary mast, electric traction motor, overhead contact wire, pantograph and sand storage. The catenary mast drops its pole item from the base part only.
- ComputerCraft route assignment now copies the route list instead of retaining a potentially shared mutable list.

### Changed
- Moved high-volume diagnostic messages across train physics, scheduling, portals, couplers, ropes and related systems from INFO to DEBUG.
- Guarded verbose wheel-slip and rail-selector diagnostics behind the debug-enabled check.
- Added stale-entry cleanup for the retarder-track chunk cache.
- Added Russian derailment-configuration and train-status translations, plus occupied-switch messages in Russian and English.
- Increased the Gradle JVM heap to 3 GB.
- The release workflow now skips assets already attached to a versioned release instead of overwriting existing release files.

## See full history in repository releases and archive for complete older entries.
