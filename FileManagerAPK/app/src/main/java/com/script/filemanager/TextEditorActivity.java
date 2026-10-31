package com.script.filemanager;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class TextEditorActivity extends Activity {
    private final int bg = 0xff10131d, panel = 0xff202838, green = 0xff20c879;
    private EditText editor;
    private Uri fileUri;
    private String fileName;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        fileUri = getIntent().getData();
        fileName = getIntent().getStringExtra("file_name");
        if (fileName == null) fileName = "Текстовый файл";
        if (fileUri == null) {
            Toast.makeText(this, "Не передан файл", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        buildScreen();
        loadFile();
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(18);
        return d;
    }

    private Button makeButton(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setBackground(rounded(primary ? 0xff087d50 : 0xff303b4d));
        return b;
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 10, 12, 10);
        root.setBackgroundColor(bg);
        setContentView(root);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, 54));

        TextView title = new TextView(this);
        title.setText(fileName);
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setTypeface(null, Typeface.BOLD);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button close = makeButton("Назад", false);
        toolbar.addView(close, new LinearLayout.LayoutParams(-2, 44));
        close.setOnClickListener(v -> finish());

        editor = new EditText(this);
        editor.setTextColor(0xffe8edf5);
        editor.setHintTextColor(0xff8995a8);
        editor.setTextSize(14);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setPadding(14, 14, 14, 14);
        editor.setBackground(rounded(panel));
        editor.setSingleLine(false);
        editor.setHorizontallyScrolling(false);
        editor.setGravity(Gravity.TOP | Gravity.START);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(editor, new ScrollView.LayoutParams(-1, -1));
        LinearLayout.LayoutParams editorLp = new LinearLayout.LayoutParams(-1, 0, 1);
        editorLp.topMargin = 8;
        editorLp.bottomMargin = 8;
        root.addView(scroll, editorLp);

        Button save = makeButton("СОХРАНИТЬ ФАЙЛ", true);
        root.addView(save, new LinearLayout.LayoutParams(-1, 50));
        save.setOnClickListener(v -> saveFile());
    }

    private void loadFile() {
        try (InputStream in = getContentResolver().openInputStream(fileUri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new Exception("Не удалось открыть файл");
            byte[] buffer = new byte[8192];
            int total = 0, count;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > 2 * 1024 * 1024) throw new Exception("Файл больше 2 МБ");
                out.write(buffer, 0, count);
            }
            editor.setText(new String(out.toByteArray(), StandardCharsets.UTF_8));
            editor.setSelection(editor.length());
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка чтения: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void saveFile() {
        try (OutputStream out = getContentResolver().openOutputStream(fileUri, "wt")) {
            if (out == null) throw new Exception("Нет доступа на запись");
            out.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            Toast.makeText(this, "Файл сохранён", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка сохранения: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
