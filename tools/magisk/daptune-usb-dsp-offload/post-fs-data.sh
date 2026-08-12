#!/system/bin/sh

MODULE_DIR=${0%/*}
PROPERTY_NAME=persist.vendor.audio.usb.offload
PROPERTY_VALUE=true
RESETPROP_BIN=/data/adb/ksu/bin/resetprop

if [ ! -x "$RESETPROP_BIN" ]; then
  RESETPROP_BIN=resetprop
fi

# Bypass property_service so the vendor property context cannot reject the
# runtime override. Do not use -p: uninstalling the module must restore the
# stock value on the next boot without leaving persistent property data.
"$RESETPROP_BIN" -n "$PROPERTY_NAME" "$PROPERTY_VALUE"

{
  echo "stage=post-fs-data"
  echo "property=$PROPERTY_NAME"
  echo "value=$(getprop "$PROPERTY_NAME")"
} > "$MODULE_DIR/boot-state.txt"
