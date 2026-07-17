import { useState } from 'react';
import type { WeaponData } from '../../types';

export default function InteractiveZeroingSimulator({ weapon }: { weapon: WeaponData }) {
  const [impactX, setImpactX] = useState<number | null>(null);
  const [impactY, setImpactY] = useState<number | null>(null);

  if (!weapon.zeroing) return null;

  // Manual logic based on weapon
  let distance = 100;
  let elevationAction = 'Rear Sight';

  if (weapon.shortName === 'G3A3') {
    distance = 25; // MPI at 25m
  } else if (weapon.shortName === 'MP5A2') {
    distance = 25;
  } else if (weapon.shortName === 'MG1A3') {
    distance = 100;
  }

  const handleTargetClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const x = e.clientX - rect.left - rect.width / 2; // Center is 0,0
    const y = e.clientY - rect.top - rect.height / 2; // Center is 0,0
    setImpactX(x);
    setImpactY(y);
  };

  return (
    <div className="glass-card" style={{ padding: 'var(--space-xl)', overflow: 'hidden' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-lg)', flexWrap: 'wrap', gap: 'var(--space-md)' }}>
        <div>
          <h3 style={{ fontSize: '1.25rem', color: 'var(--text-primary)', marginBottom: 4 }}>Zeroing Simulator: {weapon.shortName}</h3>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Click the target where your MPI (Mean Point of Impact) landed.</p>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 'var(--space-lg)', flexWrap: 'wrap' }}>
        <div style={{ flex: 1, minWidth: 280, display: 'flex', justifyContent: 'center' }}>
          <div 
            onClick={handleTargetClick}
            style={{
              width: 250, height: 250, 
              background: '#f0f0f0', 
              borderRadius: 8, 
              position: 'relative',
              cursor: 'crosshair',
              boxShadow: 'inset 0 0 20px rgba(0,0,0,0.1)',
              overflow: 'hidden'
            }}
          >
            {/* Target Rings */}
            <div style={{ position: 'absolute', inset: '10%', border: '1px solid #333', borderRadius: '50%' }} />
            <div style={{ position: 'absolute', inset: '25%', border: '2px solid #333', borderRadius: '50%' }} />
            <div style={{ position: 'absolute', inset: '40%', background: '#222', borderRadius: '50%' }} />
            {/* Crosshairs */}
            <div style={{ position: 'absolute', top: '50%', left: 0, right: 0, height: 1, background: 'rgba(0,0,0,0.2)' }} />
            <div style={{ position: 'absolute', left: '50%', top: 0, bottom: 0, width: 1, background: 'rgba(0,0,0,0.2)' }} />

            {/* Impact Marker */}
            {impactX !== null && impactY !== null && (
              <div style={{
                position: 'absolute',
                left: `calc(50% + ${impactX}px)`,
                top: `calc(50% + ${impactY}px)`,
                width: 12, height: 12,
                background: 'var(--accent-red)',
                borderRadius: '50%',
                transform: 'translate(-50%, -50%)',
                boxShadow: '0 0 10px rgba(239, 68, 68, 0.8)'
              }} />
            )}
          </div>
        </div>

        <div style={{ flex: 1, minWidth: 280 }}>
          {impactX === null ? (
            <div style={{ padding: 'var(--space-md)', background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-md)', border: '1px dashed var(--border-medium)', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', textAlign: 'center', color: 'var(--text-muted)' }}>
              Click on the paper target to register a Mean Point of Impact (MPI).
            </div>
          ) : (
            <div className="animate-fade-in" style={{ padding: 'var(--space-lg)', background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-medium)', height: '100%' }}>
              <h4 style={{ color: 'var(--text-primary)', marginBottom: 'var(--space-md)' }}>Correction Required ({distance}m)</h4>
              
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-md)', marginBottom: 'var(--space-md)' }}>
                <div style={{ padding: 'var(--space-md)', background: 'rgba(241, 245, 249, 0.8)', borderRadius: 'var(--radius-sm)' }}>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: 4 }}>Elevation</div>
                  <div style={{ fontSize: '1.2rem', fontFamily: 'var(--font-mono)', color: impactY! > 0 ? 'var(--accent-green)' : 'var(--accent-amber)' }}>
                    {Math.max(1, Math.round(Math.abs(impactY!) / 15))} clicks <br/>
                    <span style={{ fontSize: '0.8rem' }}>{impactY! > 0 ? 'UP' : 'DOWN'}</span>
                  </div>
                </div>

                <div style={{ padding: 'var(--space-md)', background: 'rgba(241, 245, 249, 0.8)', borderRadius: 'var(--radius-sm)' }}>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: 4 }}>Windage</div>
                  <div style={{ fontSize: '1.2rem', fontFamily: 'var(--font-mono)', color: impactX! < 0 ? 'var(--accent-green)' : 'var(--accent-amber)' }}>
                    {Math.max(1, Math.round(Math.abs(impactX!) / 15))} clicks <br/>
                    <span style={{ fontSize: '0.8rem' }}>{impactX! < 0 ? 'RIGHT' : 'LEFT'}</span>
                  </div>
                </div>
              </div>

              <div style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', lineHeight: 1.5, padding: 'var(--space-sm)', background: 'var(--accent-green-bg)', borderLeft: '3px solid var(--accent-green)' }}>
                <strong>Manual Formula:</strong> {weapon.zeroing.definition.slice(0, 100)}...<br/><br/>
                For the {weapon.shortName}, moving the MPI {impactY! > 0 ? 'up' : 'down'} requires turning the {elevationAction} {impactY! > 0 ? 'clockwise' : 'counter-clockwise'}.
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
