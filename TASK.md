# 家庭相册轮播 — 任务清单

## 阶段一：MVP（v2 原生方案：DreamService 屏保 + NAS Docker 服务端）
- [x] T1 服务端骨架：零依赖 Node.js 单文件 + config.json（T1-T4 合并实现）
- [x] T2 媒体扫描：递归遍历，图片/视频识别，内容指纹 tag，5分钟自动增量
- [x] T3 随机播放列表 API：/api/playlist（洗牌，一轮不重复）
- [x] T4 媒体流接口：/media/<path>，Range 206 已验证
- [x] T5a 网页版播放器（调试/备用电视）：本机已验证
- [x] T5b 电视端 APK 源码：DreamService 屏保 + SlideshowActivity 手动播放 + 设置页（🟡 编译待云端验证）
- [x] T6 遥控器交互：屏保任意键退出；设置页支持遥控器操作
- [x] T7 本机联调：服务端接口全部 curl 验证通过（2026-10-28）
- [x] T8 部署文档：README + docker-compose.yml
- [ ] T9 用户操作：GitHub 建仓推送 → Actions 出 APK → U盘装电视 → 配置屏保

## 阶段二：打磨（装上电视后按需）
- [ ] T10 HEIC/苹果实况照片兼容（服务端转码或客户端跳过）
- [ ] T11 照片拍摄日期角标（可开关）
- [ ] T12 淡入淡出/ Ken Burns 等过渡效果增强
- [ ] T13 视频编码不兼容自动转码（需 ffmpeg，部署在 NAS 端）
