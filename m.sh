F=app/src/main/java/com/android/support/MainActivity.java
sed -i '/Button start/d;/start.setText/d;/start.setOnClickListener/d;/addView(start)/d;s|info.setText(.*|info.setText("MOD MENU");|;s|setContentView(layout);|&Main.Start(this);|;s|// Do not repeatedly.*|Main.Start(MainActivity.this);|' $F
