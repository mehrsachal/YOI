import { useState } from 'react';

export default function InteractiveBeatenZone() {
  const [range, setRange] = useState(500); // 500m to 2000m
  
  // Dimensions based on SAC-81 (approximate linear interpolation)
  // 500m: 150m x 2m
  // 1000m: 70m x 4m
  // 2000m: 45m x 8m
  
  const getDimensions = (r: number) => {
    if (r <= 500) return { length: 150, width: 2 };
    if (r <= 600) return { length: 135, width: 2.2 };
    if (r <= 700) return { length: 120, width: 2.5 };
    if (r <= 800) return { length: 100, width: 3 };
    if (r <= 1000) return { length: 70, width: 4 };
    if (r <= 1500) return { length: 55, width: 6 };
    return { length: 45, width: 8 };
  };

  const dims = getDimensions(range);
  
  // Scale for visual representation
  // Length max is 150 (scale down to fit 400px width) -> scale factor 2.5
  // Width max is 8 (scale up to be visible, say factor 10)
  const visualLength = dims.length * 2.5;
  const visualWidth = dims.width * 15;

  return (
    <div className="glass-card" style={{ padding: 'var(--space-xl)', overflow: 'hidden' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-lg)', flexWrap: 'wrap', gap: 'var(--space-md)' }}>
        <div>
          <h3 style={{ fontSize: '1.25rem', color: 'var(--accent-amber)', marginBottom: 4 }}>Beaten Zone Simulator</h3>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>See how the cone of fire changes shape across distances.</p>
        </div>
        
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Target Range (m)</label>
          <input type="range" min="500" max="2000" step="100" value={range} onChange={(e) => setRange(Number(e.target.value))} />
          <span style={{ fontSize: '0.85rem', fontFamily: 'var(--font-mono)' }}>{range}m</span>
        </div>
      </div>

      <div style={{ background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-xl)', border: '1px solid var(--border-medium)', position: 'relative', height: 260, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
        
        {/* Top-down View of Beaten Zone */}
        <div style={{ position: 'relative', width: 400, height: 160, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          {/* Grid */}
          <div style={{ position: 'absolute', inset: 0, backgroundImage: 'radial-gradient(var(--border-medium) 1px, transparent 1px)', backgroundSize: '20px 20px', opacity: 0.3 }} />
          
          {/* The Ellipse */}
          <div style={{
            width: visualLength,
            height: visualWidth,
            background: 'radial-gradient(ellipse, rgba(245, 158, 11, 0.8) 0%, rgba(245, 158, 11, 0.2) 70%, transparent 100%)',
            border: '1px dashed rgba(245, 158, 11, 0.5)',
            borderRadius: '50%',
            transition: 'all 0.5s cubic-bezier(0.4, 0, 0.2, 1)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            position: 'relative'
          }}>
            <div style={{ width: 4, height: 4, background: 'var(--text-primary)', borderRadius: '50%', boxShadow: '0 0 10px rgba(255,255,255,0.5)' }} />
            
            {/* Dimension Lines */}
            <div style={{ position: 'absolute', top: -25, width: '100%', borderTop: '1px solid var(--text-muted)', textAlign: 'center' }}>
              <div style={{ position: 'absolute', top: -5, left: 0, width: 1, height: 10, background: 'var(--text-muted)' }} />
              <div style={{ position: 'absolute', top: -5, right: 0, width: 1, height: 10, background: 'var(--text-muted)' }} />
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', background: 'var(--bg-tertiary)', padding: '0 4px', position: 'relative', top: -10 }}>{dims.length}m (L)</span>
            </div>
            
            <div style={{ position: 'absolute', right: -40, height: '100%', borderRight: '1px solid var(--text-muted)', display: 'flex', alignItems: 'center' }}>
              <div style={{ position: 'absolute', right: -5, top: 0, width: 10, height: 1, background: 'var(--text-muted)' }} />
              <div style={{ position: 'absolute', right: -5, bottom: 0, width: 10, height: 1, background: 'var(--text-muted)' }} />
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', background: 'var(--bg-tertiary)', padding: '4px 0', position: 'absolute', left: 10, whiteSpace: 'nowrap' }}>{dims.width}m (W)</span>
            </div>
          </div>
        </div>
      </div>

      <div style={{ marginTop: 'var(--space-md)', padding: 'var(--space-md)', background: 'rgba(245, 158, 11, 0.05)', borderRadius: 'var(--radius-md)', border: '1px solid rgba(245, 158, 11, 0.2)' }}>
        <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
          <strong>The Principle:</strong> As range increases from 500m to 2000m, the angle of fall becomes steeper. This steeper angle means the bullets hit the ground over a <em>shorter</em> distance (length decreases from 150m to 45m), but the natural dispersion of the cone of fire causes it to become <em>wider</em> (width increases from 2m to 8m).
        </p>
      </div>
    </div>
  );
}
