package net.mythra.iconmaker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Icon Maker: pick an app, pick a picture, name it, and a home-screen icon
 * that opens that app is added. No ads, no internet.
 */
public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 1;
    /** Adaptive icons are 108dp; at 4x that is 432 px. The middle two thirds is what shows. */
    private static final int ICON_SIZE = 432;
    private static final int SOURCE_MAX = 2048;

    private ComponentName chosenApp;
    private String chosenUrl;
    private Bitmap chosenPicture;
    private Bitmap sourcePicture;
    private Button adjust;

    private TextView appLabel;
    private ImageView preview;
    private EditText nameBox;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        col.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Icon Maker");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        col.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Pick an app, pick a picture, give it a name, and the icon is added to your home screen.");
        hint.setPadding(0, dp(6), 0, dp(18));
        col.addView(hint);

        Button pickApp = new Button(this);
        pickApp.setText("1. Choose app");
        pickApp.setOnClickListener(v -> showAppList());
        col.addView(pickApp);

        Button pickLink = new Button(this);
        pickLink.setText("Or: use a web link");
        pickLink.setOnClickListener(v -> askLink());
        col.addView(pickLink);

        appLabel = new TextView(this);
        appLabel.setText("No app chosen yet");
        appLabel.setPadding(0, dp(4), 0, dp(14));
        col.addView(appLabel);

        Button pickPicture = new Button(this);
        pickPicture.setText("2. Choose picture");
        pickPicture.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "Choose a picture"), PICK_IMAGE);
        });
        col.addView(pickPicture);

        preview = new ImageView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(120), dp(120));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.setMargins(0, dp(10), 0, dp(14));
        preview.setLayoutParams(lp);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setVisibility(View.GONE);
        col.addView(preview);

        adjust = new Button(this);
        adjust.setText("Adjust crop");
        adjust.setVisibility(View.GONE);
        adjust.setOnClickListener(v -> { if (sourcePicture != null) showCrop(sourcePicture); });
        col.addView(adjust);

        TextView nameLabel = new TextView(this);
        nameLabel.setText("3. Name under the icon");
        nameLabel.setPadding(0, dp(8), 0, 0);
        col.addView(nameLabel);

        nameBox = new EditText(this);
        nameBox.setSingleLine(true);
        nameBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        col.addView(nameBox);

        Button add = new Button(this);
        add.setText("4. Add to home screen");
        add.setOnClickListener(v -> addShortcut());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.setMargins(0, dp(18), 0, 0);
        add.setLayoutParams(alp);
        col.addView(add);

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.addView(col);
        // keep the content clear of the status and navigation bars
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(scroll);
    }

    /** Every app on the phone that can be opened, alphabetically, with its own icon. */
    private void showAppList() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(main, 0);
        final List<AppEntry> apps = new ArrayList<>();
        for (ResolveInfo r : found) {
            if (r.activityInfo.packageName.equals(getPackageName())) continue;
            apps.add(new AppEntry(r.loadLabel(pm).toString(),
                    new ComponentName(r.activityInfo.packageName, r.activityInfo.name),
                    r.loadIcon(pm)));
        }
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));

        ArrayAdapter<AppEntry> adapter = new ArrayAdapter<AppEntry>(this, 0, apps) {
            @Override
            public View getView(int pos, View convert, ViewGroup parent) {
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(10), dp(16), dp(10));
                ImageView icon = new ImageView(getContext());
                icon.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
                icon.setImageDrawable(apps.get(pos).icon);
                TextView label = new TextView(getContext());
                label.setText(apps.get(pos).label);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                label.setPadding(dp(14), 0, 0, 0);
                row.addView(icon);
                row.addView(label);
                return row;
            }
        };

        new AlertDialog.Builder(this)
                .setTitle("Choose app")
                .setAdapter(adapter, (d, which) -> {
                    AppEntry e = apps.get(which);
                    chosenApp = e.component;
                    chosenUrl = null;
                    appLabel.setText("App: " + e.label);
                    nameBox.setText(e.label);   // a new app brings its own name; edit it after if you like
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** A web page instead of an app: paste its address. */
    private void askLink() {
        EditText box = new EditText(this);
        box.setHint("https://...");
        box.setSingleLine(true);
        box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        if (chosenUrl != null) box.setText(chosenUrl);
        android.content.ClipboardManager cb = getSystemService(android.content.ClipboardManager.class);
        if (chosenUrl == null && cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0) {
            CharSequence clip = cb.getPrimaryClip().getItemAt(0).getText();
            if (clip != null && clip.toString().trim().matches("(?i)https?://\\S+")) box.setText(clip.toString().trim());
        }
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("Web link")
                .setMessage("Paste the address the icon should open.")
                .setView(wrap)
                .setPositiveButton("Use", (d, w) -> {
                    String url = box.getText().toString().trim();
                    if (!url.isEmpty() && !url.matches("(?i)^[a-z][a-z0-9+.-]*:.*")) url = "https://" + url;
                    if (url.isEmpty()) return;
                    chosenUrl = url;
                    chosenApp = null;
                    appLabel.setText("Link: " + url);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_IMAGE || result != RESULT_OK || data == null || data.getData() == null) return;
        Bitmap b = loadPicture(data.getData());
        if (b == null) {
            Toast.makeText(this, "Couldn't open that picture. Try another one.", Toast.LENGTH_LONG).show();
            return;
        }
        sourcePicture = b;
        showCrop(b);
    }

    /** Full-screen crop: drag and pinch the picture under the square, then Use. */
    private void showCrop(Bitmap source) {
        Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(0xFF000000);
        col.setFitsSystemWindows(true);
        TextView hint = new TextView(this);
        hint.setText("Drag to move, pinch to zoom. What\u2019s inside the circle is your icon.");
        hint.setTextColor(0xFFFFFFFF);
        hint.setPadding(dp(16), dp(16), dp(16), dp(8));
        col.addView(hint);
        CropView crop = new CropView(this, source);
        col.addView(crop, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        row.setPadding(dp(16), dp(8), dp(16), dp(16));
        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setOnClickListener(v -> d.dismiss());
        Button use = new Button(this);
        use.setText("Use");
        use.setOnClickListener(v -> {
            chosenPicture = crop.cropped(ICON_SIZE);
            preview.setImageBitmap(roundPreview(chosenPicture));
            preview.setVisibility(View.VISIBLE);
            adjust.setVisibility(View.VISIBLE);
            d.dismiss();
        });
        row.addView(cancel);
        row.addView(use);
        col.addView(row);
        d.setContentView(col);
        d.show();
    }

    /** What a round home screen shows: the middle two thirds, cut to a circle. */
    private Bitmap roundPreview(Bitmap icon) {
        int side = icon.getWidth(), inner = side * 2 / 3, off = (side - inner) / 2;
        Bitmap out = Bitmap.createBitmap(inner, inner, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawCircle(inner / 2f, inner / 2f, inner / 2f, p);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        c.drawBitmap(icon, -off, -off, p);
        return out;
    }

    /** The picture, shrunk so its longer side is at most SOURCE_MAX pixels. */
    private Bitmap loadPicture(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, bounds); }
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            if (longest <= 0) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = 1;
            while (longest / opts.inSampleSize > SOURCE_MAX) opts.inSampleSize *= 2;
            try (InputStream in = getContentResolver().openInputStream(uri)) { return BitmapFactory.decodeStream(in, null, opts); }
        } catch (Exception e) {
            return null;
        }
    }

    private void addShortcut() {
        if (chosenApp == null && chosenUrl == null) { toast("Choose an app or a web link first."); return; }
        if (chosenPicture == null) { toast("Choose a picture first."); return; }
        String name = nameBox.getText().toString().trim();
        if (name.isEmpty()) { toast("Give the icon a name."); return; }

        // First choice: a 1x1 widget, which has no badge in its corner.
        android.appwidget.AppWidgetManager wm = getSystemService(android.appwidget.AppWidgetManager.class);
        if (wm != null && wm.isRequestPinAppWidgetSupported()) {
            try {
                String token = "t" + System.currentTimeMillis();
                IconWidget.savePending(this, token, chosenUrl != null ? "url:" + chosenUrl : chosenApp.flattenToString(), name, chosenPicture);
                Intent done = new Intent(this, IconWidget.class).setAction(IconWidget.ACTION_PINNED)
                        .putExtra(IconWidget.EXTRA_TOKEN, token);
                // mutable: the launcher adds the new widget's id to it
                android.app.PendingIntent cb = android.app.PendingIntent.getBroadcast(this, (int) (System.currentTimeMillis() & 0x7fffffff), done,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_MUTABLE);
                if (wm.requestPinAppWidget(new ComponentName(this, IconWidget.class), null, cb)) {
                    toast("Your phone will ask you to confirm. Tap Add to home screen.");
                    return;
                }
            } catch (Exception e) {
                // fall through to a shortcut
            }
        }

        // Fallback: a pinned shortcut (Android adds a small badge to these).
        ShortcutManager sm = getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            toast("Your home screen app doesn't accept added icons.");
            return;
        }
        Intent open;
        if (chosenUrl != null) {
            open = new Intent(Intent.ACTION_VIEW, Uri.parse(chosenUrl));
        } else {
            open = new Intent(Intent.ACTION_MAIN);
            open.addCategory(Intent.CATEGORY_LAUNCHER);
            open.setComponent(chosenApp);
        }
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        ShortcutInfo info = new ShortcutInfo.Builder(this, "icon-" + System.currentTimeMillis())
                .setShortLabel(name)
                .setLongLabel(name)
                .setIcon(Icon.createWithAdaptiveBitmap(chosenPicture))
                .setIntent(open)
                .build();
        if (sm.requestPinShortcut(info, null)) {
            toast("Your phone will ask you to confirm. Tap Add.");
        } else {
            toast("The home screen said no. Try again in a moment.");
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private static final class AppEntry {
        final String label;
        final ComponentName component;
        final Drawable icon;
        AppEntry(String label, ComponentName component, Drawable icon) {
            this.label = label; this.component = component; this.icon = icon;
        }
        @Override public String toString() { return label; }
    }
}
