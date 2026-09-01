import React from 'react'
import SlotBoard from './SlotBoard'
import OccupancyTimeline from './OccupancyTimeline'
import ResourceTimeline from './ResourceTimeline'

export default function LiveMonitor({ status, logs }) {
  const totalPermits = status.totalPermits || 0
  const activePermits = status.activePermits || 0
  const slots = status.slots || []
  const rejected = status.failedRejected || 0
  const attempts = status.totalRequests || 0
  // Sesi yang berhasil masuk. Tanpa angka ini, jumlah penolakan terbaca seperti salah hitung:
  // seorang pembeli bisa ditolak berkali-kali sebelum menyerah.
  const admitted = Math.max(0, attempts - rejected)
  const full = totalPermits > 0 && activePermits >= totalPermits

  return (
    <>
      <div className="board">
        <section className="panel stage">
          <div className="phead">
            <span className="ptitle">Slot Konkurensi</span>
            <span className="pnote">
              {activePermits}/{totalPermits} terpakai
              {status.reportedBy ? ` · snapshot dari ${status.reportedBy}` : ''}
            </span>
          </div>
          <div className="body">
            <SlotBoard slots={slots} leaseMs={status.sessionLeaseMs} />
          </div>
        </section>

        <section className="panel stage">
          <div className="phead">
            <span className="ptitle">Ditolak</span>
          </div>
          <div className="body">
            <div>
              <div className="bignum" style={{ color: rejected > 0 ? 'var(--crit)' : 'var(--text-faint)' }}>
                {rejected.toLocaleString('id-ID')}
              </div>
              <div className="sub">
                percobaan ditolak dari {attempts.toLocaleString('id-ID')}
              </div>
            </div>

            <div>
              <div className={`gauge${full ? ' full' : ''}`}>
                <i style={{ width: `${totalPermits ? (activePermits / totalPermits) * 100 : 0}%` }} />
              </div>
              <div className="sub" style={{ marginTop: 4 }}>
                {full ? 'semua slot terpakai, pendatang ditolak' : 'okupansi slot'}
              </div>
            </div>

            <div className="outs" style={{ marginTop: 'auto' }}>
              <div className="out">
                <span className="k">Sesi masuk</span>
                <span className="n">{admitted.toLocaleString('id-ID')}</span>
              </div>
              <div className="out">
                <span className="k">Tiket tersisa</span>
                <span className="n">{status.availableTickets ?? 0}</span>
              </div>
              <div className="out">
                <span className="k">Kapasitas</span>
                <span className="n">{status.totalTickets || 0}</span>
              </div>
            </div>
          </div>
        </section>
      </div>

      {/* Papan slot menjawab "sekarang bagaimana"; grafik ini menjawab "tadi bagaimana". */}
      <div className="duo">
        <OccupancyTimeline status={status} />
        {/* Dan yang ini menjawab "berapa mahal" — hal yang justru dilindungi batas konkurensi. */}
        <ResourceTimeline instances={status.instances} />
      </div>

      <section className="panel">
        <div className="phead">
          <span className="ptitle">Aliran Peristiwa</span>
          <span className="pnote">{logs.length} baris terakhir</span>
        </div>
        <div className="log">
          {logs.length === 0 ? (
            <div style={{ color: 'var(--text-faint)' }}>Menunggu aktivitas simulasi...</div>
          ) : (
            logs.map((item, index) => (
              <div key={index}>
                <span className="t">{item.time}</span>
                <span
                  className={
                    item.text.includes('berhasil')
                      ? 'ok'
                      : item.text.includes('gagal') || item.text.includes('tidak menjawab')
                      ? 'bad'
                      : ''
                  }
                >
                  {item.text}
                </span>
              </div>
            ))
          )}
        </div>
      </section>
    </>
  )
}
