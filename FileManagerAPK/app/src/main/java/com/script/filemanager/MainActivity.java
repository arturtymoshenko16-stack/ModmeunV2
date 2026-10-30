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
    private final int bg = 0xff10131d, panel = 0xff202838, green = 0xff20c879;
    private LinearLayout root, list;
    private EditText search;
    private TextView pathLabel;
    private DocumentFile selectedRoot, current;
    private final ArrayDeque<DocumentFile> parents = new ArrayDeque<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        build();
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

    private void build() {
        root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(16, 18, 16, 12);
        root.setBackgroundColor(bg);
        setContentView(root);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));
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
        subtitle.setText("Управление файлами и папками");
        subtitle.setTextColor(0xffaeb8c8);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, 2, 0, 12);
        root.addView(subtitle);

        Button allAccess = button("①  ДОСТУП КО ВСЕМ ФАЙЛАМ", true);
        allAccess.setOnClickListener(v -> openAllFilesSettings());
        root.addView(allAccess, new LinearLayout.LayoutParams(-1, 48));

        Button choose = button("②  ВЫБРАТЬ ПАПКУ", true);
        choose.setOnClickListener(v -> chooseFolder());
        LinearLayout.LayoutParams chooseLp = new LinearLayout.LayoutParams(-1, 48);
        chooseLp.topMargin = 8;
        root.addView(choose, chooseLp);

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        Button up = button("←  НАЗАД", false);
        up.setOnClickListener(v -> goUp());
        nav.addView(up, new LinearLayout.LayoutParams(112, 42));
        pathLabel = new TextView(this);
        pathLabel.setText("Папка не выбрана");
        pathLabel.setTextColor(green);
        pathLabel.setTextSize(12);
        pathLabel.setPadding(12, 0, 0, 0);
        nav.addView(pathLabel, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(-1, -2);
        navLp.topMargin = 12;
        navLp.bottomMargin = 10;
        root.addView(nav, navLp);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0xff8995a8);
        search.setHint("Поиск файлов и APK");
        search.setTextSize(14);
        search.setPadding(14, 0, 14, 0);
        search.setBackground(round(panel));
        root.addView(search, new LinearLayout.LayoutParams(-1, 46));
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int before,int count) { renderList(); }
            public void afterTextChanged(android.text.Editable e) {}
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollLp.topMargin = 12;
        root.addView(scroll, scrollLp);
        list = new LinearLayout(this);
        list.setOrientation(1);
        scroll.addView(list);
        showEmpty("Сначала нажми «ДОСТУП КО ВСЕМ ФАЙЛАМ» или выбери папку.");
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
        menu.setOnMenuItemClickListener(item -> {
            if ("Создать новый файл".contentEquals(item.getTitle())) {
                showCreateFileDialog();
                return true;
            }
            return false;
        });
        menu.show();
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

    private void openFile(DocumentFile f) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(f.getUri(), f.getType() != null ? f.getType() : "*/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Нет приложения для открытия этого файла", Toast.LENGTH_SHORT).show();
        }
    }
}
