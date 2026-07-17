import { Link, useLocation } from 'react-router-dom';
import { useProgress } from '../../context/ProgressContext';

export default function Navbar({ onToggleSidebar }: { onToggleSidebar: () => void }) {
  const { progress, getOverallPercent } = useProgress();
  const location = useLocation();

  const isHome = location.pathname === '/';

  return (
    <nav className="navbar" id="main-navbar">
      <Link to="/" className="navbar__brand">
        <div className="navbar__logo">⊕</div>
        <div>
          <div className="navbar__title">Gun Mastery</div>
          <div className="navbar__subtitle">SAC-81 Training System</div>
        </div>
      </Link>

      <div className="navbar__actions">
        {progress.studyStreak > 0 && (
          <div className="navbar__streak" title="Study streak">
            🔥 {progress.studyStreak} day{progress.studyStreak > 1 ? 's' : ''}
          </div>
        )}

        <div className="navbar__progress-mini" title="Overall quiz progress">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
            <circle cx="12" cy="12" r="10" opacity="0.3" />
            <path d="M12 2a10 10 0 0 1 0 20" strokeLinecap="round" />
          </svg>
          {getOverallPercent()}%
        </div>

        {!isHome && (
          <Link to="/" className="btn btn-ghost btn-sm">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z" />
              <polyline points="9,22 9,12 15,12 15,22" />
            </svg>
            Home
          </Link>
        )}

        <button className="navbar__hamburger" onClick={onToggleSidebar} aria-label="Toggle navigation">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <line x1="3" y1="6" x2="21" y2="6" />
            <line x1="3" y1="12" x2="21" y2="12" />
            <line x1="3" y1="18" x2="21" y2="18" />
          </svg>
        </button>
      </div>
    </nav>
  );
}
