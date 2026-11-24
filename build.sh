#!/bin/bash
# usage: build.sh <versionCode> <versionName>
set -e
cd "$(dirname "$0")"
VC=${1:?versionCode}; VN=${2:?versionName}
SDK=C:/Android/Sdk; BT=$SDK/build-tools/36.0.0; PL=$SDK/platforms/android-36/android.jar; JDK=C:/Java/jdk21
API=C:/Users/oscar/AppData/Local/Temp/xapi/classes.jar
rm -rf out && mkdir -p out/cls out/dex
$BT/aapt2.exe compile --dir res -o out/res.zip
$BT/aapt2.exe link -o out/unsigned.apk -I $PL --manifest AndroidManifest.xml --min-sdk-version 35 --target-sdk-version 36 --version-code $VC --version-name $VN -A assets out/res.zip
$JDK/bin/javac -source 17 -target 17 -cp "$PL;$API" -d out/cls src/dev/oscar/powermenuplus/*.java
$BT/d8.bat --lib $PL --classpath $API --min-api 35 --output out/dex $(find out/cls -name '*.class')
(cd out/dex && $JDK/bin/jar uf ../unsigned.apk classes.dex); (cd assets && $JDK/bin/jar uf ../out/unsigned.apk META-INF)
[ -f debug.keystore ] || $JDK/bin/keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias k -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=powermenuplus"
$BT/zipalign.exe -f 4 out/unsigned.apk out/aligned.apk
$BT/apksigner.bat sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out PowerMenuPlus-$VN.apk out/aligned.apk
echo BUILD_OK
