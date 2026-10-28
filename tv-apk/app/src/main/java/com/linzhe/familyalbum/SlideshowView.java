package com.linzhe.familyalbum;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.VideoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 轮播引擎:全屏黑底,图片淡入淡出 + 视频自动播放,一轮播完不重复。
 * 同时被 SlideshowDream(屏保) 和 SlideshowActivity(手动) 复用。
 * 零第三方依赖:HttpURLConnection + org.json + VideoView。
 */
public class SlideshowView extends FrameLayout {

    private static class Item {
        String type;   // "image" | "video"
        String url;    // 相对 url,如 /media/xxx.jpg
        String name;
    }

    private final ImageView imageView;
    private final VideoView videoView;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private volatile boolean running = false;
    private String server;
    private int intervalSec = 8;
    private boolean muted = true;

    private List<Item> queue = new ArrayList<>();
    private int pos = 0;

    public SlideshowView(Context c) {
        super(c);
        setBackgroundColor(0xFF000000);

        imageView = new ImageView(c);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setVisibility(View.GONE);
        addView(imageView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        videoView = new VideoView(c);
        videoView.setVisibility(View.GONE);
        addView(videoView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void start() {
        server = Prefs.server(getContext());
        intervalSec = Prefs.interval(getContext());
        muted = Prefs.muted(getContext());
        running = true;
        pos = 0;
        queue.clear();
        io.execute(this::fetchPlaylist);
    }

    public void stop() {
        running = false;
        ui.removeCallbacksAndMessages(null);
        try { videoView.stopPlayback(); } catch (Exception ignored) {}
        imageView.setVisibility(View.GONE);
        videoView.setVisibility(View.GONE);
    }

    // ---------- 网络(IO线程) ----------

    private void fetchPlaylist() {
        try {
            String body = httpGet(server + "api/playlist?count=300");
            JSONObject root = new JSONObject(body);
            JSONArray arr = root.getJSONArray("items");
            List<Item> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Item it = new Item();
                it.type = o.optString("type");
                it.url = o.optString("url");
                it.name = o.optString("name");
                if (!it.url.isEmpty()) list.add(it);
            }
            ui.post(() -> {
                if (!running) return;
                if (list.isEmpty()) {
                    scheduleRetry(15000); // 相册还没内容,15秒后重试
                    return;
                }
                queue = list;
                pos = 0;
                showNext();
            });
        } catch (Exception e) {
            e.printStackTrace();
            ui.post(() -> scheduleRetry(10000)); // 服务端不可达,10秒后重连
        }
    }

    private void scheduleRetry(long ms) {
        ui.removeCallbacks(retryRun);
        ui.postDelayed(retryRun, ms);
    }

    private final Runnable retryRun = () -> {
        if (running) io.execute(this::fetchPlaylist);
    };

    private Bitmap loadBitmap(String url) {
        try {
            byte[] raw = httpGetBytes(url);
            BitmapFactory.Options bo = new BitmapFactory.Options();
            bo.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(raw, 0, raw.length, bo);
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = calcSample(bo.outWidth, bo.outHeight, 2560);
            o2.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(raw, 0, raw.length, o2);
        } catch (Exception e) {
            return null;
        }
    }

    private static int calcSample(int w, int h, int target) {
        int s = 1;
        while (w / (s * 2) >= target / 2 && h / (s * 2) >= 720) s *= 2;
        return Math.max(1, s);
    }

    private static String httpGet(String url) throws Exception {
        return new String(httpGetBytes(url), StandardCharsets.UTF_8);
    }

    private static byte[] httpGetBytes(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(30000);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    // ---------- 播放(UI线程) ----------

    private void showNext() {
        if (!running) return;
        if (queue.isEmpty()) { io.execute(this::fetchPlaylist); return; }
        if (pos >= queue.size()) {          // 一轮播完 → 重新洗牌,期间新照片自动进入
            Collections.shuffle(queue);
            pos = 0;
            io.execute(this::fetchPlaylistRefresh); // 顺手拉一次新列表(有新内容就换)
        }
        Item it = queue.get(pos++);
        if ("video".equals(it.type)) showVideo(it);
        else showImage(it);
    }

    private void fetchPlaylistRefresh() {
        try {
            String body = httpGet(server + "api/playlist?count=300");
            JSONObject root = new JSONObject(body);
            JSONArray arr = root.getJSONArray("items");
            List<Item> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Item it = new Item();
                it.type = o.optString("type");
                it.url = o.optString("url");
                it.name = o.optString("name");
                if (!it.url.isEmpty()) list.add(it);
            }
            if (!list.isEmpty() && running) queue = list;
        } catch (Exception ignored) {}
    }

    private void showImage(Item it) {
        try { videoView.stopPlayback(); } catch (Exception ignored) {}
        videoView.setVisibility(View.GONE);
        io.execute(() -> {
            Bitmap bmp = loadBitmap(server + it.url);
            ui.post(() -> {
                if (!running || bmp == null) { showNext(); return; }
                imageView.setImageBitmap(bmp);
                imageView.setVisibility(View.VISIBLE);
                imageView.setAlpha(0f);
                imageView.animate().alpha(1f).setDuration(700).start();
                ui.postDelayed(this::showNext, intervalSec * 1000L);
            });
        });
    }

    private void showVideo(Item it) {
        ui.postDelayed(() -> {
            if (!running) return;
            videoView.setVisibility(View.VISIBLE);
            videoView.setVideoURI(Uri.parse(server + it.url));
            videoView.setOnPreparedListener(mp -> {
                mp.setVolume(muted ? 0f : 1f, muted ? 0f : 1f);
                mp.start();
            });
            videoView.setOnCompletionListener(mp -> showNext());
            videoView.setOnErrorListener((mp, what, extra) -> {
                showNext();  // 编码不兼容等 → 跳过
                return true;
            });
            videoView.start();
        }, 80);
    }
}
