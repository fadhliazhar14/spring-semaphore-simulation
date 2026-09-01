import React from 'react'

// Field yang isinya teks bebas; sisanya dipaksa menjadi angka positif.
const TEXT_FIELDS = new Set(['eventName', 'backends'])

// Satu sesi menempuh tiga langkah: pilih, bayar, terbit.
const STEPS_PER_SESSION = 3

/**
 * Memperkirakan berapa pembeli yang akan kebagian slot, sebelum simulasi dijalankan.
 *
 * Ini pertanyaan yang paling sering muncul saat melihat papan penuh penolakan: kenapa slot yang
 * sudah kosong tidak diisi pembeli lain. Jawabannya selalu perbandingan dua durasi — berapa lama
 * satu sesi menahan slot, dan berapa lama seorang pembeli bersedia terus mencoba. Kalau jatah
 * bertahan lebih pendek daripada satu sesi, semua pembeli sudah menyerah sebelum slot pertama
 * bebas, dan gelombang berikutnya tidak pernah ada.
 */
function forecast(config) {
  const sessionMs = STEPS_PER_SESSION * (config.slowMotion ? config.thinkTimeMs : 1)
  const budgetMs = config.maxAttempts * config.retryDelayMs
  const waves = 1 + Math.floor(budgetMs / Math.max(1, sessionMs))
  const reachable = Math.min(config.requestCount, config.semaphorePermits * waves)
  const drainMs = (config.requestCount / Math.max(1, config.semaphorePermits)) * sessionMs
  return { sessionMs, budgetMs, reachable, drainMs }
}

const asSeconds = (ms) => `${(ms / 1000).toFixed(1)} s`

export default function ControlPanel({
  config,
  setConfig,
  onInit,
  onStartTraffic,
  isInitializing,
  isRunning,
  currentEventId,
  instances = [],
  onInjectWave,
  onRestock
}) {
  const handleChange = (e) => {
    const { name, value } = e.target
    setConfig((prev) => ({
      ...prev,
      [name]: TEXT_FIELDS.has(name) ? value : Math.max(1, parseInt(value) || 1)
    }))
  }

  const { sessionMs, budgetMs, reachable, drainMs } = forecast(config)
  const starved = reachable < config.requestCount

  return (
    <section className="panel">
      <div className="phead">
        <span className="ptitle">Kendali Simulasi</span>
        <span className="pnote">
          {currentEventId ? `event #${currentEventId} siap` : 'belum diinisialisasi'}
        </span>
      </div>

      {/* Knob kecepatan. Waktu berpikir pembeli antar langkah — itu pula yang memperlambat
          simulasi supaya bisa diamati, jadi namanya disebut apa adanya, bukan "delay proses". */}
      <div className="speedrow">
        <label className="speedtoggle">
          <input
            type="checkbox"
            checked={config.slowMotion}
            onChange={(e) => setConfig((prev) => ({ ...prev, slowMotion: e.target.checked }))}
            disabled={isRunning}
          />
          <span>Perlambat simulasi</span>
        </label>

        <input
          type="range"
          name="thinkTimeMs"
          min="0"
          max="3000"
          step="50"
          value={config.thinkTimeMs}
          onChange={handleChange}
          disabled={isRunning || !config.slowMotion}
        />
        <span className="speedval">
          {config.slowMotion ? `${config.thinkTimeMs} ms` : 'secepat mungkin'}
        </span>
        <span className="hint">waktu berpikir pembeli antar langkah</span>
      </div>

      <div className="rail">
        <div className="fields">
          <div className="field wide">
            <label htmlFor="eventName">Nama event</label>
            <input
              id="eventName"
              type="text"
              name="eventName"
              value={config.eventName}
              onChange={handleChange}
              disabled={isRunning}
            />
          </div>

          <div className="field">
            <label htmlFor="semaphorePermits">Permit slot</label>
            <input
              id="semaphorePermits"
              type="number"
              name="semaphorePermits"
              value={config.semaphorePermits}
              onChange={handleChange}
              min="1"
              disabled={isRunning}
            />
          </div>

          <div className="field">
            <label htmlFor="totalTickets">Stok tiket</label>
            <input
              id="totalTickets"
              type="number"
              name="totalTickets"
              value={config.totalTickets}
              onChange={handleChange}
              min="1"
              disabled={isRunning}
            />
          </div>

          <div className="field">
            <label htmlFor="requestCount">Jumlah pembeli</label>
            <input
              id="requestCount"
              type="number"
              name="requestCount"
              value={config.requestCount}
              onChange={handleChange}
              min="1"
              disabled={isRunning}
            />
          </div>

          <div className="field">
            <label htmlFor="maxAttempts">Coba ulang maks (kali)</label>
            <input
              id="maxAttempts"
              type="number"
              name="maxAttempts"
              value={config.maxAttempts}
              onChange={handleChange}
              min="1"
              disabled={isRunning}
            />
          </div>

          <div className="field">
            <label htmlFor="retryDelayMs">Jeda coba ulang (ms)</label>
            <input
              id="retryDelayMs"
              type="number"
              name="retryDelayMs"
              value={config.retryDelayMs}
              onChange={handleChange}
              min="1"
              disabled={isRunning}
            />
          </div>

          <div className="field">
            <label htmlFor="paymentSuccessPercent">Peluang bayar sukses (%)</label>
            <input
              id="paymentSuccessPercent"
              type="number"
              name="paymentSuccessPercent"
              value={config.paymentSuccessPercent}
              onChange={handleChange}
              min="0"
              max="100"
              disabled={isRunning}
            />
          </div>

          <div className="field wide">
            <label htmlFor="backends">Instance back-end (dipisah koma)</label>
            <input
              id="backends"
              type="text"
              name="backends"
              value={config.backends}
              onChange={handleChange}
              disabled={isRunning}
            />
          </div>

          <div className="field wide">
            <label>Terdeteksi hidup</label>
            <div className="insts">
              {instances.length === 0 ? (
                <span className="hint">belum ada detak jantung</span>
              ) : (
                instances.map((inst) => (
                  <span
                    key={inst.id}
                    className={`inst ${inst.alive ? 'alive' : 'dead'}`}
                    title={inst.alive ? 'berdetak' : `diam ${(inst.silentMs / 1000).toFixed(1)} detik`}
                  >
                    <i />
                    {inst.id}
                    {inst.self ? ' · papan' : ''}
                  </span>
                ))
              )}
            </div>
          </div>
        </div>

        <div className="transport">
          <div className="btnrow">
            <button onClick={onInit} disabled={isInitializing || isRunning}>
              {isInitializing ? 'Menyiapkan...' : 'Inisialisasi'}
            </button>
            <button
              className="primary"
              onClick={onStartTraffic}
              disabled={!currentEventId || isRunning}
              style={{ flex: 1 }}
            >
              Jalankan
            </button>
          </div>

          {/* Suntikan di tengah jalan. Sengaja tidak ikut terkunci oleh isRunning: seluruh
              gunanya justru menambah beban ketika simulasi sedang berjalan. */}
          <div className="wave">
            <div className="field">
              <label htmlFor="waveCount">Pembeli</label>
              <input
                id="waveCount"
                type="number"
                name="waveCount"
                value={config.waveCount}
                onChange={handleChange}
                min="1"
              />
            </div>
            <button className="wavebtn" onClick={onInjectWave} disabled={!currentEventId}>
              Bom gelombang
            </button>

            <div className="field">
              <label htmlFor="restockAmount">Stok</label>
              <input
                id="restockAmount"
                type="number"
                name="restockAmount"
                value={config.restockAmount}
                onChange={handleChange}
                min="1"
              />
            </div>
            <button className="stockbtn" onClick={onRestock} disabled={!currentEventId}>
              + Stok
            </button>
          </div>

          <span className="hint">
            Tidak ada antrean. Yang tidak kebagian slot ditolak seketika, lalu mencoba lagi.
          </span>

          <div className={`forecast${starved ? ' starved' : ''}`}>
            <span>satu sesi menahan slot <b>{asSeconds(sessionMs)}</b></span>
            <span>pembeli bertahan <b>{asSeconds(budgetMs)}</b></span>
            <span>
              perkiraan kebagian <b>{reachable}</b> dari {config.requestCount} pembeli
            </span>
            {starved && (
              <span className="why">
                Jatah bertahan lebih pendek daripada satu sesi, jadi semua yang ditolak sudah
                menyerah sebelum slot pertama bebas. Melayani semuanya butuh {asSeconds(drainMs)}
                {' '}— perbesar coba ulang, perpanjang jedanya, atau perpendek waktu berpikir.
              </span>
            )}
          </div>
        </div>
      </div>
    </section>
  )
}
