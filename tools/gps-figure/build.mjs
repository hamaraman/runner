// 랜딩 페이지의 "GPS 기록" 그림을 만든다.
//   node tools/gps-figure/build.mjs           → docs/index.html의 <!-- gps-figure --> 사이에 넣는다
//   node tools/gps-figure/build.mjs --preview → 같은 폴더에 preview.html만 쓴다
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import * as k from "./iso-kit.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const INDEX = join(HERE, "..", "..", "docs", "index.html");

const WIDTH = 640;
const HEIGHT = 400;

// 1 단위 = 1 m. 트랙은 실제 400 m 트랙과 같은 크기(곡선 반지름 36.5 m).
const PLATE = { x: -150, y: -86, w: 300, d: 172, r: 8 };
const PLATE_H = 12;
const FOOT_H = 5;
const BOARD = { x: -122, y: -60, w: 204, d: 120, r: 3 };
const BOARD_H = 2;
const R = 36.5;
const STRAIGHT = (400 - 2 * Math.PI * R) / 2;
const CX = -20;
const LANE = 4;
const RAIL_Y = [-72, 72];
const RAIL_X = [-128, 70];
const BEAM_Z = 30;
const BEAM_H = 9;
const PEN_DX = 11;
const ODO = { x: 102, y: -34, w: 30, d: 68, r: 2 };
const ODO_Z = 3;
const ODO_H = 30;

const P = k.fitProjection(
  [...k.boxCorners(PLATE, -PLATE_H - FOOT_H, 0), [RAIL_X[0], RAIL_Y[0], BEAM_Z + 20], [RAIL_X[1], RAIL_Y[1], BEAM_Z + 20]],
  WIDTH,
  HEIGHT,
  { pad: 28, azimuth: 40 },
);

const at = (point) => k.iso(point, P);
const S = (paths, style) => k.solidSvg(paths, style);
const L = (d, style) => k.lineSvg(d, style);
const D = (points, style) => k.dotsSvg(points, style);
const byDepth = (points) => [...points].sort((a, b) => k.depthOf([a[0], a[1], 0], P) - k.depthOf([b[0], b[1], 0], P));
const stadium = (r) => ({ x: CX - STRAIGHT / 2 - r, y: -r, w: STRAIGHT + 2 * r, d: 2 * r, r });
const faceOf = (points) => k.pathOf(points.map(at), true);

function plate() {
  const out = [`<path class="iso-halo" d="${k.haloOf(PLATE, -PLATE_H - FOOT_H, PLATE_H, P)}"/>`];
  for (const [x, y] of byDepth(k.corners(PLATE, 24))) {
    out.push(S(k.cylinder(x, y, 7, -PLATE_H - FOOT_H, 1.6, P, 28), { tone: "lo" }));
    out.push(S(k.cylinder(x, y, 5.4, -PLATE_H - FOOT_H + 1.6, FOOT_H - 1.6, P, 28), { tone: "mid" }));
  }
  out.push(S(k.slabOf(PLATE, -PLATE_H, PLATE_H, P, 8, 1.6), { tone: "mid", inner: L(k.sideSeam(PLATE, -5, P), { tone: "faint" }) }));
  out.push(L(k.planOutline(k.insetPlan(PLATE, 5), 0, P), { tone: "lo" }));
  const screws = k.corners(PLATE, 11);
  out.push(L(screws.map(([x, y]) => k.ring(x, y, 2.2, 0, P, 16)).join(""), { tone: "lo" }));
  out.push(D(screws.map(([x, y]) => at([x, y, 0])), { size: 0.5 }));
  // 앞쪽 눈금자: 10 m마다 작은 눈금, 50 m마다 큰 눈금
  const strip = { x: -128, y: 77, w: 200, d: 5, r: 1 };
  out.push(S(k.slabOf(strip, 0, 0.6, P, 3), { tone: "lo", crease: "none" }));
  const ticks = k.topTicks(strip.x + 4, strip.x + strip.w - 4, 4, 5, strip.y + 0.6, 0.6, [1.6, 3], P);
  out.push(L(ticks.minor, { tone: "lo" }), L(ticks.major, { tone: "mid" }));
  out.push(D([at([strip.x + 2, strip.y + 2.5, 0.6]), at([strip.x + strip.w - 2, strip.y + 2.5, 0.6])], { size: 0.45 }));
  // 명판
  const tag = { x: 100, y: 46, w: 32, d: 22, r: 1.5 };
  out.push(S(k.slabOf(tag, 0, 0.8, P, 3, 0.3), { tone: "lo" }));
  out.push(L([5, 10, 15].map((dy) => k.lineOnTop([tag.x + 5, tag.y + dy + 1], [tag.x + tag.w - (dy === 15 ? 14 : 5), tag.y + dy + 1], 0.8, P)).join(""), { tone: "faint", free: true }));
  out.push(D(k.corners(tag, 2).map(([x, y]) => at([x, y, 0.8])), { size: 0.4 }));
  return out.join("");
}

function board() {
  const out = [S(k.slabOf(BOARD, 0, BOARD_H, P, 4, 0.3), { tone: "lo" })];
  const z = BOARD_H;
  const grid = [];
  for (let x = BOARD.x + 12; x < BOARD.x + BOARD.w; x += 20) grid.push(k.lineOnTop([x, BOARD.y + 0.6], [x, BOARD.y + BOARD.d - 0.6], z, P));
  for (let y = BOARD.y + 10; y < BOARD.y + BOARD.d; y += 20) grid.push(k.lineOnTop([BOARD.x + 0.6, y], [BOARD.x + BOARD.w - 0.6, y], z, P));
  out.push(L(grid.join(""), { tone: "faint" }));
  // 트랙: 안쪽·바깥쪽 경계, 가운데 레인 선, 출발선
  out.push(S(k.slabOf(stadium(R + LANE), z, 0.4, P, 24), { tone: "lo", crease: "none" }));
  out.push(S(k.slabOf(stadium(R - LANE), z + 0.4, 0.01, P, 24), { tone: "lo", flat: true }));
  out.push(L(k.planOutline(stadium(R), z + 0.4, P, 24), { tone: "faint" }));
  const sx = CX - STRAIGHT / 2;
  out.push(L(k.lineOnTop([sx, -R - LANE], [sx, -R + LANE], z + 0.4, P), { tone: "mid" }));
  return out.join("");
}

function rails() {
  const out = [];
  for (const y of RAIL_Y) {
    const rail = { x: RAIL_X[0], y: y - 3, w: RAIL_X[1] - RAIL_X[0], d: 6, r: 1 };
    out.push(S(k.slabOf(rail, 0, 4, P, 3, 0.4), { tone: "mid" }));
    const ticks = k.topTicks(rail.x + 6, rail.x + rail.w - 6, 5, 5, y - 3 + 0.5, 4, [1.2, 2.4], P);
    out.push(L(ticks.minor, { tone: "faint" }), L(ticks.major, { tone: "lo" }));
    for (const x of [RAIL_X[0] - 6, RAIL_X[1]]) {
      out.push(S(k.slabOf({ x, y: y - 5, w: 6, d: 10, r: 1.2 }, 0, 8, P, 3, 0.5), { tone: "mid" }));
      out.push(D([at([x + 3, y - 2.5, 8]), at([x + 3, y + 2.5, 8])], { size: 0.45 }));
    }
  }
  return out.join("");
}

// 다리(브리지)는 x = 0에 만들어 두고 translateAlong으로 옮긴다
function trucks() {
  const out = [];
  for (const y of RAIL_Y) {
    const truck = { x: -8, y: y - 5, w: 16, d: 10, r: 1.5 };
    out.push(S(k.slabOf(truck, 4, 6, P, 3, 0.4), { tone: "mid" }));
    const post = { x: -4, y: y - 3.5, w: 8, d: 7, r: 1 };
    out.push(S(k.slabOf(post, 10, BEAM_Z - 10, P, 3, 0.4), { tone: "mid", inner: L(k.sideSeam(post, 20, P), { tone: "faint" }) }));
    out.push(D([at([-5.5, y + 5, 7]), at([5.5, y + 5, 7]), at([6.5, y - 2.5, 10]), at([6.5, y + 2.5, 10])], { size: 0.45 }));
  }
  return out.join("");
}

function beam() {
  const plan = { x: -5, y: RAIL_Y[0] - 7, w: 10, d: RAIL_Y[1] - RAIL_Y[0] + 14, r: 1 };
  const out = [S(k.slabOf(plan, BEAM_Z, BEAM_H, P, 3, 0.6), { tone: "mid" })];
  out.push(L(k.lineOnTop([0, plan.y + 4], [0, plan.y + plan.d - 4], BEAM_Z + BEAM_H, P), { tone: "faint" }));
  out.push(D([plan.y + 4, plan.y + plan.d - 4].map((y) => at([0, y, BEAM_Z + BEAM_H])), { size: 0.5 }));
  const ticks = k.sideTicks(plan.y + 8, plan.y + plan.d - 8, 4, 5, 5, BEAM_Z + BEAM_H - 0.6, [1.2, 2.6], P, "y");
  out.push(L(ticks.minor, { tone: "faint" }), L(ticks.major, { tone: "lo" }));
  return out.join("");
}

// 펜 머리는 (0, 0)에 만들어 두고 (브리지 x, 펜 y)로 옮긴다
function head() {
  const out = [];
  const body = { x: -7, y: -9, w: 14, d: 18, r: 1.5 };
  out.push(S(k.slabOf(body, BEAM_Z - 2, BEAM_H + 6, P, 3, 0.5), { tone: "hi", crease: "lo", inner: L(k.sideSeam(body, BEAM_Z + 4, P), { tone: "faint" }) }));
  const bracket = { x: 7, y: -4, w: 7, d: 8, r: 1 };
  out.push(S(k.slabOf(bracket, BEAM_Z - 4, 10, P, 3, 0.4), { tone: "mid" }));
  out.push(D([at([10.5, -2, BEAM_Z + 6]), at([10.5, 2, BEAM_Z + 6])], { size: 0.4 }));
  // 펜 기둥, 칼라, 펜 끝(이것만 밝게)
  out.push(S(k.cylinder(PEN_DX, 0, 1.6, BOARD_H + 4, BEAM_Z - 4 - BOARD_H - 4, P, 18), { tone: "mid", crease: "none" }));
  out.push(S(k.cylinder(PEN_DX, 0, 2.6, BOARD_H + 12, 3, P, 22), { tone: "mid" }));
  out.push(L(k.knurl(PEN_DX, 0, 2.6, BOARD_H + 12, BOARD_H + 15, 16, P), { tone: "lo" }));
  out.push(S(k.cylinder(PEN_DX, 0, 1, BOARD_H + 0.2, 3.8, P, 14), { tone: "hi", lit: true, crease: "none" }));
  // GPS 수신기(패치 안테나)
  const top = BEAM_Z + BEAM_H + 4;
  out.push(S(k.cylinder(0, 0, 6.5, top, 2, P, 36, 0.5), { tone: "mid" }));
  out.push(S(k.cylinder(0, 0, 4.6, top + 2, 1.2, P, 32, 0.3), { tone: "hi", crease: "lo" }));
  out.push(L(k.ring(0, 0, 2.6, top + 3.2, P, 24), { tone: "lo" }));
  out.push(D([at([0, 0, top + 3.2])], { size: 0.55, tone: "hi" }));
  // 조임 손잡이
  out.push(S(k.slabOf({ x: -2, y: 9, w: 4, d: 2.4, r: 0.6 }, BEAM_Z + 3, 4, P, 2), { tone: "mid", crease: "none" }));
  out.push(S(k.cylinder(0, 13, 3, BEAM_Z + 2, 6, P, 20), { tone: "mid" }));
  out.push(L(k.knurl(0, 13, 3, BEAM_Z + 2, BEAM_Z + 8, 18, P), { tone: "lo" }));
  return out.join("");
}

// 주행 거리계: 오른쪽(+x) 면에 숫자 창 4개
const DIGIT_W = 7;
const DIGIT_H = 10;
const DIGIT_Z = 12;
const FACE_X = ODO.x + ODO.w;
const digitY = (index) => 20 - index * (DIGIT_W + 1.4) - (index >= 2 ? 3 : 0);

function odometer() {
  const out = [`<path class="iso-halo" d="${k.haloOf(ODO, 0, ODO_Z, P)}"/>`];
  const plinth = k.insetPlan(ODO, -3);
  out.push(S(k.slabOf(plinth, 0, ODO_Z, P, 4, 0.4), { tone: "lo" }));
  out.push(S(k.slabOf(ODO, ODO_Z, ODO_H, P, 4, 0.8), { tone: "mid", inner: L(k.sideSeam(ODO, ODO_Z + 6, P), { tone: "faint" }) }));
  out.push(L(k.planOutline(k.insetPlan(ODO, 3), ODO_Z + ODO_H, P), { tone: "faint" }));
  out.push(D(k.corners(ODO, 5).map(([x, y]) => at([x, y, ODO_Z + ODO_H])), { size: 0.45 }));
  // 창틀(베젤)과 숫자 창
  const bezel = [[FACE_X, 24, DIGIT_Z - 3], [FACE_X, 24, DIGIT_Z + DIGIT_H + 3], [FACE_X, -24, DIGIT_Z + DIGIT_H + 3], [FACE_X, -24, DIGIT_Z - 3]];
  out.push(L(faceOf(bezel), { tone: "lo" }));
  for (let i = 0; i < 4; i++) {
    const y0 = digitY(i);
    const win = [[FACE_X, y0, DIGIT_Z], [FACE_X, y0, DIGIT_Z + DIGIT_H], [FACE_X, y0 - DIGIT_W, DIGIT_Z + DIGIT_H], [FACE_X, y0 - DIGIT_W, DIGIT_Z]];
    out.push(`<path class="iso-fill" data-window d="${faceOf(win)}"/>`, L(faceOf(win), { tone: "mid" }));
  }
  out.push(D([at([FACE_X, digitY(1) - DIGIT_W - 2.2, DIGIT_Z + 1])], { size: 0.7, tone: "hi" }));
  out.push(k.faceTextSvg(k.sideMatrix([FACE_X, 4], DIGIT_Z - 4.4, P, "right"), "km", { size: 3.4, tone: "lo" }));
  out.push(D([[FACE_X, 22, DIGIT_Z + DIGIT_H + 7], [FACE_X, -22, DIGIT_Z + DIGIT_H + 7], [FACE_X, 22, ODO_Z + 4], [FACE_X, -22, ODO_Z + 4]].map(at), { size: 0.45 }));
  // 리셋 손잡이
  const knob = [ODO.x + 14, ODO.y + 14];
  out.push(S(k.cylinder(knob[0], knob[1], 4.5, ODO_Z + ODO_H, 4, P, 28), { tone: "mid" }));
  out.push(L(k.knurl(knob[0], knob[1], 4.5, ODO_Z + ODO_H, ODO_Z + ODO_H + 4, 28, P), { tone: "lo" }));
  out.push(D([at([knob[0], knob[1], ODO_Z + ODO_H + 4])], { size: 0.5, tone: "hi" }));
  return out.join("");
}

const digitText = () =>
  [0, 1, 2, 3]
    .map((i) => k.faceTextSvg(k.sideMatrix([FACE_X, digitY(i) - DIGIT_W / 2], DIGIT_Z + 2, P, "right"), "0", { size: 7.4, tone: "hi", anchor: "middle" }).replace("<text ", `<text data-digit="${i}" `))
    .join("");

const svg = k.figureSvg({
  width: WIDTH,
  height: HEIGHT,
  label:
    "400 m 트랙 판 위를 GPS 수신기를 단 플로터가 돌며 2초마다 위치를 찍습니다. 정확도가 25 m보다 나쁘거나 초속 12 m보다 빠르게 튄 점은 버리고, 남은 점 사이 거리만 오른쪽 거리계에 더합니다.",
  body: [
    plate(),
    board(),
    `<g data-part="ink"></g>`,
    rails(),
    `<g data-part="bridge">${trucks()}${beam()}<g data-part="head">${head()}</g></g>`,
    odometer(),
    `<g data-part="digits">${digitText()}</g>`,
  ],
});

const live = `
const P = ${JSON.stringify(P)};
const R = ${R}, STRAIGHT = ${STRAIGHT}, CX = ${CX}, PEN_DX = ${PEN_DX}, Z = ${BOARD_H + 0.3};
const stage = document.querySelector("#gps-figure .iso-stage");
const svg = stage.querySelector("svg");
const bridge = svg.querySelector('[data-part="bridge"]');
const headPart = svg.querySelector('[data-part="head"]');
const ink = svg.querySelector('[data-part="ink"]');
const digits = [...svg.querySelectorAll("[data-digit]")];
const readout = document.querySelector("#gps-figure [data-readout]");
const still = matchMedia("(prefers-reduced-motion: reduce)");

// 실제 앱과 같은 규칙(core Geo.isPlausible): 정확도 25 m 초과, 초속 12 m 초과면 버린다
const MAX_ACC = 25, MAX_SPEED = 12, FIX_SEC = 2, PACE = 300, SPEEDUP = 8, KEEP = 64;
const LAP = 400, SPEED = 1000 / PACE;
function onTrack(s) {
  s = ((s % LAP) + LAP) % LAP;
  const half = STRAIGHT / 2, arc = Math.PI * R;
  if (s < STRAIGHT) return [CX - half + s, -R];
  s -= STRAIGHT;
  if (s < arc) { const a = s / R; return [CX + half + Math.sin(a) * R, -Math.cos(a) * R]; }
  s -= arc;
  if (s < STRAIGHT) return [CX + half - s, R];
  const a = (s - STRAIGHT) / R;
  return [CX - half - Math.sin(a) * R, Math.cos(a) * R];
}
let seed = 7;
const rand = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
const gauss = () => Math.sqrt(-2 * Math.log(rand() + 1e-9)) * Math.cos(2 * Math.PI * rand());

let simT = 0, nextFix = 0, weak = false;
let kept = [], lastOk = null, distM = 0, accepted = 0, dropped = 0, rejects = [];
function fix() {
  const [x, y] = onTrack(simT * SPEED);
  const spike = rand() < (weak ? 0.22 : 0.05);
  const acc = spike ? 30 + rand() * 30 : 4 + rand() * 4;
  const jitter = weak ? 2.2 : 0.5;
  const off = spike ? 28 + rand() * 12 : 0;
  const ang = rand() * Math.PI * 2;
  const p = { x: x + gauss() * jitter + Math.cos(ang) * off, y: y + gauss() * jitter + Math.sin(ang) * off, t: simT, acc };
  let ok = p.acc <= MAX_ACC;
  if (ok && lastOk) {
    const d = Math.hypot(p.x - lastOk.x, p.y - lastOk.y), dt = p.t - lastOk.t;
    ok = dt > 0 && d / dt <= MAX_SPEED;
    if (ok) distM += d;
  }
  if (ok) { lastOk = p; accepted++; kept.push(p); if (kept.length > KEEP) kept.shift(); }
  else { dropped++; rejects.push({ x: p.x, y: p.y, age: 0 }); }
}
const at = (x, y) => iso([x, y, Z], P);
const r1 = (v) => Math.round(v * 10) / 10;
function cross({ x, y }) {
  const a = at(x - 2, y - 2), b = at(x + 2, y + 2), c = at(x - 2, y + 2), d = at(x + 2, y - 2);
  return "M" + r1(a[0]) + " " + r1(a[1]) + "L" + r1(b[0]) + " " + r1(b[1]) + "M" + r1(c[0]) + " " + r1(c[1]) + "L" + r1(d[0]) + " " + r1(d[1]);
}
function draw() {
  const [px, py] = onTrack(simT * SPEED);
  bridge.setAttribute("transform", translateAlong(P, [px - PEN_DX, 0, 0]));
  headPart.setAttribute("transform", translateAlong(P, [0, py, 0]));
  const pts = kept.map((p) => at(p.x, p.y));
  pts.push(iso([px, py, Z], P));
  let html = "";
  if (pts.length > 1) {
    const d = "M" + pts.map((q) => r1(q[0]) + " " + r1(q[1])).join("L");
    html += '<path class="iso-line" data-tone="lit" data-free="" d="' + d + '" style="stroke-width:1.1"/>';
  }
  html += '<g class="iso-dots" data-tone="mid">' + pts.slice(0, -1).map((q, i) => '<circle cx="' + r1(q[0]) + '" cy="' + r1(q[1]) + '" r="0.9" fill-opacity="' + Math.min(1, 0.25 + i / KEEP).toFixed(2) + '"/>').join("") + "</g>";
  for (const r of rejects) html += '<path class="iso-line" data-tone="hi" data-free="" d="' + cross(r) + '" opacity="' + Math.max(0, 1 - r.age / 2.4).toFixed(2) + '" style="stroke-width:0.9"/>';
  ink.innerHTML = html;
  const text = (distM / 1000).toFixed(2).padStart(5, "0").slice(-5).replace(".", "");
  digits.forEach((node, i) => { if (node.textContent !== text[i]) node.textContent = text[i]; });
  const line = (distM / 1000).toFixed(2) + " km · 기록 " + accepted + " · 버림 " + dropped + (weak ? " · 약한 신호" : "");
  if (readout.textContent !== line) readout.textContent = line;
}
function step(dt) {
  simT += dt * SPEEDUP;
  while (nextFix <= simT) { const keep = simT; simT = nextFix; fix(); simT = keep; nextFix += FIX_SEC; }
  rejects.forEach((r) => (r.age += dt));
  rejects = rejects.filter((r) => r.age < 2.4);
}
let frame = 0, last = 0, visible = false;
function tick(now) {
  const dt = last ? Math.min((now - last) / 1000, 1 / 20) : 1 / 60;
  last = now;
  step(dt);
  draw();
  frame = visible && !still.matches ? requestAnimationFrame(tick) : 0;
}
const run = () => { if (!frame && visible && !still.matches) { last = 0; frame = requestAnimationFrame(tick); } };
new IntersectionObserver((entries) => { visible = entries[entries.length - 1].isIntersecting; if (visible) run(); }, { rootMargin: "120px 0px" }).observe(stage);
const setWeak = (on) => { weak = on; stage.setAttribute("aria-pressed", String(on)); draw(); };
stage.addEventListener("pointerenter", () => setWeak(true));
stage.addEventListener("pointerleave", () => setWeak(false));
stage.addEventListener("keydown", (e) => { if (e.key === " " || e.key === "Enter") { e.preventDefault(); setWeak(!weak); } });
// 움직임 줄이기: 한 바퀴 반을 미리 돌려 멈춘 그림 한 장
if (still.matches) { for (let i = 0; i < 300; i++) step(0.06); rejects.forEach((r) => (r.age = 0)); }
draw();
`;

const plateBody = k.plateHtml({
  fig: "Fig 1",
  title: "GPS 기록",
  hint: "올려두면 신호가 약해져요",
  readout: "0.00 km · 기록 0 · 버림 0",
  keys: [
    { mark: "lit", label: "펜 끝과 선: 거리에 들어간 점들" },
    { mark: "raised", label: "실제 크기 400 m 트랙" },
  ],
  caption:
    "수신기가 2초마다 위치를 받아요. 정확도가 25 m보다 나쁘거나 사람이 낼 수 없는 속도(초속 12 m 초과)로 튄 점은 ×로 버리고, 남은 점 사이 거리만 거리계에 더해요. 앱이 실제로 쓰는 규칙이에요. (8배속, 5:00/km 페이스)",
  body: `<div class="iso-stage" tabindex="0" role="button" aria-pressed="false" aria-label="GPS 기록 그림: 스페이스로 약한 신호를 켜고 꺼요">${svg}</div>`,
});

const kitSource = readFileSync(join(HERE, "iso-kit.mjs"), "utf8").replace(/^export /gm, "");
const script = `<script type="module">${(kitSource + live).replace(/<\/(script)/gi, "<\\/$1")}</script>`;
const fragment = `<style>${k.ISO_CSS}#gps-figure{max-width:680px;margin:56px auto 0}#gps-figure .iso-stage{cursor:crosshair}</style><div id="gps-figure" class="iso" data-theme="light">${plateBody}</div>${script}`;

if (process.argv.includes("--preview")) {
  writeFileSync(join(HERE, "preview.html"), `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>GPS 기록</title></head><body style="margin:0;padding:24px 16px;background:#fff">${fragment}</body></html>`);
  console.log("wrote preview.html");
} else {
  const html = readFileSync(INDEX, "utf8");
  const re = /<!-- gps-figure -->[\s\S]*?<!-- \/gps-figure -->/;
  if (!re.test(html)) throw new Error("docs/index.html에 <!-- gps-figure --><!-- /gps-figure --> 표시가 없어요");
  writeFileSync(INDEX, html.replace(re, `<!-- gps-figure -->${fragment}<!-- /gps-figure -->`));
  console.log("updated docs/index.html");
}
