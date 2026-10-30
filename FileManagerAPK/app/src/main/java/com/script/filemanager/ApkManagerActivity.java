package com.script.filemanager;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ApkManagerActivity extends Activity {
    private static final int PICK_ZIP = 73;
    private static final int MAX_ENTRIES = 3000;
    private static final long MAX_FILE_BYTES = 100L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 250L * 1024L * 1024L;
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
        info.setText("Импорт Android-проекта из ZIP. Архив распаковывается в закрытую папку приложения и проверяется на устройстве.");
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
        result.setText("Выбери ZIP с Android Gradle-проектом. Я распакую его и проверю структуру.");
        result.setTextColor(0xffe8edf5);
        result.setTextSize(14);
        result.setPadding(14, 16, 14, 16);
        result.setBackgroundColor(0xff202838);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        rp.topMargin = 14;
        root.addView(result, rp);

        TextView note = new TextView(this);
        note.setText("ЛОКАЛЬНЫЙ ИМПОРТ УЖЕ РАБОТАЕТ. Сборочный движок Code On The Go ещё не встроен, поэтому APK из выбранного ZIP пока не создаётся. Не показываем фиктивную кнопку сборки.");
        note.setTextColor(0xffffc56b);
        note.setTextSize(12);
        note.setPadding(2, 16, 2, 0);
        root.addView(note);
    }

    private void pickZip() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, PICK_ZIP);
    }

    @Override protected void onActivityResult(int request, int code, Intent data) {
        super.onActivityResult(request, code, data);
        if (request == PICK_ZIP && code == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            result.setText("Импортирую и проверяю ZIP…");
            new Thread(() -> {
                String report;
                try {
                    File base = new File(getFilesDir(), "imported_projects");
                    if (!base.exists() && !base.mkdirs()) throw new Exception("Не удалось создать папку проектов.");
                    File projectDir = new File(base, "project_" + System.currentTimeMillis());
                    if (!projectDir.mkdirs()) throw new Exception("Не удалось создать папку проекта.");

                    HashSet<String> paths = new HashSet<>();
                    int count = 0;
                    long totalBytes = 0;
                    try (InputStream input = getContentResolver().openInputStream(uri);
                         ZipInputStream zip = new ZipInputStream(input)) {
                        if (input == null) throw new Exception("Не удалось открыть ZIP.");
                        ZipEntry entry;
                        byte[] buffer = new byte[8192];
                        while ((entry = zip.getNextEntry()) != null) {
                            if (++count > MAX_ENTRIES) throw new Exception("Слишком много элементов (лимит 3000).");
                            String name = entry.getName();
                            if (name == null || name.trim().isEmpty() || name.startsWith("/") ||
                                name.startsWith("\\\\") || name.contains("../") ||
                                name.equals("..") || name.contains("\\\\")) {
                                throw new Exception("В архиве обнаружен небезопасный путь.");
                            }

                            File out = new File(projectDir, name);
                            String rootPath = projectDir.getCanonicalPath() + File.separator;
                            String outPath = out.getCanonicalPath();
                            if (!outPath.startsWith(rootPath) && !outPath.equals(projectDir.getCanonicalPath())) {
                                throw new Exception("Путь файла выходит за пределы проекта.");
                            }

                            paths.add(name);
                            if (entry.isDirectory()) {
                                if (!out.exists() && !out.mkdirs()) throw new Exception("Не удалось создать папку: " + name);
                            } else {
                                File parent = out.getParentFile();
                                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                                    throw new Exception("Не удалось создать папку для: " + name);
                                }
                                long fileBytes = 0;
                                try (FileOutputStream stream = new FileOutputStream(out)) {
                                    int read;
                                    while ((read = zip.read(buffer)) != -1) {
                                        fileBytes += read;
                                        totalBytes += read;
                                        if (fileBytes > MAX_FILE_BYTES) throw new Exception("Файл слишком большой: " + name);
                                        if (totalBytes > MAX_TOTAL_BYTES) throw new Exception("Распакованный архив превышает лимит 250 МБ.");
                                        stream.write(buffer, 0, read);
                                    }
                                }
                            }
                            zip.closeEntry();
                        }
                    }

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
                    b.append("ИМПОРТ ЗАВЕРШЁН\nЭлементов: ").append(count)
                     .append("\nРаспаковано: ").append(totalBytes / 1024).append(" КБ\n\n");
                    b.append(settings ? "✓" : "✗").append(" settings.gradle\n");
                    b.append(gradle ? "✓" : "✗").append(" build.gradle\n");
                    b.append(manifest ? "✓" : "✗").append(" AndroidManifest.xml\n");
                    b.append(app ? "✓" : "•").append(" модуль app\n");
                    b.append(source ? "✓" : "•").append(" исходники Java/Kotlin\n");
                    b.append(wrapper ? "✓" : "•").append(" Gradle Wrapper\n");
                    b.append(ndk ? "• Найдены NDK/C++ или .so файлы\n" : "✓ Явные NDK-файлы не найдены\n");
                    b.append("\nПапка проекта:\n").append(projectDir.getAbsolutePath()).append("\n\n");
                    if (settings && gradle && manifest) {
                        b.append("Похоже на Android Gradle-проект. ZIP импортирован локально. Для создания APK ещё требуется встроенный Gradle runtime и Android toolchain Code On The Go.");
                    } else {
                        b.append("Архив импортирован, но структура не похожа на полный Android Gradle-проект.");
                    }
                    report = b.toString();
                } catch (Exception e) {
                    report = "Ошибка импорта: " + e.getMessage();
                }
                final String output = report;
                runOnUiThread(() -> result.setText(output));
            }).start();
        }
    }
}
