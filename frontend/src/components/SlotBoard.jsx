import React from 'react'

/**
 * Papan slot konkurensi.
 *
 * Setiap baris adalah satu slot bernomor tetap di Redis, bukan sekadar hitungan. Isinya datang apa
 * adanya dari backend: siapa pemegangnya, dari instance mana, sudah berapa lama dipegang, dan
 * berapa lama lagi sebelum reaper berhak merebutnya.
 *
 * Susunannya baris, bukan kotak berpetak, karena yang perlu dibaca berdampingan adalah nomor slot
 * dan sisa lease-nya. Pada petak, kedua angka itu terpisah jauh dan mata harus melompat.
 */

// Urutan langkah menentukan seberapa jauh sebuah sesi sudah berjalan. Nama panjangnya dipendekkan
// supaya muat di baris slot tanpa memotong userId.
const STEPS = [
  { key: 'SELECTING', label: 'pilih' },
  { key: 'PAYING', label: 'bayar' },
  { key: 'CONFIRMING', label: 'terbit' }
]

function formatMs(ms) {
  if (ms < 1000) return `${Math.max(0, Math.round(ms))}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

export default function SlotBoard({ slots = [], leaseMs }) {
  if (slots.length === 0) {
    return <div className="empty">Belum ada simulasi. Klik Inisialisasi untuk membuat slot.</div>
  }

  return (
    <div className="slots">
      {slots.map((slot) => {
        if (!slot.occupied) {
          return (
            <div key={slot.slot} className="slot idle">
              <span className="sid">#{slot.number}</span>
              <span className="who">kosong</span>
              <span className="steptrack">
                {STEPS.map((step) => (
                  <span key={step.key} className="stepseg">{step.label}</span>
                ))}
              </span>
              <span />
              <span className="held">—</span>
            </div>
          )
        }

        const expired = slot.leaseRemainingMs <= 0
        // Bar menyusut seiring lease habis. Ini yang membuat "kepemilikan berbatas waktu" terlihat
        // sebagai gerakan, bukan sekadar istilah.
        const scale = leaseMs > 0 ? leaseMs : 30000
        const leftPct = Math.max(0, Math.min(100, (slot.leaseRemainingMs / scale) * 100))

        return (
          <div key={slot.slot} className={`slot busy${expired ? ' expired' : ''}`}>
            <span className="sid">#{slot.number}</span>
            <span className="who">
              {slot.userId}
              <em>{slot.instanceId}</em>
            </span>
            <span className="steptrack" title={`langkah: ${slot.phase}`}>
              {STEPS.map((step, index) => {
                const current = STEPS.findIndex((x) => x.key === slot.phase)
                const state = index < current ? 'done' : index === current ? 'active' : ''
                return (
                  <span key={step.key} className={`stepseg ${state}`}>
                    {step.label}
                  </span>
                )
              })}
            </span>
            <span className="leasecell">
              <span className="leasebar">
                <i style={{ width: `${leftPct}%` }} />
              </span>
              <span className="lbl">
                {expired ? 'lease habis' : `lease ${formatMs(slot.leaseRemainingMs)}`}
              </span>
            </span>
            <span className="held">{formatMs(slot.heldForMs)}</span>
          </div>
        )
      })}
    </div>
  )
}
