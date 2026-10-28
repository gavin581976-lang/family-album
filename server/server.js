/**
 * 家庭相册轮播 - 媒体服务端 (零依赖 Node.js)
 * 用法: node server.js  (配置见同目录 config.json)
 * 接口:
 *   GET /                    网页版播放器(调试/备用)
 *   GET /api/playlist?count=50   随机播放列表(图片+视频混合,已打乱)
 *   GET /api/scan            强制重新扫描媒体目录
 *   GET /api/info            服务信息
 *   GET /media/<相对路径>     媒体文件流(支持Range,视频拖动必需)
 */
'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

// ---------- 配置 ----------
const CFG_FILE = path.join(__dirname, 'config.json');
const DEFAULT_CFG = { mediaDir: path.join(__dirname, 'media'), port: 8787 };
let cfg = DEFAULT_CFG;
try { cfg = Object.assign({}, DEFAULT_CFG, JSON.parse(fs.readFileSync(CFG_FILE, 'utf8'))); }
catch (e) { try { fs.writeFileSync(CFG_FILE, JSON.stringify(DEFAULT_CFG, null, 2)); } catch (_) {} }
// 环境变量优先(Docker 友好): MEDIA_DIR / PORT
const MEDIA_DIR = path.resolve(process.env.MEDIA_DIR || cfg.mediaDir);
const PORT = Number(process.env.PORT) || Number(cfg.port) || 8787;

const IMG_EXT = new Set(['.jpg', '.jpeg', '.png', '.webp', '.bmp', '.gif']);
const VID_EXT = new Set(['.mp4', '.m4v', '.mov', '.webm', '.mkv']);
const MIME = {
  '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp',
  '.bmp': 'image/bmp', '.gif': 'image/gif',
  '.mp4': 'video/mp4', '.m4v': 'video/mp4', '.mov': 'video/quicktime',
  '.webm': 'video/webm', '.mkv': 'video/x-matroska',
};

// ---------- 媒体扫描 ----------
let mediaList = [];        // [{ rel, abs, type, name, size, mtime }]
let listTag = '';          // 内容指纹,客户端据此刷新
let scanning = false;

function walk(dir, base, out) {
  let entries;
  try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { return; }
  for (const ent of entries) {
    if (ent.name.startsWith('.') || ent.name === '$RECYCLE.BIN' || ent.name === 'System Volume Information') continue;
    const abs = path.join(dir, ent.name);
    const rel = base ? base + '/' + ent.name : ent.name;
    if (ent.isDirectory()) { walk(abs, rel, out); continue; }
    if (!ent.isFile()) continue;
    const ext = path.extname(ent.name).toLowerCase();
    let type = null;
    if (IMG_EXT.has(ext)) type = 'image';
    else if (VID_EXT.has(ext)) type = 'video';
    if (!type) continue;
    let st; try { st = fs.statSync(abs); } catch (e) { continue; }
    out.push({ rel, abs, type, name: ent.name, size: st.size, mtime: st.mtimeMs });
  }
}

function scan() {
  if (scanning) return;
  scanning = true;
  setImmediate(() => {
    try {
      const out = [];
      walk(MEDIA_DIR, '', out);
      mediaList = out;
      const fingerprint = out.map(m => m.rel + ':' + m.size + ':' + Math.round(m.mtime)).join('|');
      listTag = crypto.createHash('md5').update(fingerprint).digest('hex').slice(0, 10);
      console.log(`[scan] ${new Date().toLocaleString()} 共 ${out.length} 个媒体文件 (图片 ${out.filter(m => m.type === 'image').length} / 视频 ${out.filter(m => m.type === 'video').length}) tag=${listTag}`);
    } catch (e) { console.error('[scan] 失败:', e.message); }
    scanning = false;
  });
}
scan();
setInterval(scan, 5 * 60 * 1000); // 每5分钟增量感知新照片

// ---------- 工具 ----------
function shuffle(arr) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}
function sendJSON(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Access-Control-Allow-Origin': '*' });
  res.end(body);
}
// 安全:防路径穿越
function safePath(rel) {
  const p = path.normalize(path.join(MEDIA_DIR, decodeURIComponent(rel)));
  if (!p.startsWith(MEDIA_DIR + path.sep) && p !== MEDIA_DIR) return null;
  return p;
}

// ---------- 媒体流(含Range) ----------
function serveMedia(req, res, rel) {
  const abs = safePath(rel);
  if (!abs) { res.writeHead(403); return res.end('Forbidden'); }
  let st;
  try { st = fs.statSync(abs); } catch (e) { res.writeHead(404); return res.end('Not Found'); }
  const ext = path.extname(abs).toLowerCase();
  const mime = MIME[ext] || 'application/octet-stream';
  const range = req.headers.range;
  const common = { 'Accept-Ranges': 'bytes', 'Access-Control-Allow-Origin': '*', 'Cache-Control': 'max-age=3600' };
  if (range) {
    const m = /bytes=(\d*)-(\d*)/.exec(range);
    let start = m && m[1] ? parseInt(m[1], 10) : 0;
    let end = m && m[2] ? parseInt(m[2], 10) : st.size - 1;
    if (isNaN(start) || start >= st.size) { res.writeHead(416, { 'Content-Range': `bytes */${st.size}` }); return res.end(); }
    if (isNaN(end) || end >= st.size) end = st.size - 1;
    res.writeHead(206, Object.assign(common, {
      'Content-Type': mime, 'Content-Range': `bytes ${start}-${end}/${st.size}`,
      'Content-Length': end - start + 1,
    }));
    fs.createReadStream(abs, { start, end }).pipe(res);
  } else {
    res.writeHead(200, Object.assign(common, { 'Content-Type': mime, 'Content-Length': st.size }));
    fs.createReadStream(abs).pipe(res);
  }
}

// ---------- 网页版播放器(调试/备用电视) ----------
function playerPage(req, res) {
  const html = `<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8">
<title>家庭相册轮播</title><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}
#stage{position:fixed;inset:0;display:flex;align-items:center;justify-content:center}
img,video{max-width:100%;max-height:100%;opacity:0;transition:opacity .8s;object-fit:contain}
.show{opacity:1}
#bar{position:fixed;left:0;right:0;bottom:-60px;transition:bottom .3s;background:rgba(0,0,0,.55);
 color:#fff;display:flex;gap:16px;align-items:center;padding:12px 20px;font:14px/1 sans-serif}
body.ctrl #bar{bottom:0} button{font-size:16px}</style></head><body>
<div id="stage"></div>
<div id="bar"><span id="now">…</span><button id="bPrev">◀</button><button id="bPause">⏸</button>
<button id="bNext">▶</button><button id="bMute">🔇静音中</button>
每张 <input id="sec" type="number" min="2" max="120" style="width:56px"> 秒</div>
<script>
let list=[],idx=-1,paused=false,sec=8,muted=true,timer=null,cur=null;
const stage=document.getElementById('stage');
function shuffle(a){a=a.slice();for(let i=a.length-1;i>0;i--){const j=Math.random()*(i+1)|0;[a[i],a[j]]=[a[j],a[i]]}return a}
async function load(){const r=await fetch('/api/playlist?count=500');const d=await r.json();
 list=shuffle(d.items||[]);idx=-1;next()}
function show(item){
 if(cur){cur.remove()} 
 const el=document.createElement(item.type==='video'?'video':'img');
 el.src=item.url+(item.type==='image'?'':'');
 el.className='show';el.autoplay=true;el.muted=muted;el.playsInline=true;
 if(item.type==='video'){el.onended=next;el.onerror=next}
 el.onerror=()=>setTimeout(next,300);
 stage.appendChild(el);cur=el;
 document.getElementById('now').textContent=item.name;
 clearTimeout(timer);if(item.type==='image'&&!paused){timer=setTimeout(next,sec*1000)}
}
function next(){if(!list.length)return;idx=(idx+1)%list.length;
 if(idx===0)fetch('/api/playlist?count=500').then(r=>r.json()).then(d=>{if(d.tag!==tag)list=shuffle(d.items||[])});
 show(list[idx])}
function prev(){if(!list.length)return;idx=(idx-2+list.length)%list.length;next()}
let tag='';
load();setInterval(()=>{fetch('/api/info').then(r=>r.json()).then(d=>{if(d.tag!==tag){tag=d.tag;load()}}).catch(()=>{})},60000);
document.getElementById('bNext').onclick=next;document.getElementById('bPrev').onclick=prev;
document.getElementById('bPause').onclick=e=>{paused=!paused;e.target.textContent=paused?'▶':'⏸';
 if(cur&&cur.tagName==='VIDEO'){paused?cur.pause():cur.play()}else if(!paused)next()};
document.getElementById('bMute').onclick=e=>{muted=!muted;
 e.target.textContent=muted?'🔇静音中':'🔊有声';if(cur&&cur.tagName==='VIDEO')cur.muted=muted};
document.getElementById('sec').value=8;document.getElementById('sec').onchange=e=>{sec=+e.target.value||8};
document.addEventListener('keydown',e=>{if(e.key==='ArrowRight')next();if(e.key==='ArrowLeft')prev();
 if(e.key===' '){document.getElementById('bPause').click()}});
let t;document.addEventListener('mousemove',()=>{document.body.classList.add('ctrl');clearTimeout(t);
 t=setTimeout(()=>document.body.classList.remove('ctrl'),3000)});
</script></body></html>`;
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(html);
}

// ---------- HTTP 路由 ----------
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  const p = url.pathname;
  if (p === '/' || p === '/index.html') return playerPage(req, res);
  if (p === '/api/info') return sendJSON(res, 200, {
    tag: listTag, count: mediaList.length,
    images: mediaList.filter(m => m.type === 'image').length,
    videos: mediaList.filter(m => m.type === 'video').length, mediaDir: MEDIA_DIR,
  });
  if (p === '/api/scan') { scan(); return sendJSON(res, 200, { ok: true }); }
  if (p === '/api/playlist') {
    let count = parseInt(url.searchParams.get('count') || '100', 10);
    if (!(count > 0)) count = 100; if (count > 2000) count = 2000;
    const items = shuffle(mediaList).slice(0, count).map(m => ({
      type: m.type, url: '/media/' + m.rel.split('/').map(encodeURIComponent).join('/'),
      name: m.name, size: m.size,
    }));
    return sendJSON(res, 200, { tag: listTag, total: mediaList.length, items });
  }
  if (p.startsWith('/media/')) return serveMedia(req, res, p.slice('/media/'.length));
  res.writeHead(404); res.end('Not Found');
});
server.listen(PORT, () => {
  console.log(`家庭相册轮播服务已启动: http://0.0.0.0:${PORT}`);
  console.log(`媒体目录: ${MEDIA_DIR}`);
});
