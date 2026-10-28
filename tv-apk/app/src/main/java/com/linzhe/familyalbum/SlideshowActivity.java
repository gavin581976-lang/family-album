package com.linzhe.familyalbum;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

/** 手动全屏轮播(数字相框模式):从设置页点"开始播放"进入 */
public class SlideshowActivity extends Activity {

    private SlideshowView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideSystemUi();
        view = new SlideshowView(this);
        setContentView(view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (view != null) view.start();
    }

    @Override
    protected void onPause() {
        if (view != null) view.stop();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
