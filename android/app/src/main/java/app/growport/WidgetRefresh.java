package app.growport;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

/**
 * The widget's refresh button: loads the app page in a hidden WebView, where it fetches fresh
 * prices, sends the widget its numbers (setWidget) and calls widgetDone. All calculations stay in
 * index.html, so the widget shows exactly what the app would. The app does not open.
 */
public class WidgetRefresh extends JobService {

    private static final int JOB_ID = 1001;
    /** Market sources answer within seconds; give up well before the system would stop the job. */
    private static final long TIMEOUT = 90 * 1000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private JobParameters job;

    static void start(Context ctx) {
        JobScheduler js = ctx.getSystemService(JobScheduler.class);
        JobInfo info = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, WidgetRefresh.class))
                .setOverrideDeadline(0)
                .build();
        if (js == null || js.schedule(info) != JobScheduler.RESULT_SUCCESS) ChartWidget.refreshDone(ctx);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        job = params;
        try {
            final WebViewAssetLoader loader = MainActivity.assetLoader(this, new WebUpdater(this));
            webView = new WebView(this);
            WebSettings s = webView.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setAllowFileAccess(false);
            s.setAllowContentAccess(false);
            webView.addJavascriptInterface(new Bridge(), "GrowPortAndroid");
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    return loader.shouldInterceptRequest(request.getUrl());
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return true;
                }
            });
            webView.loadUrl(MainActivity.START_URL);
            handler.postDelayed(this::finish, TIMEOUT);
            return true;
        } catch (Exception e) {
            finish();
            return false;
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        cleanUp();
        ChartWidget.refreshDone(this);
        return false;
    }

    private void finish() {
        if (job == null) return;
        JobParameters p = job;
        cleanUp();
        ChartWidget.refreshDone(this);
        jobFinished(p, false);
    }

    private void cleanUp() {
        job = null;
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
    }

    /** The part of MainActivity's bridge the page uses when it runs for the widget. */
    private class Bridge {
        @JavascriptInterface
        public boolean widgetJob() {
            return true;
        }

        @JavascriptInterface
        public void widgetDone() {
            handler.post(WidgetRefresh.this::finish);
        }

        @JavascriptInterface
        public void setWidget(final String json) {
            ChartWidget.save(WidgetRefresh.this, json);
        }

        @JavascriptInterface
        public String getVersion() {
            return BuildConfig.VERSION_NAME;
        }

        @JavascriptInterface
        public boolean canSelfUpdate() {
            return false;
        }

        @JavascriptInterface
        public void http(final int id, final String method, final String url, final String body, final String type) {
            new Thread(() -> {
                final String js = MainActivity.httpJs(id, method, url, body, type);
                handler.post(() -> { if (webView != null) webView.evaluateJavascript(js, null); });
            }).start();
        }
    }
}
