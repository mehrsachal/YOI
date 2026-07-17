import { useState } from 'react';
import type { WeaponData } from '../../types';

export default function InteractiveWeaponAnatomy({ weapon }: { weapon: WeaponData }) {
  const [activePart, setActivePart] = useState<string | null>(null);

  // Extract all numeric/specific specs to show when hovering over parts
  const specs = weapon.specs.reduce((acc, curr) => {
    acc[curr.label] = `${curr.value} ${curr.unit || ''}`.trim();
    return acc;
  }, {} as Record<string, string>);

  const parts = [
    {
      id: 'barrel',
      name: 'Barrel & Muzzle',
      description: 'Houses the rifling and directs the projectile.',
      stats: [
        { label: 'Length w/o Flash Hider', value: specs['Length w/o flash hider'] || specs['Barrel Length'] || 'N/A' },
        { label: 'Rifling Twist', value: specs['Rifling'] || 'N/A' },
        { label: 'Muzzle Velocity', value: specs['Muzzle Velocity'] || 'N/A' }
      ],
      path: weapon.category === 'smg' ? 'M 10 60 L 100 60 L 100 80 L 10 80 Z' :
            weapon.category === 'mg' ? 'M 10 30 L 200 30 L 200 60 L 10 60 Z' :
            weapon.category === 'sniper' ? 'M 10 50 L 180 50 L 180 65 L 10 65 Z' :
            'M 10 40 L 150 40 L 150 50 L 10 50 Z'
    },
    {
      id: 'receiver',
      name: 'Receiver & Action',
      description: 'Contains the operating mechanism and bolt assembly.',
      stats: [
        { label: 'Weight (Unloaded)', value: specs['Weight (unloaded)'] || specs['Weight'] || 'N/A' },
        { label: 'Operation', value: weapon.functions ? weapon.functions[0] : 'N/A' },
        { label: 'Caliber', value: weapon.caliber }
      ],
      path: weapon.category === 'smg' ? 'M 100 50 L 220 50 L 220 100 L 100 100 Z' :
            weapon.category === 'mg' ? 'M 200 20 L 320 20 L 320 90 L 200 90 Z' :
            weapon.category === 'sniper' ? 'M 180 40 L 280 40 L 280 80 L 180 80 Z' :
            'M 150 30 L 250 30 L 250 70 L 150 70 Z'
    },
    {
      id: 'magazine',
      name: 'Magazine / Feed',
      description: 'Ammunition storage and feeding system.',
      stats: [
        { label: 'Capacity', value: specs['Magazine Capacity'] || specs['Feed System'] || 'N/A' },
        { label: 'Weight (Empty)', value: specs['Weight of empty magazine'] || 'N/A' },
        { label: 'Weight (Full)', value: specs['Weight of full magazine'] || 'N/A' }
      ],
      path: weapon.category === 'smg' ? 'M 150 100 L 190 100 L 170 170 L 130 170 Z' :
            weapon.category === 'mg' ? 'M 180 90 L 240 90 L 260 150 L 200 150 Z' :
            weapon.category === 'sniper' ? 'M 210 80 L 250 80 L 250 130 L 210 130 Z' :
            'M 180 70 L 220 70 L 230 140 L 190 135 Z'
    },
    {
      id: 'stock',
      name: 'Butt Stock / Grip',
      description: 'Stabilizes the weapon against the shoulder.',
      stats: [
        { label: 'Total Length', value: specs['Length'] || specs['Length (fixed stock)'] || 'N/A' },
        { label: 'Length (Retracted)', value: specs['Length (retracted stock)'] || 'N/A' }
      ],
      path: weapon.category === 'smg' ? 'M 220 60 L 320 60 L 320 100 L 220 90 Z' :
            weapon.category === 'mg' ? 'M 320 40 L 400 40 L 410 100 L 320 90 Z' :
            weapon.category === 'sniper' ? 'M 280 45 L 380 45 L 390 90 L 280 80 Z' :
            'M 250 40 L 350 40 L 360 80 L 250 70 Z'
    }
  ];

  return (
    <div className="glass-card" style={{ padding: 'var(--space-xl)', overflow: 'hidden' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-lg)' }}>
        <div>
          <h3 style={{ fontSize: '1.25rem', color: 'var(--text-primary)', marginBottom: 4 }}>Interactive Anatomy: {weapon.shortName}</h3>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>Hover or click components to cram exact manual specifications.</p>
        </div>
      </div>

      <div style={{ background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-xl)', border: '1px solid var(--border-medium)', position: 'relative', height: 260, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <svg width="400" height="180" viewBox="0 0 400 180">
          {parts.map(part => (
            <g
              key={part.id}
              onMouseEnter={() => setActivePart(part.id)}
              onMouseLeave={() => setActivePart(null)}
              style={{ cursor: 'crosshair', transition: 'all 0.2s' }}
            >
              <path
                d={part.path}
                fill={activePart === part.id ? 'var(--accent-green)' : 'var(--text-secondary)'}
                fillOpacity={activePart === part.id ? 0.6 : 0.2}
                stroke={activePart === part.id ? 'var(--accent-green)' : 'var(--border-medium)'}
                strokeWidth={activePart === part.id ? 2 : 1}
              />
              {activePart === part.id && (
                <text x="200" y="20" fill="var(--accent-green)" textAnchor="middle" fontSize="14" fontWeight="bold">
                  {part.name.toUpperCase()}
                </text>
              )}
            </g>
          ))}
        </svg>

        {activePart && (
          <div className="animate-fade-in-up" style={{
            position: 'absolute',
            bottom: 'var(--space-md)',
            left: 'var(--space-md)',
            right: 'var(--space-md)',
            background: 'var(--bg-secondary)',
            padding: 'var(--space-md)',
            borderRadius: 'var(--radius-md)',
            border: '1px solid var(--accent-green)',
            boxShadow: '0 4px 20px rgba(0,0,0,0.5)'
          }}>
            <h4 style={{ color: 'var(--accent-green)', marginBottom: 8 }}>
              {parts.find(p => p.id === activePart)?.name}
            </h4>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 8 }}>
              {parts.find(p => p.id === activePart)?.stats.map((stat, i) => stat.value !== 'N/A' && (
                <div key={i}>
                  <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textTransform: 'uppercase' }}>{stat.label}</div>
                  <div style={{ fontSize: '0.9rem', color: 'var(--text-primary)', fontFamily: 'var(--font-mono)', fontWeight: 600 }}>{stat.value}</div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
