# 家庭相册轮播系统

电视屏保自动全屏轮播 NAS 上的照片和视频。零第三方依赖、零付费。

```
手机 ─自动备份→ 极空间NAS ─Docker媒体服务端─局域网→ 红米电视(家庭相册APK屏保)
```

## 一、极空间部署服务端(Docker)

极空间 Docker 界面操作(或用 docker-compose.yml):

1. 把手机照片/视频备份到极空间某个文件夹(极空间个人相册App可自动备份)
2. Docker → 镜像 → 仓库搜索 `node:20-alpine` 下载
3. 上传本项目 `server/` 目录(server.js + config.json + Dockerfile)到极空间
4. 用该镜像创建容器:
   - 挂载文件夹: 照片库 → `/media`(只读)
   - 端口: 本地 `8787` → 容器 `8787`
   - 启动命令保持默认 `node server.js`
5. 电脑浏览器访问 `http://<极空间IP>:8787` 能看到轮播页即成功

> config.json 中 mediaDir 保持 `/media`(容器内路径);本地调试用环境变量 MEDIA_DIR 覆盖。

## 二、电视安装 APK

APK 由 GitHub Actions 自动编译(见 `.github/workflows/build-apk.yml`):

1. 把本目录推到您的 GitHub 仓库 → Actions 页等构建完成(约3分钟)
2. 下载 artifact `family-album-apk` 里的 `app-debug.apk`
3. U盘拷贝 → 插电视 → 文件管理安装(需允许"未知来源")

## 三、电视端配置

1. 打开"家庭相册"App:
   - 服务端地址填 `http://<极空间IP>:8787`
   - 秒数=每张照片停留时间(默认8秒,可调)
   - 默认静音,想听视频原声就去掉勾选
   - 点"测试连接"确认 ✓ → "开始播放"立即体验
2. **开启屏保自动轮播(核心)**:
   电视 设置 → 屏保/待机显示 → 选择「家庭相册屏保」
   之后电视闲置即自动全屏轮播,遥控器任意键退出。

## 四、本地开发调试

```bash
cd server
MEDIA_DIR=./media node server.js   # media/ 放测试照片
# 浏览器打开 http://localhost:8787 即网页版轮播(兼做安卓以外设备的备用方案)
```

## 目录结构

- `server/` 媒体服务端(零依赖 Node.js + Dockerfile)
- `tv-apk/` 安卓电视应用(DreamService 屏保 + 手动播放 + 设置页)
- `.github/workflows/build-apk.yml` 云端编译流水线
