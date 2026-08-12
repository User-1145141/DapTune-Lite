# DapTune

[![CI](https://github.com/silverpoetry/DapTune/actions/workflows/ci.yml/badge.svg)](https://github.com/silverpoetry/DapTune/actions/workflows/ci.yml)
[![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)](docs/compatibility.md)
[![License](https://img.shields.io/github/license/silverpoetry/DapTune)](LICENSE)

English | [简体中文](README.md)

DapTune is a local Dolby DAP 20-band equalizer controller for selected Xiaomi-derived firmware.
It manages curves and writes parameter 110 (`GraphicEqualizerBandGains`) to an existing vendor DSP
through the system-wide `AudioEffect`. It does not process PCM, record audio, enable or circumvent
Dolby, or alter unrelated spatial-audio parameters.

> [!WARNING]
> This is an independent experimental project, not software from Dolby Laboratories or Xiaomi.
> Private audio-effect interfaces can change with a system update. Verify an obvious test curve at
> low volume after first installation, every system update, and every new output path.

## Highlights

- Exact 20-band Dolby DAP plan with native signed Q4 precision (1/16 dB), a `+10 dB` boost ceiling,
  and no artificial attenuation floor;
- Material 3 editor with a large interactive graph, 20 tactile sliders, and a dynamic gain axis;
- Named custom profiles, built-in curves, lossless DapTune JSON, and common EQ-file conversion;
- Peak/mean normalization, smoothing, shift, scale, and a configurable hard upper threshold;
- Per-device rules for speaker, wired, Bluetooth, LE Audio, USB, and HDMI outputs;
- Event-driven foreground automation with clearable operation logs and no polling or audio thread;
- Transactional readback and rollback for proxy DAP, plus correct setter-only handling for
  `DAP_offload` implementations.

## Requirements and scope

- Android 11 (API 30) or newer;
- a system exposing a compatible Xiaomi Dolby DAP implementation;
- Dolby Atmos already enabled and the active playback path actually routed through that DAP.

Validated systems do not require root, LSPosed, Shizuku, or a persistent ADB shell. DapTune checks
the effect descriptor, control ownership, DAP state, profile count, and readback capability before
writing. It does not assume compatibility from a device model name. See
[Compatibility](docs/compatibility.md) for exact identifiers and limitations.

## Native profile format

DapTune JSON v1 is a closed, versioned object that preserves the signed Q4 values without a
floating-point round trip. All six fields are required, unknown fields are rejected, the frequency
array must exactly match the fixed 20-band plan, and each gain must be a signed 32-bit integer no
greater than `160` (`+10 dB`). Negative gains have no application-level floor.

Read the normative [DapTune JSON v1 specification](docs/daptune-json-format.md), use the
[JSON Schema](docs/schema/daptune-profile-v1.schema.json), or start from one of the importable
examples:

- [Flat](examples/profiles/flat.daptune.json);
- [Warm](examples/profiles/warm.daptune.json), Q4-identical to the built-in preset;
- [Deep attenuation](examples/profiles/deep-attenuation.daptune.json), demonstrating values below
  `-10 dB`.

The exact conversion behavior for GraphicEQ, Equalizer APO/AutoEq ParametricEQ, CSV, and TSV is
documented in [Import formats](docs/import-formats.md).

## Build and verify

Use JDK 17 and Android SDK 36:

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintRelease :app:assembleRelease
node .\tools\validate-profile-contract.mjs
```

`release` enables R8 and resource shrinking. Without the complete signing environment it produces
an unsigned APK for source verification only. The `optimized` build uses the same optimization but
a `.debug` application ID and the local debug certificate; it is never an official release. See
[Release signing](docs/release-signing.md).

## Documentation

- [Installation](docs/installation.md)
- [Compatibility and limitations](docs/compatibility.md)
- [DapTune JSON v1](docs/daptune-json-format.md)
- [Import formats](docs/import-formats.md)
- [Architecture](docs/architecture.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Privacy](PRIVACY.md), [Security](SECURITY.md), and [Contributing](CONTRIBUTING.md)

## License and trademarks

The source is licensed under the [Apache License 2.0](LICENSE). Third-party components are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Dolby, Dolby Atmos, Xiaomi, and all other names and
marks belong to their respective owners. References describe compatibility and test environments
only; they do not imply endorsement, authorization, or affiliation.
