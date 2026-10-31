package com.script.filemanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.documentfile.provider.DocumentFile;
import java.util.*;

public class MainActivity extends Activity {
    private static final int PICK_FOLDER = 41;
    private static final int CREATE_APP_COPY = 53;
    private android.content.pm.ApplicationInfo appToExport;
    private final int bg = 0xff10131d, panel = 0xff202838, green = 0xff20c879;
    private LinearLayout root, list;
    private EditText search;
    private TextView pathLabel, accessStatus;
    private boolean askedAccessThisSession = false;
    private Uri pendingApkInstall;
    private boolean pendingSplitArchive = false;
    private boolean openingInstallPermissionSettings = false;
    private DocumentFile selectedRoot, current;
    private final ArrayDeque<DocumentFile> parents = new ArrayDeque<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        build();
        refreshAccessStatus();
        root.postDelayed(() -> promptForAllFilesAccessIfNeeded(), 450);
        String saved = getPreferences(0).getString("tree", null);
        if (saved != null) {
            try {
                Uri uri = Uri.parse(saved);
                selectedRoot = DocumentFile.fromTreeUri(this, uri);
                current = selectedRoot;
                render();
            } catch (Exception ignored) { }
        }
    }

    private LinearLayout homePage, settingsPage;

    private void build() {
        root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(16, 16, 16, 10);
        root.setBackgroundColor(bg);
        setContentView(root);

        homePage = new LinearLayout(this);
        homePage.setOrientation(1);
        root.addView(homePage, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        homePage.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));
        TextView title = new TextView(this);
        title.setText("FILE MANAGER");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setTypeface(null, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button more = button("⋮", false);
        more.setOnClickListener(this::showMenu);
        titleRow.addView(more, new LinearLayout.LayoutParams(48, 44));

        TextView subtitle = new TextView(this);
        subtitle.setText("Файлы • Папки • APK");
        subtitle.setTextColor(0xffaeb8c8);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, 2, 0, 12);
        homePage.addView(subtitle);

        pathLabel = new TextView(this);
        pathLabel.setText("Папка не выбрана");
        pathLabel.setTextColor(green);
        pathLabel.setTextSize(12);
        pathLabel.setPadding(2, 4, 2, 8);
        homePage.addView(pathLabel);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xff8995a8);
        search.setHint("Поиск файлов и APK");
        search.setTextSize(14);
        search.setPadding(14, 0, 14, 0);
        search.setBackground(round(panel));
        homePage.addView(search, new LinearLayout.LayoutParams(-1, 46));
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int count,int after) {}
            public void onTextChanged(CharSequence s,int st,int before,int count) { renderList(); }
            public void afterTextChanged(android.text.Editable e) {}
        });

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(1);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(-1, -2);
        actionsLp.topMargin = 9;
        homePage.addView(actions, actionsLp);
        Button choose = button("ВЫБРАТЬ ПАПКУ", true);
        choose.setOnClickListener(v -> chooseFolder());
        actions.addView(choose, new LinearLayout.LayoutParams(-1, 46));
        Button up = button("←  НАЗАД", false);
        up.setOnClickListener(v -> goUp());
        LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(-1, 40);
        upLp.topMargin = 6;
        actions.addView(up, upLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollLp.topMargin = 10;
        homePage.addView(scroll, scrollLp);
        list = new LinearLayout(this);
        list.setOrientation(1);
        scroll.addView(list);
        showEmpty("Выбери папку, чтобы увидеть её файлы.");

        settingsPage = new LinearLayout(this);
        settingsPage.setOrientation(1);
        settingsPage.setPadding(8, 12, 8, 8);
        settingsPage.setVisibility(View.GONE);
        root.addView(settingsPage, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView settingsTitle = new TextView(this);
        settingsTitle.setText("SETTINGS");
        settingsTitle.setTextColor(Color.WHITE);
        settingsTitle.setTextSize(24);
        settingsTitle.setTypeface(null, Typeface.BOLD);
        settingsPage.addView(settingsTitle);

        TextView settingsInfo = new TextView(this);
        settingsInfo.setText("Разрешения приложения");
        settingsInfo.setTextColor(0xffaeb8c8);
        settingsInfo.setTextSize(14);
        settingsInfo.setPadding(0, 12, 0, 10);
        settingsPage.addView(settingsInfo);

        accessStatus = new TextView(this);
        accessStatus.setTextColor(0xffffc56b);
        accessStatus.setTextSize(14);
        accessStatus.setPadding(0, 8, 0, 14);
        settingsPage.addView(accessStatus);

        Button permissionButton = button("ОТКРЫТЬ НАСТРОЙКИ ДОСТУПА", true);
        permissionButton.setOnClickListener(v -> openAllFilesSettings());
        settingsPage.addView(permissionButton, new LinearLayout.LayoutParams(-1, 48));

        TextView note = new TextView(this);
        note.setText("Android не показывает обычное всплывающее окно для этого специального разрешения. Откроется системная страница, где нужно вручную включить «Разрешить управление всеми файлами». После возврата статус обновится.");
        note.setTextColor(0xffaeb8c8);
        note.setTextSize(13);
        note.setPadding(2, 14, 2, 0);
        settingsPage.addView(note);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(0);
        tabs.setPadding(0, 8, 0, 0);
        root.addView(tabs, new LinearLayout.LayoutParams(-1, 54));
        Button houseTab = button("⌂ House", true);
        Button apkTab = button("📦 APK", false);
        Button settingsTab = button("⚙ Settings", false);
        tabs.addView(houseTab, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams apkLp = new LinearLayout.LayoutParams(0, -1, 1);
        apkLp.leftMargin = 4;
        tabs.addView(apkTab, apkLp);
        LinearLayout.LayoutParams settingsTabLp = new LinearLayout.LayoutParams(0, -1, 1);
        settingsTabLp.leftMargin = 4;
        tabs.addView(settingsTab, settingsTabLp);

        houseTab.setOnClickListener(v -> {
            homePage.setVisibility(View.VISIBLE);
            settingsPage.setVisibility(View.GONE);
            houseTab.setBackground(round(0xff087d50));
            apkTab.setBackground(round(0xff303b4d));
            settingsTab.setBackground(round(0xff303b4d));
        });
        apkTab.setOnClickListener(v -> startActivity(new Intent(this, ApkManagerActivity.class)));
        settingsTab.setOnClickListener(v -> {
            homePage.setVisibility(View.GONE);
            settingsPage.setVisibility(View.VISIBLE);
            settingsTab.setBackground(round(0xff087d50));
            houseTab.setBackground(round(0xff303b4d));
            apkTab.setBackground(round(0xff303b4d));
            refreshAccessStatus();
        });
    }

    private boolean hasAllFilesAccess() {
        return android.os.Build.VERSION.SDK_INT < 30 || android.os.Environment.isExternalStorageManager();
    }

    private void refreshAccessStatus() {
        if (accessStatus == null) return;
        boolean granted = hasAllFilesAccess();
        accessStatus.setText(granted ? "✓ Доступ ко всем файлам включён" : "⚠ Доступ ко всем файлам выключен");
        accessStatus.setTextColor(granted ? 0xff20c879 : 0xffffc56b);
    }

    private void promptForAllFilesAccessIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < 30 || hasAllFilesAccess() || isFinishing()) return;
        new AlertDialog.Builder(this)
            .setTitle("Требуется доступ к файлам")
            .setMessage("Чтобы просматривать файлы устройства, открой системные настройки и включи «Разрешить управление всеми файлами».")
            .setCancelable(false)
            .setNegativeButton("Позже", (d, w) -> d.dismiss())
            .setPositiveButton("Открыть настройки", (d, w) -> openAllFilesSettings())
            .show();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshAccessStatus();
        if (openingInstallPermissionSettings) {
            openingInstallPermissionSettings = false;
            if (android.os.Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls()) {
                if (pendingApkInstall != null) {
                    Uri uri = pendingApkInstall;
                    pendingApkInstall = null;
                    if (pendingSplitArchive) {
                        pendingSplitArchive = false;
                        installSplitArchiveNow(DocumentFile.fromSingleUri(this, uri));
                    } else {
                        launchApkInstaller(uri);
                    }
                }
            } else {
                pendingApkInstall = null;
                Toast.makeText(this, "Разрешение на установку APK не выдано", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openAllFilesSettings() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } else {
                chooseFolder();
            }
        } catch (Exception e) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Exception ignored) {
                chooseFolder();
            }
        }
    }

    private void chooseFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_FOLDER);
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Создать новый файл").setEnabled(current != null);
        menu.getMenu().add("Сделать копию установленного приложения");
        menu.setOnMenuItemClickListener(item -> {
            if ("Создать новый файл".contentEquals(item.getTitle())) {
                showCreateFileDialog();
                return true;
            }
            if ("Сделать копию установленного приложения".contentEquals(item.getTitle())) {
                showInstalledAppsForExport();
                return true;
            }
            return false;
        });
        menu.show();
    }


    private void showInstalledAppsForExport() {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            java.util.List<android.content.pm.ApplicationInfo> apps =
                pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA);
            java.util.Collections.sort(apps, (a, b) ->
                pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString()));
            java.util.ArrayList<android.content.pm.ApplicationInfo> userApps = new java.util.ArrayList<>();
            java.util.ArrayList<String> labels = new java.util.ArrayList<>();
            for (android.content.pm.ApplicationInfo app : apps) {
                if ((app.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                    userApps.add(app);
                    labels.add(pm.getApplicationLabel(app).toString());
                }
            }
            if (userApps.isEmpty()) {
                new AlertDialog.Builder(this).setMessage("Не найдено установленных приложений.").setPositiveButton("OK", null).show();
                return;
            }
            ListView appList = new ListView(this);
            appList.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels));
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Выбери приложение для копирования")
                .setView(appList)
                .setNegativeButton("Закрыть", null)
                .create();
            appList.setOnItemClickListener((parent, view, position, id) -> {
                dialog.dismiss();
                beginAppExport(userApps.get(position));
            });
            dialog.show();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Ошибка")
                .setMessage("Не удалось получить список приложений: " + e.getMessage())
                .setPositiveButton("OK", null).show();
        }
    }

    private void beginAppExport(android.content.pm.ApplicationInfo app) {
        appToExport = app;
        String label = getPackageManager().getApplicationLabel(app).toString();
        String[] splits = app.splitSourceDirs;
        boolean hasSplits = splits != null && splits.length > 0;
        String extension = hasSplits ? ".apks" : ".apk";
        String safeName = label.replaceAll("[^A-Za-z0-9А-Яа-я._ -]", "_").trim();
        if (safeName.isEmpty()) safeName = "application";
        Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        create.addCategory(Intent.CATEGORY_OPENABLE);
        create.setType(hasSplits ? "application/zip" : "application/vnd.android.package-archive");
        create.putExtra(Intent.EXTRA_TITLE, safeName + extension);
        startActivityForResult(create, CREATE_APP_COPY);
    }

    private void exportSelectedApp(Uri destination) {
        if (appToExport == null) return;
        android.content.pm.ApplicationInfo app = appToExport;
        appToExport = null;
        try (java.io.OutputStream raw = getContentResolver().openOutputStream(destination, "w")) {
            if (raw == null) throw new Exception("Не удалось создать файл");
            String[] splits = app.splitSourceDirs;
            if (splits != null && splits.length > 0) {
                java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(raw);
                copyIntoZip(zip, app.sourceDir, "base.apk");
                for (int i = 0; i < splits.length; i++) {
                    String fileName = new java.io.File(splits[i]).getName();
                    copyIntoZip(zip, splits[i], fileName);
                }
                zip.finish();
                zip.flush();
            } else {
                copyFileToStream(app.sourceDir, raw);
                raw.flush();
            }
            Toast.makeText(this, "Копия приложения сохранена", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Не удалось скопировать APK")
                .setMessage(e.getMessage()).setPositiveButton("OK", null).show();
        }
    }

    private void copyIntoZip(java.util.zip.ZipOutputStream zip, String source, String entryName) throws Exception {
        zip.putNextEntry(new java.util.zip.ZipEntry(entryName));
        try (java.io.InputStream in = new java.io.FileInputStream(source)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = in.read(buffer)) != -1) zip.write(buffer, 0, count);
        }
        zip.closeEntry();
    }

    private void copyFileToStream(String source, java.io.OutputStream out) throws Exception {
        try (java.io.InputStream in = new java.io.FileInputStream(source)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
    }

    private void showCreateFileDialog() {
        if (current == null || !current.canWrite()) {
            Toast.makeText(this, "Сначала выбери папку с доступом на запись", Toast.LENGTH_LONG).show();
            return;
        }
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Например, D.txt");
        new AlertDialog.Builder(this)
            .setTitle("Создать новый файл")
            .setView(input)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Создать", (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\\\")) {
                    Toast.makeText(this, "Некорректное имя файла", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (current.findFile(name) != null) {
                    Toast.makeText(this, "Такой файл уже существует", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    String mime = name.toLowerCase(Locale.ROOT).endsWith(".txt") ? "text/plain" : "application/octet-stream";
                    DocumentFile created = current.createFile(mime, name);
                    if (created != null) {
                        render();
                        Toast.makeText(this, "Файл создан", Toast.LENGTH_SHORT).show();
                    } else Toast.makeText(this, "Не удалось создать файл", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    private android.graphics.drawable.Drawable round(int color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(14);
        return d;
    }

    private Button button(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTypeface(null, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(8, 0, 8, 0);
        b.setBackground(round(primary ? 0xff087d50 : 0xff303b4d));
        return b;
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == CREATE_APP_COPY) {
            if (result == RESULT_OK && data != null) exportSelectedApp(data.getData());
            else appToExport = null;
            return;
        }
        if (request == PICK_FOLDER && result == RESULT_OK && data != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
            } catch (Exception ignored) {}
            selectedRoot = DocumentFile.fromTreeUri(this, uri);
            current = selectedRoot;
            parents.clear();
            getPreferences(0).edit().putString("tree", uri.toString()).apply();
            render();
        }
    }

    private void goUp() {
        if (!parents.isEmpty()) {
            current = parents.pop();
            render();
        } else if (selectedRoot != null) {
            current = selectedRoot;
            render();
        }
    }

    private void render() {
        pathLabel.setText(current == null ? "Папка не выбрана" : "📁 " + current.getName());
        renderList();
    }

    private void showEmpty(String message) {
        TextView t = new TextView(this);
        t.setText(message);
        t.setTextColor(0xffaeb8c8);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setPadding(20, 42, 20, 42);
        list.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }

    private void renderList() {
        if (list == null) return;
        list.removeAllViews();
        if (current == null) {
            showEmpty("Файлы пока не отображаются. Выдай доступ или выбери папку выше.");
            return;
        }
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        DocumentFile[] files;
        try {
            files = current.listFiles();
        } catch (Exception e) {
            showEmpty("Не удалось прочитать эту папку. Выбери её заново.");
            return;
        }
        Arrays.sort(files, (a,b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(b.getName()));
        });
        int shown = 0;
        for (DocumentFile f : files) {
            String name = f.getName() == null ? "Без имени" : f.getName();
            if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q)) continue;
            shown++;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(12, 8, 10, 8);
            row.setBackground(round(panel));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, 66);
            rp.bottomMargin = 8;
            list.addView(row, rp);
            TextView icon = new TextView(this);
            icon.setText(f.isDirectory() ? "📁" : (name.toLowerCase(Locale.ROOT).endsWith(".apk") ? "📦" : "📄"));
            icon.setTextSize(23);
            row.addView(icon);
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(1);
            info.setPadding(12, 0, 0, 0);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            TextView n = new TextView(this);
            n.setText(name);
            n.setTextColor(Color.WHITE);
            n.setTextSize(14);
            n.setMaxLines(1);
            n.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(n);
            TextView meta = new TextView(this);
            meta.setTextColor(0xffaeb8c8);
            meta.setTextSize(11);
            meta.setText(f.isDirectory() ? "Папка" : android.text.format.Formatter.formatFileSize(this, f.length()));
            info.addView(meta);
            row.setOnClickListener(v -> {
                if (f.isDirectory()) {
                    parents.push(current);
                    current = f;
                    search.setText("");
                    render();
                } else openFile(f);
            });
        }
        if (shown == 0) showEmpty(q.isEmpty() ? "В этой папке пока нет файлов." : "Ничего не найдено.");
    }

    private void installApk(DocumentFile file) {
        String fileName = file.getName() == null ? "" : file.getName().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".xapk") || fileName.endsWith(".apkm") || fileName.endsWith(".apks")) {
            installSplitArchive(file);
            return;
        }
        Uri uri = file.getUri();
        if (android.os.Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            pendingSplitArchive = false;
            pendingApkInstall = uri;
            openingInstallPermissionSettings = true;
            new AlertDialog.Builder(this)
                .setTitle("Разрешить установку APK")
                .setMessage("Android попросит разрешить этому приложению устанавливать APK. На следующем экране включи «Разрешить установку из этого источника», затем вернись сюда.")
                .setNegativeButton("Отмена", (d, w) -> { pendingApkInstall = null; })
                .setPositiveButton("Настройки", (d, w) -> {
                    try {
                        Intent settings = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                        settings.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(settings);
                    } catch (Exception e) {
                        openingInstallPermissionSettings = false;
                        pendingApkInstall = null;
                        Toast.makeText(this, "Не удалось открыть настройки установки", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
            return;
        }
        launchApkInstaller(uri);
    }

    private void installSplitArchive(DocumentFile file) {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            pendingSplitArchive = true;
            pendingApkInstall = file.getUri();
            openingInstallPermissionSettings = true;
            new AlertDialog.Builder(this)
                .setTitle("Разрешить установку приложений")
                .setMessage("Включи разрешение «Установка неизвестных приложений», затем вернись сюда.")
                .setNegativeButton("Отмена", (d, w) -> { pendingApkInstall = null; })
                .setPositiveButton("Настройки", (d, w) -> {
                    try {
                        Intent settings = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                        settings.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(settings);
                    } catch (Exception e) {
                        openingInstallPermissionSettings = false;
                        pendingApkInstall = null;
                        Toast.makeText(this, "Не удалось открыть настройки", Toast.LENGTH_LONG).show();
                    }
                }).show();
            return;
        }
        installSplitArchiveNow(file);
    }

    private void installSplitArchiveNow(DocumentFile file) {
        android.content.pm.PackageInstaller installer = getPackageManager().getPackageInstaller();
        int sessionId = -1;
        try {
            android.content.pm.PackageInstaller.SessionParams params =
                new android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            sessionId = installer.createSession(params);
            android.content.pm.PackageInstaller.Session session = installer.openSession(sessionId);
            int apkCount = 0;
            try (java.io.InputStream raw = getContentResolver().openInputStream(file.getUri());
                 java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(raw)) {
                java.util.zip.ZipEntry entry;
                byte[] buffer = new byte[16384];
                while ((entry = zip.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if (!entry.isDirectory() && entryName.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                        String baseName = entryName.substring(entryName.lastIndexOf('/') + 1);
                        String splitName = baseName.equalsIgnoreCase("base.apk") ? "base" :
                            baseName.substring(0, baseName.length() - 4).replaceAll("[^A-Za-z0-9_.-]", "_");
                        try (java.io.OutputStream out = session.openWrite(splitName, 0, -1)) {
                            int count;
                            while ((count = zip.read(buffer)) != -1) out.write(buffer, 0, count);
                            session.fsync(out);
                        }
                        apkCount++;
                    }
                    zip.closeEntry();
                }
            }
            if (apkCount == 0) throw new Exception("В архиве не найдено ни одного APK-файла.");
            Intent statusIntent = new Intent(this, MainActivity.class);
            statusIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            android.app.PendingIntent pending = android.app.PendingIntent.getActivity(
                this, sessionId, statusIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT |
                (android.os.Build.VERSION.SDK_INT >= 23 ? android.app.PendingIntent.FLAG_MUTABLE : 0));
            session.commit(pending.getIntentSender());
            Toast.makeText(this, "Архив передан системному установщику", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            if (sessionId >= 0) {
                try { installer.abandonSession(sessionId); } catch (Exception ignored) { }
            }
            new AlertDialog.Builder(this).setTitle("Не удалось установить архив")
                .setMessage(e.getMessage() == null ? "Неподдерживаемый архив." : e.getMessage())
                .setPositiveButton("OK", null).show();
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.hasExtra(android.content.pm.PackageInstaller.EXTRA_STATUS)) {
            int status = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS,
                android.content.pm.PackageInstaller.STATUS_FAILURE);
            if (status == android.content.pm.PackageInstaller.STATUS_SUCCESS) {
                Toast.makeText(this, "Приложение успешно установлено", Toast.LENGTH_LONG).show();
            } else if (status == android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) startActivity(confirm);
            } else {
                String message = intent.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE);
                new AlertDialog.Builder(this).setTitle("Ошибка установки")
                    .setMessage(message == null ? "Системный установщик отклонил пакет." : message)
                    .setPositiveButton("OK", null).show();
            }
        }
    }

    private void launchApkInstaller(Uri uri) {
        try {
            Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            startActivityForResult(install, 52);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW);
                fallback.setDataAndType(uri, "application/vnd.android.package-archive");
                fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(fallback);
            } catch (Exception second) {
                Toast.makeText(this, "Не удалось запустить установщик APK: " + second.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean isTextFile(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        String[] extensions = {".txt",".lua",".json",".xml",".html",".htm",".css",".js",".java",".kt",".md",".csv",".log",".ini",".cfg",".conf",".yml",".yaml",".properties",".sh",".py",".c",".cpp",".h"};
        for (String ext : extensions) if (n.endsWith(ext)) return true;
        return false;
    }

    private String readText(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0, count;
        while ((count = in.read(buffer)) != -1) {
            total += count;
            if (total > 2 * 1024 * 1024) throw new Exception("Файл больше 2 МБ. Просмотр ограничен.");
            out.write(buffer, 0, count);
        }
        return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private void showTextEditor(DocumentFile file, String content) {
        EditText editor = new EditText(this);
        editor.setText(content);
        editor.setTextColor(0xffe8edf5);
        editor.setHintTextColor(0xff8995a8);
        editor.setTextSize(13);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setPadding(14, 14, 14, 14);
        editor.setBackground(round(panel));
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(false);
        editor.setMinLines(14);
        editor.setSelection(editor.length());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(editor);
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Редактор: " + file.getName())
            .setView(scroll)
            .setNegativeButton("Закрыть", null)
            .setPositiveButton("СОХРАНИТЬ", null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try (java.io.OutputStream out = getContentResolver().openOutputStream(file.getUri(), "wt")) {
                if (out == null) throw new Exception("Не удалось открыть файл для записи");
                out.write(editor.getText().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.flush();
                Toast.makeText(this, "Файл сохранён: " + file.getName(), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } catch (Exception e) {
                new AlertDialog.Builder(this)
                    .setTitle("Ошибка сохранения")
                    .setMessage("Не удалось сохранить файл. Проверь разрешение на запись.\\n" + e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
            }
        }));
        dialog.show();
    }

    private void showTextViewer(String title, String content) {
        ScrollView scroll = new ScrollView(this);
        TextView text = new TextView(this);
        text.setText(content);
        text.setTextColor(0xffe8edf5);
        text.setTextSize(13);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextIsSelectable(true);
        text.setPadding(14, 14, 14, 14);
        scroll.addView(text);
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("Закрыть", null)
            .show();
    }

    private void openZip(DocumentFile file) {
        try {
            java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(getContentResolver().openInputStream(file.getUri()));
            ArrayList<String> names = new ArrayList<>();
            java.util.zip.ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (names.size() < 500) names.add(entry.getName() + (entry.isDirectory() ? "/" : ""));
                zin.closeEntry();
            }
            zin.close();
            if (names.isEmpty()) {
                new AlertDialog.Builder(this).setTitle(file.getName()).setMessage("Архив пуст или не поддерживается.").setPositiveButton("OK", null).show();
                return;
            }
            ListView listView = new ListView(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, names);
            listView.setAdapter(adapter);
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("ZIP: " + file.getName())
                .setView(listView)
                .setNegativeButton("Закрыть", null)
                .create();
            listView.setOnItemClickListener((parent, view, position, id) -> {
                String selected = names.get(position);
                if (selected.endsWith("/")) return;
                if (!isTextFile(selected)) {
                    Toast.makeText(this, "Встроенный просмотр доступен для текстовых файлов", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    java.util.zip.ZipInputStream stream = new java.util.zip.ZipInputStream(getContentResolver().openInputStream(file.getUri()));
                    java.util.zip.ZipEntry item;
                    while ((item = stream.getNextEntry()) != null) {
                        if (item.getName().equals(selected)) {
                            String value = readText(stream);
                            stream.close();
                            showTextViewer(selected, value);
                            return;
                        }
                        stream.closeEntry();
                    }
                    stream.close();
                    Toast.makeText(this, "Файл внутри архива не найден", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Не удалось прочитать: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
            dialog.show();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Ошибка ZIP").setMessage("Не удалось открыть архив: " + e.getMessage()).setPositiveButton("OK", null).show();
        }
    }

    private void openFile(DocumentFile f) {
        String name = f.getName() == null ? "" : f.getName();
        String lower = name.toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".apk") || lower.endsWith(".xapk") || lower.endsWith(".apkm") || lower.endsWith(".apks")) {
                new AlertDialog.Builder(this)
                    .setTitle("Установить приложение?")
                    .setMessage("Файл: " + name + "\\nУстанавливай APK только из источника, которому доверяешь.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Установить", (dialog, which) -> installApk(f))
                    .show();
            } else if (isTextFile(name)) {
                java.io.InputStream in = getContentResolver().openInputStream(f.getUri());
                if (in == null) throw new Exception("Не удалось открыть файл");
                String content = readText(in);
                in.close();
                showTextEditor(f, content);
            } else if (lower.endsWith(".zip")) {
                openZip(f);
            } else if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")) {
                java.io.InputStream in = getContentResolver().openInputStream(f.getUri());
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(in);
                if (in != null) in.close();
                if (bitmap == null) throw new Exception("Формат изображения не поддерживается");
                ImageView image = new ImageView(this);
                image.setImageBitmap(bitmap);
                image.setAdjustViewBounds(true);
                ScrollView scroll = new ScrollView(this);
                scroll.addView(image);
                new AlertDialog.Builder(this).setTitle(name).setView(scroll).setPositiveButton("Закрыть", null).show();
            } else {
                new AlertDialog.Builder(this)
                    .setTitle("Просмотр файла")
                    .setMessage("Встроенный просмотр пока поддерживает текстовые файлы, ZIP и изображения PNG/JPG/WEBP. Для этого формата просмотр ещё не добавлен.")
                    .setPositiveButton("Понятно", null)
                    .show();
            }
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Не удалось открыть файл").setMessage(e.getMessage()).setPositiveButton("OK", null).show();
        }
    }
}
