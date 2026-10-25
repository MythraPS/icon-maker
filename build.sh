#!/usr/bin/env bash
# Quick local build of IconMaker.apk with the Android command-line tools, no Gradle.
# (F-Droid and anyone with Android Studio use the Gradle build instead: ./gradlew assembleRelease)
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/opt/jdk-21.0.13+9}"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"; BT="$SDK/build-tools/36.1.0"; JAR="$SDK/platforms/android-36/android.jar"
KEY="$HOME/.config/icon-maker/release.keystore"
M=app/src/main
VCODE=$(grep -oP 'versionCode\s*=\s*\K\d+' app/build.gradle.kts); VNAME=$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts)
rm -rf build && mkdir -p build/gen build/classes build/dex
# Gradle takes the package from the build file; aapt2 needs it in the manifest
sed 's|<manifest xmlns:android="http://schemas.android.com/apk/res/android">|<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="net.mythra.iconmaker">|' $M/AndroidManifest.xml > build/AndroidManifest.xml
"$BT/aapt2" compile --dir $M/res -o build/res.zip
"$BT/aapt2" link -I "$JAR" --manifest build/AndroidManifest.xml -o build/unsigned.apk --java build/gen build/res.zip \
  --min-sdk-version 26 --target-sdk-version 36 --version-code "$VCODE" --version-name "$VNAME"
"$JAVA_HOME/bin/javac" --release 17 -classpath "$JAR" -d build/classes $(find $M/java build/gen -name '*.java')
"$BT/d8" --release --min-api 26 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
python3 -c "import zipfile; zipfile.ZipFile('build/unsigned.apk', 'a', zipfile.ZIP_DEFLATED).write('build/dex/classes.dex', 'classes.dex')"
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [ ! -f "$KEY" ]; then
  mkdir -p "$(dirname "$KEY")"
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KEY" -storepass iconmaker -keypass iconmaker -alias iconmaker \
    -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=Icon Maker" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KEY" --ks-pass pass:iconmaker --key-pass pass:iconmaker --out IconMaker.apk build/aligned.apk
"$BT/apksigner" verify IconMaker.apk && echo "built: $(pwd)/IconMaker.apk $VNAME ($VCODE)"
