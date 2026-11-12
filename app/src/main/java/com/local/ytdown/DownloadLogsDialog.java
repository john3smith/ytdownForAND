package com.local.ytdown;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Bounded scroll content and fixed actions leave room for text selection handles. */
final class DownloadLogsDialog {
    private final Activity activity;
    private Dialog listDialog;
    private Dialog detailDialog;

    DownloadLogsDialog(Activity activity) { this.activity = activity; }

    void dismiss() {
        if (detailDialog != null) detailDialog.dismiss();
        if (listDialog != null) listDialog.dismiss();
    }

    void show() {
        if (listDialog != null && listDialog.isShowing()) return;
        List<DownloadLogStore.Entry> entries;
        String system;
        try {
            entries = DownloadLogStore.links(activity);
            system = DownloadLogStore.systemLog(activity);
        } catch (IllegalStateException unreadable) {
            new AlertDialog.Builder(activity).setTitle(R.string.download_log)
                    .setMessage(unreadable.getMessage()).setPositiveButton(R.string.close, null).show();
            return;
        }
        Dialog dialog = new Dialog(activity);
        listDialog = dialog;
        LinearLayout body = body(dialog, activity.getString(R.string.download_log));
        TextView hint = text(activity.getString(R.string.download_log_hint), 13);
        body.addView(hint);
        List<String> rows = new ArrayList<>();
        for (DownloadLogStore.Entry entry : entries) {
            rows.add((entry.url.isEmpty() ? activity.getString(R.string.previous_download) : entry.url)
                    + "\n" + entry.summary() + "\n" + activity.getString(R.string.view_log_details));
        }
        if (!system.isEmpty()) rows.add(activity.getString(R.string.system_log) + "\n"
                + activity.getString(R.string.system_log_description));
        if (rows.isEmpty()) {
            TextView empty = text(activity.getString(R.string.download_log_empty), 15);
            body.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            ListView list = new ListView(activity);
            list.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, rows));
            list.setOnItemClickListener((parent, view, position, id) -> {
                if (position < entries.size()) {
                    DownloadLogStore.Entry entry = entries.get(position);
                    showDetail(entry.url, entry.text);
                } else showDetail(activity.getString(R.string.system_log), system);
            });
            body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        LinearLayout actions = actions(body);
        button(actions, R.string.clear_log, view -> {
            DownloadLogStore.clear(activity);
            dialog.dismiss();
            show();
            Toast.makeText(activity, R.string.log_cleared, Toast.LENGTH_SHORT).show();
        });
        button(actions, R.string.close, view -> dialog.dismiss());
        showBounded(dialog, body);
    }

    private void showDetail(String url, String log) {
        if (detailDialog != null) detailDialog.dismiss();
        Dialog dialog = new Dialog(activity);
        detailDialog = dialog;
        LinearLayout body = body(dialog, activity.getString(R.string.download_log_details));
        TextView hint = text(activity.getString(R.string.log_selection_hint), 13);
        body.addView(hint);
        String displayed = (url.isEmpty() ? "" : url + "\n\n") + log;
        TextView content = text(displayed, 12);
        content.setTypeface(Typeface.MONOSPACE);
        content.setTextIsSelectable(true);
        content.setPadding(dp(4), dp(12), dp(4), dp(16));
        ScrollView scroll = new ScrollView(activity);
        scroll.setClipToPadding(false);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout actions = actions(body);
        button(actions, R.string.copy_log, view -> {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(url, displayed));
                Toast.makeText(activity, R.string.log_copied, Toast.LENGTH_SHORT).show();
            }
        });
        button(actions, R.string.close, view -> dialog.dismiss());
        showBounded(dialog, body);
    }

    private LinearLayout body(Dialog dialog, String title) {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setTitle(title);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(16), dp(16), dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(activity.getColor(R.color.surface));
        background.setCornerRadius(dp(16));
        body.setBackground(background);
        TextView heading = text(title, 19);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        androidx.core.view.ViewCompat.setAccessibilityHeading(heading, true);
        body.addView(heading);
        return body;
    }

    private TextView text(String value, int size) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(activity.getColor(R.color.text_primary));
        return text;
    }

    private LinearLayout actions(LinearLayout body) {
        LinearLayout actions = new LinearLayout(activity);
        actions.setPadding(0, dp(8), 0, 0);
        body.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        return actions;
    }

    private void button(LinearLayout actions, int label, View.OnClickListener listener) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setMinimumWidth(0);
        button.setOnClickListener(listener);
        actions.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void showBounded(Dialog dialog, LinearLayout body) {
        dialog.setContentView(body);
        dialog.show();
        Rect available = new Rect();
        activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(available);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(Math.min(dp(600), (int) (available.width() * .94f)),
                    (int) (available.height() * .88f));
        }
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
