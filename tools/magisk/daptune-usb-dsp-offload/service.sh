#!/system/bin/sh

MODULE_DIR=${0%/*}
PROPERTY_NAME=persist.vendor.audio.usb.offload
PROPERTY_VALUE=true
RESETPROP_BIN=/data/adb/ksu/bin/resetprop
WAIT_COUNT=0
WAIT_LIMIT=120

if [ ! -x "$RESETPROP_BIN" ]; then
  RESETPROP_BIN=resetprop
fi

while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$WAIT_COUNT" -lt "$WAIT_LIMIT" ]; do
  sleep 1
  WAIT_COUNT=$((WAIT_COUNT + 1))
done

# Some vendor init scripts rewrite persist.vendor.audio.* properties after the
# post-fs-data stage. Reassert the non-persistent override before USB playback.
"$RESETPROP_BIN" -n "$PROPERTY_NAME" "$PROPERTY_VALUE"

{
  echo "stage=late-start"
  echo "boot_completed=$(getprop sys.boot_completed)"
  echo "property=$PROPERTY_NAME"
  echo "value=$(getprop "$PROPERTY_NAME")"
} > "$MODULE_DIR/runtime-state.txt"
