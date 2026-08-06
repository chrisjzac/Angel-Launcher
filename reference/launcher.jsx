import React, { useState, useRef, useEffect, useMemo, useCallback } from "react";
import {
  AlarmClock, Archive, Shield, Landmark, Battery, BookOpen, Calculator,
  CalendarDays, Camera, Clock, Users, Mic, Download, HardDrive, Mail,
  FileText, Folder, Dumbbell, Flashlight, Image, Gamepad2, Heart, Home,
  Notebook, Key, Library, Map, MessageSquare, Music, Newspaper, PenTool,
  Phone, Podcast, Radio, Rss, Settings, ShoppingBag, Moon, Terminal,
  Timer, Bus, Tv, Video, Wallet, Cloud, Wifi, Inbox,
  Sun, CloudRain, CloudSnow, CloudLightning, CloudFog, LoaderCircle,
  Fingerprint, Lightbulb, Thermometer, Lock, Fan,
  Speaker, Droplet, Minus, Plus, Wind, ScanLine, RefreshCw,
  TrendingUp, TrendingDown, AlertCircle,
} from "lucide-react";

/* ---------- sky palettes ---------- */

const SKY = {
  sunny: {
    label: "Clear", Icon: Sun, accent: "#ffb457",
    wall: `radial-gradient(78% 48% at 84% 86%,rgba(255,150,58,.26) 0%,transparent 62%),
           radial-gradient(88% 58% at 10% 4%,rgba(152,70,28,.42) 0%,transparent 68%),
           linear-gradient(168deg,#26190f 0%,#180f09 55%,#0d0805 100%)`,
    stage: "radial-gradient(120% 90% at 50% 0%,#2b1d12 0%,#100a06 70%)",
  },
  clouds: {
    label: "Overcast", Icon: Cloud, accent: "#a9b6d0",
    wall: `radial-gradient(75% 45% at 82% 88%,rgba(170,180,200,.13) 0%,transparent 62%),
           radial-gradient(85% 55% at 12% 6%,rgba(88,98,120,.36) 0%,transparent 66%),
           linear-gradient(168deg,#1d2028 0%,#15171d 55%,#101116 100%)`,
    stage: "radial-gradient(120% 90% at 50% 0%,#252932 0%,#0d0e12 70%)",
  },
  rain: {
    label: "Rain", Icon: CloudRain, accent: "#f2a65a",
    wall: `radial-gradient(75% 45% at 82% 88%,rgba(242,166,90,.20) 0%,transparent 62%),
           radial-gradient(85% 55% at 12% 6%,rgba(59,75,122,.42) 0%,transparent 66%),
           linear-gradient(168deg,#1a1e2c 0%,#12141c 55%,#0f1117 100%)`,
    stage: "radial-gradient(120% 90% at 50% 0%,#20243a 0%,#0d0f16 70%)",
  },
  storm: {
    label: "Storms", Icon: CloudLightning, accent: "#b79cff",
    wall: `radial-gradient(72% 44% at 80% 86%,rgba(150,120,230,.22) 0%,transparent 60%),
           radial-gradient(88% 58% at 14% 4%,rgba(52,46,96,.50) 0%,transparent 68%),
           linear-gradient(168deg,#191627 0%,#12111c 55%,#0c0b12 100%)`,
    stage: "radial-gradient(120% 90% at 50% 0%,#241f3c 0%,#0b0a12 70%)",
  },
  snow: {
    label: "Snow", Icon: CloudSnow, accent: "#a8d0f0",
    wall: `radial-gradient(76% 46% at 82% 88%,rgba(198,216,236,.18) 0%,transparent 62%),
           radial-gradient(86% 56% at 12% 6%,rgba(92,132,178,.36) 0%,transparent 66%),
           linear-gradient(168deg,#1b2230 0%,#141a24 55%,#0f1319 100%)`,
    stage: "radial-gradient(120% 90% at 50% 0%,#222d3e 0%,#0c1015 70%)",
  },
  fog: {
    label: "Fog", Icon: CloudFog, accent: "#cfc8b8",
    wall: `radial-gradient(80% 50% at 78% 84%,rgba(196,192,182,.12) 0%,transparent 64%),
           radial-gradient(84% 54% at 14% 8%,rgba(108,112,122,.32) 0%,transparent 66%),
           linear-gradient(168deg,#202227 0%,#17181c 55%,#121316 100%)`,
    stage: "radial-gradient(120% 90% at 50% 0%,#292b30 0%,#0f1012 70%)",
  },
};

const CYCLE = ["sunny", "clouds", "rain", "storm", "snow", "fog"];

function fromCode(c) {
  if (c === 0 || c === 1) return "sunny";
  if (c === 2 || c === 3) return "clouds";
  if (c === 45 || c === 48) return "fog";
  if (c >= 95) return "storm";
  if ((c >= 71 && c <= 77) || c === 85 || c === 86) return "snow";
  return "rain";
}

/* ---------- apps ---------- */

const APPS = [
  { name: "Alarm", Icon: AlarmClock }, { name: "Archive", Icon: Archive },
  { name: "Authenticator", Icon: Shield }, { name: "Banking", Icon: Landmark },
  { name: "Battery Saver", Icon: Battery }, { name: "Books", Icon: BookOpen },
  { name: "Calculator", Icon: Calculator }, { name: "Calendar", Icon: CalendarDays },
  { name: "Camera", Icon: Camera }, { name: "Clock", Icon: Clock },
  { name: "Contacts", Icon: Users }, { name: "Dictaphone", Icon: Mic },
  { name: "Downloads", Icon: Download }, { name: "Drive", Icon: HardDrive },
  { name: "Email", Icon: Mail }, { name: "Expenses", Icon: FileText },
  { name: "Files", Icon: Folder }, { name: "Fitness", Icon: Dumbbell },
  { name: "Flashlight", Icon: Flashlight }, { name: "Gallery", Icon: Image },
  { name: "Games", Icon: Gamepad2 }, { name: "Health", Icon: Heart },
  { name: "Home Control", Icon: Home }, { name: "Inbox", Icon: Inbox },
  { name: "Journal", Icon: Notebook }, { name: "Keys", Icon: Key },
  { name: "Library", Icon: Library }, { name: "Maps", Icon: Map },
  { name: "Messages", Icon: MessageSquare }, { name: "Music", Icon: Music },
  { name: "News", Icon: Newspaper }, { name: "Notes", Icon: PenTool },
  { name: "Phone", Icon: Phone }, { name: "Podcasts", Icon: Podcast },
  { name: "Radio", Icon: Radio }, { name: "Reader", Icon: Rss },
  { name: "Settings", Icon: Settings }, { name: "Shopping", Icon: ShoppingBag },
  { name: "Sleep", Icon: Moon }, { name: "Terminal", Icon: Terminal },
  { name: "Timer", Icon: Timer }, { name: "Transit", Icon: Bus },
  { name: "TV", Icon: Tv }, { name: "Video", Icon: Video },
  { name: "Wallet", Icon: Wallet }, { name: "Weather", Icon: Cloud },
  { name: "Wi-Fi", Icon: Wifi },
];

const PINNED = ["Phone", "Messages", "Camera", "Maps", "Music", "Settings"];
const LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".split("");
const FALLOFF = 30;
const HOLD_MS = 850;

const ENTITIES = [
  { id: "lamp", name: "Living room", Icon: Lightbulb, on: true, on_t: "On", off_t: "Off" },
  { id: "kitchen", name: "Kitchen", Icon: Lightbulb, on: false, on_t: "On", off_t: "Off" },
  { id: "door", name: "Front door", Icon: Lock, on: true, on_t: "Locked", off_t: "Unlocked" },
  { id: "fan", name: "Study fan", Icon: Fan, on: false, on_t: "Running", off_t: "Off" },
  { id: "speaker", name: "Speakers", Icon: Speaker, on: true, on_t: "Playing", off_t: "Idle" },
  { id: "humid", name: "Humidifier", Icon: Droplet, on: false, on_t: "On", off_t: "Off" },
  { id: "tv", name: "Living room TV", Icon: Tv, on: false, on_t: "On", off_t: "Standby" },
  { id: "ac", name: "Bedroom AC", Icon: Wind, on: true, on_t: "Cooling", off_t: "Off" },
];

/* =======================================================================
   SMS PAYMENT PARSER
   The inbox below is mock data — no browser API can read real SMS.
   Everything from here down is real parsing logic, portable to Android.
   ======================================================================= */

const INBOX = [
  "HDFC Bank: Rs.450.00 debited from A/c XX1234 on 05-Aug-26 to VPA bluetokai@okhdfcbank. Ref 902133.",
  "INR 1,249.00 spent on ICICI Bank Card XX9012 on 05-Aug-26 at SWIGGY. Avl Lmt: INR 48,751.00",
  "Your A/c XX1234 is credited with Rs 1,85,000.00 on 01-Aug-26 by NEFT-SALARY. -HDFC Bank",
  "Rs 649 debited via UPI to NETFLIX INDIA on 03-Aug-26. UPI Ref 445512. -SBI",
  "Rs.2,340.00 debited from A/c XX1234 on 04-Aug-26 to VPA indianoil@ybl. Ref 771902.",
  "INR 12,400.00 paid towards HDFC Credit Card XX3344 on 02-Aug-26. Thank you.",
  "Rs 899.00 spent on ICICI Bank Card XX9012 on 04-Aug-26 at AMAZON PAY. Avl Lmt: INR 49,650.00",
  "Your A/c XX1234 is credited with Rs 3,200.00 on 03-Aug-26 by UPI from ZERODHA PAYOUT.",
  "Dear customer, your BESCOM bill of Rs 1,860.00 is due on 12-Aug-26. Pay to avoid disconnection.",
  "Get 40% off on your next 3 rides! Use code RIDE40. T&C apply.",
];

const RX = {
  out: /\b(debited|spent|withdrawn|debit|paid|sent)\b/i,
  in: /\b(credited|received|refunded|refund|deposited)\b/i,
  amt: /(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)/i,
  acct: /(?:a\/c|acct|account|card)\s*(?:no\.?)?\s*[xX*]{2,}(\d{3,4})/i,
  date: /(\d{1,2})[-/ ]([A-Za-z]{3}|\d{1,2})[-/ ](\d{2,4})/,
  vpa: /VPA\s+([\w.\-]+@[\w.\-]+)/i,
  at: /\bat\s+([A-Z][A-Za-z0-9&.\- ]{2,28}?)(?=\s*(?:\.|,|on\b|$))/,
  to: /\bto\s+([A-Z][A-Za-z0-9&.\- ]{2,28}?)(?=\s*(?:\.|,|on\b|$))/,
  from: /\bfrom\s+([A-Z][A-Za-z0-9&.\- ]{2,28}?)(?=\s*(?:\.|,|on\b|$))/,
  towards: /\btowards\s+([A-Za-z0-9&.\- ]{2,28}?)(?=\s*(?:\.|,|on\b|$))/i,
  by: /\bby\s+([A-Z][A-Za-z0-9&.\- ]{2,28}?)(?=\s*(?:\.|,|on\b|$))/,
  channel: /\b(UPI|IMPS|NEFT|RTGS|ATM|Card)\b/i,
  bank: /\b(HDFC|ICICI|SBI|AXIS|KOTAK|IDFC|YES BANK)\b/i,
};

const CATS = [
  { key: "Income", tint: "#7fd6a8", hits: ["salary", "payout", "interest", "dividend"] },
  { key: "Food", tint: "#f2a65a", hits: ["swiggy", "zomato", "tokai", "cafe", "restaurant", "dominos"] },
  { key: "Fuel", tint: "#e0785c", hits: ["indianoil", "hpcl", "bharat", "shell", "petrol"] },
  { key: "Shopping", tint: "#9db4e8", hits: ["amazon", "flipkart", "myntra", "ajio"] },
  { key: "Subscriptions", tint: "#b79cff", hits: ["netflix", "spotify", "prime", "youtube", "hotstar"] },
  { key: "Bills", tint: "#8fd0d6", hits: ["bescom", "airtel", "jio", "electric", "gas", "broadband"] },
  { key: "Card dues", tint: "#d6c48f", hits: ["credit card"] },
];

function categorize(merchant) {
  const m = (merchant || "").toLowerCase();
  for (const c of CATS) if (c.hits.some((h) => m.includes(h))) return c;
  return { key: "Other", tint: "#8a90a6" };
}

function parseSms(text, id) {
  const amtM = text.match(RX.amt);
  if (!amtM) return { id, text, ok: false, why: "No amount found" };

  const isOut = RX.out.test(text);
  const isIn = RX.in.test(text);
  if (!isOut && !isIn) return { id, text, ok: false, why: "No debit or credit verb" };

  const amount = parseFloat(amtM[1].replace(/,/g, ""));
  if (!isFinite(amount)) return { id, text, ok: false, why: "Unreadable amount" };

  const dir = isIn && !isOut ? "in" : "out";
  const merchant =
    (text.match(RX.vpa)?.[1] ||
      text.match(RX.at)?.[1] ||
      (dir === "in" ? text.match(RX.from)?.[1] || text.match(RX.by)?.[1] : null) ||
      text.match(RX.to)?.[1] ||
      text.match(RX.towards)?.[1] ||
      "Unknown"
    ).trim().replace(/[.,;:\-]+$/, "");

  const d = text.match(RX.date);
  return {
    id, text, ok: true, dir, amount, merchant,
    acct: text.match(RX.acct)?.[1] || null,
    date: d ? `${d[1]} ${d[2]}` : null,
    channel: (text.match(RX.channel)?.[1] || "Bank").toUpperCase(),
    bank: text.match(RX.bank)?.[1]?.toUpperCase() || null,
    cat: categorize(merchant.replace(/@.*$/, "")),
  };
}

/* ---------- holdings ---------- */

const HOLDINGS = [
  { sym: "INFY", name: "Infosys", qty: 40, avg: 1480, ltp: 1596.4, open: 1596.4 },
  { sym: "TCS", name: "Tata Consultancy", qty: 12, avg: 3720, ltp: 3588.15, open: 3588.15 },
  { sym: "HDFCBANK", name: "HDFC Bank", qty: 25, avg: 1520, ltp: 1673.8, open: 1673.8 },
  { sym: "RELIANCE", name: "Reliance", qty: 18, avg: 2740, ltp: 2891.05, open: 2891.05 },
  { sym: "NIFTYBEES", name: "Nifty 50 ETF", qty: 150, avg: 248, ltp: 271.6, open: 271.6 },
];

const inr = (n, d = 0) =>
  "₹" + new Intl.NumberFormat("en-IN", { minimumFractionDigits: d, maximumFractionDigits: d }).format(n);

export default function RailLauncher() {
  const [letter, setLetter] = useState(null);
  const [pointerY, setPointerY] = useState(null);
  const [dragging, setDragging] = useState(false);
  const [opened, setOpened] = useState(null);
  const [now, setNow] = useState(new Date());
  const [calm, setCalm] = useState(false);
  const [sky, setSky] = useState("rain");
  const [mode, setMode] = useState("locating");
  const [temp, setTemp] = useState(null);

  const [page, setPage] = useState(0); // -1 wealth · 0 launcher · 1 home
  const [dx, setDx] = useState(0);
  const [swiping, setSwiping] = useState(false);

  const [haOpen, setHaOpen] = useState(false);
  const [wealthOpen, setWealthOpen] = useState(false);
  const [pct, setPct] = useState(0);
  const [authMsg, setAuthMsg] = useState("");
  const [bio, setBio] = useState(null);

  const [entities, setEntities] = useState(ENTITIES);
  const [target, setTarget] = useState(21.5);
  const [house, setHouse] = useState({ temp: 22.4, hum: 48, kw: 0.94 });

  const [tab, setTab] = useState("payments");
  const [scan, setScan] = useState("done");
  const [rows, setRows] = useState(() => INBOX.map(parseSms));
  const [showSkipped, setShowSkipped] = useState(false);
  const [holdings, setHoldings] = useState(HOLDINGS);
  const [qy, setQy] = useState(0);
  const [torch, setTorch] = useState(false);
  const [spark, setSpark] = useState([]);

  const railRef = useRef(null);
  const phoneRef = useRef(null);
  const trackRef = useRef(null);
  const gesture = useRef(null);
  const moved = useRef(false);
  const holdTimer = useRef(null);
  const holdDone = useRef(false);
  const quick = useRef(null);
  const swiped = useRef(false);

  useEffect(() => {
    const t = setInterval(() => setNow(new Date()), 10000);
    return () => clearInterval(t);
  }, []);

  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    const on = () => setCalm(mq.matches);
    on();
    mq.addEventListener("change", on);
    return () => mq.removeEventListener("change", on);
  }, []);

  useEffect(() => {
    if (!opened) return;
    const t = setTimeout(() => setOpened(null), 1800);
    return () => clearTimeout(t);
  }, [opened]);

  useEffect(() => {
    let alive = true;
    if (!navigator.geolocation) { setMode("off"); return; }
    navigator.geolocation.getCurrentPosition(
      async ({ coords }) => {
        try {
          const res = await fetch(
            "https://api.open-meteo.com/v1/forecast?latitude=" +
            coords.latitude.toFixed(3) + "&longitude=" + coords.longitude.toFixed(3) +
            "&current=weather_code,temperature_2m");
          if (!res.ok) throw new Error("bad response");
          const data = await res.json();
          if (!alive) return;
          setSky(fromCode(data.current.weather_code));
          setTemp(Math.round(data.current.temperature_2m));
          setMode("live");
        } catch { if (alive) setMode("off"); }
      },
      () => { if (alive) setMode("off"); },
      { timeout: 9000, maximumAge: 600000 }
    );
    return () => { alive = false; };
  }, []);

  useEffect(() => {
    const P = window.PublicKeyCredential;
    if (P?.isUserVerifyingPlatformAuthenticatorAvailable) {
      P.isUserVerifyingPlatformAuthenticatorAvailable()
        .then((v) => setBio(!!v)).catch(() => setBio(false));
    } else setBio(false);
  }, []);

  // house sensors drift while the pane is open — replace with the HA websocket
  useEffect(() => {
    if (page !== -1 || !haOpen) return;
    const t = setInterval(() => {
      setHouse((h) => ({
        temp: +(h.temp + (Math.random() - 0.5) * 0.2).toFixed(1),
        hum: Math.round(Math.min(70, Math.max(30, h.hum + (Math.random() - 0.5) * 1.4))),
        kw: +Math.max(0.2, h.kw + (Math.random() - 0.5) * 0.14).toFixed(2),
      }));
    }, 4000);
    return () => clearInterval(t);
  }, [page, haOpen]);

  // simulated market tick — replace with a quotes API
  useEffect(() => {
    if (page !== 1 || !wealthOpen || tab !== "stocks") return;
    const t = setInterval(() => {
      setHoldings((hs) =>
        hs.map((h) => ({ ...h, ltp: +(h.ltp * (1 + (Math.random() - 0.5) * 0.005)).toFixed(2) })));
    }, 3500);
    return () => clearInterval(t);
  }, [page, wealthOpen, tab]);

  const portfolio = useMemo(() => {
    const value = holdings.reduce((a, h) => a + h.qty * h.ltp, 0);
    const cost = holdings.reduce((a, h) => a + h.qty * h.avg, 0);
    const open = holdings.reduce((a, h) => a + h.qty * (h.open ?? h.ltp), 0);
    return { value, cost, day: value - open, pl: value - cost, plPct: ((value - cost) / cost) * 100 };
  }, [holdings]);

  useEffect(() => {
    setSpark((s) => [...s, portfolio.value].slice(-40));
  }, [portfolio.value]);

  const parsed = useMemo(() => rows.filter((r) => r.ok), [rows]);
  const skipped = useMemo(() => rows.filter((r) => !r.ok), [rows]);
  const money = useMemo(() => {
    const out = parsed.filter((r) => r.dir === "out").reduce((a, r) => a + r.amount, 0);
    const inn = parsed.filter((r) => r.dir === "in").reduce((a, r) => a + r.amount, 0);
    const byCat = {};
    for (const r of parsed.filter((r) => r.dir === "out")) {
      const k = r.cat.key;
      byCat[k] = byCat[k] || { key: k, tint: r.cat.tint, total: 0 };
      byCat[k].total += r.amount;
    }
    const outs = parsed.filter((r) => r.dir === "out");
    return {
      out, inn, net: inn - out, count: parsed.length,
      largest: outs.length ? Math.max(...outs.map((r) => r.amount)) : 0,
      cats: Object.values(byCat).sort((a, b) => b.total - a.total),
    };
  }, [parsed]);

  const rescan = () => {
    if (moved.current || scan === "scanning") return;
    setScan("scanning");
    setTimeout(() => { setRows(INBOX.map(parseSms)); setScan("done"); }, 850);
  };

  const cycleSky = () => {
    setSky((s) => CYCLE[(CYCLE.indexOf(s) + 1) % CYCLE.length]);
    setMode("manual");
  };

  const byLetter = useMemo(() => {
    const m = {};
    for (const a of APPS) {
      const k = a.name[0].toUpperCase();
      (m[k] = m[k] || []).push(a);
    }
    return m;
  }, []);
  const pinned = useMemo(
    () => PINNED.map((n) => APPS.find((a) => a.name === n)).filter(Boolean), []);
  const shown = letter ? byLetter[letter] || [] : pinned;

  /* ----- alphabet rail ----- */
  const track = useCallback((clientY) => {
    const rail = railRef.current;
    if (!rail) return;
    const r = rail.getBoundingClientRect();
    const local = clientY - r.top;
    setPointerY(local);
    const step = r.height / LETTERS.length;
    const i = Math.max(0, Math.min(LETTERS.length - 1, Math.floor(local / step)));
    setLetter(LETTERS[i]);
  }, []);

  const onRailDown = (e) => {
    e.currentTarget.setPointerCapture?.(e.pointerId);
    setDragging(true); track(e.clientY);
  };
  const onRailMove = (e) => dragging && track(e.clientY);
  const onRailUp = (e) => {
    e.currentTarget.releasePointerCapture?.(e.pointerId);
    setDragging(false); setPointerY(null);
  };
  const onRailKey = (e) => {
    const i = letter ? LETTERS.indexOf(letter) : -1;
    if (e.key === "ArrowDown" || e.key === "ArrowRight") {
      e.preventDefault(); setLetter(LETTERS[Math.min(25, i + 1)]);
    } else if (e.key === "ArrowUp" || e.key === "ArrowLeft") {
      e.preventDefault(); setLetter(LETTERS[Math.max(0, i - 1)]);
    } else if (e.key === "Escape") setLetter(null);
  };
  const magnify = (i) => {
    if (calm || !dragging || pointerY == null || !railRef.current) return null;
    const h = railRef.current.getBoundingClientRect().height;
    const step = h / LETTERS.length;
    const d = Math.abs(step * (i + 0.5) - pointerY);
    const f = Math.exp(-Math.pow(d / FALLOFF, 2));
    return { scale: 1 + 1.05 * f, shift: -20 * f, glow: f };
  };

  /* ----- pane swipe (3 pages) ----- */
  const width = () => phoneRef.current?.getBoundingClientRect().width || 360;

  const onPaneDown = (e) => {
    if (e.target.closest?.(".nl-rail")) return;
    moved.current = false;
    gesture.current = { x: e.clientX, y: e.clientY, axis: null, id: e.pointerId };
  };
  const onPaneMove = (e) => {
    const g = gesture.current;
    if (!g || e.pointerId !== g.id) return;
    const ax = e.clientX - g.x, ay = e.clientY - g.y;
    if (!g.axis) {
      if (Math.abs(ax) < 8 && Math.abs(ay) < 8) return;
      g.axis = Math.abs(ax) > Math.abs(ay) ? "h" : "v";
      if (g.axis === "h") { trackRef.current?.setPointerCapture?.(e.pointerId); setSwiping(true); }
    }
    if (g.axis !== "h") return;
    moved.current = true;
    const W = width();
    const lo = page < 1 ? -W : 0;
    const hi = page > -1 ? W : 0;
    setDx(Math.max(lo, Math.min(hi, ax)));
  };
  const onPaneUp = (e) => {
    const g = gesture.current;
    gesture.current = null;
    setSwiping(false);
    if (g?.axis === "h") {
      trackRef.current?.releasePointerCapture?.(e.pointerId);
      const trip = Math.min(70, width() * 0.2);
      if (dx < -trip && page < 1) setPage(page + 1);
      else if (dx > trip && page > -1) setPage(page - 1);
    }
    setDx(0);
  };

  /* ----- quick action: tap dialer · swipe up camera · swipe down torch ----- */
  const QUICK_TRIP = 28;

  const fireQuick = (d) => {
    if (d < -QUICK_TRIP) { setOpened("Opened Camera"); return true; }
    if (d > QUICK_TRIP) {
      setTorch((t) => { setOpened(t ? "Torch off" : "Torch on"); return !t; });
      return true;
    }
    return false;
  };

  const onQuickDown = (e) => {
    e.stopPropagation();
    e.currentTarget.setPointerCapture?.(e.pointerId);
    quick.current = { y: e.clientY, id: e.pointerId };
    setQy(0);
  };
  const onQuickMove = (e) => {
    const q = quick.current;
    if (!q || e.pointerId !== q.id) return;
    setQy(Math.max(-70, Math.min(70, e.clientY - q.y)));
  };
  const onQuickUp = (e) => {
    const q = quick.current;
    if (!q) return;
    quick.current = null;
    e.currentTarget.releasePointerCapture?.(e.pointerId);
    swiped.current = fireQuick(e.clientY - q.y);
    setQy(0);
  };
  const onQuickClick = () => {
    if (swiped.current) { swiped.current = false; return; }
    setOpened("Opened Phone");
  };
  const onQuickKey = (e) => {
    if (e.key === "ArrowUp") { e.preventDefault(); fireQuick(-99); }
    else if (e.key === "ArrowDown") { e.preventDefault(); fireQuick(99); }
  };

  /* ----- hold to unlock ----- */
  const beginHold = (which) => {
    if (holdTimer.current) return;
    holdDone.current = false;
    setAuthMsg("");
    const t0 = Date.now();
    holdTimer.current = setInterval(() => {
      const p = Math.min(1, (Date.now() - t0) / HOLD_MS);
      setPct(p);
      if (p >= 1) {
        clearInterval(holdTimer.current);
        holdTimer.current = null;
        holdDone.current = true;
        which === "ha" ? setHaOpen(true) : setWealthOpen(true);
        setPct(0);
      }
    }, 16);
  };
  const endHold = () => {
    if (holdTimer.current) {
      clearInterval(holdTimer.current);
      holdTimer.current = null;
      if (!holdDone.current) setAuthMsg("Hold until the ring closes.");
    }
    setPct(0);
  };
  useEffect(() => () => holdTimer.current && clearInterval(holdTimer.current), []);
  useEffect(() => {
    if (page === 0) { endHold(); setAuthMsg(""); setHaOpen(false); setWealthOpen(false); }
  }, [page]);

  const toggle = (id) =>
    setEntities((es) => es.map((e) => (e.id === id ? { ...e, on: !e.on } : e)));

  const time = now.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  const date = now.toLocaleDateString([], { weekday: "long", month: "short", day: "numeric" });

  const active = SKY[sky];
  const SkyIcon = mode === "locating" ? LoaderCircle : active.Icon;
  const skyText =
    mode === "locating" ? "Reading the sky"
    : mode === "off" ? "Location off — tap to set"
    : mode === "manual" ? active.label + " · set by hand"
    : active.label + (temp != null ? " · " + temp + "°" : "");

  const R = 29, C = 2 * Math.PI * R;
  const QuickIcon = qy < -14 ? Camera : qy > 14 ? Flashlight : Phone;

  const Gate = ({ which, title, blurb }) => (
    <div className="nl-gate">
      <div className="nl-gateTop">
        <p>{blurb}</p>
        {authMsg && <p className="nl-warn">{authMsg}</p>}
      </div>

      <div className="nl-sensor">
        <span className="nl-glow" style={{ opacity: 0.10 + pct * 0.45 }} />
        <button className="nl-pad" aria-label={"Touch and hold to unlock " + title}
                onPointerDown={() => beginHold(which)} onPointerUp={endHold}
                onPointerLeave={endHold} onPointerCancel={endHold}>
          <svg className="nl-ring" width="72" height="72" viewBox="0 0 72 72">
            <circle cx="36" cy="36" r={R} fill="none" strokeWidth="1.5" stroke="rgba(237,235,230,.16)" />
            <circle cx="36" cy="36" r={R} fill="none" strokeWidth="1.5" stroke={active.accent}
                    strokeLinecap="round" strokeDasharray={C} strokeDashoffset={C * (1 - pct)} />
          </svg>
          <Fingerprint className="nl-print" strokeWidth={1.3} />
        </button>
      </div>
    </div>
  );

  const sparkPath = () => {
    if (spark.length < 2) return "";
    const lo = Math.min(...spark), hi = Math.max(...spark), rng = hi - lo || 1;
    return spark.map((v, i) =>
      `${(i / (spark.length - 1)) * 100},${26 - ((v - lo) / rng) * 22}`).join(" ");
  };

  return (
    <div className="nl-stage" style={{ background: active.stage, "--amber": active.accent }}>
      <style>{`
@import url('https://fonts.googleapis.com/css2?family=Bodoni+Moda:wght@400;500;600&family=Hanken+Grotesk:wght@400;500;600&family=JetBrains+Mono:wght@500;700&display=swap');

.nl-stage{--ground:#14161f;--ink:#edebe6;--dim:#7f859b;--up:#7fd6a8;--down:#e0785c;
  min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px 16px;
  font-family:'Hanken Grotesk',system-ui,sans-serif;box-sizing:border-box;transition:background 900ms ease;}
.nl-stage *,.nl-stage *::before,.nl-stage *::after{box-sizing:border-box;}
.nl-serif{font-family:'Bodoni Moda','Didot',Georgia,serif;}
.nl-mono{font-family:'JetBrains Mono',monospace;}

.nl-phone{position:relative;width:min(360px,100%);aspect-ratio:9/19.5;max-height:92vh;border-radius:44px;
  overflow:hidden;border:1px solid rgba(237,235,230,.10);background:var(--ground);
  box-shadow:0 40px 90px -30px #000,0 0 0 10px #0a0b10;}
.nl-wall{position:absolute;inset:0;opacity:0;transition:opacity 900ms ease;}
.nl-wall[data-on="1"]{opacity:1;}
.nl-grain{position:absolute;inset:0;opacity:.5;
  background-image:radial-gradient(rgba(237,235,230,.05) 1px,transparent 1px);background-size:3px 3px;}

.nl-track{position:absolute;inset:0;display:flex;will-change:transform;
  touch-action:pan-y;user-select:none;-webkit-user-select:none;}
.nl-track[data-anim="1"]{transition:transform 340ms cubic-bezier(.22,.61,.36,1);}
.nl-pane{flex:0 0 100%;position:relative;}

.nl-ui{position:absolute;inset:0;display:flex;flex-direction:column;padding:34px 0 22px;}
.nl-clock{padding:0 26px;flex-shrink:0;}
.nl-time{font-weight:500;font-size:60px;line-height:.9;letter-spacing:-.012em;color:var(--ink);margin:0;}
.nl-date{font-family:'JetBrains Mono',monospace;font-weight:500;font-size:10.5px;
  letter-spacing:.2em;text-transform:uppercase;color:var(--dim);margin:13px 0 0;}
.nl-sky{display:flex;align-items:center;gap:8px;margin:14px 0 0;padding:5px 10px 5px 8px;
  background:rgba(237,235,230,.05);border:1px solid rgba(237,235,230,.07);border-radius:999px;
  font-family:'JetBrains Mono',monospace;font-weight:500;font-size:9.5px;letter-spacing:.16em;
  text-transform:uppercase;color:var(--dim);cursor:pointer;transition:color .5s ease,background .16s ease;}
.nl-sky:hover{background:rgba(237,235,230,.09);color:var(--ink);}
.nl-sky:focus-visible{outline:2px solid var(--amber);outline-offset:2px;}
.nl-sky svg{width:12px;height:12px;color:var(--amber);flex-shrink:0;transition:color .5s ease;}
.nl-sky[data-wait="1"] svg{animation:nl-spin 1.1s linear infinite;}
@keyframes nl-spin{to{transform:rotate(360deg);}}

.nl-body{flex:1;display:flex;min-height:0;margin-top:24px;}
.nl-col{flex:1;position:relative;min-width:0;display:flex;flex-direction:column;}
.nl-ghost{position:absolute;right:-30px;top:50%;transform:translateY(-50%);font-weight:600;
  font-size:265px;line-height:1;color:var(--ink);opacity:.06;pointer-events:none;user-select:none;}
.nl-tag{font-family:'JetBrains Mono',monospace;font-weight:700;font-size:9.5px;letter-spacing:.22em;
  text-transform:uppercase;color:var(--dim);padding:0 26px;margin-bottom:14px;flex-shrink:0;}
.nl-tag b{color:var(--amber);font-weight:700;transition:color .5s ease;}
.nl-list{list-style:none;margin:0;padding:0 8px 0 14px;overflow-y:auto;flex:1;
  scrollbar-width:none;position:relative;z-index:1;}
.nl-list::-webkit-scrollbar{display:none;}
.nl-item{width:100%;display:flex;align-items:center;gap:15px;background:none;border:0;
  padding:11px 12px;border-radius:13px;cursor:pointer;color:var(--ink);text-align:left;
  font-family:inherit;font-size:19px;font-weight:500;letter-spacing:-.012em;
  transition:background .16s ease;animation:nl-in .26s ease both;}
.nl-item:hover{background:rgba(237,235,230,.06);}
.nl-item:focus-visible{outline:2px solid var(--amber);outline-offset:-2px;}
.nl-item svg{width:19px;height:19px;color:var(--dim);flex-shrink:0;}
@keyframes nl-in{from{opacity:0;transform:translateY(7px);}to{opacity:1;transform:none;}}
.nl-empty{padding:6px 26px;color:var(--dim);font-size:15px;line-height:1.55;max-width:210px;}

.nl-rail{width:46px;flex-shrink:0;display:flex;flex-direction:column;padding:2px 0;
  touch-action:none;cursor:grab;border-radius:16px;position:relative;z-index:2;}
.nl-rail:focus-visible{outline:2px solid var(--amber);outline-offset:2px;}
.nl-key{flex:1;display:flex;align-items:center;justify-content:center;
  font-family:'JetBrains Mono',monospace;font-weight:700;font-size:10.5px;color:var(--dim);
  opacity:.45;transform-origin:right center;transition:color .14s ease,opacity .14s ease;user-select:none;}
.nl-key[data-has="1"]{opacity:.85;}
.nl-key[data-on="1"]{color:var(--amber);opacity:1;}

.nl-foot{flex-shrink:0;padding:14px 56px 0 26px;display:flex;align-items:flex-end;
  justify-content:space-between;gap:12px;}
.nl-footL{flex:1;min-width:0;}
.nl-quick{width:48px;height:48px;border-radius:50%;flex-shrink:0;touch-action:none;cursor:pointer;
  background:rgba(237,235,230,.06);border:1px solid rgba(237,235,230,.12);color:var(--ink);
  display:flex;align-items:center;justify-content:center;
  transition:background .16s ease,border-color .3s ease,color .3s ease;}
.nl-quick:hover{background:rgba(237,235,230,.12);}
.nl-quick:focus-visible{outline:2px solid var(--amber);outline-offset:3px;}
.nl-quick svg{width:19px;height:19px;}
.nl-quick[data-on="1"]{color:var(--amber);border-color:var(--amber);}
.nl-footLine{min-height:19px;}
.nl-hint{font-family:'JetBrains Mono',monospace;font-size:9.5px;letter-spacing:.18em;
  text-transform:uppercase;color:var(--dim);opacity:.55;margin:0;}
.nl-open{display:flex;align-items:center;gap:9px;font-size:13.5px;color:var(--ink);margin:0;
  animation:nl-in .2s ease both;}
.nl-dot{width:6px;height:6px;border-radius:50%;background:var(--amber);flex-shrink:0;transition:background .5s ease;}
.nl-dots{display:flex;gap:6px;margin-top:12px;}
.nl-dots i{width:5px;height:5px;border-radius:50%;background:var(--ink);opacity:.22;
  transition:opacity .3s ease,background .5s ease;}
.nl-dots i[data-on="1"]{opacity:1;background:var(--amber);}

/* ---- shared pane shell ---- */
.nl-sheet{position:absolute;inset:0;display:flex;flex-direction:column;padding:34px 22px 24px;
  background:rgba(8,9,13,.55);backdrop-filter:blur(3px);}

.nl-gate{position:absolute;inset:0;}
.nl-gateTop{position:absolute;left:0;right:0;top:32%;transform:translateY(-50%);padding:0 34px;
  display:flex;flex-direction:column;align-items:center;gap:10px;text-align:center;}
.nl-gate p{font-size:13.5px;line-height:1.6;color:var(--dim);margin:0;max-width:215px;}
.nl-warn{color:var(--amber);font-size:12.5px;margin:0;transition:color .5s ease;}
/* Galaxy S23: ultrasonic reader sits ~25mm up from the foot of a ~141mm display */
.nl-sensor{position:absolute;left:50%;top:80%;transform:translate(-50%,-50%);width:72px;height:72px;}
.nl-glow{position:absolute;left:50%;top:50%;width:170px;height:170px;border-radius:50%;
  transform:translate(-50%,-50%);pointer-events:none;transition:opacity .25s ease;
  background:radial-gradient(circle,var(--amber) 0%,transparent 66%);}
.nl-pad{position:relative;width:72px;height:72px;background:none;border:0;padding:0;cursor:pointer;
  touch-action:none;border-radius:50%;color:var(--ink);}
.nl-pad:focus-visible{outline:2px solid var(--amber);outline-offset:6px;}
.nl-pad svg.nl-print{position:absolute;inset:0;margin:auto;width:30px;height:30px;color:var(--amber);
  transition:color .5s ease;}
.nl-ring{transform:rotate(-90deg);display:block;}

/* ---- home assistant ---- */
.nl-hero{display:flex;align-items:flex-end;justify-content:space-between;
  padding-bottom:18px;border-bottom:1px solid rgba(237,235,230,.09);flex-shrink:0;}
.nl-heroT{font-weight:500;font-size:46px;line-height:1;color:var(--ink);margin:6px 0 0;}
.nl-heroL{font-family:'JetBrains Mono',monospace;font-size:9px;letter-spacing:.2em;
  text-transform:uppercase;color:var(--dim);margin:0;}
.nl-step{display:flex;gap:8px;}
.nl-step button{width:34px;height:34px;border-radius:50%;background:rgba(237,235,230,.06);
  border:1px solid rgba(237,235,230,.10);color:var(--ink);cursor:pointer;
  display:flex;align-items:center;justify-content:center;}
.nl-step svg{width:15px;height:15px;}
.nl-grid{display:grid;grid-template-columns:1fr 1fr;gap:9px;margin-top:12px;overflow-y:auto;
  scrollbar-width:none;padding-bottom:4px;}
.nl-grid::-webkit-scrollbar{display:none;}
.nl-tile{display:flex;flex-direction:column;align-items:flex-start;gap:9px;padding:14px;
  border-radius:17px;background:rgba(237,235,230,.05);border:1px solid rgba(237,235,230,.08);
  cursor:pointer;text-align:left;font-family:inherit;transition:background .16s,border-color .16s;}
.nl-tile:hover{background:rgba(237,235,230,.09);}
.nl-tile svg{width:20px;height:20px;color:var(--dim);transition:color .2s;}
.nl-tile[data-on="1"]{background:rgba(237,235,230,.10);border-color:rgba(237,235,230,.16);}
.nl-tile[data-on="1"] svg{color:var(--amber);}
.nl-tName{font-size:14.5px;font-weight:500;color:var(--ink);}
.nl-tState{font-family:'JetBrains Mono',monospace;font-size:8.5px;letter-spacing:.16em;
  text-transform:uppercase;color:var(--dim);margin-top:3px;display:block;}

/* ---- wealth ---- */
.nl-tabs{display:flex;gap:4px;padding:3px;border-radius:12px;background:rgba(237,235,230,.05);
  border:1px solid rgba(237,235,230,.08);flex-shrink:0;margin-bottom:16px;}
.nl-tabs button{flex:1;padding:8px;border:0;border-radius:9px;background:none;cursor:pointer;
  font-family:'JetBrains Mono',monospace;font-size:9px;letter-spacing:.18em;text-transform:uppercase;
  color:var(--dim);transition:background .16s,color .16s;}
.nl-tabs button[data-on="1"]{background:rgba(237,235,230,.12);color:var(--ink);}
.nl-scroll{flex:1;overflow-y:auto;scrollbar-width:none;min-height:0;}
.nl-scroll::-webkit-scrollbar{display:none;}

.nl-money{display:flex;gap:10px;flex-shrink:0;}
.nl-money div{flex:1;padding:13px 14px;border-radius:16px;background:rgba(237,235,230,.05);
  border:1px solid rgba(237,235,230,.08);}
.nl-mLab{font-family:'JetBrains Mono',monospace;font-size:8.5px;letter-spacing:.18em;
  text-transform:uppercase;color:var(--dim);margin:0;}
.nl-mVal{font-weight:500;font-size:25px;color:var(--ink);margin:7px 0 0;letter-spacing:-.01em;}
.nl-mini{display:flex;gap:9px;margin-top:10px;}
.nl-mini>div{flex:1;min-width:0;padding:10px 11px;border-radius:14px;
  background:rgba(237,235,230,.05);border:1px solid rgba(237,235,230,.08);}
.nl-miniVal{font-family:'JetBrains Mono',monospace;font-size:11.5px;color:var(--ink);
  margin:6px 0 0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}
.nl-sensors{display:flex;gap:9px;margin-top:14px;flex-shrink:0;}
.nl-sensors>div{flex:1;padding:11px 12px;border-radius:15px;
  background:rgba(237,235,230,.05);border:1px solid rgba(237,235,230,.08);}
.nl-sVal{font-weight:500;font-size:19px;color:var(--ink);margin:6px 0 0;letter-spacing:-.01em;}

.nl-scanbar{display:flex;align-items:center;gap:9px;width:100%;margin:14px 0 4px;padding:10px 13px;
  border-radius:13px;background:none;border:1px dashed rgba(237,235,230,.16);color:var(--dim);
  cursor:pointer;font-family:'JetBrains Mono',monospace;font-size:8.5px;letter-spacing:.16em;
  text-transform:uppercase;text-align:left;}
.nl-scanbar:hover{color:var(--ink);border-color:rgba(237,235,230,.3);}
.nl-scanbar svg{width:14px;height:14px;flex-shrink:0;}
.nl-scanbar[data-busy="1"] svg{animation:nl-spin 1s linear infinite;}

.nl-bars{margin:16px 0 6px;}
.nl-bar{margin-bottom:11px;}
.nl-barTop{display:flex;justify-content:space-between;align-items:baseline;margin-bottom:5px;}
.nl-barName{font-size:12.5px;color:var(--ink);}
.nl-barVal{font-family:'JetBrains Mono',monospace;font-size:10px;color:var(--dim);}
.nl-barTrk{height:3px;border-radius:2px;background:rgba(237,235,230,.08);overflow:hidden;}
.nl-barFil{height:100%;border-radius:2px;transition:width .5s ease;}

.nl-txn{display:flex;align-items:center;gap:11px;padding:11px 2px;
  border-bottom:1px solid rgba(237,235,230,.06);}
.nl-tdot{width:7px;height:7px;border-radius:2px;flex-shrink:0;}
.nl-txnMain{flex:1;min-width:0;}
.nl-txnName{font-size:14px;color:var(--ink);white-space:nowrap;overflow:hidden;
  text-overflow:ellipsis;display:block;}
.nl-txnMeta{font-family:'JetBrains Mono',monospace;font-size:8.5px;letter-spacing:.13em;
  text-transform:uppercase;color:var(--dim);margin-top:3px;display:block;}
.nl-txnAmt{font-family:'JetBrains Mono',monospace;font-size:12.5px;flex-shrink:0;}
.nl-txnAmt[data-dir="in"]{color:var(--up);}
.nl-txnAmt[data-dir="out"]{color:var(--ink);}

.nl-skip{width:100%;margin-top:14px;padding:11px 13px;border-radius:13px;text-align:left;
  background:rgba(224,120,92,.07);border:1px solid rgba(224,120,92,.2);color:var(--down);
  cursor:pointer;display:flex;align-items:center;gap:9px;font-family:'JetBrains Mono',monospace;
  font-size:8.5px;letter-spacing:.16em;text-transform:uppercase;}
.nl-skip svg{width:14px;height:14px;flex-shrink:0;}
.nl-skipItem{padding:9px 2px;border-bottom:1px solid rgba(237,235,230,.06);}
.nl-skipTxt{font-size:11.5px;color:var(--dim);line-height:1.5;}
.nl-skipWhy{font-family:'JetBrains Mono',monospace;font-size:8px;letter-spacing:.14em;
  text-transform:uppercase;color:var(--down);margin-top:4px;display:block;}

.nl-pf{padding-bottom:16px;border-bottom:1px solid rgba(237,235,230,.09);}
.nl-pfVal{font-weight:500;font-size:42px;line-height:1;color:var(--ink);margin:7px 0 0;}
.nl-pfPl{display:flex;align-items:center;gap:6px;font-family:'JetBrains Mono',monospace;
  font-size:11px;margin-top:9px;}
.nl-pfPl svg{width:13px;height:13px;}
.nl-spark{width:100%;height:30px;margin-top:12px;display:block;}
.nl-hold{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:12px 2px;
  border-bottom:1px solid rgba(237,235,230,.06);}
.nl-hSym{font-family:'JetBrains Mono',monospace;font-size:12px;color:var(--ink);letter-spacing:.06em;}
.nl-hQty{font-family:'JetBrains Mono',monospace;font-size:8.5px;letter-spacing:.14em;
  text-transform:uppercase;color:var(--dim);margin-top:4px;display:block;}
.nl-hVal{text-align:right;flex-shrink:0;}
.nl-hLtp{font-family:'JetBrains Mono',monospace;font-size:12px;color:var(--ink);}
.nl-hPl{font-family:'JetBrains Mono',monospace;font-size:9px;margin-top:4px;display:block;}
.nl-up{color:var(--up);}
.nl-down{color:var(--down);}

@media (prefers-reduced-motion:reduce){.nl-stage *{animation:none!important;}}
      `}</style>

      <div className="nl-phone" ref={phoneRef}>
        {Object.keys(SKY).map((k) => (
          <div key={k} className="nl-wall" data-on={sky === k ? "1" : "0"}
               style={{ backgroundImage: SKY[k].wall }} />
        ))}
        <div className="nl-grain" />

        <div className="nl-track" ref={trackRef} data-anim={swiping ? "0" : "1"}
             style={{ transform: `translateX(calc(${-100 * (page + 1)}% + ${dx}px))` }}
             onPointerDown={onPaneDown} onPointerMove={onPaneMove}
             onPointerUp={onPaneUp} onPointerCancel={onPaneUp}>

          {/* ============ pane -1 : home assistant ============ */}
          <div className="nl-pane">
            <div className="nl-sheet">
              {!haOpen ? (
                <Gate which="ha" title="Home Assistant"
                      blurb="Unlock to reach your devices. Locks again when you leave." />
              ) : (
                <>
                  <div className="nl-hero">
                    <div>
                      <p className="nl-heroL">Thermostat · heating</p>
                      <p className="nl-heroT nl-serif">{target.toFixed(1)}°</p>
                    </div>
                    <div className="nl-step">
                      <button onClick={() => setTarget((t) => Math.max(5, t - 0.5))}
                              aria-label="Lower target temperature"><Minus strokeWidth={2} /></button>
                      <button onClick={() => setTarget((t) => Math.min(30, t + 0.5))}
                              aria-label="Raise target temperature"><Plus strokeWidth={2} /></button>
                    </div>
                  </div>
                  <div className="nl-sensors">
                    <div><p className="nl-mLab">Indoor</p><p className="nl-sVal nl-serif">{house.temp}°</p></div>
                    <div><p className="nl-mLab">Humidity</p><p className="nl-sVal nl-serif">{house.hum}%</p></div>
                    <div><p className="nl-mLab">Drawing</p><p className="nl-sVal nl-serif">{house.kw} kW</p></div>
                  </div>

                  <div className="nl-grid">
                    {entities.map(({ id, name, Icon, on, on_t, off_t }) => (
                      <button key={id} className="nl-tile" data-on={on ? "1" : "0"}
                              onClick={() => !moved.current && toggle(id)}>
                        <Icon strokeWidth={1.6} />
                        <span>
                          <span className="nl-tName">{name}</span>
                          <span className="nl-tState">{on ? on_t : off_t}</span>
                        </span>
                      </button>
                    ))}
                  </div>
                </>
              )}
            </div>
          </div>

          {/* ============ pane 0 : launcher ============ */}
          <div className="nl-pane">
            <div className="nl-ui">
              <div className="nl-clock">
                <p className="nl-time nl-serif" onClick={() => setLetter(null)}>{time}</p>
                <p className="nl-date">{date}</p>
                <button className="nl-sky" data-wait={mode === "locating" ? "1" : "0"}
                        onClick={() => !moved.current && cycleSky()}>
                  <SkyIcon strokeWidth={1.8} />{skyText}
                </button>
              </div>

              <div className="nl-body">
                <div className="nl-col">
                  {letter && <div className="nl-ghost nl-serif">{letter}</div>}
                  <div className="nl-tag">
                    {letter
                      ? <><b>{letter}</b> — {shown.length} {shown.length === 1 ? "app" : "apps"}</>
                      : "Pinned"}
                  </div>
                  {shown.length > 0 ? (
                    <ul className="nl-list">
                      {shown.map(({ name, Icon }, i) => (
                        <li key={name}>
                          <button className="nl-item"
                                  style={{ animationDelay: calm ? "0ms" : `${i * 26}ms` }}
                                  onClick={() => !moved.current && setOpened("Opened " + name)}>
                            <Icon strokeWidth={1.6} />{name}
                          </button>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="nl-empty">Nothing filed under {letter}. Slide to a brighter letter.</p>
                  )}
                </div>

                <div ref={railRef} className="nl-rail" data-drag={dragging ? "1" : "0"}
                     role="slider" tabIndex={0}
                     aria-label="Alphabet rail — drag or use arrow keys to jump to a letter"
                     aria-valuetext={letter || "none"} aria-valuemin={0} aria-valuemax={25}
                     aria-valuenow={letter ? LETTERS.indexOf(letter) : 0}
                     onPointerDown={onRailDown} onPointerMove={onRailMove}
                     onPointerUp={onRailUp} onPointerCancel={onRailUp} onKeyDown={onRailKey}>
                  {LETTERS.map((L, i) => {
                    const m = magnify(i);
                    const has = (byLetter[L] || []).length > 0;
                    return (
                      <span key={L} className="nl-key"
                            data-on={letter === L ? "1" : "0"} data-has={has ? "1" : "0"}
                            style={m ? {
                              transform: `translateX(${m.shift}px) scale(${m.scale})`,
                              opacity: Math.min(1, (has ? 0.85 : 0.45) + m.glow * 0.6),
                            } : undefined}>{L}</span>
                    );
                  })}
                </div>
              </div>

              <div className="nl-foot">
                <div className="nl-footL">
                  <div className="nl-footLine">
                    {opened ? (
                      <p className="nl-open"><span className="nl-dot" />{opened}</p>
                    ) : letter ? (
                      <p className="nl-hint">Tap the clock to reset</p>
                    ) : null}
                  </div>
                  <div className="nl-dots" aria-hidden="true">
                    {[-1, 0, 1].map((p) => <i key={p} data-on={page === p ? "1" : "0"} />)}
                  </div>
                </div>

                <button className="nl-quick" data-on={torch ? "1" : "0"}
                        aria-label="Phone — swipe up for camera, down for flashlight"
                        style={{ transform: `translateY(${Math.max(-10, Math.min(10, qy * 0.3))}px)` }}
                        onPointerDown={onQuickDown} onPointerMove={onQuickMove}
                        onPointerUp={onQuickUp} onPointerCancel={onQuickUp}
                        onClick={onQuickClick} onKeyDown={onQuickKey}>
                  <QuickIcon strokeWidth={1.7} />
                </button>
              </div>
            </div>
          </div>

          {/* ============ pane 1 : wealth ============ */}
          <div className="nl-pane">
            <div className="nl-sheet">
              {!wealthOpen ? (
                <Gate which="wealth" title="Money"
                      blurb="Unlock to read your payment messages and holdings." />
              ) : (
                <>
                  <div className="nl-tabs">
                    <button data-on={tab === "payments" ? "1" : "0"}
                            onClick={() => !moved.current && setTab("payments")}>Payments</button>
                    <button data-on={tab === "stocks" ? "1" : "0"}
                            onClick={() => !moved.current && setTab("stocks")}>Stocks</button>
                  </div>

                  {tab === "payments" ? (
                    <div className="nl-scroll">
                      <div className="nl-money">
                        <div>
                          <p className="nl-mLab">Out</p>
                          <p className="nl-mVal nl-serif">{inr(money.out)}</p>
                        </div>
                        <div>
                          <p className="nl-mLab">In</p>
                          <p className="nl-mVal nl-serif">{inr(money.inn)}</p>
                        </div>
                      </div>

                      <div className="nl-mini">
                        <div>
                          <p className="nl-mLab">Net</p>
                          <p className={"nl-miniVal " + (money.net >= 0 ? "nl-up" : "nl-down")}>
                            {money.net >= 0 ? "+" : "−"}{inr(Math.abs(money.net))}
                          </p>
                        </div>
                        <div>
                          <p className="nl-mLab">Entries</p>
                          <p className="nl-miniVal">{money.count}</p>
                        </div>
                        <div>
                          <p className="nl-mLab">Largest</p>
                          <p className="nl-miniVal">{inr(money.largest)}</p>
                        </div>
                      </div>

                      <button className="nl-scanbar" data-busy={scan === "scanning" ? "1" : "0"}
                              onClick={rescan}>
                        {scan === "scanning" ? <RefreshCw strokeWidth={1.8} /> : <ScanLine strokeWidth={1.8} />}
                        {scan === "scanning"
                          ? "Reading messages"
                          : `${rows.length} messages · ${parsed.length} matched · ${skipped.length} skipped`}
                      </button>

                      <div className="nl-bars">
                        {money.cats.slice(0, 5).map((c) => (
                          <div className="nl-bar" key={c.key}>
                            <div className="nl-barTop">
                              <span className="nl-barName">{c.key}</span>
                              <span className="nl-barVal">{inr(c.total)}</span>
                            </div>
                            <div className="nl-barTrk">
                              <div className="nl-barFil"
                                   style={{ width: (c.total / money.cats[0].total) * 100 + "%",
                                            background: c.tint }} />
                            </div>
                          </div>
                        ))}
                      </div>

                      {parsed.map((r) => (
                        <div className="nl-txn" key={r.id}>
                          <span className="nl-tdot" style={{ background: r.cat.tint }} />
                          <span className="nl-txnMain">
                            <span className="nl-txnName">{r.merchant}</span>
                            <span className="nl-txnMeta">
                              {[r.date, r.channel, r.acct && "··" + r.acct].filter(Boolean).join(" · ")}
                            </span>
                          </span>
                          <span className="nl-txnAmt" data-dir={r.dir}>
                            {r.dir === "in" ? "+" : "−"}{inr(r.amount)}
                          </span>
                        </div>
                      ))}

                      {skipped.length > 0 && (
                        <>
                          <button className="nl-skip"
                                  onClick={() => !moved.current && setShowSkipped((v) => !v)}>
                            <AlertCircle strokeWidth={1.8} />
                            {skipped.length} messages the parser couldn't read
                          </button>
                          {showSkipped && skipped.map((r) => (
                            <div className="nl-skipItem" key={r.id}>
                              <span className="nl-skipTxt">{r.text}</span>
                              <span className="nl-skipWhy">{r.why}</span>
                            </div>
                          ))}
                        </>
                      )}
                    </div>
                  ) : (
                    <div className="nl-scroll">
                      <div className="nl-pf">
                        <p className="nl-mLab">Portfolio</p>
                        <p className="nl-pfVal nl-serif">{inr(portfolio.value)}</p>
                        <p className={"nl-pfPl " + (portfolio.pl >= 0 ? "nl-up" : "nl-down")}>
                          {portfolio.pl >= 0 ? <TrendingUp strokeWidth={2} /> : <TrendingDown strokeWidth={2} />}
                          {portfolio.pl >= 0 ? "+" : "−"}{inr(Math.abs(portfolio.pl))}
                          {"  ("}{portfolio.plPct.toFixed(2)}%{")"}
                        </p>
                        {spark.length > 2 && (
                          <svg className="nl-spark" viewBox="0 0 100 28" preserveAspectRatio="none">
                            <polyline points={sparkPath()} fill="none" stroke={active.accent}
                                      strokeWidth="1" vectorEffect="non-scaling-stroke" />
                          </svg>
                        )}
                      </div>

                      <div className="nl-mini">
                        <div>
                          <p className="nl-mLab">Invested</p>
                          <p className="nl-miniVal">{inr(portfolio.cost)}</p>
                        </div>
                        <div>
                          <p className="nl-mLab">Today</p>
                          <p className={"nl-miniVal " + (portfolio.day >= 0 ? "nl-up" : "nl-down")}>
                            {portfolio.day >= 0 ? "+" : "−"}{inr(Math.abs(portfolio.day))}
                          </p>
                        </div>
                        <div>
                          <p className="nl-mLab">Holdings</p>
                          <p className="nl-miniVal">{holdings.length}</p>
                        </div>
                      </div>

                      {holdings.map((h) => {
                        const pl = (h.ltp - h.avg) * h.qty;
                        const pct2 = ((h.ltp - h.avg) / h.avg) * 100;
                        return (
                          <div className="nl-hold" key={h.sym}>
                            <span>
                              <span className="nl-hSym">{h.sym}</span>
                              <span className="nl-hQty">{h.qty} sh · avg {inr(h.avg)}</span>
                            </span>
                            <span className="nl-hVal">
                              <span className="nl-hLtp">{inr(h.qty * h.ltp)}</span>
                              <span className={"nl-hPl " + (pl >= 0 ? "nl-up" : "nl-down")}>
                                {pl >= 0 ? "+" : "−"}{inr(Math.abs(pl))} ({pct2.toFixed(1)}%)
                              </span>
                            </span>
                          </div>
                        );
                      })}
                      <p className="nl-note" style={{ marginTop: 16 }}>
                        Prices simulated · wire a quotes API to go live
                      </p>
                    </div>
                  )}

                </>
              )}
            </div>
          </div>


        </div>
      </div>
    </div>
  );
}
