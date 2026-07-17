import { useState } from 'react';

export default function InteractiveTrajectory() {
  const [distance, setDistance] = useState(500); // 100 to 1000m
  const [wind, setWind] = useState(0); // -10 to +10 m/s
  const [gravity] = useState(9.8); // standard
  
  // Simulated physics based on the inputs
  const maxOrdinate = (distance / 1000) * (distance / 1000) * gravity * 12; // Exaggerated for visual effect
  const drift = (distance / 1000) * wind * 20;

  return (
    <div className="glass-card" style={{ padding: 'var(--space-xl)', overflow: 'hidden' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-lg)', flexWrap: 'wrap', gap: 'var(--space-md)' }}>
        <div>
          <h3 style={{ fontSize: '1.25rem', color: 'var(--accent-cyan)', marginBottom: 4 }}>External Ballistics Simulator</h3>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Adjust environmental variables to see effects on trajectory.</p>
        </div>
        
        <div style={{ display: 'flex', gap: 'var(--space-md)' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Target Distance (m)</label>
            <input type="range" min="100" max="1000" step="100" value={distance} onChange={(e) => setDistance(Number(e.target.value))} />
            <span style={{ fontSize: '0.85rem', fontFamily: 'var(--font-mono)' }}>{distance}m</span>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <label style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>Wind Speed (m/s)</label>
            <input type="range" min="-10" max="10" step="1" value={wind} onChange={(e) => setWind(Number(e.target.value))} />
            <span style={{ fontSize: '0.85rem', fontFamily: 'var(--font-mono)' }}>{wind > 0 ? `Right ${wind}` : wind < 0 ? `Left ${Math.abs(wind)}` : 'Calm'}</span>
          </div>
        </div>
      </div>

      {/* SVG Trajectory Visualization */}
      <div style={{ position: 'relative', width: '100%', height: '300px', background: 'rgba(241, 245, 249, 0.4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--border-dim)' }}>
        <svg width="100%" height="100%" viewBox="0 0 1000 300" preserveAspectRatio="none">
          {/* Grid lines */}
          <line x1="0" y1="250" x2="1000" y2="250" stroke="var(--border-medium)" strokeWidth="1" strokeDasharray="5,5" />
          <line x1="100" y1="0" x2="100" y2="300" stroke="var(--border-dim)" strokeWidth="1" />
          <line x1="500" y1="0" x2="500" y2="300" stroke="var(--border-dim)" strokeWidth="1" />
          
          {/* Line of sight (straight) */}
          <line x1="0" y1="200" x2={distance} y2="200" stroke="rgba(255, 255, 255, 0.2)" strokeWidth="2" strokeDasharray="10,5" />
          
          {/* Trajectory path (curved) */}
          <path 
            d={`M 0 200 Q ${distance/2} ${200 - maxOrdinate} ${distance} ${200 + (gravity > 0 ? (distance/1000)*50 : 0)}`} 
            fill="none" 
            stroke="var(--accent-cyan)" 
            strokeWidth="4" 
            className="trajectory-path"
          />

          {/* Wind Drift Projection (Top-down shadow effect) */}
          <path
            d={`M 0 250 Q ${distance/2} ${250 + drift/2} ${distance} ${250 + drift}`}
            fill="none"
            stroke="var(--accent-amber)"
            strokeWidth="2"
            opacity="0.5"
            strokeDasharray="4,4"
          />

          {/* Target */}
          <rect x={distance - 10} y={150} width="20" height="100" fill="var(--text-secondary)" opacity="0.8" rx="4" />
          <circle cx={distance} cy={200} r="6" fill="var(--accent-red)" />
          
          {/* Muzzle */}
          <circle cx="0" cy="200" r="8" fill="var(--text-primary)" />
          
          {/* Max Ordinate Marker */}
          {distance > 200 && (
            <g transform={`translate(${distance/2}, ${200 - maxOrdinate/2})`}>
              <circle cx="0" cy={-(maxOrdinate/2)} r="4" fill="var(--accent-green)" />
              <line x1="0" y1={-(maxOrdinate/2)} x2="0" y2="0" stroke="var(--accent-green)" strokeWidth="1" strokeDasharray="2,2" />
              <text x="0" y={-(maxOrdinate/2) - 10} fill="var(--accent-green)" fontSize="12" textAnchor="middle" fontFamily="var(--font-mono)">Max Ordinate</text>
            </g>
          )}

          {/* Impact Point */}
          <circle cx={distance} cy={200 + (gravity > 0 ? (distance/1000)*50 : 0)} r="5" fill="var(--accent-cyan)" />
        </svg>

        <div style={{ position: 'absolute', bottom: 10, left: 10, fontSize: '0.75rem', color: 'var(--text-muted)', fontFamily: 'var(--font-mono)' }}>
          <span style={{ color: 'var(--accent-cyan)' }}>— Trajectory</span> &nbsp;|&nbsp; 
          <span style={{ color: 'var(--accent-amber)' }}>- - Wind Drift (Top View)</span> &nbsp;|&nbsp; 
          <span style={{ color: 'rgba(255, 255, 255, 0.4)' }}>- - Line of Sight</span>
        </div>
      </div>
      
      {/* Explanation output */}
      <div style={{ marginTop: 'var(--space-md)', padding: 'var(--space-md)', background: 'rgba(34, 211, 238, 0.05)', borderRadius: 'var(--radius-md)', border: '1px solid rgba(34, 211, 238, 0.2)' }}>
        <h4 style={{ fontSize: '0.9rem', color: 'var(--accent-cyan)', marginBottom: 4 }}>Terminal Ballistics Report</h4>
        <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', lineHeight: 1.6 }}>
          At <strong style={{color:'var(--text-primary)'}}>{distance}m</strong>, the bullet experiences a drop from the line of sight (falling branch). 
          The highest point above line of sight (Max Ordinate) occurs around <strong style={{color:'var(--accent-green)'}}>{Math.round(distance/2)}m</strong>.
          {wind !== 0 && <span> The <strong style={{color:'var(--accent-amber)'}}>{wind} m/s wind</strong> causes a lateral drift of {Math.round(Math.abs(drift))}cm to the {wind > 0 ? 'right' : 'left'}.</span>}
        </p>
      </div>

      <style>{`
        .trajectory-path {
          stroke-dasharray: 2000;
          stroke-dashoffset: 2000;
          animation: drawPath 2s ease-out forwards;
        }
        @keyframes drawPath {
          to { stroke-dashoffset: 0; }
        }
      `}</style>
    </div>
  );
}
