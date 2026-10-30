sed -i 's/"Button_Кнопка/"Toggle_Toggles/' app/src/main/cpp/main.cpp
sed -i '/abiFilters/d;s/buildTypes {/splits { abi { enable true; reset(); include "arm64-v8a","armeabi-v7a"; universalApk false } }\n buildTypes {/' app/build.gradle
