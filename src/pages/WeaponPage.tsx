import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { allWeapons } from '../data/weapons';
import type { WeaponData } from '../types';
import InteractiveWeaponAnatomy from '../components/Visualizations/InteractiveWeaponAnatomy';
import InteractiveZeroingSimulator from '../components/Visualizations/InteractiveZeroingSimulator';

const CATEGORY_COLORS: Record<string, string> = {
  rifle: 'var(--accent-green)',
  smg: 'var(--accent-cyan)',
  mg: 'var(--accent-amber)',
  pistol: 'var(--accent-purple)',
  sniper: 'var(--accent-red)',
  aamg: 'var(--accent-amber)',
  grenade: 'var(--accent-red)',
};

const CATEGORY_LABELS: Record<string, string> = {
  rifle: 'Assault Rifle',
  smg: 'Sub Machine Gun',
  mg: 'Machine Gun',
  pistol: 'Pistol',
  sniper: 'Sniper Rifle',
  aamg: 'Heavy MG',
  grenade: 'Grenade',
};

type Tab = 'overview' | 'parts' | 'stoppages' | 'zeroing' | 'details';

export default function WeaponPage() {
  const { weaponId } = useParams<{ weaponId: string }>();
  const [activeTab, setActiveTab] = useState<Tab>('overview');
  const [expandedPart, setExpandedPart] = useState<string | null>(null);

  const weapon = weaponId ? allWeapons[weaponId] : undefined;

  if (!weapon) {
    return (
      <div className="page-container animate-fade-in">
        <div className="page-header">
          <h1>Weapon Not Found</h1>
          <p>The requested weapon does not exist.</p>
          <Link to="/" className="btn btn-primary" style={{ marginTop: '16px' }}>Back to Dashboard</Link>
        </div>
      </div>
    );
  }

  const accent = CATEGORY_COLORS[weapon.category] || 'var(--accent-green)';

  const tabs: { id: Tab; label: string; show: boolean }[] = [
    { id: 'overview', label: 'Overview & Specs', show: true },
    { id: 'parts', label: 'Parts Breakdown', show: weapon.mainParts.length > 0 },
    { id: 'stoppages', label: 'Stoppages', show: !!weapon.stoppages && weapon.stoppages.length > 0 },
    { id: 'zeroing', label: 'Zeroing', show: !!weapon.zeroing },
    { id: 'details', label: 'Additional Info', show: !!weapon.additionalInfo && weapon.additionalInfo.length > 0 },
  ];

  return (
    <div className="page-container animate-fade-in">
      {/* Header */}
      <div className="page-header">
        <div className="page-header__breadcrumb">
          <Link to="/">Home</Link>
          <span>›</span>
          <span>Weapons</span>
          <span>›</span>
          <span style={{ color: 'var(--text-secondary)' }}>{weapon.shortName}</span>
        </div>
        <div className="wpn-header" style={{ '--wpn-accent': accent } as React.CSSProperties}>
          <div className="wpn-header__info">
            <span className="badge" style={{ background: `${accent}15`, color: accent, border: `1px solid ${accent}30` }}>
              {CATEGORY_LABELS[weapon.category]}
            </span>
            <h1 className="wpn-header__title">{weapon.name}</h1>
            <p className="wpn-header__origin">{weapon.caliber} — {weapon.origin}</p>
            <p className="wpn-header__desc">{weapon.description}</p>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="tab-group">
        {tabs.filter(t => t.show).map(tab => (
          <button
            key={tab.id}
            className={`tab-group__tab ${activeTab === tab.id ? 'tab-group__tab--active' : ''}`}
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab Content */}
      <div className="wpn-content animate-fade-in" key={activeTab}>
        {activeTab === 'overview' && <OverviewTab weapon={weapon} accent={accent} />}
        {activeTab === 'parts' && <PartsTab weapon={weapon} expandedPart={expandedPart} setExpandedPart={setExpandedPart} />}
        {activeTab === 'stoppages' && weapon.stoppages && <StoppagesTab weapon={weapon} accent={accent} />}
        {activeTab === 'zeroing' && weapon.zeroing && <ZeroingTab weapon={weapon} accent={accent} />}
        {activeTab === 'details' && weapon.additionalInfo && <DetailsTab weapon={weapon} />}
      </div>

      <style>{`
        .wpn-header {
          padding: var(--space-xl) var(--space-2xl);
          border-radius: var(--radius-xl);
          background: linear-gradient(135deg, rgba(241, 245, 249, 0.8), rgba(241, 245, 249, 0.4));
          border: 1px solid var(--border-subtle);
          position: relative;
          overflow: hidden;
        }
        .wpn-header::before {
          content: '';
          position: absolute;
          top: 0; left: 0; right: 0;
          height: 3px;
          background: linear-gradient(90deg, var(--wpn-accent), transparent);
        }
        .wpn-header__title {
          font-size: 1.75rem;
          margin: var(--space-sm) 0 var(--space-xs);
        }
        .wpn-header__origin {
          font-size: 0.9rem;
          color: var(--text-muted);
          margin-bottom: var(--space-md);
          font-family: var(--font-mono);
        }
        .wpn-header__desc {
          font-size: 0.95rem;
          max-width: 720px;
          line-height: 1.7;
        }

        .wpn-content {
          animation: fadeInUp 0.3s ease-out;
        }

        /* Spec Grid */
        .spec-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
          gap: 1px;
          background: var(--border-dim);
          border-radius: var(--radius-lg);
          overflow: hidden;
          border: 1px solid var(--border-dim);
        }
        .spec-item {
          display: flex;
          justify-content: space-between;
          align-items: center;
          padding: 12px 18px;
          background: var(--bg-tertiary);
        }
        .spec-item__label {
          font-size: 0.85rem;
          color: var(--text-muted);
        }
        .spec-item__value {
          font-family: var(--font-mono);
          font-size: 0.9rem;
          font-weight: 600;
          color: var(--text-primary);
        }
        .spec-item__unit {
          font-size: 0.75rem;
          color: var(--text-muted);
          margin-left: 4px;
          font-weight: 400;
        }

        /* ROF Section */
        .rof-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
          gap: var(--space-md);
        }
        .rof-card {
          padding: var(--space-lg);
          border-radius: var(--radius-lg);
          border: 1px solid var(--border-dim);
          background: var(--bg-tertiary);
        }
        .rof-card__mode {
          font-family: var(--font-heading);
          font-weight: 700;
          font-size: 1rem;
          color: var(--text-primary);
          margin-bottom: 4px;
        }
        .rof-card__rate {
          font-family: var(--font-mono);
          font-size: 1.5rem;
          font-weight: 700;
          margin-bottom: var(--space-sm);
        }
        .rof-card__desc {
          font-size: 0.8rem;
          color: var(--text-muted);
          line-height: 1.5;
        }

        /* Chars */
        .chars-list {
          list-style: none;
          display: flex;
          flex-direction: column;
          gap: 8px;
        }
        .chars-list li {
          display: flex;
          align-items: flex-start;
          gap: 10px;
          font-size: 0.9rem;
          padding: 8px 12px;
          border-radius: var(--radius-md);
          background: rgba(241, 245, 249, 0.4);
        }
        .chars-list li::before {
          content: '▹';
          color: var(--accent-green);
          font-weight: 700;
          flex-shrink: 0;
          margin-top: 1px;
        }

        /* Funcs */
        .func-list {
          list-style: none;
          display: flex;
          flex-direction: column;
          gap: 2px;
        }
        .func-item {
          display: flex;
          align-items: flex-start;
          gap: 12px;
          padding: 14px 18px;
          border-left: 2px solid var(--border-dim);
          transition: border-color var(--duration-fast);
        }
        .func-item:hover {
          border-left-color: var(--accent-green);
          background: rgba(74, 222, 128, 0.02);
        }
        .func-item__num {
          font-family: var(--font-mono);
          font-size: 0.75rem;
          font-weight: 700;
          color: var(--accent-green);
          background: var(--accent-green-bg);
          width: 28px;
          height: 28px;
          border-radius: var(--radius-full);
          display: flex;
          align-items: center;
          justify-content: center;
          flex-shrink: 0;
        }
        .func-item__text {
          font-size: 0.9rem;
          line-height: 1.6;
        }
        .func-item__name {
          color: var(--text-primary);
          font-weight: 600;
        }

        /* Parts */
        .parts-grid {
          display: flex;
          flex-direction: column;
          gap: 2px;
          border: 1px solid var(--border-dim);
          border-radius: var(--radius-lg);
          overflow: hidden;
        }
        .part-item {
          background: var(--bg-tertiary);
        }
        .part-item__header {
          display: flex;
          align-items: center;
          justify-content: space-between;
          padding: 14px 20px;
          cursor: pointer;
          transition: background var(--duration-fast);
          border: none;
          background: transparent;
          width: 100%;
          text-align: left;
          color: var(--text-primary);
          font-family: var(--font-body);
          font-size: 0.95rem;
          font-weight: 500;
        }
        .part-item__header:hover {
          background: rgba(74, 222, 128, 0.03);
        }
        .part-item__count {
          font-size: 0.75rem;
          color: var(--text-muted);
          background: var(--border-dim);
          padding: 2px 8px;
          border-radius: var(--radius-full);
        }
        .part-item__subs {
          padding: 0 20px 14px;
          display: flex;
          flex-wrap: wrap;
          gap: 6px;
        }
        .part-sub {
          padding: 4px 12px;
          border-radius: var(--radius-full);
          background: var(--accent-green-bg);
          color: var(--accent-green);
          font-size: 0.8rem;
          border: 1px solid rgba(74, 222, 128, 0.12);
        }

        /* Stoppages */
        .stoppages-list {
          display: flex;
          flex-direction: column;
          gap: var(--space-md);
        }
        .stoppage-card {
          border: 1px solid var(--border-dim);
          border-radius: var(--radius-lg);
          overflow: hidden;
        }
        .stoppage-card__header {
          padding: 14px 20px;
          background: rgba(239, 68, 68, 0.04);
          border-bottom: 1px solid var(--border-dim);
          font-weight: 600;
          color: var(--text-primary);
          font-size: 0.95rem;
        }
        .stoppage-card__body {
          display: grid;
          grid-template-columns: 1fr 1fr;
          gap: 0;
        }
        .stoppage-col {
          padding: 14px 20px;
        }
        .stoppage-col:first-child {
          border-right: 1px solid var(--border-dim);
        }
        .stoppage-col__title {
          font-size: 0.7rem;
          text-transform: uppercase;
          letter-spacing: 0.08em;
          color: var(--text-muted);
          font-weight: 600;
          margin-bottom: 8px;
        }
        .stoppage-col ul {
          list-style: none;
          display: flex;
          flex-direction: column;
          gap: 6px;
        }
        .stoppage-col li {
          font-size: 0.85rem;
          color: var(--text-secondary);
          padding-left: 14px;
          position: relative;
        }
        .stoppage-col li::before {
          content: '•';
          position: absolute;
          left: 0;
        }
        .stoppage-col:first-child li::before { color: var(--accent-red); }
        .stoppage-col:last-child li::before { color: var(--accent-green); }

        /* Zeroing */
        .zeroing-def {
          padding: var(--space-lg);
          border-radius: var(--radius-lg);
          background: var(--accent-green-bg);
          border: 1px solid rgba(74, 222, 128, 0.12);
          font-size: 0.95rem;
          color: var(--text-secondary);
          margin-bottom: var(--space-xl);
          line-height: 1.7;
        }
        .zeroing-occasions {
          margin-bottom: var(--space-xl);
        }
        .zeroing-occasions h3 {
          margin-bottom: var(--space-md);
        }
        .zeroing-steps {
          display: flex;
          flex-direction: column;
          gap: 2px;
        }
        .zeroing-step {
          display: flex;
          gap: var(--space-md);
          padding: 16px 20px;
          background: rgba(241, 245, 249, 0.8);
          border-radius: var(--radius-md);
          align-items: flex-start;
        }
        .zeroing-step__num {
          font-family: var(--font-mono);
          font-weight: 700;
          font-size: 0.9rem;
          width: 32px;
          height: 32px;
          border-radius: var(--radius-full);
          display: flex;
          align-items: center;
          justify-content: center;
          flex-shrink: 0;
          border: 2px solid var(--border-medium);
        }
        .zeroing-step__text {
          font-size: 0.9rem;
          line-height: 1.6;
        }
        .zeroing-step__note {
          font-size: 0.8rem;
          color: var(--accent-amber);
          margin-top: 4px;
          font-style: italic;
        }

        /* Details */
        .detail-cards {
          display: flex;
          flex-direction: column;
          gap: var(--space-md);
        }
        .detail-card {
          padding: var(--space-lg);
          border-radius: var(--radius-lg);
          border: 1px solid var(--border-dim);
          background: var(--bg-tertiary);
        }
        .detail-card h3 {
          font-size: 1rem;
          margin-bottom: var(--space-sm);
          color: var(--text-primary);
        }
        .detail-card p {
          font-size: 0.9rem;
          line-height: 1.7;
        }

        @media (max-width: 768px) {
          .spec-grid { grid-template-columns: 1fr; }
          .stoppage-card__body { grid-template-columns: 1fr; }
          .stoppage-col:first-child { border-right: none; border-bottom: 1px solid var(--border-dim); }
          .rof-grid { grid-template-columns: 1fr; }
        }
      `}</style>
    </div>
  );
}

// ─── Sub-Components ───

function OverviewTab({ weapon, accent }: { weapon: WeaponData; accent: string }) {
  return (
    <>
      <InteractiveWeaponAnatomy weapon={weapon} />
      <br />
      {/* Specs */}
      <div className="content-section">
        <h2 className="section-title">
          Technical Specifications
          <span className="section-title__line" />
        </h2>
        <div className="spec-grid">
          {weapon.specs.map((spec, i) => (
            <div key={i} className="spec-item">
              <span className="spec-item__label">{spec.label}</span>
              <span className="spec-item__value">
                {spec.value}
                {spec.unit && <span className="spec-item__unit">{spec.unit}</span>}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* Rates of Fire */}
      {weapon.ratesOfFire && weapon.ratesOfFire.length > 0 && (
        <div className="content-section">
          <h2 className="section-title">
            Rates of Fire
            <span className="section-title__line" />
          </h2>
          <div className="rof-grid">
            {weapon.ratesOfFire.map((rof, i) => (
              <div key={i} className="rof-card">
                <div className="rof-card__mode">{rof.mode}</div>
                <div className="rof-card__rate" style={{ color: accent }}>{rof.rate}</div>
                <div className="rof-card__desc">{rof.description}</div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Characteristics */}
      <div className="content-section">
        <h2 className="section-title">
          Characteristics
          <span className="section-title__line" />
        </h2>
        <ul className="chars-list">
          {weapon.characteristics.map((char, i) => (
            <li key={i}>{char}</li>
          ))}
        </ul>
      </div>

      {/* Cycle of Operation */}
      {weapon.functions && weapon.functions.length > 0 && (
        <div className="content-section">
          <h2 className="section-title">
            Cycle of Operation
            <span className="section-title__line" />
          </h2>
          <div className="func-list">
            {weapon.functions.map((func, i) => {
              const [name, ...rest] = func.split(' — ');
              return (
                <div key={i} className="func-item">
                  <span className="func-item__num">{i + 1}</span>
                  <div className="func-item__text">
                    <span className="func-item__name">{name}</span>
                    {rest.length > 0 && <span> — {rest.join(' — ')}</span>}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </>
  );
}

function PartsTab({ weapon, expandedPart, setExpandedPart }: {
  weapon: WeaponData;
  expandedPart: string | null;
  setExpandedPart: (p: string | null) => void;
}) {
  return (
    <div className="content-section">
      <h2 className="section-title">
        Parts Breakdown ({weapon.mainParts.length} main components)
        <span className="section-title__line" />
      </h2>
      <div className="parts-grid">
        {weapon.mainParts.map((part, i) => (
          <div key={i} className="part-item">
            <button
              className="part-item__header"
              onClick={() => setExpandedPart(expandedPart === part.name ? null : part.name)}
            >
              <span>{part.name}</span>
              {part.subParts && part.subParts.length > 0 && (
                <span className="part-item__count">{part.subParts.length} sub-parts</span>
              )}
            </button>
            {expandedPart === part.name && part.subParts && part.subParts.length > 0 && (
              <div className="part-item__subs">
                {part.subParts.map((sub, j) => (
                  <span key={j} className="part-sub">{sub}</span>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

function StoppagesTab({ weapon }: { weapon: WeaponData; accent: string }) {
  return (
    <div className="content-section">
      <h2 className="section-title">
        Stoppages & Remedial Actions ({weapon.stoppages!.length} types)
        <span className="section-title__line" />
      </h2>
      <div className="stoppages-list">
        {weapon.stoppages!.map((stop, i) => (
          <div key={i} className="stoppage-card">
            <div className="stoppage-card__header">{stop.type}</div>
            <div className="stoppage-card__body">
              <div className="stoppage-col">
                <div className="stoppage-col__title">Causes</div>
                <ul>
                  {stop.causes.map((c, j) => <li key={j}>{c}</li>)}
                </ul>
              </div>
              <div className="stoppage-col">
                <div className="stoppage-col__title">Remedies</div>
                <ul>
                  {stop.remedies.map((r, j) => <li key={j}>{r}</li>)}
                </ul>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function ZeroingTab({ weapon, accent }: { weapon: WeaponData; accent: string }) {
  const z = weapon.zeroing!;
  return (
    <>
      <div className="content-section">
        <InteractiveZeroingSimulator weapon={weapon} />
        <br />
        <h2 className="section-title">
          Zeroing Procedure
          <span className="section-title__line" />
        </h2>
        <div className="zeroing-def">{z.definition}</div>

        <div className="zeroing-occasions">
          <h3>Occasions Requiring Zeroing</h3>
          <ul className="chars-list">
            {z.occasions.map((occ, i) => <li key={i}>{occ}</li>)}
          </ul>
        </div>

        <h3 style={{ marginBottom: 'var(--space-md)' }}>Step-by-Step Procedure</h3>
        <div className="zeroing-steps">
          {z.steps.map((step, i) => (
            <div key={i} className="zeroing-step">
              <span className="zeroing-step__num" style={{ color: accent, borderColor: accent }}>
                {step.step}
              </span>
              <div>
                <div className="zeroing-step__text">{step.instruction}</div>
                {step.note && <div className="zeroing-step__note">⚡ {step.note}</div>}
              </div>
            </div>
          ))}
        </div>
      </div>
    </>
  );
}

function DetailsTab({ weapon }: { weapon: WeaponData }) {
  return (
    <div className="content-section">
      <h2 className="section-title">
        Additional Information
        <span className="section-title__line" />
      </h2>
      <div className="detail-cards">
        {weapon.additionalInfo!.map((info, i) => (
          <div key={i} className="detail-card">
            <h3>{info.title}</h3>
            <p>{info.content}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
