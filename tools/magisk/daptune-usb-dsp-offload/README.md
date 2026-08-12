# DapTune USB DSP Offload (turner)

This is a minimal Magisk/KernelSU module for the Xiaomi `turner` product. It
overrides only:

```properties
persist.vendor.audio.usb.offload=true
```

The stock product sets the property to `false`, which selects
`AudioALSAPlaybackHandlerUsb` and bypasses the MediaTek Aurisys/Dolby DSP
pipeline. This module is intended to test the dormant
`AudioALSAPlaybackHandlerDsp` / `DSP_Playback_USB` route.

The module does not modify `/vendor`, does not persist the property to Android's
persistent property storage, and does not change `vendor.audio.usb.super_hifi`
or `persist.vendor.audio.hwdap`.

## Rollback

Disable or uninstall `daptune_usb_dsp_offload` in the root manager and reboot.
The stock `false` value will be restored during the next boot.

If Android cannot finish booting, create the module's `disable` marker from
recovery or ADB and reboot:

```sh
touch /data/adb/modules/daptune_usb_dsp_offload/disable
```

## Validation

Do not treat the property value alone as success. While USB audio is playing,
verify that the active handler changes from `AudioALSAPlaybackHandlerUsb` to
`AudioALSAPlaybackHandlerDsp`, that `DSP_Playback_USB` becomes active, and that
Dolby DAP remains ready after the route has settled.
