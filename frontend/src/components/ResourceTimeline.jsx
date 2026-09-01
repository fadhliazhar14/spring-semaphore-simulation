import React, { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Pemakaian sumber daya back-end selama 60 detik terakhir.
 *
 * Papan slot menunjukkan akibat; grafik ini menunjukkan sebabnya dibatasi. Batas konkurensi
 * dipasang justru untuk menjaga angka-angka di sini, jadi tanpa menampilkannya simulasi cuma
 * memperlihatkan orang berebut tanpa memperlihatkan apa yang sedang dilindungi.
 *
 * Yang digambar adalah instance yang menyusun snapshot. Instance lain tetap terdaftar dengan
 * angka terakhirnya, karena sebuah JVM hanya bisa mengukur dirinya sendiri.
 */

const WINDOW_MS = 60000
const MAX_SAMPLES = 900

const PAD_LEFT = 40
const PAD_RIGHT = 46
const PAD_TOP = 8
const PAD_BOTTOM = 18
const HEIGHT = 150

const plotWidth = (width) => Math.max(1, width - PAD_LEFT - PAD_RIGHT)

const asMb = (bytes) => bytes / (1024 * 1024)
const fmtMb = (bytes) => `${asMb(bytes).toFixed(0)} MB`

export default function ResourceTimeline({ instances = [] }) {
  const canvasRef = useRef(null)
  const samplesRef = useRef([])
  const hoverXRef = useRef(null)

  const [peak, setPeak] = useState({ heap: 0, cpu: 0, threads: 0 })
  const [readout, setReadout] = useState(null)

  // Instance yang menyusun snapshot ini. Hanya JVM itu yang riwayatnya bisa digambar.
  const self = instances.find((inst) => inst.self) || null
  const usage = self ? self.usage : null

  const readAt = useCallback((hoverX, samples, now) => {
    const canvas = canvasRef.current
    if (hoverX == null || samples.length === 0 || !canvas) {
      return null
    }
    const time = now - WINDOW_MS
      + ((hoverX - PAD_LEFT) / plotWidth(canvas.clientWidth)) * WINDOW_MS

    let nearest = samples[0]
    for (const sample of samples) {
      if (Math.abs(sample.t - time) < Math.abs(nearest.t - time)) nearest = sample
    }
    return {
      x: hoverX,
      flip: hoverX > canvas.clientWidth - 160,
      agoMs: Math.max(0, now - nearest.t),
      heap: nearest.heap,
      heapMax: nearest.heapMax,
      cpu: nearest.cpu,
      threads: nearest.threads
    }
  }, [])

  useEffect(() => {
    if (!usage) {
      return
    }
    const samples = samplesRef.current
    const now = Date.now()
    samples.push({
      t: now,
      heap: usage.heapUsedBytes || 0,
      heapMax: usage.heapMaxBytes || 0,
      cpu: usage.cpuPercent,
      threads: usage.threadCount || 0
    })
    while (samples.length > 0 && (now - samples[0].t > WINDOW_MS || samples.length > MAX_SAMPLES)) {
      samples.shift()
    }
    draw(canvasRef.current, samples, now, hoverXRef.current)

    const next = samples.reduce((max, s) => ({
      heap: Math.max(max.heap, s.heap),
      cpu: Math.max(max.cpu, s.cpu < 0 ? 0 : s.cpu),
      threads: Math.max(max.threads, s.threads)
    }), { heap: 0, cpu: 0, threads: 0 })
    setPeak((prev) =>
      prev.heap === next.heap && Math.round(prev.cpu) === Math.round(next.cpu)
        && prev.threads === next.threads ? prev : next)

    setReadout(readAt(hoverXRef.current, samples, now))
  }, [instances, usage, readAt])

  useEffect(() => {
    const onResize = () => draw(canvasRef.current, samplesRef.current, Date.now(), hoverXRef.current)
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  const handleMove = (event) => {
    const rect = event.currentTarget.getBoundingClientRect()
    hoverXRef.current = event.clientX - rect.left
    const now = Date.now()
    draw(canvasRef.current, samplesRef.current, now, hoverXRef.current)
    setReadout(readAt(hoverXRef.current, samplesRef.current, now))
  }

  const handleLeave = () => {
    hoverXRef.current = null
    draw(canvasRef.current, samplesRef.current, Date.now(), null)
    setReadout(null)
  }

  const cpuAvailable = usage && usage.cpuPercent >= 0
  const others = instances.filter((inst) => !inst.self && inst.usage)

  return (
    <section className="panel">
      <div className="phead">
        <span className="ptitle">Pemakaian Sumber Daya Back-end</span>
        <span className="pnote">
          {self ? self.id : 'menunggu instance'} &middot; 60 detik terakhir
        </span>
      </div>

      <div className="tlwrap">
        <canvas ref={canvasRef} onMouseMove={handleMove} onMouseLeave={handleLeave} />
        {readout && (
          <div
            className="tltip"
            style={{
              left: readout.x,
              transform: readout.flip ? 'translate(calc(-100% - 12px), 0)' : 'translate(12px, 0)'
            }}
          >
            <div className="t">{(readout.agoMs / 1000).toFixed(1)} detik lalu</div>
            <div>
              <i style={{ background: 'var(--accent)' }} />
              heap {fmtMb(readout.heap)}
              {readout.heapMax > 0 ? ` / ${fmtMb(readout.heapMax)}` : ''}
            </div>
            <div>
              <i style={{ background: 'var(--hot)' }} />
              cpu {readout.cpu < 0 ? 'tak terukur' : `${readout.cpu.toFixed(1)}%`}
            </div>
            <div>
              <i style={{ background: 'var(--line)' }} />
              {readout.threads} thread
            </div>
          </div>
        )}
      </div>

      <div className="legend">
        <span className="lg">
          <i style={{ background: 'var(--accent)' }} />
          heap <em>{usage ? fmtMb(usage.heapUsedBytes) : '-'} &middot; puncak {fmtMb(peak.heap)}</em>
        </span>
        <span className="lg">
          <i style={{ background: 'var(--hot)' }} />
          cpu <em>
            {cpuAvailable ? `${usage.cpuPercent.toFixed(1)}%` : 'tak terukur'}
            {cpuAvailable ? ` · puncak ${peak.cpu.toFixed(1)}%` : ''}
          </em>
        </span>
        <span className="lg">
          <i style={{ background: 'var(--line)' }} />
          thread <em>{usage ? usage.threadCount : '-'} &middot; puncak {peak.threads}</em>
        </span>
      </div>

      {others.length > 0 && (
        <div className="legend" style={{ borderTop: '1px dashed var(--line-soft)' }}>
          {others.map((inst) => (
            <span key={inst.id} className="lg">
              <i style={{ background: inst.alive ? 'var(--good)' : 'var(--crit)' }} />
              {inst.id} <em>
                {fmtMb(inst.usage.heapUsedBytes)}
                {inst.usage.cpuPercent >= 0 ? ` · ${inst.usage.cpuPercent.toFixed(1)}% cpu` : ''}
                {` · ${inst.usage.threadCount} thread`}
              </em>
            </span>
          ))}
        </div>
      )}
    </section>
  )
}

function draw(canvas, samples, now, hoverX) {
  if (!canvas || !canvas.parentElement) return

  const ratio = window.devicePixelRatio || 1
  const width = canvas.parentElement.clientWidth
  const height = HEIGHT
  if (width <= 0) return

  canvas.width = width * ratio
  canvas.height = height * ratio
  canvas.style.height = `${height}px`

  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.setTransform(ratio, 0, 0, ratio, 0, 0)
  ctx.clearRect(0, 0, width, height)

  const plotW = width - PAD_LEFT - PAD_RIGHT
  const plotH = height - PAD_TOP - PAD_BOTTOM
  if (plotW <= 0) return

  const css = getComputedStyle(document.documentElement)
  const token = (name, fallback) => (css.getPropertyValue(name) || fallback).trim()
  const accent = token('--accent', '#0A8794')
  const hot = token('--hot', '#7A4BAE')
  const dim = token('--text-faint', '#7F8D92')
  const gridline = token('--line-soft', '#DCE3E4')

  // Skala heap mengikuti batas heap kalau diketahui, supaya tinggi grafik berarti "seberapa dekat
  // ke batas", bukan sekadar bentuk relatif terhadap puncaknya sendiri yang selalu menyentuh atap.
  const last = samples[samples.length - 1]
  const heapMax = last && last.heapMax > 0
    ? last.heapMax
    : Math.max(1, samples.reduce((max, s) => Math.max(max, s.heap), 0) * 1.25)

  const x = (t) => PAD_LEFT + ((t - (now - WINDOW_MS)) / WINDOW_MS) * plotW
  const yHeap = (v) => PAD_TOP + plotH - (v / heapMax) * plotH
  const yCpu = (v) => PAD_TOP + plotH - (Math.max(0, v) / 100) * plotH

  ctx.strokeStyle = gridline
  ctx.lineWidth = 1
  ctx.font = '10px ui-monospace, monospace'
  ctx.fillStyle = dim
  for (let i = 0; i <= 4; i++) {
    const y = Math.round(PAD_TOP + (plotH / 4) * i) + 0.5
    ctx.beginPath()
    ctx.moveTo(PAD_LEFT, y)
    ctx.lineTo(PAD_LEFT + plotW, y)
    ctx.stroke()
    ctx.textAlign = 'right'
    ctx.fillText(`${asMb(heapMax * (1 - i / 4)).toFixed(0)}`, PAD_LEFT - 6, y + 3)
  }

  ctx.textAlign = 'left'
  ctx.fillText('100%', PAD_LEFT + plotW + 6, PAD_TOP + 3)
  ctx.fillText('0%', PAD_LEFT + plotW + 6, PAD_TOP + plotH + 3)

  if (samples.length < 2) {
    ctx.textAlign = 'center'
    ctx.fillText('menunggu data...', PAD_LEFT + plotW / 2, PAD_TOP + plotH / 2)
    return
  }

  // Heap sebagai luasan, karena yang menarik adalah seberapa penuh ruangnya.
  ctx.beginPath()
  ctx.moveTo(x(samples[0].t), PAD_TOP + plotH)
  samples.forEach((s) => ctx.lineTo(x(s.t), yHeap(s.heap)))
  ctx.lineTo(x(samples[samples.length - 1].t), PAD_TOP + plotH)
  ctx.closePath()
  ctx.fillStyle = withAlpha(accent, 0.2)
  ctx.fill()
  ctx.strokeStyle = accent
  ctx.lineWidth = 1.5
  ctx.stroke()

  // CPU sebagai garis pada skala persen tersendiri di sisi kanan.
  const measured = samples.filter((s) => s.cpu >= 0)
  if (measured.length > 1) {
    ctx.beginPath()
    measured.forEach((s, i) => {
      const px = x(s.t)
      const py = yCpu(s.cpu)
      if (i === 0) ctx.moveTo(px, py)
      else ctx.lineTo(px, py)
    })
    ctx.strokeStyle = hot
    ctx.lineWidth = 1.5
    ctx.stroke()
  }

  if (hoverX != null && hoverX >= PAD_LEFT && hoverX <= PAD_LEFT + plotW) {
    ctx.strokeStyle = dim
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(Math.round(hoverX) + 0.5, PAD_TOP)
    ctx.lineTo(Math.round(hoverX) + 0.5, PAD_TOP + plotH)
    ctx.stroke()
  }

  ctx.fillStyle = dim
  ctx.textAlign = 'left'
  ctx.fillText('-60s  heap MB', PAD_LEFT, height - 5)
  ctx.textAlign = 'right'
  ctx.fillText('sekarang', PAD_LEFT + plotW, height - 5)
}

function withAlpha(color, alpha) {
  const hex = color.startsWith('#') ? color.slice(1) : null
  if (!hex || (hex.length !== 6 && hex.length !== 3)) return `rgba(10,135,148,${alpha})`
  const full = hex.length === 3 ? hex.split('').map((c) => c + c).join('') : hex
  const r = parseInt(full.slice(0, 2), 16)
  const g = parseInt(full.slice(2, 4), 16)
  const b = parseInt(full.slice(4, 6), 16)
  return `rgba(${r},${g},${b},${alpha})`
}
