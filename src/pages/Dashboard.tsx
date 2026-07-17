import { Link } from 'react-router-dom';
import { useProgress } from '../context/ProgressContext';

const HERO_STATS = [
  { label: 'Chapters', value: '69', icon: '📖' },
  { label: 'Weapons', value: '9', icon: '⊕' },
  { label: 'Quiz Questions', value: '200+', icon: '✦' },
  { label: 'Pages', value: '248', icon: '📄' },
];

const SECTIONS = [
  {
    id: 'theory',
    title: 'Theory of SAs',
    desc: 'Ballistics, trajectory, ammunition, and operating mechanisms — all visualized',
    icon: '◎',
    color: 'cyan',
    to: '/theory',
    topics: ['Internal Ballistics', 'External Ballistics', 'Trajectory', 'Ammo Types'],
  },
  {
    id: 'weapons',
    title: 'Weapons Encyclopedia',
    desc: '9 weapon systems with interactive specs, parts, stoppages, and zeroing guides',
    icon: '⊕',
    color: 'green',
    to: '/weapons/g3a3',
    topics: ['G3A3', 'MP5A2', 'MG1A3', 'SMG', 'Pistols', 'SC-76', 'SSR', 'AAMG', 'Grenades'],
  },
  {
    id: 'snipers',
    title: 'Sniper Academy',
    desc: 'Camouflage, concealment, field skills, and sniper employment doctrine',
    icon: '⊹',
    color: 'amber',
    to: '/snipers',
    topics: ['Field Skills', 'Ghillie Suit', 'Cover & Concealment', 'Employment'],
  },
  {
    id: 'threat',
    title: 'Threat Intelligence',
    desc: 'Indian small arms database with full specifications and comparative analysis',
    icon: '⚠',
    color: 'red',
    to: '/threat',
    topics: ['Glock 17', 'AKM', 'FN FAL', 'M16/M4', 'Tavor', 'INSAS'],
  },
  {
    id: 'nvds',
    title: 'NVDs & Optics',
    desc: 'Night vision devices, thermal imagers, and telescopic sight references',
    icon: '◉',
    color: 'purple',
    to: '/nvds',
    topics: ['AN/PVS-5A', 'AN/PVS-7D', 'SKUA-LR', 'LRTWS'],
  },
  {
    id: 'quiz',
    title: 'Quiz Engine',
    desc: 'High-difficulty quizzes for verbatim memorization across 10 categories',
    icon: '✦',
    color: 'green',
    to: '/quiz',
    topics: ['MCQ', 'Fill-in-blank', 'Match Pairs', 'Order Steps', 'Timed Mode'],
  },
];

const COLOR_MAP: Record<string, string> = {
  green: 'var(--accent-green)',
  amber: 'var(--accent-amber)',
  red: 'var(--accent-red)',
  cyan: 'var(--accent-cyan)',
  purple: 'var(--accent-purple)',
};

const GLOW_MAP: Record<string, string> = {
  green: 'var(--accent-green-glow)',
  amber: 'var(--accent-amber-glow)',
  red: 'var(--accent-red-glow)',
  cyan: 'var(--accent-cyan-glow)',
  purple: 'var(--accent-purple-glow)',
};

export default function Dashboard() {
  const { getOverallPercent } = useProgress();
  const overallPercent = getOverallPercent();

  return (
    <div className="dashboard">
      {/* Hero */}
      <section className="dashboard__hero animate-fade-in-up">
        <div className="dashboard__hero-content">
          <div className="dashboard__hero-badge">
            <span className="badge badge-green">SAC-81 Methodology</span>
          </div>
          <h1 className="dashboard__hero-title">
            Master Your <span className="dashboard__hero-accent">Weapons Knowledge</span>
          </h1>
          <p className="dashboard__hero-desc">
            The complete SAC-81 Methodology manual transformed into an interactive training system.
            Study theory, explore weapons, and test your knowledge with verbatim-level quizzes.
          </p>
          <div className="dashboard__hero-actions">
            <Link to="/theory" className="btn btn-primary btn-lg">
              Start Learning
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                <path d="M5 12h14M12 5l7 7-7 7" />
              </svg>
            </Link>
            <Link to="/quiz" className="btn btn-secondary btn-lg">
              Take a Quiz
            </Link>
          </div>
        </div>

        {/* Progress Ring */}
        <div className="dashboard__hero-ring">
          <svg viewBox="0 0 120 120" className="progress-ring">
            <circle cx="60" cy="60" r="52" fill="none" stroke="var(--border-dim)" strokeWidth="6" />
            <circle
              cx="60" cy="60" r="52"
              fill="none"
              stroke="var(--accent-green)"
              strokeWidth="6"
              strokeLinecap="round"
              strokeDasharray={`${2 * Math.PI * 52}`}
              strokeDashoffset={`${2 * Math.PI * 52 * (1 - overallPercent / 100)}`}
              transform="rotate(-90 60 60)"
              style={{ transition: 'stroke-dashoffset 1s ease-out' }}
            />
            <text x="60" y="55" textAnchor="middle" fill="var(--text-primary)" fontSize="24" fontWeight="700" fontFamily="var(--font-heading)">
              {overallPercent}%
            </text>
            <text x="60" y="72" textAnchor="middle" fill="var(--text-muted)" fontSize="10" fontWeight="500">
              MASTERY
            </text>
          </svg>
        </div>
      </section>

      {/* Stats */}
      <section className="dashboard__stats stagger-children">
        {HERO_STATS.map(stat => (
          <div key={stat.label} className="dashboard__stat glass-card">
            <span className="dashboard__stat-icon">{stat.icon}</span>
            <span className="dashboard__stat-value">{stat.value}</span>
            <span className="dashboard__stat-label">{stat.label}</span>
          </div>
        ))}
      </section>

      {/* Sections */}
      <section className="dashboard__sections">
        <h2 className="section-title">
          <span className="section-title__icon">📚</span>
          Training Modules
          <span className="section-title__line" />
        </h2>
        <div className="grid-auto stagger-children">
          {SECTIONS.map(section => (
            <Link
              key={section.id}
              to={section.to}
              className="dashboard__card glass-card"
              style={{
                '--card-accent': COLOR_MAP[section.color],
                '--card-glow': GLOW_MAP[section.color],
              } as React.CSSProperties}
            >
              <div className="dashboard__card-header">
                <span className="dashboard__card-icon" style={{ color: COLOR_MAP[section.color] }}>
                  {section.icon}
                </span>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--text-muted)" strokeWidth="2">
                  <path d="M7 17L17 7M17 7H7M17 7V17" />
                </svg>
              </div>
              <h3 className="dashboard__card-title">{section.title}</h3>
              <p className="dashboard__card-desc">{section.desc}</p>
              <div className="dashboard__card-tags">
                {section.topics.slice(0, 4).map(t => (
                  <span key={t} className="dashboard__card-tag">{t}</span>
                ))}
                {section.topics.length > 4 && (
                  <span className="dashboard__card-tag">+{section.topics.length - 4}</span>
                )}
              </div>
            </Link>
          ))}
        </div>
      </section>

      <style>{`
        .dashboard {
          animation: fadeIn 0.4s ease-out;
        }

        .dashboard__hero {
          display: flex;
          align-items: center;
          justify-content: space-between;
          gap: var(--space-3xl);
          padding: var(--space-3xl) var(--space-2xl);
          border-radius: var(--radius-xl);
          background: linear-gradient(135deg, rgba(74, 222, 128, 0.04), rgba(34, 211, 238, 0.03), transparent);
          border: 1px solid var(--border-dim);
          margin-bottom: var(--space-2xl);
          position: relative;
          overflow: hidden;
        }

        .dashboard__hero::before {
          content: '';
          position: absolute;
          top: -50%;
          right: -20%;
          width: 500px;
          height: 500px;
          background: radial-gradient(circle, rgba(74, 222, 128, 0.06), transparent 60%);
          pointer-events: none;
        }

        .dashboard__hero-content {
          flex: 1;
          position: relative;
          z-index: 1;
        }

        .dashboard__hero-badge {
          margin-bottom: var(--space-md);
        }

        .dashboard__hero-title {
          font-size: 2.75rem;
          font-weight: 800;
          line-height: 1.1;
          margin-bottom: var(--space-lg);
          letter-spacing: -0.03em;
        }

        .dashboard__hero-accent {
          background: linear-gradient(135deg, var(--accent-green), var(--accent-cyan));
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
          background-clip: text;
        }

        .dashboard__hero-desc {
          font-size: 1.05rem;
          max-width: 540px;
          margin-bottom: var(--space-xl);
          line-height: 1.7;
        }

        .dashboard__hero-actions {
          display: flex;
          gap: var(--space-md);
          flex-wrap: wrap;
        }

        .dashboard__hero-ring {
          flex-shrink: 0;
          position: relative;
          z-index: 1;
        }

        .progress-ring {
          width: 180px;
          height: 180px;
          filter: drop-shadow(0 0 12px rgba(74, 222, 128, 0.15));
        }

        /* Stats */
        .dashboard__stats {
          display: grid;
          grid-template-columns: repeat(4, 1fr);
          gap: var(--space-md);
          margin-bottom: var(--space-3xl);
        }

        .dashboard__stat {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 4px;
          padding: var(--space-lg) var(--space-md);
          text-align: center;
        }

        .dashboard__stat-icon {
          font-size: 1.5rem;
          margin-bottom: 4px;
        }

        .dashboard__stat-value {
          font-family: var(--font-heading);
          font-size: 1.75rem;
          font-weight: 700;
          color: var(--text-primary);
        }

        .dashboard__stat-label {
          font-size: 0.75rem;
          color: var(--text-muted);
          text-transform: uppercase;
          letter-spacing: 0.06em;
          font-weight: 500;
        }

        /* Section Cards */
        .dashboard__card {
          padding: var(--space-xl);
          text-decoration: none;
          display: flex;
          flex-direction: column;
          gap: var(--space-sm);
          position: relative;
          overflow: hidden;
        }

        .dashboard__card::before {
          content: '';
          position: absolute;
          top: 0;
          left: 0;
          right: 0;
          height: 2px;
          background: linear-gradient(90deg, var(--card-accent), transparent);
          opacity: 0;
          transition: opacity var(--duration-normal) var(--ease-out);
        }

        .dashboard__card:hover::before {
          opacity: 1;
        }

        .dashboard__card:hover {
          border-color: var(--card-accent);
          box-shadow: 0 0 30px var(--card-glow);
          transform: translateY(-2px);
        }

        .dashboard__card-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: var(--space-sm);
        }

        .dashboard__card-icon {
          font-size: 1.75rem;
        }

        .dashboard__card-title {
          font-size: 1.15rem;
          color: var(--text-primary);
          margin-bottom: 4px;
        }

        .dashboard__card-desc {
          font-size: 0.85rem;
          color: var(--text-muted);
          line-height: 1.6;
          flex: 1;
        }

        .dashboard__card-tags {
          display: flex;
          flex-wrap: wrap;
          gap: 4px;
          margin-top: var(--space-sm);
        }

        .dashboard__card-tag {
          padding: 2px 8px;
          border-radius: var(--radius-full);
          font-size: 0.7rem;
          background: rgba(148, 163, 184, 0.06);
          color: var(--text-muted);
          border: 1px solid var(--border-dim);
        }

        /* Responsive */
        @media (max-width: 1024px) {
          .dashboard__hero {
            flex-direction: column;
            text-align: center;
            padding: var(--space-2xl) var(--space-lg);
          }

          .dashboard__hero-title {
            font-size: 2rem;
          }

          .dashboard__hero-desc {
            margin-left: auto;
            margin-right: auto;
          }

          .dashboard__hero-actions {
            justify-content: center;
          }

          .progress-ring {
            width: 140px;
            height: 140px;
          }

          .dashboard__stats {
            grid-template-columns: repeat(2, 1fr);
          }
        }

        @media (max-width: 768px) {
          .dashboard__hero-title {
            font-size: 1.6rem;
          }

          .dashboard__stats {
            grid-template-columns: repeat(2, 1fr);
          }
        }
      `}</style>
    </div>
  );
}
