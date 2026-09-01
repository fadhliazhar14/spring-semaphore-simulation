import React, { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Riwayat okupansi slot dan laju penolakan.
 *
 * Papan slot menjawab "sekarang bagaimana"; grafik ini menjawab "tadi bagaimana". Keduanya perlu,
 * karena yang paling ingin diamati justru hubungan antar waktu: okupansi menempel di batas atas,
 * dan tepat selama itu penolakan melonjak. Pada angka yang berkedip sepuluh kali per detik pola
 * itu tidak terbaca; sebagai bentuk, langsung terlihat.
 *
 * Yang digambar adalah laju penolakan per detik, bukan jumlah kumulatifnya. Jumlah kumulatif hanya
 * bisa naik, jadi grafiknya selalu berbentuk tanjakan dan tidak pernah menunjukkan kapan tekanan
 * datang. Laju menunjukkannya.
 *
 * Riwayat disimpan di ref, bukan state, karena satu sampel masuk tiap 100 ms dan menaruhnya di
 * state berarti memaksa seluruh pohon komponen menggambar ulang sepuluh kali per detik.
 */

const WINDOW_MS = 60000
const MAX_SAMPLES = 900

// Tata letak kanvas dipakai bersama oleh penggambar dan penerjemah posisi kursor, jadi harus
// berupa satu sumber nilai. Dua salinan yang melenceng membuat tooltip menunjuk waktu yang salah.
const PAD_LEFT = 34
const PAD_RIGHT = 46
const PAD_TOP = 8
const PAD_BOTTOM = 18
const HEIGHT = 160

const plotWidth = (width) => Math.max(1, width - PAD_LEFT - PAD_RIGHT)

export default function OccupancyTimeline({ status }) {
  const canvasRef = useRef(null)
  const samplesRef = useRef([])

  // Posisi kursor di atas kanvas, disimpan di ref supaya penggambaran ulang tiap sampel bisa
  // membacanya tanpa menjadikan gerakan tetikus sebagai pemicu render.
  const hoverXRef = useRef(null)

  // Puncak tidak bisa dibaca dari snapshot terakhir, jadi hanya itu yang disimpan di state.
  // Nilainya jarang berubah, sehingga tidak memicu gambar ulang tiap sampel.
  const [peak, setPeak] = useState({ rate: 0, active: 0 })
  const [readout, setReadout] = useState(null)

  /** Menyusun isi tooltip dari sampel yang paling dekat dengan kursor. */
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
      // Sisi tooltip ditentukan di sini, tempat lebar kanvas memang boleh dibaca. Menghitungnya
      // saat render berarti membaca ref di luar efek, dan hasilnya bisa berasal dari ukuran lama.
      flip: hoverX > canvas.clientWidth - 150,
      agoMs: Math.max(0, now - nearest.t),
      active: nearest.active,
      total: nearest.total,
      rate: nearest.rate,
      rejected: nearest.rejected
    }
  }, [])

  useEffect(() => {
    const samples = samplesRef.current
    const now = Date.now()
    const previous = samples[samples.length - 1]
    const rejected = status.failedRejected || 0

    // Laju dihitung dari selisih dua cacah berurutan. Inisialisasi ulang simulasi membuat cacahnya
    // mundur; selisih negatif diperlakukan sebagai nol agar grafiknya tidak melonjak terbalik.
    let rate = 0
    if (previous) {
      const dt = (now - previous.t) / 1000
      if (dt > 0) rate = Math.max(0, (rejected - previous.rejected) / dt)
    }

    samples.push({
      t: now,
      active: status.activePermits || 0,
      total: status.totalPermits || 0,
      rejected,
      rate
    })
    while (samples.length > 0 && (now - samples[0].t > WINDOW_MS || samples.length > MAX_SAMPLES)) {
      samples.shift()
    }
    draw(canvasRef.current, samples, now, hoverXRef.current)

    const next = samples.reduce(
      (max, s) => ({ rate: Math.max(max.rate, s.rate), active: Math.max(max.active, s.active) }),
      { rate: 0, active: 0 })
    setPeak((prev) =>
      Math.round(prev.rate) === Math.round(next.rate) && prev.active === next.active ? prev : next)

    // Isi tooltip ikut disegarkan tiap sampel. Kalau tidak, angkanya membeku pada saat kursor
    // berhenti bergerak padahal grafiknya terus bergeser ke kiri di bawahnya.
    setReadout(readAt(hoverXRef.current, samples, now))
  }, [status, readAt])

  // Kanvas perlu digambar ulang saat ukurannya berubah, kalau tidak gambarnya ikut teregang.
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

  return (
    <section className="panel">
      <div className="phead">
        <span className="ptitle">Riwayat Okupansi &amp; Penolakan</span>
        <span className="pnote">60 detik terakhir &middot; arahkan kursor untuk membaca angkanya</span>
      </div>

      <div className="tlwrap">
        <canvas ref={canvasRef} onMouseMove={handleMove} onMouseLeave={handleLeave} />
        {readout && (
          <div
            className="tltip"
            style={{
              left: readout.x,
              // Dibalik ke kiri kursor begitu mendekati tepi kanan supaya tidak terpotong panel.
              transform: readout.flip ? 'translate(calc(-100% - 12px), 0)' : 'translate(12px, 0)'
            }}
          >
            <div className="t">{(readout.agoMs / 1000).toFixed(1)} detik lalu</div>
            <div>
              <i style={{ background: 'var(--accent)' }} />
              slot {readout.active}/{readout.total}
            </div>
            <div>
              <i style={{ background: 'var(--crit)' }} />
              {Math.round(readout.rate)} tolak/detik
            </div>
            <div>
              <i style={{ background: 'var(--line)' }} />
              total ditolak {readout.rejected.toLocaleString('id-ID')}
            </div>
          </div>
        )}
      </div>

      <div className="legend">
        <span className="lg">
          <i style={{ background: 'var(--accent)' }} />
          slot terpakai <em>{status.activePermits || 0}/{status.totalPermits || 0} &middot; puncak {peak.active}</em>
        </span>
        <span className="lg">
          <i style={{ background: 'var(--crit)' }} />
          laju penolakan <em>puncak {Math.round(peak.rate)}/detik</em>
        </span>
        <span className="lg">
          <i style={{ background: 'var(--line)' }} />
          total ditolak <em>{(status.failedRejected || 0).toLocaleString('id-ID')}</em>
        </span>
      </div>
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

  // Warna diambil dari token tema, bukan ditulis tetap, supaya kanvas ikut berubah saat mode
  // terang atau gelap berganti.
  const css = getComputedStyle(document.documentElement)
  const token = (name, fallback) => (css.getPropertyValue(name) || fallback).trim()
  const accent = token('--accent', '#0A8794')
  const crit = token('--crit', '#BC4530')
  const dim = token('--text-faint', '#7F8D92')
  const gridline = token('--line-soft', '#DCE3E4')

  const totalPermits = Math.max(1, samples.length ? samples[samples.length - 1].total : 1)
  // Skala penolakan mengikuti puncaknya sendiri: laju bisa ratusan kali jumlah slot, dan satu
  // skala untuk keduanya akan membuat garis okupansi rata di dasar grafik.
  const peakRate = Math.max(1, samples.reduce((max, s) => Math.max(max, s.rate), 0))

  const x = (t) => PAD_LEFT + ((t - (now - WINDOW_MS)) / WINDOW_MS) * plotW
  const yOcc = (v) => PAD_TOP + plotH - (v / totalPermits) * plotH
  const yRate = (v) => PAD_TOP + plotH - (v / peakRate) * plotH

  // Garis bantu satu per slot, sampai maksimal delapan supaya tidak berubah jadi arsiran.
  const step = Math.max(1, Math.ceil(totalPermits / 8))
  ctx.strokeStyle = gridline
  ctx.lineWidth = 1
  ctx.font = '10px ui-monospace, monospace'
  ctx.fillStyle = dim
  for (let v = 0; v <= totalPermits; v += step) {
    const y = Math.round(yOcc(v)) + 0.5
    ctx.beginPath()
    ctx.moveTo(PAD_LEFT, y)
    ctx.lineTo(PAD_LEFT + plotW, y)
    ctx.stroke()
    ctx.textAlign = 'right'
    ctx.fillText(String(v), PAD_LEFT - 6, y + 3)
  }

  ctx.textAlign = 'left'
  ctx.fillText(`${Math.round(peakRate)}/s`, PAD_LEFT + plotW + 6, PAD_TOP + 3)
  ctx.fillText('0', PAD_LEFT + plotW + 6, PAD_TOP + plotH + 3)

  if (samples.length < 2) {
    ctx.textAlign = 'center'
    ctx.fillText('menunggu data...', PAD_LEFT + plotW / 2, PAD_TOP + plotH / 2)
    return
  }

  // Okupansi digambar sebagai luasan bertangga, bukan garis miring: jumlah slot terpakai berubah
  // seketika saat slot diambil atau dilepas, dan garis miring akan menyiratkan perubahan bertahap
  // yang tidak pernah terjadi.
  ctx.beginPath()
  ctx.moveTo(x(samples[0].t), PAD_TOP + plotH)
  samples.forEach((s, i) => {
    const px = x(s.t)
    if (i > 0) ctx.lineTo(px, yOcc(samples[i - 1].active))
    ctx.lineTo(px, yOcc(s.active))
  })
  ctx.lineTo(x(samples[samples.length - 1].t), PAD_TOP + plotH)
  ctx.closePath()
  ctx.fillStyle = withAlpha(accent, 0.2)
  ctx.fill()
  ctx.strokeStyle = accent
  ctx.lineWidth = 1.5
  ctx.stroke()

  // Batas atas: berapa pun derasnya trafik, luasan tidak boleh melewati garis ini.
  const limitY = Math.round(yOcc(totalPermits)) + 0.5
  ctx.strokeStyle = accent
  ctx.globalAlpha = 0.5
  ctx.setLineDash([4, 4])
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.moveTo(PAD_LEFT, limitY)
  ctx.lineTo(PAD_LEFT + plotW, limitY)
  ctx.stroke()
  ctx.setLineDash([])
  ctx.globalAlpha = 1

  ctx.beginPath()
  samples.forEach((s, i) => {
    const px = x(s.t)
    const py = yRate(s.rate)
    if (i === 0) ctx.moveTo(px, py)
    else ctx.lineTo(px, py)
  })
  ctx.strokeStyle = crit
  ctx.lineWidth = 1.5
  ctx.stroke()

  // Penunjuk kursor digambar paling akhir supaya berada di atas kedua kurva.
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
  ctx.fillText('-60s', PAD_LEFT, height - 5)
  ctx.textAlign = 'right'
  ctx.fillText('sekarang', PAD_LEFT + plotW, height - 5)
}

/** Versi tembus pandang dari sebuah warna token, dipakai sebagai isian luasan okupansi. */
function withAlpha(color, alpha) {
  const hex = color.startsWith('#') ? color.slice(1) : null
  if (!hex || (hex.length !== 6 && hex.length !== 3)) return `rgba(10,135,148,${alpha})`
  const full = hex.length === 3 ? hex.split('').map((c) => c + c).join('') : hex
  const r = parseInt(full.slice(0, 2), 16)
  const g = parseInt(full.slice(2, 4), 16)
  const b = parseInt(full.slice(4, 6), 16)
  return `rgba(${r},${g},${b},${alpha})`
}
