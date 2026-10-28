package com.linzhe.familyalbum;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 设置页:服务端地址 / 轮播秒数 / 静音开关 / 测试连接 / 手动全屏播放 */
public class MainActivity extends Activity {

    private EditText etServer, etInterval;
    private CheckBox cbMute;
    private TextView tvStatus;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etServer = findViewById(R.id.etServer);
        etInterval = findViewById(R.id.etInterval);
        cbMute = findViewById(R.id.cbMute);
        tvStatus = findViewById(R.id.tvStatus);
        Button btnTest = findViewById(R.id.btnTest);
        Button btnPlay = findViewById(R.id.btnPlay);

        etServer.setText(Prefs.server(this));
        etInterval.setText(String.valueOf(Prefs.interval(this)));
        cbMute.setChecked(Prefs.muted(this));

        btnTest.setOnClickListener(v -> {
            savePrefs();
            testConnection();
        });
        btnPlay.setOnClickListener(v -> {
            savePrefs();
            startActivity(new android.content.Intent(this, SlideshowActivity.class));
        });
    }

    private void savePrefs() {
        Prefs.setServer(this, etServer.getText().toString());
        try {
            Prefs.setInterval(this, Integer.parseInt(etInterval.getText().toString()));
        } catch (Exception ignored) {}
        Prefs.setMuted(this, cbMute.isChecked());
    }

    private void testConnection() {
        String addr = Prefs.server(this);
        tvStatus.setTextColor(0xFFCCCCCC);
        tvStatus.setText("正在连接 " + addr + " …");
        new Thread(() -> {
            String result;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(addr + "api/info").openConnection();
                conn.setConnectTimeout(5000);
                InputStream in = conn.getInputStream();
                byte[] buf = new byte[4096];
                int n = in.read(buf);
                in.close();
                conn.disconnect();
                JSONObject o = new JSONObject(new String(buf, 0, Math.max(0, n), StandardCharsets.UTF_8));
                result = "连接成功 ✓ 共 " + o.optInt("count") + " 个媒体文件 (图片 "
                        + o.optInt("images") + " / 视频 " + o.optInt("videos") + ")";
            } catch (Exception e) {
                result = "连接失败 ✗ " + e.getMessage() + "(检查:电视和NAS是否同一WiFi、IP是否正确)";
            }
            String r = result;
            ui.post(() -> {
                tvStatus.setTextColor(r.contains("✓") ? 0xFF8BC34A : 0xFFFF7043);
                tvStatus.setText(r);
            });
        }).start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 遥控器:菜单键也可触发测试
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            testConnection();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
