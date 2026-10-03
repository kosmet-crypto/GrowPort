package app.growport;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Bundle;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Home-screen widget: total value, today's change and the value line for 7 days, 30 days, 1 year
 * or all time. The page (index.html) sends the numbers through GrowPortAndroid.setWidget whenever
 * it renders. The period buttons (top left) switch between the lines the page sent; the refresh
 * button (top right) has WidgetRefresh fetch fresh prices in the background. Tapping anywhere
 * else opens the app.
 */
public class ChartWidget extends AppWidgetProvider {

    private static final String PREFS = "widget";
    private static final String ACTION_PERIOD = "app.growport.WIDGET_PERIOD";
    private static final String ACTION_REFRESH = "app.growport.WIDGET_REFRESH";
    private static final String EXTRA_PERIOD = "period";
    /** A refresh that has not reported back after this long is treated as over. */
    private static final long REFRESH_STALE = 2 * 60 * 1000L;
    private static final String[] PERIODS = {"7", "30", "365", "all"};
    private static final int[] CHIPS = {R.id.w_p7, R.id.w_p30, R.id.w_p365, R.id.w_pall};

    /** Stores the page's numbers and redraws every placed widget. */
    static void save(Context ctx, String json) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("data", json).apply();
        updateAll(ctx);
    }

    /** Called by WidgetRefresh when it is done (or gave up). */
    static void refreshDone(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("refreshAt").apply();
        updateAll(ctx);
    }

    private static void updateAll(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        for (int id : m.getAppWidgetIds(new ComponentName(ctx, ChartWidget.class))) update(ctx, m, id);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (ACTION_PERIOD.equals(intent.getAction())) {
            prefs.edit().putString("period", intent.getStringExtra(EXTRA_PERIOD)).apply();
            updateAll(ctx);
        } else if (ACTION_REFRESH.equals(intent.getAction())) {
            if (System.currentTimeMillis() - prefs.getLong("refreshAt", 0) < REFRESH_STALE) return;
            prefs.edit().putLong("refreshAt", System.currentTimeMillis()).apply();
            updateAll(ctx);
            WidgetRefresh.start(ctx);
        } else {
            super.onReceive(ctx, intent);
        }
    }

    private static PendingIntent action(Context ctx, int code, String action, String period) {
        Intent i = new Intent(ctx, ChartWidget.class).setAction(action);
        if (period != null) i.putExtra(EXTRA_PERIOD, period);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(ctx, m, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager m, int id, Bundle options) {
        update(ctx, m, id);
    }

    private static void update(Context ctx, AppWidgetManager m, int id) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_chart);
        Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        v.setOnClickPendingIntent(R.id.w_refresh, action(ctx, 10, ACTION_REFRESH, null));
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String period = prefs.getString("period", "30");
        boolean refreshing = System.currentTimeMillis() - prefs.getLong("refreshAt", 0) < REFRESH_STALE;
        v.setInt(R.id.w_refresh, "setImageAlpha", refreshing ? 70 : 255);
        boolean night = (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int good = night ? 0xFF3CCF7A : 0xFF0F7B3F, bad = night ? 0xFFF06F78 : 0xFFC9303E;
        int ink = ctx.getColor(R.color.w_ink), muted = ctx.getColor(R.color.w_muted);
        try {
            JSONObject d = new JSONObject(prefs.getString("data", "{}"));
            JSONArray periods = d.optJSONArray("periods");
            for (int k = 0; k < CHIPS.length; k++) {
                boolean on = PERIODS[k].equals(period);
                v.setOnClickPendingIntent(CHIPS[k], action(ctx, 20 + k, ACTION_PERIOD, PERIODS[k]));
                v.setInt(CHIPS[k], "setBackgroundResource", on ? R.drawable.widget_chip_on : 0);
                v.setTextColor(CHIPS[k], on ? ink : muted);
                JSONObject pd = find(periods, PERIODS[k]);
                if (pd != null && !pd.optString("label").isEmpty()) v.setTextViewText(CHIPS[k], pd.optString("label"));
            }
            if (!d.has("value")) throw new IllegalStateException("no data yet");
            v.setTextViewText(R.id.w_value, d.optString("value"));
            v.setTextViewText(R.id.w_updated, refreshing ? d.optString("updating", "…") : d.optString("updated"));
            setChange(v, R.id.w_today, d.optJSONObject("today"), good, bad);
            // Widgets fed by an older page only have the month's line.
            JSONObject pd = find(periods, period);
            setChange(v, R.id.w_month, pd != null ? pd.optJSONObject("change") : d.optJSONObject("month"), good, bad);
            JSONArray p = pd != null ? pd.optJSONArray("points") : d.optJSONArray("points");
            if (p != null && p.length() > 1) {
                float[] pts = new float[p.length()];
                for (int k = 0; k < pts.length; k++) pts[k] = (float) p.optDouble(k);
                Bundle o = m.getAppWidgetOptions(id);
                float dens = ctx.getResources().getDisplayMetrics().density;
                int wDp = Math.max(160, o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250));
                int hDp = Math.max(36, o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 140) - 100);
                v.setImageViewBitmap(R.id.w_chart, chart(pts, Math.round((wDp - 24) * dens), Math.round(hDp * dens),
                        pts[pts.length - 1] >= pts[0] ? good : bad, night, dens));
            } else {
                v.setImageViewBitmap(R.id.w_chart, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888));
            }
        } catch (Exception e) {
            v.setTextViewText(R.id.w_value, ctx.getString(R.string.widget_open));
        }
        m.updateAppWidget(id, v);
    }

    private static JSONObject find(JSONArray periods, String key) {
        if (periods == null) return null;
        for (int k = 0; k < periods.length(); k++) {
            JSONObject o = periods.optJSONObject(k);
            if (o != null && key.equals(o.optString("key"))) return o;
        }
        return null;
    }

    private static void setChange(RemoteViews v, int view, JSONObject c, int good, int bad) {
        if (c == null) { v.setTextViewText(view, ""); return; }
        v.setTextViewText(view, c.optString("text"));
        v.setTextColor(view, c.optBoolean("up", true) ? good : bad);
    }

    /** The value line: smooth, with a fading fill and a dashed line at the starting value; no dots. */
    private static Bitmap chart(float[] pts, int w, int h, int color, boolean night, float dens) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float p : pts) { min = Math.min(min, p); max = Math.max(max, p); }
        if (max - min < 1e-6f) { max += 1; min -= 1; }
        float pad = (max - min) * 0.1f, top = 3 * dens, bottom = h - 2 * dens;
        min -= pad; max += pad;
        float[] xs = new float[pts.length], ys = new float[pts.length];
        for (int k = 0; k < pts.length; k++) {
            xs[k] = w * k / (float) (pts.length - 1);
            ys[k] = top + (bottom - top) * (1 - (pts[k] - min) / (max - min));
        }
        Path line = new Path();
        line.moveTo(xs[0], ys[0]);
        for (int k = 1; k < pts.length; k++) {
            float mx = (xs[k - 1] + xs[k]) / 2;
            line.cubicTo(mx, ys[k - 1], mx, ys[k], xs[k], ys[k]);
        }
        Path fill = new Path(line);
        fill.lineTo(xs[pts.length - 1], h);
        fill.lineTo(xs[0], h);
        fill.close();
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setShader(new LinearGradient(0, top, 0, h, (color & 0x00FFFFFF) | 0x47000000, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawPath(fill, f);
        Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
        base.setStyle(Paint.Style.STROKE);
        base.setStrokeWidth(dens);
        base.setColor(night ? 0x66A9B4C8 : 0x668A94A6);
        base.setPathEffect(new DashPathEffect(new float[]{3 * dens, 4 * dens}, 0));
        c.drawLine(0, ys[0], w, ys[0], base);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.4f * dens);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        c.drawPath(line, p);
        return b;
    }
}
