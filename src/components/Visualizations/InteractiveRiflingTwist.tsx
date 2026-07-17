import { useState } from 'react';

export default function InteractiveRiflingTwist() {
  const [twist, setTwist] = useState(12); // Twist rate in inches
  const [bulletPos, setBulletPos] = useState(0);

  // Math for rotation: 
  // 1 full turn every `twist` inches.
  // Let's say barrel is 24 inches long.
  // Total turns = 24 / twist.
  // We map bulletPos (0 to 100) to the length of the barrel.
  const barrelLength = 24; // inches
  const currentPosInches = (bulletPos / 100) * barrelLength;
  const turns = currentPosInches / twist;
  const rotation = turns * 360;

  return (
    <div className="glass-card" style={{ padding: 'var(--space-xl)', overflow: 'hidden' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-lg)', flexWrap: 'wrap', gap: 'var(--space-md)' }}>
        <div>
          <h3 style={{ fontSize: '1.25rem', color: 'var(--accent-purple)', marginBottom: 4 }}>Rifling Twist Simulator</h3>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Visualize how twist rate (1-in-X inches) affects projectile rotation.</p>
        </div>
        
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Twist Rate (1-in-X inches)</label>
          <input type="range" min="7" max="15" step="1" value={twist} onChange={(e) => setTwist(Number(e.target.value))} />
          <span style={{ fontSize: '0.85rem', fontFamily: 'var(--font-mono)' }}>1 turn every {twist}"</span>
        </div>
      </div>

      <div style={{ background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-xl)', border: '1px solid var(--border-medium)', position: 'relative', height: 260, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
        
        {/* Barrel Cross-section SVG */}
        <svg width="600" height="150" viewBox="0 0 600 150">
          <defs>
            <linearGradient id="metal" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#444" />
              <stop offset="50%" stopColor="#888" />
              <stop offset="100%" stopColor="#222" />
            </linearGradient>
            <linearGradient id="bulletColor" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#d4af37" />
              <stop offset="100%" stopColor="#996515" />
            </linearGradient>
          </defs>

          {/* Barrel Exterior */}
          <rect x="50" y="40" width="500" height="70" fill="url(#metal)" stroke="#111" />
          
          {/* Rifling Lines (Perspective trick) */}
          <g opacity="0.3">
            {[...Array(5)].map((_, i) => (
              <path key={i} d={`M 50 ${45 + i*15} Q 300 ${45 + i*15 + (12/twist)*30} 550 ${45 + i*15}`} fill="none" stroke="#000" strokeWidth="2" />
            ))}
          </g>

          {/* Bullet */}
          <g transform={`translate(${50 + (bulletPos/100)*450}, 75)`}>
            <g transform={`rotate(${rotation})`}>
              {/* Bullet Shape */}
              <path d="M -20 -15 L 10 -15 Q 30 -15 40 0 Q 30 15 10 15 L -20 15 Z" fill="url(#bulletColor)" />
              {/* Rifling Engraving Marks on Bullet */}
              <line x1="-15" y1="-15" x2="5" y2="15" stroke="#774505" strokeWidth="2" />
              <line x1="0" y1="-15" x2="20" y2="15" stroke="#774505" strokeWidth="2" />
            </g>
          </g>
        </svg>

        <div style={{ width: '80%', marginTop: 'var(--space-lg)' }}>
          <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', display: 'flex', justifyContent: 'space-between' }}>
            <span>Breech</span>
            <span>Muzzle (24" Barrel)</span>
          </label>
          <input type="range" min="0" max="100" value={bulletPos} onChange={(e) => setBulletPos(Number(e.target.value))} style={{ width: '100%' }} />
        </div>
      </div>

      <div style={{ marginTop: 'var(--space-md)', padding: 'var(--space-md)', background: 'rgba(168, 85, 247, 0.05)', borderRadius: 'var(--radius-md)', border: '1px solid rgba(168, 85, 247, 0.2)' }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 'var(--space-md)' }}>
          <div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Rotations in 24" Barrel</div>
            <div style={{ fontSize: '1.5rem', fontFamily: 'var(--font-mono)', color: 'var(--accent-purple)' }}>{turns.toFixed(2)} turns</div>
          </div>
          <div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Gyroscopic Stability</div>
            <div style={{ fontSize: '1.5rem', fontFamily: 'var(--font-heading)', color: twist <= 9 ? 'var(--accent-green)' : twist >= 12 ? 'var(--accent-amber)' : 'var(--text-primary)' }}>
              {twist <= 9 ? 'High (Heavy Bullets)' : twist >= 12 ? 'Low (Light Bullets)' : 'Medium'}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
