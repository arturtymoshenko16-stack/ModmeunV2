mkdir ~/o
cd app/build/outputs/apk/debug
cp *arm64*.apk ~/o/MOD_MENU_64bit.apk
cp *armeabi*.apk ~/o/MOD_MENU_32bit.apk
cd ../../..
cp $(find . -name libGkModMenu.so -path '*arm64*'|head -1) ~/o/libGkModMenu_64bit.so
cp $(find . -name libGkModMenu.so -path '*armeabi*'|head -1) ~/o/libGkModMenu_32bit.so
