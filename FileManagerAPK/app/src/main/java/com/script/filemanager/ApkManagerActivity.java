package com.script.filemanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import java.util.HashSet;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ApkManagerActivity extends Activity {
    private static final int PICK_ZIP = 73;
    private LinearLayout root;
    private TextView result;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff10131d);
        getWindow().setNavigationBarColor(0xff10131d);
        root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(18, 18, 18, 18);
        root.setBackgroundColor(0xff10131d);
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("APK MANAGER");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("Автономный анализ Android-проектов из ZIP. Архив проверяется прямо на устройстве — без отправки на сервер.");
        info.setTextColor(0xffaeb8c8);
        info.setTextSize(14);
        info.setPadding(0, 10, 0, 16);
        root.addView(info);

        Button pick = new Button(this);
        pick.setText("ВЫБРАТЬ ZIP ПРОЕКТА");
        pick.setTextColor(Color.WHITE);
        pick.setAllCaps(false);
        pick.setBackgroundColor(0xff087d50);
        root.addView(pick, new LinearLayout.LayoutParams(-1, 52));
        pick.setOnClickListener(v -> pickZip());

        result = new TextView(this);
        result.setText("Выбери ZIP-файл, чтобы проверить его структуру.");
        result.setTextColor(0xffe8edf5);
        result.setTextSize(14);
        result.setPadding(14, 16, 14, 16);
        result.setBackgroundColor(0xff202838);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = 14;
        root.addView(result, rp);

        TextView note = new TextView(this);
        note.setText("ВАЖНО: пока доступен только анализ ZIP. Компиляция APK на телефоне ещё не подключена. Не запускай неизвестные скрипты и собирай только проекты, на которые у тебя есть разрешение.");
        note.setTextColor(0xffffc56b);
        note.setTextSize(12);
        note.setPadding(2, 16, 2, 0);
        root.addView(note);
    }

    private void pickZip() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, PICK_ZIP);
    }

    @Override protected void onActivityResult(int request, int code, Intent data) {
        super.onActivityResult(request, code, data);
        if (request == PICK_ZIP && code == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            result.setText("Проверяю архив…");
            new Thread(() -> {
                String report;
                try {
                    ZipInputStream zip = new ZipInputStream(getContentResolver().openInputStream(uri));
                    HashSet<String> paths = new HashSet<>();
                    int count = 0;
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (++count > 3000) throw new Exception("Слишком много элементов (лимит 3000).");
                        String name = entry.getName();
                        if (name.startsWith("/") || name.contains("../") || name.equals("..")) {
                            throw new Exception("Обнаружен небезопасный путь внутри архива.");
                        }
                        paths.add(name);
                        zip.closeEntry();
                    }
                    zip.close();

                    boolean settings = false, gradle = false, manifest = false;
                    boolean app = false, source = false, wrapper = false, ndk = false;
                    for (String path : paths) {
                        String p = path.toLowerCase(Locale.ROOT);
                        if (p.equals("settings.gradle") || p.equals("settings.gradle.kts") || p.endsWith("/settings.gradle") || p.endsWith("/settings.gradle.kts")) settings = true;
                        if (p.equals("build.gradle") || p.equals("build.gradle.kts") || p.endsWith("/build.gradle") || p.endsWith("/build.gradle.kts")) gradle = true;
                        if (p.endsWith("src/main/androidmanifest.xml")) manifest = true;
                        if (p.startsWith("app/") || p.contains("/app/")) app = true;
                        if (p.endsWith(".java") || p.endsWith(".kt")) source = true;
                        if (p.equals("gradlew") || p.endsWith("/gradlew") || p.endsWith("gradle-wrapper.properties")) wrapper = true;
                        if (p.endsWith(".so") || p.endsWith("cmakelists.txt") || p.endsWith("android.mk")) ndk = true;
                    }
                    StringBuilder b = new StringBuilder();
                    b.append("АНАЛИЗ ГОТОВ\nЭлементов в ZIP: ").append(count).append("\n\n");
                    b.append(settings ? "✓" : "✗").append(" settings.gradle\n");
                    b.append(gradle ? "✓" : "✗").append(" build.gradle\n");
                    b.append(manifest ? "✓" : "✗").append(" AndroidManifest.xml\n");
                    b.append(app ? "✓" : "•").append(" модуль app\n");
                    b.append(source ? "✓" : "•").append(" исходники Java/Kotlin\n");
                    b.append(wrapper ? "✓" : "•").append(" Gradle Wrapper\n");
                    b.append(ndk ? "• Найдены признаки NDK/C++ или .so\n" : "✓ Явные NDK-файлы не найдены\n\n");
                    if (settings && gradle && manifest) {
                        b.append("Похоже на Android Gradle-проект. Для сборки ещё нужно проверить версии Gradle, Android SDK и зависимости.");
                    } else {
                        b.append("Похоже, архив неполный или содержит не готовый Android Gradle-проект.");
                    }
                    report = b.toString();
                } catch (Exception e) {
                    report = "Ошибка анализа: " + e.getMessage();
                }
                final String output = report;
                runOnUiThread(() -> result.setText(output));
            }).start();
        }
    }
}
