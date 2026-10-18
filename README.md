# Shimmer

HDR menu panoramas for Minecraft Java Edition 26.3. Shimmer loads Radiance RGBE `.hdr` textures directly from resource packs and tone maps them for SDR displays, using floating-point GPU textures on OpenGL and Vulkan.

## Installation

Requires **Minecraft 26.3**, **Java 25**, and **Fabric Loader 0.19.5 or later**.

1. Download the Shimmer JAR from [Releases](https://github.com/kkaemok/Shimmer/releases) and put it in your client's `mods` folder.
2. Enable a resource pack containing six HDR panorama faces.
3. Adjust exposure through Mod Menu or `config/shimmer.json`.

The required Fabric resource-loading modules are bundled. [Mod Menu 21.0.0](https://modrinth.com/mod/modmenu/version/21.0.0) is optional. Shimmer is client-only.

## Resource packs

Use this layout inside a Java resource pack:

```text
pack.mcmeta
assets/minecraft/textures/gui/title/background/
  panorama_0.hdr
  panorama_1.hdr
  panorama_2.hdr
  panorama_3.hdr
  panorama_4.hdr
  panorama_5.hdr
```

Example `pack.mcmeta` for Minecraft 26.3:

```json
{
  "pack": {
    "description": "HDR panorama",
    "min_format": [97, 1],
    "max_format": [97, 1]
  }
}
```

All six faces must be square and have matching dimensions, up to 2048×2048. Use vanilla's panorama face numbering and orientation. Supported files use `FORMAT=32-bit_rle_rgbe`, flat RGBE or modern scanline RLE, and `-Y height +X width` orientation. Files are limited to 64 MiB each.

Shimmer selects a complete set from a single pack, following resource-pack priority. Missing or invalid sets fall back to the vanilla panorama. Proprietary panorama assets are not included.

## Settings

With Mod Menu, open **Mods → Shimmer → Configure**. The screen includes manual exposure entry, ±0.5 EV buttons, tone mapping, the vanilla overlay, and untagged color space. English and Korean translations are available.

**Save & Apply** saves settings and reloads resources. **Cancel** or Escape discards edits. **Reset Defaults** resets the draft until saved.

You can also edit `config/shimmer.json` and press F3+T:

```json
{
  "enabled": true,
  "exposure": 0.0,
  "toneMapping": "ACES",
  "preserveVanillaOverlay": true,
  "untaggedColorSpace": "LINEAR_SRGB"
}
```

| Setting | Values |
| --- | --- |
| `enabled` | Enable HDR panoramas. |
| `exposure` | Manual EV from −20 to +20. +1 doubles brightness; −1 halves it. |
| `toneMapping` | `ACES` or luminance-preserving `REINHARD`. |
| `preserveVanillaOverlay` | Keep vanilla's panorama overlay. Menu blur remains a Minecraft setting. |
| `untaggedColorSpace` | `LINEAR_SRGB` or `RADIANCE` for files without a `PRIMARIES` header. Tagged files use their own primaries. |

Bright panoramas may need negative exposure. Exposure is entirely manual.

## Rendering

RGBE data stays in linear floating point through decoding and color conversion. Cubemaps use `RGBA16_FLOAT`, switching to `RGBA32_FLOAT` when the source range requires it. Tone mapping and sRGB encoding run in the panorama fragment shader. Textures are uploaded on resource reload, while vanilla controls panorama rotation and projection.

Shimmer renders HDR content to SDR; it does not enable HDR10 monitor output. Mods that replace menu rendering may need compatibility testing. At 2048×2048, cubemaps require 192 MiB of GPU storage for half precision or 384 MiB for float32, plus temporary decoding/upload memory.

## Building

Use Java 25 and the included Gradle wrapper:

```sh
./gradlew build
./gradlew runClient
./gradlew runClient -PwithModMenu=true
./gradlew runClient -PshimmerBackend=VULKAN
```

On Windows, use `gradlew.bat`. The compiled JAR is written to `build/libs`.

`build` runs the decoder, color conversion, resource selection, cleanup, and configuration tests. `generateTestPack` creates original labeled HDR fixtures in `build/test-resourcepack`. Development clients use the isolated `run` folder.
