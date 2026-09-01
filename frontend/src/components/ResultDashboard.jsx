import React from 'react'

/**
 * Rekap hasil.
 *
 * Angka di sini punya dua pembagi yang berbeda, dan menyamakannya membuat semuanya menyesatkan.
 * Penolakan dihitung terhadap jumlah percobaan, karena seorang pembeli bisa ditolak berkali-kali.
 * Hasil akhir pembelian dihitung terhadap jumlah sesi yang berhasil masuk, karena hanya merekalah
 * yang pernah sampai ke langkah membeli. Dulu keduanya dibagi jumlah percobaan, sehingga "tiket
 * terbit satu persen" terbaca seperti kegagalan besar padahal delapan dari sepuluh sesi yang masuk
 * justru berhasil.
 */
export default function ResultDashboard({ status }) {
  const attempts = status.totalRequests || 0
  const rejected = status.failedRejected || 0
  const success = status.successRequests || 0
  const outOfStock = status.failedOutOfStock || 0
  const paymentFailed = status.failedPayment || 0
  const abandoned = status.abandoned || 0
  const gaveUp = status.gaveUp || 0

  const availableTickets = status.availableTickets ?? 0
  const totalTickets = status.totalTickets || 0
  const sold = Math.max(0, totalTickets - availableTickets)

  // Sesi yang berhasil merebut slot. Sisanya tidak pernah sampai ke langkah membeli.
  const admitted = Math.max(0, attempts - rejected)
  const settled = success + outOfStock + paymentFailed + abandoned
  // Pembeli yang berangkat: yang kebagian slot ditambah yang pulang tanpa pernah kebagian.
  const buyers = admitted + gaveUp

  const ofAttempts = (n) => (attempts > 0 ? (n / attempts) * 100 : 0)
  const ofAdmitted = (n) => (settled > 0 ? (n / settled) * 100 : 0)
  const fmt = (n) => n.toLocaleString('id-ID')

  return (
    <>
      <div className="metrics">
        <div className="metric">
          <span className="k">Percobaan</span>
          <span className="v">{fmt(attempts)}</span>
          <span className="h">termasuk percobaan ulang</span>
        </div>
        <div className="metric">
          <span className="k">Ditolak</span>
          <span className="v" style={{ color: 'var(--crit)' }}>{fmt(rejected)}</span>
          <span className="h">{ofAttempts(rejected).toFixed(1)}% dari percobaan</span>
        </div>
        <div className="metric">
          <span className="k">Sesi masuk</span>
          <span className="v" style={{ color: 'var(--accent-ink)' }}>{fmt(admitted)}</span>
          <span className="h">dari {fmt(buyers)} pembeli</span>
        </div>
        <div className="metric">
          <span className="k">Menyerah</span>
          <span className="v" style={{ color: 'var(--crit)' }}>{fmt(gaveUp)}</span>
          <span className="h">jatah percobaan habis</span>
        </div>
        <div className="metric">
          <span className="k">Tiket terbit</span>
          <span className="v" style={{ color: 'var(--good)' }}>{fmt(success)}</span>
          <span className="h">{ofAdmitted(success).toFixed(1)}% dari sesi masuk</span>
        </div>
        <div className="metric">
          <span className="k">Terjual</span>
          <span className="v">{fmt(sold)}</span>
          <span className="h">dari kapasitas {fmt(totalTickets)}</span>
        </div>
      </div>

      <div className="duo">
        <section className="panel">
          <div className="phead">
            <span className="ptitle">Nasib Pembeli</span>
            <span className="pnote">{fmt(buyers)} pembeli &middot; {fmt(attempts)} percobaan</span>
          </div>
          <div className="body" style={{ padding: '11px 13px' }}>
            <div className="breakdown">
              <i style={{ width: `${buyers > 0 ? (admitted / buyers) * 100 : 0}%`, background: 'var(--accent)' }}
                 title={`Dapat slot ${fmt(admitted)}`} />
              <i style={{ width: `${buyers > 0 ? (gaveUp / buyers) * 100 : 0}%`, background: 'var(--crit)' }}
                 title={`Menyerah ${fmt(gaveUp)}`} />
            </div>
            <div className="legend" style={{ padding: '9px 0 0', borderTop: 0 }}>
              <span className="lg"><i style={{ background: 'var(--accent)' }} />Dapat slot <em>{fmt(admitted)}</em></span>
              <span className="lg"><i style={{ background: 'var(--crit)' }} />Menyerah <em>{fmt(gaveUp)}</em></span>
            </div>
            <p className="hint" style={{ marginTop: 4 }}>
              {fmt(rejected)} penolakan dari {fmt(attempts)} percobaan. Satu pembeli bisa ditolak
              berkali-kali sebelum jatah percobaannya habis.
            </p>
          </div>
        </section>

        <section className="panel">
          <div className="phead">
            <span className="ptitle">Nasib Sesi yang Masuk</span>
            <span className="pnote">{fmt(availableTickets)} / {fmt(totalTickets)} tiket tersisa</span>
          </div>
          <div className="body" style={{ padding: '11px 13px' }}>
            <div className="breakdown">
              <i style={{ width: `${ofAdmitted(success)}%`, background: 'var(--good)' }}
                 title={`Tiket terbit ${ofAdmitted(success).toFixed(1)}%`} />
              <i style={{ width: `${ofAdmitted(paymentFailed)}%`, background: 'var(--hot)' }}
                 title={`Bayar gagal ${ofAdmitted(paymentFailed).toFixed(1)}%`} />
              <i style={{ width: `${ofAdmitted(outOfStock)}%`, background: 'var(--warn)' }}
                 title={`Stok habis ${ofAdmitted(outOfStock).toFixed(1)}%`} />
              <i style={{ width: `${ofAdmitted(abandoned)}%`, background: 'var(--text-faint)' }}
                 title={`Ditinggalkan ${ofAdmitted(abandoned).toFixed(1)}%`} />
            </div>
            <div className="legend" style={{ padding: '9px 0 0', borderTop: 0 }}>
              <span className="lg"><i style={{ background: 'var(--good)' }} />Tiket terbit <em>{fmt(success)}</em></span>
              <span className="lg"><i style={{ background: 'var(--hot)' }} />Bayar gagal <em>{fmt(paymentFailed)}</em></span>
              <span className="lg"><i style={{ background: 'var(--warn)' }} />Stok habis <em>{fmt(outOfStock)}</em></span>
              <span className="lg"><i style={{ background: 'var(--text-faint)' }} />Ditinggalkan <em>{fmt(abandoned)}</em></span>
            </div>
            {settled < admitted && (
              <p className="hint" style={{ marginTop: 4 }}>
                {fmt(admitted - settled)} sesi masih berjalan.
              </p>
            )}
          </div>
        </section>
      </div>
    </>
  )
}
