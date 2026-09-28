// Hangly for Android - Real-time Physics Engine & Interactive Simulation

const canvas = document.getElementById('hanglyCanvas');
const ctx = canvas.getContext('2d');

// UI Elements
const sliderPosition = document.getElementById('sliderPosition');
const sliderRopeLength = document.getElementById('sliderRopeLength');
const sliderRopeThickness = document.getElementById('sliderRopeThickness');
const sliderCharmSize = document.getElementById('sliderCharmSize');
const sliderGravity = document.getElementById('sliderGravity');

const valPosition = document.getElementById('valPosition');
const valRopeLength = document.getElementById('valRopeLength');
const valRopeThickness = document.getElementById('valRopeThickness');
const valCharmSize = document.getElementById('valCharmSize');
const valGravity = document.getElementById('valGravity');

const metricFPS = document.getElementById('metricFPS');
const metricCPU = document.getElementById('metricCPU');
const engineStateText = document.getElementById('engineStateText');
const pullTooltip = document.getElementById('pullTooltip');

const notifShade = document.getElementById('notificationsShade');
const qsShade = document.getElementById('quickSettingsShade');
const btnCloseNotif = document.getElementById('btnCloseNotif');
const btnCloseQS = document.getElementById('btnCloseQS');

// Physics Configuration
let horizontalPercent = 0.50; // Center notch
let ropeLength = 130;
let ropeThickness = 4;
let charmSize = 68;
let gravityMultiplier = 0.75; // 75% of normal gravity
let ropeColor = '#212121';
let ropeStyle = 'web'; // Default to authentic Mac Spider Web!
let connectorStyle = 'crescent'; // Small graphic crescent moon or ring connector
let currentCharm = 'spiderman';

// Custom Image Processing State
let customRawImage = null;
let customProcessedCanvas = null;
let currentCropShape = 'circle';
let cropZoom = 1.0;
let cropPanX = 0.5;
let cropPanY = 0.5;

// Multi-Node Verlet Chain
const NUM_NODES = 6;
const nodes = [];
for (let i = 0; i < NUM_NODES; i++) {
  nodes.push({ x: 0, y: 0, px: 0, py: 0, ax: 0, ay: 0 });
}

let anchorX = 0;
let anchorY = 0;
let isBeingDragged = false;
let touchX = 0;
let touchY = 0;
let lastTouchX = 0;
let lastTouchY = 0;
let lastTouchTime = 0;

let isResting = false;
let settleFrameCounter = 0;
let animationFrameId = null;

let lastFrameTime = performance.now();
let frameCount = 0;
let fpsLastTime = performance.now();

// Resize Canvas
function resizeCanvas() {
  const rect = canvas.getBoundingClientRect();
  const dpr = window.devicePixelRatio || 1;
  canvas.width = rect.width * dpr;
  canvas.height = rect.height * dpr;
  ctx.scale(dpr, dpr);

  updateAnchor();
  resetPositions();
  wakeUp();
}

function updateAnchor() {
  const rect = canvas.getBoundingClientRect();
  anchorX = rect.width * horizontalPercent;
  anchorY = 0;
}

function resetPositions() {
  const segLength = ropeLength / (NUM_NODES - 1);
  for (let i = 0; i < NUM_NODES; i++) {
    const ny = anchorY + i * segLength;
    nodes[i].x = anchorX;
    nodes[i].y = ny;
    nodes[i].px = anchorX;
    nodes[i].py = ny;
    nodes[i].ax = 0;
    nodes[i].ay = 0;
  }
  isResting = false;
  settleFrameCounter = 0;
}

function wakeUp() {
  isResting = false;
  settleFrameCounter = 0;
  updateEngineStateUI(true);
  if (!animationFrameId) {
    lastFrameTime = performance.now();
    animationFrameId = requestAnimationFrame(renderLoop);
  }
}

function updateEngineStateUI(isActive) {
  if (isActive) {
    engineStateText.textContent = "Physics: Active (60 FPS)";
    metricCPU.textContent = "1.2%";
  } else {
    engineStateText.textContent = "Physics: Sleeping (0% CPU)";
    metricCPU.textContent = "0.0%";
    metricFPS.textContent = "0";
  }
}

// Physics Step
function updatePhysics(dt) {
  if (isResting && !isBeingDragged) return;

  const safeDt = Math.min(dt, 0.033);
  const dtSq = safeDt * safeDt;

  // 75% normal gravity
  const effectiveGravity = 1800 * gravityMultiplier;
  const damping = 0.978;

  let totalMotion = 0;

  for (let i = 1; i < NUM_NODES; i++) {
    const node = nodes[i];
    node.ay += effectiveGravity;

    const vx = (node.x - node.px) * damping;
    const vy = (node.y - node.py) * damping;

    node.px = node.x;
    node.py = node.y;

    node.x += vx + node.ax * dtSq;
    node.y += vy + node.ay * dtSq;

    totalMotion += Math.hypot(vx, vy);

    node.ax = 0;
    node.ay = 0;
  }

  // Constraints
  const segLength = ropeLength / (NUM_NODES - 1);
  const iterations = 6;

  for (let iter = 0; iter < iterations; iter++) {
    nodes[0].x = anchorX;
    nodes[0].y = anchorY;

    if (isBeingDragged) {
      const last = nodes[NUM_NODES - 1];
      last.x += (touchX - last.x) * 0.55;
      last.y += (touchY - last.y) * 0.55;
    }

    for (let i = 0; i < NUM_NODES - 1; i++) {
      const n1 = nodes[i];
      const n2 = nodes[i + 1];

      let dx = n2.x - n1.x;
      let dy = n2.y - n1.y;
      const dist = Math.hypot(dx, dy);
      if (dist === 0) continue;

      const targetDist = (isBeingDragged && i === NUM_NODES - 2) ? segLength * 1.15 : segLength;
      const diff = (dist - targetDist) / dist;
      dx *= diff;
      dy *= diff;

      if (i === 0) {
        n2.x -= dx;
        n2.y -= dy;
      } else if (isBeingDragged && i + 1 === NUM_NODES - 1) {
        n1.x += dx * 0.7;
        n1.y += dy * 0.7;
        n2.x -= dx * 0.3;
        n2.y -= dy * 0.3;
      } else {
        n1.x += dx * 0.5;
        n1.y += dy * 0.5;
        n2.x -= dx * 0.5;
        n2.y -= dy * 0.5;
      }
    }
  }

  // Fast Sleep Detection
  if (!isBeingDragged) {
    if (totalMotion < 0.25) {
      settleFrameCounter++;
      if (settleFrameCounter >= 8) {
        for (let i = 1; i < NUM_NODES; i++) {
          nodes[i].px = nodes[i].x;
          nodes[i].py = nodes[i].y;
        }
        isResting = true;
      }
    } else {
      settleFrameCounter = 0;
    }
  }
}

// Shape Path Generator (Pure Borderless)
function buildShapePath(ctx, shape, size) {
  const pad = 0;
  const w = size;
  const h = size;
  const cx = size / 2;
  const cy = size / 2;

  ctx.beginPath();
  if (shape === 'heart') {
    ctx.moveTo(cx, h * 0.28);
    ctx.bezierCurveTo(cx, h * 0.05, 0, h * 0.05, 0, h * 0.45);
    ctx.bezierCurveTo(0, h * 0.68, cx - w * 0.25, h * 0.82, cx, h);
    ctx.bezierCurveTo(cx + w * 0.25, h * 0.82, w, h * 0.68, w, h * 0.45);
    ctx.bezierCurveTo(w, h * 0.05, cx, h * 0.05, cx, h * 0.28);
  } else if (shape === 'square') {
    ctx.roundRect(0, 0, w, h, size * 0.20);
  } else if (shape === 'star') {
    const numPoints = 5;
    const outerR = w / 2;
    const innerR = outerR * 0.42;
    const step = Math.PI / numPoints;
    for (let i = 0; i < numPoints * 2; i++) {
      const r = i % 2 === 0 ? outerR : innerR;
      const angle = i * step - Math.PI / 2;
      const x = cx + r * Math.cos(angle);
      const y = cy + r * Math.sin(angle);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
  } else if (shape === 'shield') {
    ctx.moveTo(cx, 0);
    ctx.lineTo(w, h * 0.12);
    ctx.lineTo(w, h * 0.58);
    ctx.bezierCurveTo(w, h * 0.82, cx, h * 0.95, cx, h);
    ctx.bezierCurveTo(cx, h * 0.95, 0, h * 0.82, 0, h * 0.58);
    ctx.lineTo(0, h * 0.12);
  } else {
    // Circle
    ctx.arc(cx, cy, w / 2, 0, Math.PI * 2);
  }
  ctx.closePath();
}

/**
 * Auto-Cutout Background Removal Algorithm:
 * Detects background color from image corners and flood-fills with transparency.
 */
function extractCharacterCutoutJS(img) {
  const off = document.createElement('canvas');
  off.width = img.width;
  off.height = img.height;
  const octx = off.getContext('2d');
  octx.drawImage(img, 0, 0);

  const imgData = octx.getImageData(0, 0, off.width, off.height);
  const data = imgData.data;
  const w = off.width;
  const h = off.height;

  // Sample 4 corners
  const corners = [
    { r: data[0], g: data[1], b: data[2] },
    { r: data[(w - 1) * 4], g: data[(w - 1) * 4 + 1], b: data[(w - 1) * 4 + 2] },
    { r: data[((h - 1) * w) * 4], g: data[((h - 1) * w) * 4 + 1], b: data[((h - 1) * w) * 4 + 2] },
    { r: data[((h - 1) * w + w - 1) * 4], g: data[((h - 1) * w + w - 1) * 4 + 1], b: data[((h - 1) * w + w - 1) * 4 + 2] }
  ];

  const tolerance = 40;
  function isBg(r, g, b) {
    for (const c of corners) {
      const dist = Math.hypot(r - c.r, g - c.g, b - c.b);
      if (dist <= tolerance) return true;
    }
    return false;
  }

  // Flood fill from edges
  const visited = new Uint8Array(w * h);
  const queue = [];

  for (let x = 0; x < w; x++) {
    queue.push(x, 0);
    queue.push(x, h - 1);
  }
  for (let y = 0; y < h; y++) {
    queue.push(0, y);
    queue.push(w - 1, y);
  }

  let head = 0;
  while (head < queue.length) {
    const x = queue[head++];
    const y = queue[head++];
    const idx = y * w + x;

    if (visited[idx]) continue;
    visited[idx] = 1;

    const p = idx * 4;
    if (isBg(data[p], data[p + 1], data[p + 2])) {
      data[p + 3] = 0; // Transparent

      if (x > 0 && !visited[idx - 1]) queue.push(x - 1, y);
      if (x < w - 1 && !visited[idx + 1]) queue.push(x + 1, y);
      if (y > 0 && !visited[idx - w]) queue.push(x, y - 1);
      if (y < h - 1 && !visited[idx + w]) queue.push(x, y + 1);
    }
  }

  octx.putImageData(imgData, 0, 0);

  // Crop tight bounding box
  let minX = w, maxX = 0, minY = h, maxY = 0;
  let hasPixel = false;
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      if (data[(y * w + x) * 4 + 3] > 30) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
        hasPixel = true;
      }
    }
  }

  if (!hasPixel) return off;

  const cropW = Math.max(10, maxX - minX + 8);
  const cropH = Math.max(10, maxY - minY + 8);
  const croppedCanvas = document.createElement('canvas');
  croppedCanvas.width = cropW;
  croppedCanvas.height = cropH;
  const cctx = croppedCanvas.getContext('2d');
  cctx.drawImage(off, Math.max(0, minX - 4), Math.max(0, minY - 4), cropW, cropH, 0, 0, cropW, cropH);

  return croppedCanvas;
}

/**
 * Borderless Shape Cropping with Interactive Pan and Zoom
 */
function cropBorderlessShapeJS(img, shape, zoom, panXPercent, panYPercent) {
  const targetSize = 260;
  const off = document.createElement('canvas');
  off.width = targetSize;
  off.height = targetSize;
  const octx = off.getContext('2d');

  // Clip into shape (NO BORDER!)
  buildShapePath(octx, shape, targetSize);
  octx.save();
  octx.clip();

  const minDim = Math.min(img.width, img.height);
  const cropDim = Math.max(20, minDim / zoom);

  const maxOffsetX = img.width - cropDim;
  const maxOffsetY = img.height - cropDim;

  const sx = Math.max(0, Math.min(maxOffsetX, maxOffsetX * panXPercent));
  const sy = Math.max(0, Math.min(maxOffsetY, maxOffsetY * panYPercent));

  octx.drawImage(img, sx, sy, cropDim, cropDim, 0, 0, targetSize, targetSize);
  octx.restore();

  // Notice: NO BORDER drawn! Pure, borderless shape.
  return off;
}

function updateCustomCharm() {
  if (!customRawImage) return;

  if (isAutoCutoutMode) {
    customProcessedCanvas = extractCharacterCutoutJS(customRawImage);
  } else {
    customProcessedCanvas = cropBorderlessShapeJS(customRawImage, currentCropShape, cropZoom, cropPanX, cropPanY);
  }
  currentCharm = 'custom';
  wakeUp();
}

// Drawing Preset Charms
function drawSpiderMan(ctx, size) {
  ctx.save();
  ctx.scale(size / 120, size / 120);

  ctx.strokeStyle = '#FFFFFF';
  ctx.lineWidth = 2.5;
  ctx.beginPath();
  ctx.moveTo(60, 0); ctx.lineTo(60, 18);
  ctx.stroke();

  ctx.fillStyle = '#1565C0';
  ctx.beginPath();
  ctx.moveTo(52, 38); ctx.lineTo(42, 16); ctx.lineTo(47, 14); ctx.lineTo(56, 34); ctx.closePath();
  ctx.fill();
  ctx.beginPath();
  ctx.moveTo(68, 38); ctx.lineTo(78, 16); ctx.lineTo(73, 14); ctx.lineTo(64, 34); ctx.closePath();
  ctx.fill();

  ctx.fillStyle = '#D32F2F';
  ctx.beginPath();
  ctx.moveTo(44, 18); ctx.lineTo(50, 6); ctx.lineTo(58, 6); ctx.lineTo(56, 18); ctx.closePath();
  ctx.fill();
  ctx.beginPath();
  ctx.moveTo(76, 18); ctx.lineTo(70, 6); ctx.lineTo(62, 6); ctx.lineTo(64, 18); ctx.closePath();
  ctx.fill();

  ctx.fillStyle = '#FFF';
  ctx.beginPath();
  ctx.arc(60, 14, 5, 0, Math.PI * 2);
  ctx.fill();

  ctx.fillStyle = '#0D47A1';
  ctx.beginPath();
  ctx.moveTo(46, 38); ctx.lineTo(40, 65); ctx.lineTo(46, 75); ctx.lineTo(74, 75); ctx.lineTo(80, 65); ctx.lineTo(74, 38); ctx.closePath();
  ctx.fill();

  ctx.fillStyle = '#D32F2F';
  ctx.beginPath();
  ctx.moveTo(50, 38); ctx.lineTo(47, 65); ctx.lineTo(52, 76); ctx.lineTo(68, 76); ctx.lineTo(73, 65); ctx.lineTo(70, 38); ctx.closePath();
  ctx.fill();

  ctx.beginPath();
  ctx.moveTo(42, 72); ctx.lineTo(30, 60); ctx.lineTo(36, 80); ctx.lineTo(44, 78); ctx.closePath();
  ctx.fill();
  ctx.beginPath();
  ctx.moveTo(78, 72); ctx.lineTo(90, 60); ctx.lineTo(84, 80); ctx.lineTo(76, 78); ctx.closePath();
  ctx.fill();

  ctx.fillStyle = '#111';
  ctx.beginPath();
  ctx.moveTo(60, 56); ctx.lineTo(57, 60); ctx.lineTo(60, 63); ctx.lineTo(63, 60); ctx.closePath();
  ctx.fill();

  ctx.fillStyle = '#D32F2F';
  ctx.beginPath();
  ctx.ellipse(60, 102, 22, 28, 0, 0, Math.PI * 2);
  ctx.fill();

  ctx.fillStyle = '#111';
  ctx.beginPath();
  ctx.moveTo(57, 94); ctx.lineTo(44, 102); ctx.lineTo(48, 112); ctx.lineTo(57, 106); ctx.closePath();
  ctx.fill();
  ctx.fillStyle = '#FFF';
  ctx.beginPath();
  ctx.moveTo(56, 96); ctx.lineTo(46, 102); ctx.lineTo(49, 110); ctx.lineTo(56, 105); ctx.closePath();
  ctx.fill();

  ctx.fillStyle = '#111';
  ctx.beginPath();
  ctx.moveTo(63, 94); ctx.lineTo(76, 102); ctx.lineTo(72, 112); ctx.lineTo(63, 106); ctx.closePath();
  ctx.fill();
  ctx.fillStyle = '#FFF';
  ctx.beginPath();
  ctx.moveTo(64, 96); ctx.lineTo(74, 102); ctx.lineTo(71, 110); ctx.lineTo(64, 105); ctx.closePath();
  ctx.fill();

  ctx.restore();
}

function drawEvilEye(ctx, size) {
  ctx.save();
  ctx.scale(size / 120, size / 120);

  ctx.strokeStyle = '#FFD700';
  ctx.lineWidth = 4;
  ctx.beginPath();
  ctx.arc(60, 12, 8, 0, Math.PI * 2);
  ctx.stroke();

  ctx.fillStyle = '#15297C';
  ctx.beginPath();
  ctx.arc(60, 72, 48, 0, Math.PI * 2);
  ctx.fill();

  ctx.fillStyle = '#00B0FF';
  ctx.beginPath();
  ctx.arc(60, 72, 34, 0, Math.PI * 2);
  ctx.fill();

  ctx.fillStyle = '#FFFFFF';
  ctx.beginPath();
  ctx.arc(60, 72, 22, 0, Math.PI * 2);
  ctx.fill();

  ctx.fillStyle = '#111118';
  ctx.beginPath();
  ctx.arc(60, 72, 11, 0, Math.PI * 2);
  ctx.fill();

  ctx.fillStyle = 'rgba(255, 255, 255, 0.45)';
  ctx.beginPath();
  ctx.ellipse(50, 60, 7, 4, -Math.PI / 4, 0, Math.PI * 2);
  ctx.fill();

  ctx.restore();
}

function drawCapShield(ctx, size) {
  ctx.save();
  ctx.scale(size / 120, size / 120);

  ctx.strokeStyle = '#CFD8DC';
  ctx.lineWidth = 4;
  ctx.beginPath();
  ctx.arc(60, 12, 8, 0, Math.PI * 2);
  ctx.stroke();

  ctx.fillStyle = '#C62828';
  ctx.beginPath();
  ctx.arc(60, 72, 48, 0, Math.PI * 2);
  ctx.fill();

  ctx.fillStyle = '#ECEFF1';
  ctx.beginPath();
  ctx.arc(60, 72, 38, 0, Math.PI * 2);
  ctx.fill();

  ctx.fillStyle = '#C62828';
  ctx.beginPath();
  ctx.arc(60, 72, 28, 0, Math.PI * 2);
  ctx.fill();

  ctx.fillStyle = '#1565C0';
  ctx.beginPath();
  ctx.arc(60, 72, 18, 0, Math.PI * 2);
  ctx.fill();

  ctx.fillStyle = '#FFFFFF';
  ctx.beginPath();
  for (let i = 0; i < 5; i++) {
    const angle = (i * 4 * Math.PI) / 5 - Math.PI / 2;
    const x = 60 + 13 * Math.cos(angle);
    const y = 72 + 13 * Math.sin(angle);
    if (i === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  }
  ctx.closePath();
  ctx.fill();

  ctx.restore();
}

function drawLuckyCat(ctx, size) {
  ctx.save();
  ctx.scale(size / 120, size / 120);

  ctx.strokeStyle = '#E53935';
  ctx.lineWidth = 3.5;
  ctx.beginPath();
  ctx.arc(60, 12, 8, 0, Math.PI * 2);
  ctx.stroke();

  ctx.fillStyle = '#FFFFFF';
  ctx.beginPath();
  ctx.ellipse(60, 76, 30, 44, 0, 0, Math.PI * 2);
  ctx.fill();

  ctx.beginPath();
  ctx.moveTo(34, 46); ctx.lineTo(24, 24); ctx.lineTo(44, 38); ctx.closePath();
  ctx.fill();
  ctx.fillStyle = '#FF8A80';
  ctx.beginPath();
  ctx.moveTo(33, 44); ctx.lineTo(26, 28); ctx.lineTo(41, 38); ctx.closePath();
  ctx.fill();

  ctx.fillStyle = '#FFFFFF';
  ctx.beginPath();
  ctx.moveTo(86, 46); ctx.lineTo(96, 24); ctx.lineTo(76, 38); ctx.closePath();
  ctx.fill();
  ctx.fillStyle = '#FF8A80';
  ctx.beginPath();
  ctx.moveTo(87, 44); ctx.lineTo(94, 28); ctx.lineTo(79, 38); ctx.closePath();
  ctx.fill();

  ctx.fillStyle = '#FFFFFF';
  ctx.beginPath();
  ctx.ellipse(32, 54, 8, 14, -0.4, 0, Math.PI * 2);
  ctx.fill();

  ctx.fillStyle = '#E53935';
  ctx.fillRect(44, 76, 32, 5);
  ctx.fillStyle = '#FFD700';
  ctx.beginPath();
  ctx.arc(60, 83, 6, 0, Math.PI * 2);
  ctx.fill();

  ctx.fillStyle = '#FFCA28';
  ctx.beginPath();
  ctx.ellipse(60, 102, 9, 13, 0, 0, Math.PI * 2);
  ctx.fill();

  ctx.strokeStyle = '#212121';
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.arc(48, 56, 4, Math.PI, 0); ctx.stroke();
  ctx.beginPath();
  ctx.arc(72, 56, 4, Math.PI, 0); ctx.stroke();

  ctx.restore();
}

function drawGhost(ctx, size) {
  ctx.save();
  ctx.scale(size / 120, size / 120);

  ctx.strokeStyle = '#7C4DFF';
  ctx.lineWidth = 3.5;
  ctx.beginPath();
  ctx.arc(60, 12, 8, 0, Math.PI * 2);
  ctx.stroke();

  ctx.fillStyle = '#F8FAFC';
  ctx.beginPath();
  ctx.moveTo(34, 74);
  ctx.bezierCurveTo(34, 38, 86, 38, 86, 74);
  ctx.lineTo(86, 114);
  ctx.quadraticCurveTo(80, 122, 74, 114);
  ctx.quadraticCurveTo(68, 122, 60, 114);
  ctx.quadraticCurveTo(52, 122, 46, 114);
  ctx.quadraticCurveTo(40, 122, 34, 114);
  ctx.closePath();
  ctx.fill();

  ctx.fillStyle = '#1E293B';
  ctx.beginPath();
  ctx.arc(50, 68, 5, 0, Math.PI * 2); ctx.fill();
  ctx.beginPath();
  ctx.arc(70, 68, 5, 0, Math.PI * 2); ctx.fill();

  ctx.fillStyle = '#FFF';
  ctx.beginPath();
  ctx.arc(48, 66, 1.8, 0, Math.PI * 2); ctx.fill();
  ctx.beginPath();
  ctx.arc(68, 66, 1.8, 0, Math.PI * 2); ctx.fill();

  ctx.fillStyle = '#FF80AB';
  ctx.beginPath();
  ctx.arc(44, 76, 3.5, 0, Math.PI * 2); ctx.fill();
  ctx.beginPath();
  ctx.arc(76, 76, 3.5, 0, Math.PI * 2); ctx.fill();

  ctx.restore();
}

// Small Graphic Ring / Crescent Moon Connector linking rope and charm
function drawCharmConnector(ctx, style = 'crescent') {
  const ringRadius = Math.max(7, Math.min(13, ropeThickness * 1.4));
  const connectorH = ringRadius * 2.2;

  ctx.save();

  if (style === 'ring') {
    // --- 1. JUMP RING CONNECTOR ---
    const cy = -ringRadius * 0.35;
    const rOuter = ringRadius;
    const rInner = ringRadius * 0.58;

    // Top loop connecting to rope end
    ctx.fillStyle = '#CFD8DC';
    ctx.beginPath();
    ctx.arc(0, -connectorH * 0.48, 2.6, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.arc(0, -connectorH * 0.48, 1.2, 0, Math.PI * 2);
    ctx.fill();

    // Main circular ring
    ctx.fillStyle = '#CFD8DC';
    ctx.beginPath();
    ctx.arc(0, cy, rOuter, 0, Math.PI * 2);
    ctx.arc(0, cy, rInner, 0, Math.PI * 2, true);
    ctx.fill();

    // 3D bevel edges
    ctx.strokeStyle = '#607D8B';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.arc(0, cy, rInner, 0, Math.PI * 2);
    ctx.stroke();
    ctx.beginPath();
    ctx.arc(0, cy, rOuter, 0, Math.PI * 2);
    ctx.stroke();

    // Upper-left gleam arc
    ctx.strokeStyle = '#FFFFFF';
    ctx.lineWidth = 1.6;
    ctx.beginPath();
    ctx.arc(0, cy, rOuter - 0.5, Math.PI * 1.1, Math.PI * 1.55);
    ctx.stroke();

    // Bottom clasp bracket gripping top of charm
    const claspW = ringRadius * 1.15;
    const claspH = 4.2;
    ctx.fillStyle = '#607D8B';
    ctx.beginPath();
    ctx.roundRect(-claspW / 2, -1, claspW, claspH, 2);
    ctx.fill();
    ctx.fillStyle = '#CFD8DC';
    ctx.beginPath();
    ctx.roundRect(-claspW / 2 + 0.8, 0, claspW - 1.6, claspH - 1.8, 1.5);
    ctx.fill();
    // Rivet
    ctx.fillStyle = '#78909C';
    ctx.beginPath();
    ctx.arc(0, 1.2, 1.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.arc(0, 1.0, 0.7, 0, Math.PI * 2);
    ctx.fill();

  } else {
    // --- 2. CRESCENT MOON CONNECTOR (Default) ---
    const cy = -ringRadius * 0.35;
    const moonR = ringRadius * 1.15;

    // Top eyelet ring connecting to rope end
    const eyeletY = -connectorH * 0.52;
    ctx.fillStyle = '#607D8B';
    ctx.beginPath();
    ctx.arc(0, eyeletY, 2.8, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#CFD8DC';
    ctx.beginPath();
    ctx.arc(0, eyeletY, 2.2, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.arc(0, eyeletY, 1.0, 0, Math.PI * 2);
    ctx.fill();

    // Crescent Moon Body
    ctx.beginPath();
    ctx.arc(0, cy, moonR, Math.PI * 0.6, Math.PI * 1.95, false);
    ctx.arc(2.0, cy, moonR * 0.76, Math.PI * 1.9, Math.PI * 0.65, true);
    ctx.closePath();

    ctx.fillStyle = '#CFD8DC';
    ctx.fill();
    ctx.strokeStyle = '#607D8B';
    ctx.lineWidth = 1.1;
    ctx.stroke();

    // Outer curve gleam
    ctx.strokeStyle = '#FFFFFF';
    ctx.lineWidth = 1.8;
    ctx.beginPath();
    ctx.arc(0, cy, moonR - 0.8, Math.PI * 0.8, Math.PI * 1.4);
    ctx.stroke();

    // Star accent
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.arc(-moonR * 0.52, cy, 1.2, 0, Math.PI * 2);
    ctx.fill();

    // Bottom clasp bracket
    const claspW = ringRadius * 1.1;
    const claspH = 4.2;
    ctx.fillStyle = '#607D8B';
    ctx.beginPath();
    ctx.roundRect(-claspW / 2, -1, claspW, claspH, 2);
    ctx.fill();
    ctx.fillStyle = '#CFD8DC';
    ctx.beginPath();
    ctx.roundRect(-claspW / 2 + 0.8, 0, claspW - 1.6, claspH - 1.8, 1.5);
    ctx.fill();
    // Rivet
    ctx.fillStyle = '#78909C';
    ctx.beginPath();
    ctx.arc(0, 1.2, 1.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.arc(0, 1.0, 0.7, 0, Math.PI * 2);
    ctx.fill();
  }

  ctx.restore();
}

// Color Manipulation Helpers for Rope Rendering
function hexToRgb(hex) {
  hex = hex.replace('#', '');
  if (hex.length === 3) hex = hex.split('').map(c => c + c).join('');
  const num = parseInt(hex, 16);
  return [(num >> 16) & 255, (num >> 8) & 255, num & 255];
}

function rgbToHex(r, g, b) {
  return '#' + [r, g, b].map(x => Math.round(Math.max(0, Math.min(255, x))).toString(16).padStart(2, '0')).join('');
}

function darkenHex(hex, factor) {
  const [r, g, b] = hexToRgb(hex);
  return rgbToHex(r * factor, g * factor, b * factor);
}

function lightenHex(hex, factor) {
  const [r, g, b] = hexToRgb(hex);
  return rgbToHex(r + (255 - r) * factor, g + (255 - g) * factor, b + (255 - b) * factor);
}

// Authentic Hangly Mac Spider Web Rendering
function renderMacSpiderWeb(ctx) {
  const ax = nodes[0].x;
  const ay = nodes[0].y;

  const [r, g, b] = hexToRgb(ropeColor);
  const isLight = (r > 210 && g > 210 && b > 210);

  const scaleRatio = Math.max(0.6, ropeThickness / 4);
  const fanSpread = 18 * scaleRatio;

  // 1. Notch silk anchor fan (scales with ropeThickness)
  ctx.strokeStyle = isLight ? 'rgba(255, 255, 255, 0.7)' : `rgba(${r}, ${g}, ${b}, 0.65)`;
  ctx.lineWidth = Math.max(1, ropeThickness * 0.35);
  ctx.beginPath();
  ctx.moveTo(ax, ay); ctx.lineTo(ax - fanSpread, ay + 6 * scaleRatio);
  ctx.moveTo(ax, ay); ctx.lineTo(ax - fanSpread * 0.5, ay + 12 * scaleRatio);
  ctx.moveTo(ax, ay); ctx.lineTo(ax + fanSpread * 0.5, ay + 12 * scaleRatio);
  ctx.moveTo(ax, ay); ctx.lineTo(ax + fanSpread, ay + 6 * scaleRatio);
  ctx.stroke();

  // Anchor silk patch
  ctx.fillStyle = isLight ? '#FFF' : ropeColor;
  ctx.beginPath();
  ctx.arc(ax, ay + 2 * scaleRatio, Math.max(2.5, ropeThickness * 0.8), 0, Math.PI * 2);
  ctx.fill();

  // 2. Smooth path through nodes
  ctx.beginPath();
  ctx.moveTo(nodes[0].x, nodes[0].y);
  for (let i = 1; i < NUM_NODES; i++) {
    const p0 = nodes[i - 1];
    const p1 = nodes[i];
    const midX = (p0.x + p1.x) / 2;
    const midY = (p0.y + p1.y) / 2;
    ctx.quadraticCurveTo(p0.x, p0.y, midX, midY);
  }
  const last = nodes[NUM_NODES - 1];
  ctx.lineTo(last.x, last.y);

  // 3. Glistening soft aura
  ctx.strokeStyle = isLight ? 'rgba(255, 255, 255, 0.28)' : `rgba(${r}, ${g}, ${b}, 0.35)`;
  ctx.lineWidth = Math.max(3, ropeThickness * 1.35);
  ctx.stroke();

  // 4. Core silk line
  ctx.strokeStyle = isLight ? '#FFFFFF' : ropeColor;
  ctx.lineWidth = Math.max(1.5, ropeThickness * 0.65);
  ctx.stroke();

  // 5. Twisted helical silk spiral
  ctx.strokeStyle = isLight ? '#D0E8FF' : lightenHex(ropeColor, 0.35);
  ctx.lineWidth = Math.max(1, ropeThickness * 0.35);
  ctx.beginPath();
  const steps = 30;
  for (let step = 0; step <= steps; step++) {
    const t = step / steps;
    const nodeIdx = Math.min(NUM_NODES - 2, Math.floor(t * (NUM_NODES - 1)));
    const localT = (t * (NUM_NODES - 1)) - nodeIdx;

    const nA = nodes[nodeIdx];
    const nB = nodes[nodeIdx + 1];

    const bx = nA.x + (nB.x - nA.x) * localT;
    const by = nA.y + (nB.y - nA.y) * localT;

    const angle = step * 1.8;
    const wave = Math.sin(angle) * Math.max(1.5, ropeThickness * 0.45);

    const dx = nB.x - nA.x;
    const dy = nB.y - nA.y;
    const len = Math.hypot(dx, dy) || 1;
    const px = bx + (-dy / len) * wave;
    const py = by + (dx / len) * wave;

    if (step === 0) ctx.moveTo(px, py);
    else ctx.lineTo(px, py);
  }
  ctx.stroke();

  // 6. Bottom web knot
  ctx.fillStyle = isLight ? '#FFF' : ropeColor;
  ctx.beginPath();
  ctx.arc(last.x, last.y, Math.max(3, ropeThickness * 0.9), 0, Math.PI * 2);
  ctx.fill();
}

// Braided 3-Strand Rope Rendering
function renderBraidedRope(ctx) {
  const braidW = Math.max(4, ropeThickness * 1.4);
  const steps = 36;
  const strandColors = [
    darkenHex(ropeColor, 0.72),
    ropeColor,
    lightenHex(ropeColor, 0.22)
  ];
  const strandOffsets = [0, 2.094, 4.188];

  ctx.lineWidth = Math.max(1.5, ropeThickness * 0.55);
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';

  strandOffsets.forEach((phase, sIdx) => {
    ctx.strokeStyle = strandColors[sIdx];
    ctx.beginPath();
    for (let step = 0; step <= steps; step++) {
      const t = step / steps;
      const nodeIdx = Math.min(NUM_NODES - 2, Math.floor(t * (NUM_NODES - 1)));
      const localT = (t * (NUM_NODES - 1)) - nodeIdx;

      const nA = nodes[nodeIdx];
      const nB = nodes[nodeIdx + 1];

      const bx = nA.x + (nB.x - nA.x) * localT;
      const by = nA.y + (nB.y - nA.y) * localT;

      const angle = step * 1.6 + phase;
      const wave = Math.sin(angle) * (braidW * 0.42);

      const dx = nB.x - nA.x;
      const dy = nB.y - nA.y;
      const len = Math.hypot(dx, dy) || 1;
      const px = bx + (-dy / len) * wave;
      const py = by + (dx / len) * wave;

      if (step === 0) ctx.moveTo(px, py);
      else ctx.lineTo(px, py);
    }
    ctx.stroke();
  });
}

// Render Rope Styles on Canvas
function renderRope(ctx) {
  if (ropeStyle === 'web') {
    renderMacSpiderWeb(ctx);
  } else if (ropeStyle === 'chain') {
    const linkW = Math.max(5, ropeThickness * 1.7);
    const linkLen = linkW * 1.85;
    for (let i = 0; i < NUM_NODES - 1; i++) {
      const n1 = nodes[i];
      const n2 = nodes[i + 1];
      const dx = n2.x - n1.x;
      const dy = n2.y - n1.y;
      const dist = Math.hypot(dx, dy);
      const angle = Math.atan2(dy, dx);
      const numLinks = Math.max(1, Math.floor(dist / (linkLen * 0.72)));

      for (let j = 0; j < numLinks; j++) {
        const t = (j + 0.5) / numLinks;
        const lx = n1.x + dx * t;
        const ly = n1.y + dy * t;

        ctx.save();
        ctx.translate(lx, ly);
        ctx.rotate(angle);

        if (j % 2 === 0) {
          ctx.strokeStyle = ropeColor;
          ctx.lineWidth = Math.max(1.5, ropeThickness * 0.45);
          ctx.beginPath();
          ctx.roundRect(-linkLen / 2, -linkW / 2, linkLen, linkW, linkW / 2);
          ctx.stroke();
        } else {
          ctx.strokeStyle = darkenHex(ropeColor, 0.72);
          ctx.lineWidth = Math.max(1, ropeThickness * 0.28);
          ctx.beginPath();
          ctx.roundRect(-linkLen / 2, -linkW / 3.8, linkLen, linkW / 1.9, linkW / 3.8);
          ctx.stroke();
        }
        ctx.restore();
      }
    }
  } else if (ropeStyle === 'beads') {
    ctx.strokeStyle = darkenHex(ropeColor, 0.7);
    ctx.lineWidth = Math.max(1, ropeThickness * 0.35);
    ctx.beginPath();
    ctx.moveTo(nodes[0].x, nodes[0].y);
    for (let i = 1; i < NUM_NODES; i++) {
      ctx.lineTo(nodes[i].x, nodes[i].y);
    }
    ctx.stroke();

    const beadR = Math.max(3.5, ropeThickness * 1.25);
    for (let i = 0; i < NUM_NODES - 1; i++) {
      const n1 = nodes[i];
      const n2 = nodes[i + 1];
      const dx = n2.x - n1.x;
      const dy = n2.y - n1.y;
      const dist = Math.hypot(dx, dy);
      const numBeads = Math.max(2, Math.floor(dist / (beadR * 2.3)));

      for (let j = 0; j < numBeads; j++) {
        const t = (j + 0.5) / numBeads;
        const bx = n1.x + dx * t;
        const by = n1.y + dy * t;

        ctx.fillStyle = ropeColor;
        ctx.beginPath();
        ctx.arc(bx, by, beadR, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = 'rgba(255, 255, 255, 0.7)';
        ctx.beginPath();
        ctx.arc(bx - beadR * 0.35, by - beadR * 0.35, beadR * 0.35, 0, Math.PI * 2);
        ctx.fill();
      }
    }
  } else if (ropeStyle === 'braided' || ropeStyle === 'ribbon') {
    renderBraidedRope(ctx);
  } else {
    // Classic Cord
    ctx.strokeStyle = ropeColor;
    ctx.lineWidth = ropeThickness;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.beginPath();
    ctx.moveTo(nodes[0].x, nodes[0].y);
    for (let i = 1; i < NUM_NODES; i++) {
      const p0 = nodes[i - 1];
      const p1 = nodes[i];
      const midX = (p0.x + p1.x) / 2;
      const midY = (p0.y + p1.y) / 2;
      ctx.quadraticCurveTo(p0.x, p0.y, midX, midY);
    }
    ctx.lineTo(nodes[NUM_NODES - 1].x, nodes[NUM_NODES - 1].y);
    ctx.stroke();
  }
}

// Render Loop
function renderLoop(now) {
  const dt = (now - lastFrameTime) / 1000;
  lastFrameTime = now;

  frameCount++;
  if (now - fpsLastTime >= 500) {
    const currentFPS = Math.round((frameCount * 1000) / (now - fpsLastTime));
    metricFPS.textContent = currentFPS;
    frameCount = 0;
    fpsLastTime = now;
  }

  updatePhysics(dt);

  const rect = canvas.getBoundingClientRect();
  ctx.clearRect(0, 0, rect.width, rect.height);

  // 1. Anchor
  if (ropeStyle !== 'web') {
    ctx.fillStyle = '#37474F';
    ctx.beginPath();
    ctx.roundRect(anchorX - 14, 0, 28, 8, 4);
    ctx.fill();
  }

  // 2. Rope
  renderRope(ctx);

  // 3. Charm
  const nPrev = nodes[NUM_NODES - 2];
  const last = nodes[NUM_NODES - 1];
  const charmX = last.x;
  const charmY = last.y;
  const angle = Math.atan2(last.x - nPrev.x, last.y - nPrev.y);

  ctx.save();
  ctx.translate(charmX, charmY);
  ctx.rotate(-angle);

  ctx.save();
  if (currentCharm === 'custom' && customProcessedCanvas) {
    const imgAspect = customProcessedCanvas.width / customProcessedCanvas.height;
    let renderW = charmSize;
    let renderH = charmSize;
    if (imgAspect > 1) {
      renderH = charmSize / imgAspect;
    } else {
      renderW = charmSize * imgAspect;
    }
    ctx.translate(-renderW / 2, 0);
    ctx.drawImage(customProcessedCanvas, 0, 0, renderW, renderH);
  } else {
    ctx.translate(-charmSize / 2, 0);
    if (currentCharm === 'evil_eye') {
      drawEvilEye(ctx, charmSize);
    } else if (currentCharm === 'cap_shield') {
      drawCapShield(ctx, charmSize);
    } else if (currentCharm === 'lucky_cat') {
      drawLuckyCat(ctx, charmSize);
    } else if (currentCharm === 'ghost') {
      drawGhost(ctx, charmSize);
    } else {
      drawSpiderMan(ctx, charmSize);
    }
  }
  ctx.restore();

  // Draw Connector Hardware (Crescent Moon or Jump Ring)
  drawCharmConnector(ctx, connectorStyle);

  ctx.restore();

  // Check Pull Down Threshold
  const pullDist = Math.max(0, charmY - anchorY - ropeLength);
  const isLeftSide = anchorX < rect.width / 2;

  if (isBeingDragged && pullDist > 25) {
    pullTooltip.classList.add('active');
    pullTooltip.style.left = `${charmX}px`;
    pullTooltip.style.top = `${charmY + charmSize + 16}px`;

    if (pullDist >= 75) {
      pullTooltip.textContent = isLeftSide ? "Release: Inbuilt Notifications 🔔" : "Release: Inbuilt Quick Settings ⚡";
      pullTooltip.style.borderColor = '#00E676';
      pullTooltip.style.color = '#00E676';
    } else {
      pullTooltip.textContent = isLeftSide ? "Pull for Notifications" : "Pull for Quick Settings";
      pullTooltip.style.borderColor = '#00E5FF';
      pullTooltip.style.color = '#00E5FF';
    }
  } else {
    pullTooltip.classList.remove('active');
  }

  // Check Sleep
  if (isResting && !isBeingDragged) {
    animationFrameId = null;
    updateEngineStateUI(false);
  } else {
    animationFrameId = requestAnimationFrame(renderLoop);
  }
}

// Pointer Events
function handlePointerDown(clientX, clientY) {
  const rect = canvas.getBoundingClientRect();
  const x = clientX - rect.left;
  const y = clientY - rect.top;

  const last = nodes[NUM_NODES - 1];
  const dist = Math.hypot(x - last.x, y - (last.y + charmSize / 2));

  if (dist <= charmSize * 1.3 || (y <= last.y + charmSize && Math.abs(x - last.x) < 30)) {
    isBeingDragged = true;
    touchX = x;
    touchY = y;
    lastTouchX = x;
    lastTouchY = y;
    lastTouchTime = performance.now();
    wakeUp();
  }
}

function handlePointerMove(clientX, clientY) {
  if (!isBeingDragged) return;
  const rect = canvas.getBoundingClientRect();
  const x = clientX - rect.left;
  const y = clientY - rect.top;

  touchX = x;
  touchY = y;
  lastTouchX = x;
  lastTouchY = y;
  lastTouchTime = performance.now();
  wakeUp();
}

function handlePointerUp() {
  if (!isBeingDragged) return;

  const rect = canvas.getBoundingClientRect();
  const last = nodes[NUM_NODES - 1];
  const pullDist = Math.max(0, last.y - anchorY - ropeLength);
  const isLeftSide = anchorX < rect.width / 2;

  if (pullDist >= 75) {
    if (isLeftSide) {
      notifShade.classList.add('visible');
    } else {
      qsShade.classList.add('visible');
    }
  }

  const dt = Math.max(1, performance.now() - lastTouchTime);
  const vx = (touchX - lastTouchX) / dt * 15;
  const vy = (touchY - lastTouchY) / dt * 15;

  last.px -= vx;
  last.py -= vy;

  isBeingDragged = false;
  pullTooltip.classList.remove('active');
  wakeUp();
}

canvas.addEventListener('mousedown', (e) => handlePointerDown(e.clientX, e.clientY));
window.addEventListener('mousemove', (e) => handlePointerMove(e.clientX, e.clientY));
window.addEventListener('mouseup', handlePointerUp);

canvas.addEventListener('touchstart', (e) => {
  if (e.touches.length > 0) {
    handlePointerDown(e.touches[0].clientX, e.touches[0].clientY);
  }
}, { passive: true });

window.addEventListener('touchmove', (e) => {
  if (e.touches.length > 0) {
    handlePointerMove(e.touches[0].clientX, e.touches[0].clientY);
  }
}, { passive: true });

window.addEventListener('touchend', handlePointerUp);

// Sliders
sliderPosition.addEventListener('input', (e) => {
  const val = parseInt(e.target.value);
  horizontalPercent = val / 100;
  updateAnchor();
  valPosition.textContent = `${val}% (${val < 35 ? 'Left side' : val > 65 ? 'Right side' : 'Center Notch'})`;
  wakeUp();
});

document.getElementById('btnChipLeft').addEventListener('click', () => {
  sliderPosition.value = 20;
  sliderPosition.dispatchEvent(new Event('input'));
});
document.getElementById('btnChipCenter').addEventListener('click', () => {
  sliderPosition.value = 50;
  sliderPosition.dispatchEvent(new Event('input'));
});
document.getElementById('btnChipRight').addEventListener('click', () => {
  sliderPosition.value = 80;
  sliderPosition.dispatchEvent(new Event('input'));
});

sliderRopeLength.addEventListener('input', (e) => {
  ropeLength = parseInt(e.target.value);
  valRopeLength.textContent = `${ropeLength} px`;
  wakeUp();
});

sliderRopeThickness.addEventListener('input', (e) => {
  ropeThickness = parseInt(e.target.value);
  valRopeThickness.textContent = `${ropeThickness} px`;
  wakeUp();
});

sliderCharmSize.addEventListener('input', (e) => {
  charmSize = parseInt(e.target.value);
  valCharmSize.textContent = `${charmSize} px`;
  wakeUp();
});

sliderGravity.addEventListener('input', (e) => {
  const val = parseInt(e.target.value);
  gravityMultiplier = val / 100;
  valGravity.textContent = `${val}% (${(val / 100).toFixed(2)}g)`;
  wakeUp();
});

// Rope Style Buttons
document.querySelectorAll('.rope-style-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.rope-style-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    ropeStyle = btn.dataset.rope;
    wakeUp();
  });
});

// Connector Style Buttons
document.querySelectorAll('.connector-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.connector-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    connectorStyle = btn.dataset.connector;
    const valConnector = document.getElementById('valConnector');
    if (valConnector) {
      valConnector.textContent = connectorStyle === 'crescent' ? 'Crescent Moon' : 'Jump Ring';
    }
    wakeUp();
  });
});

// Color Swatches
document.querySelectorAll('.color-swatch').forEach(swatch => {
  swatch.addEventListener('click', () => {
    document.querySelectorAll('.color-swatch').forEach(s => s.classList.remove('active'));
    swatch.classList.add('active');
    ropeColor = swatch.dataset.color;
    wakeUp();
  });
});

// Charm Selector Buttons
document.querySelectorAll('.charm-btn').forEach(btn => {
  if (btn.dataset.charm) {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.charm-btn').forEach(b => b.classList.remove('active'));
      const customLabel = document.getElementById('labelCustomPhoto');
      if (customLabel) customLabel.classList.remove('active');
      btn.classList.add('active');
      currentCharm = btn.dataset.charm;
      customRawImage = null;
      updateCustomCharm();
      wakeUp();
    });
  }
});

// Shape Cropping Chips
document.querySelectorAll('.btn-shape-chip').forEach(chip => {
  chip.addEventListener('click', () => {
    document.querySelectorAll('.btn-shape-chip').forEach(c => c.classList.remove('active'));
    chip.classList.add('active');
    currentCropShape = chip.dataset.shape;
    updateCustomCharm();
  });
});

// Interactive Charm Window Elements (Zoom In/Out, Reset, Pan, Wheel)
const cropViewport = document.getElementById('cropViewport');
const cropWindowCanvas = document.getElementById('cropWindowCanvas');
const btnCropZoomIn = document.getElementById('btnCropZoomIn');
const btnCropZoomOut = document.getElementById('btnCropZoomOut');
const btnCropReset = document.getElementById('btnCropReset');
const valCropZoom = document.getElementById('valCropZoom');

if (btnCropZoomIn) {
  btnCropZoomIn.addEventListener('click', () => {
    cropZoom = Math.min(3.0, +(cropZoom + 0.2).toFixed(1));
    if (valCropZoom) valCropZoom.textContent = `${cropZoom.toFixed(1)}x`;
    updateCustomCharm();
  });
}

if (btnCropZoomOut) {
  btnCropZoomOut.addEventListener('click', () => {
    cropZoom = Math.max(0.6, +(cropZoom - 0.2).toFixed(1));
    if (valCropZoom) valCropZoom.textContent = `${cropZoom.toFixed(1)}x`;
    updateCustomCharm();
  });
}

if (btnCropReset) {
  btnCropReset.addEventListener('click', () => {
    cropZoom = 1.0;
    cropPanX = 0.5;
    cropPanY = 0.5;
    if (valCropZoom) valCropZoom.textContent = '1.0x';
    updateCustomCharm();
  });
}

if (cropViewport) {
  let isDraggingCrop = false;
  let startX = 0;
  let startY = 0;

  cropViewport.addEventListener('mousedown', (e) => {
    isDraggingCrop = true;
    startX = e.clientX;
    startY = e.clientY;
    e.preventDefault();
  });

  window.addEventListener('mousemove', (e) => {
    if (!isDraggingCrop) return;
    const dx = (e.clientX - startX) / 160;
    const dy = (e.clientY - startY) / 160;
    cropPanX = Math.max(0, Math.min(1, cropPanX - dx * 0.8));
    cropPanY = Math.max(0, Math.min(1, cropPanY - dy * 0.8));
    startX = e.clientX;
    startY = e.clientY;
    updateCustomCharm();
  });

  window.addEventListener('mouseup', () => {
    isDraggingCrop = false;
  });

  cropViewport.addEventListener('wheel', (e) => {
    e.preventDefault();
    if (e.deltaY < 0) {
      cropZoom = Math.min(3.0, +(cropZoom + 0.15).toFixed(2));
    } else {
      cropZoom = Math.max(0.6, +(cropZoom - 0.15).toFixed(2));
    }
    if (valCropZoom) valCropZoom.textContent = `${cropZoom.toFixed(1)}x`;
    updateCustomCharm();
  }, { passive: false });

  cropViewport.addEventListener('touchstart', (e) => {
    if (e.touches.length === 1) {
      isDraggingCrop = true;
      startX = e.touches[0].clientX;
      startY = e.touches[0].clientY;
    }
  }, { passive: true });

  cropViewport.addEventListener('touchmove', (e) => {
    if (!isDraggingCrop || e.touches.length !== 1) return;
    const dx = (e.touches[0].clientX - startX) / 160;
    const dy = (e.touches[0].clientY - startY) / 160;
    cropPanX = Math.max(0, Math.min(1, cropPanX - dx * 0.8));
    cropPanY = Math.max(0, Math.min(1, cropPanY - dy * 0.8));
    startX = e.touches[0].clientX;
    startY = e.touches[0].clientY;
    updateCustomCharm();
  }, { passive: true });

  cropViewport.addEventListener('touchend', () => {
    isDraggingCrop = false;
  });
}

function createShapePath(c, shape, w, h) {
  c.beginPath();
  const cx = w / 2;
  const cy = h / 2;

  if (shape === 'heart') {
    c.moveTo(cx, h * 0.28);
    c.bezierCurveTo(cx, h * 0.05, 0, h * 0.05, 0, h * 0.45);
    c.bezierCurveTo(0, h * 0.68, cx - w * 0.25, h * 0.82, cx, h);
    c.bezierCurveTo(cx + w * 0.25, h * 0.82, w, h * 0.68, w, h * 0.45);
    c.bezierCurveTo(w, h * 0.05, cx, h * 0.05, cx, h * 0.28);
    c.closePath();
  } else if (shape === 'square') {
    const radius = w * 0.20;
    c.roundRect(0, 0, w, h, radius);
  } else if (shape === 'star') {
    const numPoints = 5;
    const outerRadius = w / 2;
    const innerRadius = outerRadius * 0.42;
    const angleStep = Math.PI / numPoints;
    for (let i = 0; i < numPoints * 2; i++) {
      const r = i % 2 === 0 ? outerRadius : innerRadius;
      const angle = i * angleStep - Math.PI / 2;
      const x = cx + r * Math.cos(angle);
      const y = cy + r * Math.sin(angle);
      if (i === 0) c.moveTo(x, y);
      else c.lineTo(x, y);
    }
    c.closePath();
  } else if (shape === 'shield') {
    c.moveTo(cx, 0);
    c.lineTo(w, h * 0.12);
    c.lineTo(w, h * 0.58);
    c.bezierCurveTo(w, h * 0.82, cx, h * 0.95, cx, h);
    c.bezierCurveTo(cx, h * 0.95, 0, h * 0.82, 0, h * 0.58);
    c.lineTo(0, h * 0.12);
    c.closePath();
  } else {
    // Default: circle
    c.arc(cx, cy, w / 2, 0, Math.PI * 2);
    c.closePath();
  }
}

function applyShapeCropToImage(img) {
  const targetSize = 300;
  const offCanvas = document.createElement('canvas');
  offCanvas.width = targetSize;
  offCanvas.height = targetSize;
  const octx = offCanvas.getContext('2d');

  // Clip shape (ZERO border)
  createShapePath(octx, currentCropShape, targetSize, targetSize);
  octx.clip();

  const srcW = img.naturalWidth || img.width;
  const srcH = img.naturalHeight || img.height;
  const minDim = Math.min(srcW, srcH);
  const cropDim = Math.max(10, Math.min(minDim, minDim / cropZoom));

  const maxOffsetX = srcW - cropDim;
  const maxOffsetY = srcH - cropDim;

  const cropLeft = Math.max(0, Math.min(maxOffsetX, maxOffsetX * cropPanX));
  const cropTop = Math.max(0, Math.min(maxOffsetY, maxOffsetY * cropPanY));

  octx.drawImage(img, cropLeft, cropTop, cropDim, cropDim, 0, 0, targetSize, targetSize);

  customProcessedCanvas = offCanvas;
  currentCharm = 'custom';
  wakeUp();
}



function getCurrentCharmSourceCanvas() {
  if (customRawImage) {
    return customRawImage;
  }
  const c = document.createElement('canvas');
  c.width = 300;
  c.height = 300;
  const ctx2 = c.getContext('2d');
  ctx2.save();
  ctx2.translate(150, 20);
  if (currentCharm === 'evil_eye') {
    drawEvilEye(ctx2, 260);
  } else if (currentCharm === 'cap_shield') {
    drawCapShield(ctx2, 260);
  } else if (currentCharm === 'lucky_cat') {
    drawLuckyCat(ctx2, 260);
  } else if (currentCharm === 'ghost') {
    drawGhost(ctx2, 260);
  } else {
    drawSpiderMan(ctx2, 260);
  }
  ctx2.restore();
  return c;
}

function renderCropWindow() {
  if (!cropWindowCanvas) return;
  const w = cropWindowCanvas.width;
  const h = cropWindowCanvas.height;
  const cctx = cropWindowCanvas.getContext('2d');
  cctx.clearRect(0, 0, w, h);

  // Background pattern for transparency
  cctx.fillStyle = '#1A212D';
  cctx.fillRect(0, 0, w, h);

  const src = getCurrentCharmSourceCanvas();
  if (!src) return;

  const srcW = src.naturalWidth || src.width;
  const srcH = src.naturalHeight || src.height;
  const minDim = Math.min(srcW, srcH);
  const cropDim = Math.max(10, Math.min(minDim, minDim / cropZoom));

  const maxOffsetX = srcW - cropDim;
  const maxOffsetY = srcH - cropDim;
  const cropLeft = Math.max(0, Math.min(maxOffsetX, maxOffsetX * cropPanX));
  const cropTop = Math.max(0, Math.min(maxOffsetY, maxOffsetY * cropPanY));

  cctx.save();
  createShapePath(cctx, currentCropShape, w, h);
  cctx.clip();

  cctx.drawImage(src, cropLeft, cropTop, cropDim, cropDim, 0, 0, w, h);
  cctx.restore();

  // Outer dashed guide boundary (visual shape indicator)
  cctx.save();
  createShapePath(cctx, currentCropShape, w, h);
  cctx.setLineDash([4, 4]);
  cctx.strokeStyle = 'rgba(0, 229, 255, 0.45)';
  cctx.lineWidth = 1.5;
  cctx.stroke();
  cctx.restore();
}

function updateCustomCharm() {
  const src = getCurrentCharmSourceCanvas();
  if (src) {
    applyShapeCropToImage(src);
  }
  renderCropWindow();
}

// Custom Photo Upload
const customPhotoInput = document.getElementById('customPhotoInput');
if (customPhotoInput) {
  customPhotoInput.addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (event) => {
        const img = new Image();
        img.onload = () => {
          document.querySelectorAll('.charm-btn').forEach(b => b.classList.remove('active'));
          const customLabel = document.getElementById('labelCustomPhoto');
          if (customLabel) customLabel.classList.add('active');
          const customText = document.getElementById('customPhotoText');
          if (customText) customText.textContent = file.name.substring(0, 8) + '...';
          customRawImage = img;
          currentCharm = 'custom';
          updateCustomCharm();
          wakeUp();
        };
        img.src = event.target.result;
      };
      reader.readAsDataURL(file);
    }
  });
}

// Render Previews
function renderCharmPreviews() {
  const previews = [
    { id: 'prevSpiderman', draw: drawSpiderMan },
    { id: 'prevEvilEye', draw: drawEvilEye },
    { id: 'prevCapShield', draw: drawCapShield },
    { id: 'prevLuckyCat', draw: drawLuckyCat },
    { id: 'prevGhost', draw: drawGhost }
  ];

  previews.forEach(p => {
    const container = document.getElementById(p.id);
    if (!container) return;
    const mini = document.createElement('canvas');
    mini.width = 44;
    mini.height = 44;
    const mctx = mini.getContext('2d');
    p.draw(mctx, 40);
    container.appendChild(mini);
  });
}

// Close Shade Buttons
btnCloseNotif.addEventListener('click', () => notifShade.classList.remove('visible'));
btnCloseQS.addEventListener('click', () => qsShade.classList.remove('visible'));

// Test Action Buttons
document.getElementById('btnTestNotif').addEventListener('click', () => {
  notifShade.classList.toggle('visible');
  qsShade.classList.remove('visible');
});

document.getElementById('btnTestQS').addEventListener('click', () => {
  qsShade.classList.toggle('visible');
  notifShade.classList.remove('visible');
});

document.getElementById('btnResetWeb').addEventListener('click', () => {
  sliderPosition.value = 50;
  sliderRopeLength.value = 130;
  sliderRopeThickness.value = 4;
  sliderCharmSize.value = 68;
  sliderGravity.value = 75;

  sliderPosition.dispatchEvent(new Event('input'));
  sliderRopeLength.dispatchEvent(new Event('input'));
  sliderRopeThickness.dispatchEvent(new Event('input'));
  sliderCharmSize.dispatchEvent(new Event('input'));
  sliderGravity.dispatchEvent(new Event('input'));

  document.getElementById('btnRopeWeb').click();
  document.getElementById('btnCharmSpiderman').click();
  document.querySelector('.color-swatch[data-color="#212121"]').click();
});

// Live Clock
function updateClock() {
  const now = new Date();
  const hrs = String(now.getHours()).padStart(2, '0');
  const mins = String(now.getMinutes()).padStart(2, '0');
  document.getElementById('statusClock').textContent = `${hrs}:${mins}`;
}
setInterval(updateClock, 1000);
updateClock();

window.addEventListener('resize', resizeCanvas);
window.addEventListener('load', () => {
  resizeCanvas();
  renderCharmPreviews();
  renderCropWindow();
});
