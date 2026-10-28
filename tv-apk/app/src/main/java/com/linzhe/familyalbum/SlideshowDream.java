package com.linzhe.familyalbum;

import android.service.dreams.DreamService;

/**
 * 系统屏保服务:电视闲置后由系统自动唤起,全屏轮播。
 * 启用方法(红米电视): 设置 → 显示/屏保(或"待机显示") → 屏保选择"家庭相册屏保"
 */
public class SlideshowDream extends DreamService {

    private SlideshowView view;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        view = new SlideshowView(this);
        setContentView(view);
        view.start();
    }

    @Override
    public void onDetachedFromWindow() {
        if (view != null) view.stop();
        view = null;
        super.onDetachedFromWindow();
    }
}
