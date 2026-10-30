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
    private final int bg = Color.rgb(15, 18, 27), panel = Color.rgb(27, 32, 45), green = Color.rgb(91, 220, 145);
    private LinearLayout root, list;
    private EditText search;
    private TextView pathLabel, empty;
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
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                selectedRoot = DocumentFile.fromTreeUri(this, uri);
                current = selectedRoot;
                render();
            } catch (Exception ignored) { }
        }
    }

    private void build() {
        root = new LinearLayout(this); root.setOrientation(1); root.setPadding(18, 18, 18, 10); root.setBackgroundColor(bg);
        setContentView(root);
        LinearLayout titleRow = new LinearLayout(this); titleRow.setGravity(Gravity.CENTER_VERTICAL); root.addView(titleRow);
        TextView title = new TextView(this); title.setText("FILE MANAGER"); title.setTextColor(Color.WHITE); title.setTextSize(23); title.setTypeface(null, Typeface.BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button more = button("⋮"); more.setTextSize(22); more.setOnClickListener(this::showMenu); titleRow.addView(more, new LinearLayout.LayoutParams(48, 46));
        TextView subtitle = new TextView(this); subtitle.setText("Файлы • Папки • APK"); subtitle.setTextColor(0xff9ba6b8); subtitle.setTextSize(13); root.addView(subtitle);
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(0); actions.setPadding(0,16,0,12); root.addView(actions);
        Button choose = button("Разрешить доступ к папке"); choose.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i, PICK_FOLDER);
        }); actions.addView(choose, new LinearLayout.LayoutParams(0, 48, 1));
        Button up = button("← Назад"); up.setOnClickListener(v -> goUp()); LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(0,48,1); upLp.leftMargin=8; actions.addView(up, upLp);
        search = new EditText(this); search.setSingleLine(true); search.setTextColor(Color.WHITE); search.setHintTextColor(0xff8993a5); search.setHint("Поиск файлов и APK"); search.setPadding(14,0,14,0); search.setBackground(round(panel));
        root.addView(search, new LinearLayout.LayoutParams(-1,48));
        search.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int before,int count) { renderList(); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        pathLabel = new TextView(this); pathLabel.setTextColor(green); pathLabel.setTextSize(12); pathLabel.setPadding(2,14,2,10); root.addView(pathLabel);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        list = new LinearLayout(this); list.setOrientation(1); scroll.addView(list);
        empty = new TextView(this); empty.setTextColor(0xffaab3c2); empty.setGravity(Gravity.CENTER); empty.setText("Нажми «Разрешить доступ к папке» и выбери папку"); root.addView(empty);
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Новый файл").setEnabled(current != null);
        menu.setOnMenuItemClickListener(item -> {
            if ("Новый файл".contentEquals(item.getTitle())) { showCreateFileDialog(); return true; }
            return false;
        });
        menu.show();
    }

    private void showCreateFileDialog() {
        if (current == null || !current.canWrite()) {
            Toast.makeText(this, "Сначала выбери папку с доступом на запись", Toast.LENGTH_LONG).show();
            return;
        }
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setTextColor(Color.BLACK);
        nameInput.setHint("Например, D.txt");
        nameInput.setPadding(28, 12, 28, 12);
        new AlertDialog.Builder(this)
            .setTitle("Создать новый файл")
            .setView(nameInput)
            .setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss())
            .setPositiveButton("OK", (dialog, which) -> {
                String name = nameInput.getText().toString().trim();
                if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\")) {
                    Toast.makeText(this, "Введи корректное имя файла", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (current.findFile(name) != null) {
                    Toast.makeText(this, "Файл с таким именем уже существует", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    String mime = name.toLowerCase(Locale.ROOT).endsWith(".txt") ? "text/plain" : "application/octet-stream";
                    DocumentFile created = current.createFile(mime, name);
                    if (created != null) {
                        render();
                        Toast.makeText(this, "Файл создан: " + name, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Не удалось создать файл", Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Ошибка создания файла: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    private android.graphics.drawable.Drawable round(int color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(); d.setColor(color); d.setCornerRadius(16); return d;
    }
    private Button button(String text) {
        Button b = new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setTextSize(13); b.setBackground(round(0xff315b49)); return b;
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if(request==PICK_FOLDER && result==RESULT_OK && data!=null) {
            Uri uri=data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)); } catch(Exception ignored) {}
            selectedRoot=DocumentFile.fromTreeUri(this,uri); current=selectedRoot; parents.clear();
            getPreferences(0).edit().putString("tree",uri.toString()).apply(); render();
        }
    }
    private void goUp() {
        if(!parents.isEmpty()) { current=parents.pop(); render(); }
        else if(selectedRoot!=null) { current=selectedRoot; render(); }
    }
    private void render() {
        pathLabel.setText(current==null ? "Папка не выбрана" : "📁 " + current.getName());
        renderList();
    }
    private void renderList() {
        if(list==null) return;
        list.removeAllViews();
        if(current==null) { empty.setVisibility(View.VISIBLE); return; }
        empty.setVisibility(View.GONE);
        String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        DocumentFile[] files=current.listFiles();
        Arrays.sort(files,(a,b)->{
            if(a.isDirectory()!=b.isDirectory()) return a.isDirectory()?-1:1;
            return String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(b.getName()));
        });
        int shown=0;
        for(DocumentFile f:files) {
            String name=f.getName()==null?"Без имени":f.getName();
            if(!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q)) continue;
            shown++;
            LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(12,10,10,10); row.setBackground(round(panel));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,64); rp.bottomMargin=7; list.addView(row,rp);
            TextView icon=new TextView(this); icon.setText(f.isDirectory()?"📁":(name.toLowerCase(Locale.ROOT).endsWith(".apk")?"📦":"📄")); icon.setTextSize(23); row.addView(icon);
            LinearLayout info=new LinearLayout(this); info.setOrientation(1); info.setPadding(12,0,0,0); row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            TextView n=new TextView(this); n.setText(name); n.setTextColor(Color.WHITE); n.setTextSize(14); n.setMaxLines(1); n.setEllipsize(android.text.TextUtils.TruncateAt.END); info.addView(n);
            TextView meta=new TextView(this); meta.setTextColor(0xff9ba6b8); meta.setTextSize(11); meta.setText(f.isDirectory()?"Папка":android.text.format.Formatter.formatFileSize(this,f.length())); info.addView(meta);
            row.setOnClickListener(v->{
                if(f.isDirectory()) { parents.push(current); current=f; search.setText(""); render(); }
                else openFile(f);
            });
        }
        if(shown==0) { TextView t=new TextView(this); t.setText("Папка пуста"); t.setTextColor(0xffaab3c2); t.setGravity(Gravity.CENTER); t.setPadding(0,35,0,0); list.addView(t); }
    }
    private void openFile(DocumentFile f) {
        try {
            Intent i=new Intent(Intent.ACTION_VIEW); i.setDataAndType(f.getUri(), f.getType()!=null?f.getType():"*/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(i);
        } catch(Exception e) { Toast.makeText(this,"Не найдено приложение для открытия файла",Toast.LENGTH_SHORT).show(); }
    }
}
