#!/usr/bin/env bash
# Construye e instala la sonda sin Gradle: JBR de Android Studio + aapt2/d8/apksigner.
# Deliberadamente artesanal: es una sonda desechable, no el esqueleto del proyecto.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
PLATFORM="$SDK/platforms/android-37.0"
BT="$SDK/build-tools/36.0.0"
JAVA_HOME_DIR="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
OUT="$HERE/build"

# d8, apksigner y zipalign son envoltorios que invocan `java` del PATH.
export JAVA_HOME="$JAVA_HOME_DIR"
export PATH="$JAVA_HOME_DIR/bin:$PATH"

rm -rf "$OUT"; mkdir -p "$OUT/classes"

echo "1/4 compilando..."
"$JAVA_HOME_DIR/bin/javac" --release 11 -nowarn \
  -cp "$PLATFORM/android.jar:$PLATFORM/optional/android.car.jar" \
  -d "$OUT/classes" $(find "$HERE/src" -name '*.java')

echo "2/4 dexando..."
"$BT/d8" --min-api 28 --lib "$PLATFORM/android.jar" \
  --output "$OUT" $(find "$OUT/classes" -name '*.class')

echo "3/4 empaquetando..."
"$BT/aapt2" link -I "$PLATFORM/android.jar" \
  --manifest "$HERE/AndroidManifest.xml" \
  --min-sdk-version 28 --target-sdk-version 34 \
  -o "$OUT/unsigned.apk"
(cd "$OUT" && zip -q unsigned.apk classes.dex)

echo "4/4 firmando..."
KS="$HOME/.android/debug.keystore"
if [ ! -f "$KS" ]; then
  "$JAVA_HOME_DIR/bin/keytool" -genkeypair -keystore "$KS" -storepass android \
    -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/probe.apk"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey "$OUT/probe.apk"

echo "APK listo: $OUT/probe.apk"
