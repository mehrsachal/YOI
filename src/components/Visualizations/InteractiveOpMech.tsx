import { useState, useEffect } from 'react';

export default function InteractiveOpMech() {
  const [mech, setMech] = useState<'blowback' | 'delayed' | 'gas'>('delayed');
  const [firing, setFiring] = useState(false);
  const [cycle, setCycle] = useState(0); // 0 = ready, 1 = fired, 2 = recoiling, 3 = returning

  useEffect(() => {
    if (firing) {
      setCycle(1); // Fire (gas expands)
      const t1 = setTimeout(() => setCycle(2), 200); // Recoil
      const t2 = setTimeout(() => setCycle(3), 800); // Return
      const t3 = setTimeout(() => {
        setCycle(0);
        setFiring(false);
      }, 1200); // Ready
      return () => { clearTimeout(t1); clearTimeout(t2); clearTimeout(t3); };
    }
  }, [firing]);

  return (
    <div className="glass-card" style={{ padding: 'var(--space-xl)', overflow: 'hidden' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-lg)', flexWrap: 'wrap', gap: 'var(--space-md)' }}>
        <div>
          <h3 style={{ fontSize: '1.25rem', color: 'var(--accent-purple)', marginBottom: 4 }}>Operating Mechanisms</h3>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Interactive breakdown of how different weapons cycle.</p>
        </div>
        
        <div style={{ display: 'flex', gap: 'var(--space-sm)' }}>
          <button className={`btn btn-sm ${mech === 'blowback' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setMech('blowback')}>Simple Blowback</button>
          <button className={`btn btn-sm ${mech === 'delayed' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setMech('delayed')}>Delayed (Roller)</button>
          <button className={`btn btn-sm ${mech === 'gas' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setMech('gas')}>Gas Operated</button>
        </div>
      </div>

      <div style={{ background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-xl)', border: '1px solid var(--border-medium)', position: 'relative', height: 240, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        
        <svg width="600" height="150" viewBox="0 0 600 150">
          <defs>
            <linearGradient id="gasGlow" x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%" stopColor="var(--accent-red)" stopOpacity="0.8" />
              <stop offset="100%" stopColor="var(--accent-amber)" stopOpacity="0" />
            </linearGradient>
          </defs>

          {/* Barrel */}
          <rect x="50" y="60" width="300" height="30" fill="var(--border-medium)" stroke="var(--border-dim)" />
          {/* Chamber */}
          <rect x="50" y="55" width="80" height="40" fill="var(--bg-secondary)" stroke="var(--text-muted)" />
          
          {/* Bullet */}
          <path d={`M ${cycle > 0 ? (cycle === 1 ? 150 : 600) : 100} 65 L ${cycle > 0 ? (cycle === 1 ? 180 : 630) : 130} 75 L ${cycle > 0 ? (cycle === 1 ? 150 : 600) : 100} 85 Z`} fill="var(--accent-amber)" style={{ transition: cycle === 1 ? 'all 0.1s linear' : cycle === 2 ? 'all 0.3s ease-out' : 'none', opacity: cycle === 0 ? 1 : cycle === 1 ? 1 : 0 }} />
          
          {/* Firing Gas */}
          {cycle === 1 && <rect x="50" y="62" width="100" height="26" fill="url(#gasGlow)" />}

          {/* Mechanisms */}
          {mech === 'blowback' && (
            <g transform={`translate(${cycle === 2 ? 150 : cycle === 3 ? 0 : 0}, 0)`} style={{ transition: cycle === 2 ? 'transform 0.4s ease-out' : cycle === 3 ? 'transform 0.3s ease-in' : 'none' }}>
              {/* Heavy Bolt */}
              <rect x="130" y="50" width="120" height="50" fill="var(--text-secondary)" rx="4" />
              <text x="190" y="80" fill="var(--bg-primary)" fontSize="12" textAnchor="middle" fontWeight="bold">HEAVY BOLT</text>
              {/* Recoil Spring */}
              <path d="M 250 75 Q 260 60 270 75 T 290 75 T 310 75 T 330 75" fill="none" stroke="var(--accent-cyan)" strokeWidth="4" />
            </g>
          )}

          {mech === 'delayed' && (
            <g transform={`translate(${cycle === 2 ? 150 : cycle === 3 ? 0 : 0}, 0)`} style={{ transition: cycle === 2 ? 'transform 0.4s ease-out' : cycle === 3 ? 'transform 0.3s ease-in' : 'none' }}>
              {/* Lighter Bolt Carrier */}
              <rect x="130" y="50" width="100" height="50" fill="var(--text-secondary)" rx="4" />
              {/* Rollers (expanding out if not recoiling) */}
              <circle cx="140" cy={cycle === 0 || cycle === 1 ? 45 : 60} r="8" fill="var(--accent-purple)" style={{ transition: 'all 0.2s' }} />
              <circle cx="140" cy={cycle === 0 || cycle === 1 ? 105 : 90} r="8" fill="var(--accent-purple)" style={{ transition: 'all 0.2s' }} />
              {/* Locking Piece */}
              <polygon points="140,60 160,75 140,90" fill="var(--accent-green)" />
              <text x="180" y="80" fill="var(--bg-primary)" fontSize="10" textAnchor="middle" fontWeight="bold">G3/MP5 BOLT</text>
            </g>
          )}

          {mech === 'gas' && (
            <g>
              {/* Gas Port & Tube */}
              <path d="M 280 60 L 280 30 L 180 30 L 180 50" fill="none" stroke="var(--border-medium)" strokeWidth="10" />
              {/* Gas flowing into tube */}
              {(cycle === 1 || cycle === 2) && <path d="M 280 60 L 280 30 L 200 30" fill="none" stroke="var(--accent-red)" strokeWidth="6" strokeDasharray="5,5" className="trajectory-path" />}
              
              <g transform={`translate(${cycle === 2 ? 150 : cycle === 3 ? 0 : 0}, 0)`} style={{ transition: cycle === 2 ? 'transform 0.4s ease-out' : cycle === 3 ? 'transform 0.3s ease-in' : 'none' }}>
                {/* Piston */}
                <rect x="130" y="25" width="50" height="10" fill="var(--accent-green)" />
                <rect x="130" y="30" width="10" height="25" fill="var(--accent-green)" />
                {/* Bolt Carrier */}
                <rect x="130" y="55" width="80" height="40" fill="var(--text-secondary)" rx="2" />
                <text x="170" y="80" fill="var(--bg-primary)" fontSize="10" textAnchor="middle" fontWeight="bold">CARRIER</text>
              </g>
            </g>
          )}
        </svg>

        <button 
          className="btn btn-primary btn-lg" 
          style={{ position: 'absolute', bottom: 'var(--space-lg)', right: 'var(--space-lg)' }}
          onClick={() => setFiring(true)}
          disabled={firing}
        >
          {firing ? 'Cycling...' : 'Fire Weapon'}
        </button>
      </div>

      <div style={{ marginTop: 'var(--space-md)', padding: 'var(--space-md)', background: 'rgba(168, 85, 247, 0.05)', borderRadius: 'var(--radius-md)', border: '1px solid rgba(168, 85, 247, 0.2)' }}>
        {mech === 'blowback' && <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', lineHeight: 1.6 }}><strong>Simple Blowback:</strong> Gas pressure forces the heavy bolt rearward immediately. The mass of the bolt and strength of the recoil spring delay opening just enough for the bullet to leave the barrel.</p>}
        {mech === 'delayed' && <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', lineHeight: 1.6 }}><strong>Delayed Blowback (Roller):</strong> Used in <span style={{color:'var(--accent-green)'}}>G3A3</span> and <span style={{color:'var(--accent-green)'}}>MP5A2</span>. The bolt is initially locked by rollers pushed into the barrel extension. Gas pressure must force the locking piece rearward, pulling the rollers INWARD, before the bolt can travel back. This allows a lighter bolt mass.</p>}
        {mech === 'gas' && <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', lineHeight: 1.6 }}><strong>Gas Operated:</strong> Used in <span style={{color:'var(--accent-green)'}}>SMG Chinese (AK)</span>. Gas is bled from a port in the barrel to drive a piston rearward. The piston pushes the bolt carrier, unlocking the bolt and cycling the weapon. Highly reliable.</p>}
      </div>
    </div>
  );
}
