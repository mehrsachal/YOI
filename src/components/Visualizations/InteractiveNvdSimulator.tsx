import { useState } from 'react';

export default function InteractiveNvdSimulator() {
  const [distance, setDistance] = useState(100); // 10m to 2500m
  const [lightLevel, setLightLevel] = useState(50); // 0 (Starlight) to 100 (Full Moon)
  const [mode, setMode] = useState<'i2' | 'thermal'>('i2'); // Image Intensifier vs Thermal

  // NVD Math:
  // AN/PVS-5A (I2): Needs some ambient light. Range drops to 50m in starlight, 150m in moonlight.
  // SKUA-LR (Thermal): Doesn't need ambient light. Detects man at 2.5km.

  const getVisibility = () => {
    if (mode === 'thermal') {
      if (distance <= 2500) return 1; // Fully visible
      return Math.max(0, 1 - ((distance - 2500) / 1000)); // Fades out
    } else {
      // I2 depends on light
      const maxRange = 50 + (lightLevel / 100) * 100; // 50m to 150m max
      if (distance <= maxRange) return 1;
      return Math.max(0, 1 - ((distance - maxRange) / 50));
    }
  };

  const visibility = getVisibility();

  return (
    <div className="glass-card" style={{ padding: 'var(--space-xl)', overflow: 'hidden' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-lg)', flexWrap: 'wrap', gap: 'var(--space-md)' }}>
        <div>
          <h3 style={{ fontSize: '1.25rem', color: mode === 'thermal' ? '#f97316' : '#22c55e', marginBottom: 4 }}>
            Night Vision Simulator: {mode === 'thermal' ? 'SKUA-LR Thermal' : 'AN/PVS-5A (I²)'}
          </h3>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Visualize detection range limits under different conditions.</p>
        </div>
        
        <div style={{ display: 'flex', gap: 'var(--space-sm)' }}>
          <button className={`btn btn-sm ${mode === 'i2' ? 'btn-primary' : 'btn-secondary'}`} style={mode === 'i2' ? {background: '#22c55e', color: '#000'} : {}} onClick={() => setMode('i2')}>Image Intensifier (I²)</button>
          <button className={`btn btn-sm ${mode === 'thermal' ? 'btn-primary' : 'btn-secondary'}`} style={mode === 'thermal' ? {background: '#f97316', color: '#000'} : {}} onClick={() => setMode('thermal')}>Thermal Imaging</button>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 'var(--space-md)', marginBottom: 'var(--space-lg)' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Target Distance (m)</label>
          <input type="range" min="10" max="3000" step="50" value={distance} onChange={(e) => setDistance(Number(e.target.value))} />
          <span style={{ fontSize: '0.85rem', fontFamily: 'var(--font-mono)' }}>{distance}m</span>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Ambient Light (Moonlight)</label>
          <input type="range" min="0" max="100" step="10" value={lightLevel} onChange={(e) => setLightLevel(Number(e.target.value))} disabled={mode === 'thermal'} style={{ opacity: mode === 'thermal' ? 0.3 : 1 }} />
          <span style={{ fontSize: '0.85rem', fontFamily: 'var(--font-mono)' }}>{lightLevel}% {mode === 'thermal' ? '(N/A for Thermal)' : ''}</span>
        </div>
      </div>

      <div style={{ 
        background: '#000', 
        borderRadius: '50%', 
        border: `4px solid ${mode === 'thermal' ? '#f97316' : '#22c55e'}`, 
        position: 'relative', 
        width: 300, height: 300, 
        margin: '0 auto',
        overflow: 'hidden',
        boxShadow: `0 0 40px ${mode === 'thermal' ? 'rgba(249, 115, 22, 0.2)' : 'rgba(34, 197, 94, 0.2)'}`
      }}>
        {/* Phosphor noise overlay */}
        <div style={{ 
          position: 'absolute', inset: 0, 
          background: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E")`, 
          opacity: mode === 'thermal' ? 0.1 : 0.4 + (1 - lightLevel/100) * 0.4,
          mixBlendMode: mode === 'thermal' ? 'screen' : 'overlay',
          pointerEvents: 'none'
        }} />

        {/* Viewport tint */}
        <div style={{ position: 'absolute', inset: 0, background: mode === 'thermal' ? '#000' : '#052e16', mixBlendMode: 'multiply' }} />
        {mode === 'i2' && <div style={{ position: 'absolute', inset: 0, background: '#22c55e', mixBlendMode: 'screen', opacity: 0.2 + (lightLevel/100)*0.2 }} />}

        {/* Reticle */}
        <div style={{ position: 'absolute', top: '50%', left: '10%', right: '10%', height: 1, background: mode === 'thermal' ? 'rgba(255,255,255,0.5)' : 'rgba(0,0,0,0.5)' }} />
        <div style={{ position: 'absolute', left: '50%', top: '10%', bottom: '10%', width: 1, background: mode === 'thermal' ? 'rgba(255,255,255,0.5)' : 'rgba(0,0,0,0.5)' }} />

        {/* Target */}
        <div style={{
          position: 'absolute',
          top: '50%', left: '50%',
          width: Math.max(5, 100 - distance/30),
          height: Math.max(10, 200 - distance/15),
          background: mode === 'thermal' ? '#fff' : '#000',
          transform: 'translate(-50%, -50%)',
          opacity: visibility,
          filter: mode === 'thermal' ? 'blur(2px)' : `blur(${distance > 100 ? 2 : 0}px)`,
          borderRadius: '40% 40% 10% 10%'
        }} />
      </div>

      <div style={{ marginTop: 'var(--space-md)', textAlign: 'center', color: visibility > 0.5 ? (mode === 'thermal' ? '#f97316' : '#22c55e') : 'var(--accent-red)', fontWeight: 'bold' }}>
        {visibility > 0.8 ? 'Target Clearly Visible' : visibility > 0.1 ? 'Target Barely Detectable' : 'Target Undetectable'}
      </div>
    </div>
  );
}
