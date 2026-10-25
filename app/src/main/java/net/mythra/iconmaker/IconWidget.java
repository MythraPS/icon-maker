package net.mythra.iconmaker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.widget.RemoteViews;

import java.io.File;
import java.io.FileOutputStream;

/**
 * A home-screen icon made as a 1x1 widget: the picture (cut to a circle, like the
 * launcher's own icons), the name under it, and a tap opens the chosen app.
 * Widgets carry no "made by another app" badge, which pinned shortcuts do.
 *
 * Adding one: MainActivity saves the icon under a one-off token and asks the
 * launcher to pin the widget; when the user accepts, the launcher sends
 * ACTION_PINNED with the new widget's id, and the token's data moves to that id.
 */
public class IconWidget extends AppWidgetProvider {
    static final String ACTION_PINNED = "net.mythra.iconmaker.PINNED";
    static final String EXTRA_TOKEN = "token";
    private static final String PREFS = "widgets";

    /** Save a pending icon (before the user has confirmed it). */
    /** `target` = an app (ComponentName flattened) or "url:" + a web address. */
    static void savePending(Context ctx, String token, String target, String label, Bitmap icon) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file(ctx, token))) {
            icon.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(token + ".app", target)
                .putString(token + ".label", label)
                .apply();
    }

    private static File file(Context ctx, String key) { return new File(ctx.getFilesDir(), "icon-" + key + ".png"); }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ACTION_PINNED.equals(intent.getAction())) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            String token = intent.getStringExtra(EXTRA_TOKEN);
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID && token != null) {
                SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                String key = "w" + id;
                p.edit()
                        .putString(key + ".app", p.getString(token + ".app", null))
                        .putString(key + ".label", p.getString(token + ".label", ""))
                        .remove(token + ".app").remove(token + ".label")
                        .apply();
                file(ctx, token).renameTo(file(ctx, key));
                render(ctx, AppWidgetManager.getInstance(ctx), id);
            }
            return;
        }
        super.onReceive(ctx, intent);
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) render(ctx, mgr, id);
    }

    @Override
    public void onDeleted(Context ctx, int[] ids) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (int id : ids) {
            e.remove("w" + id + ".app").remove("w" + id + ".label");
            file(ctx, "w" + id).delete();
        }
        e.apply();
    }

    private static void render(Context ctx, AppWidgetManager mgr, int id) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String app = p.getString("w" + id + ".app", null);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        if (app == null) {
            // a widget dragged in from the widget list, not made in Icon Maker: point back to the app
            v.setImageViewResource(R.id.icon, R.drawable.ic_app);
            v.setTextViewText(R.id.label, "Icon Maker");
            Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, id, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            mgr.updateAppWidget(id, v);
            return;
        }
        Bitmap icon = BitmapFactory.decodeFile(file(ctx, "w" + id).getPath());
        if (icon != null) v.setImageViewBitmap(R.id.icon, round(icon));
        v.setTextViewText(R.id.label, p.getString("w" + id + ".label", ""));
        Intent launch;
        if (app.startsWith("url:")) {
            launch = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(app.substring(4)));
        } else {
            launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            launch.setComponent(ComponentName.unflattenFromString(app));
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, id, launch,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        mgr.updateAppWidget(id, v);
    }

    /** The middle two thirds of the icon, cut to a circle, 192 px across. */
    static Bitmap round(Bitmap icon) {
        int side = icon.getWidth(), inner = side * 2 / 3, off = (side - inner) / 2, out = 192;
        Bitmap b = Bitmap.createBitmap(out, out, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawCircle(out / 2f, out / 2f, out / 2f, p);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        float k = (float) out / inner;
        c.scale(k, k);
        c.drawBitmap(icon, -off, -off, p);
        return b;
    }
}
