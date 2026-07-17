import { NavLink } from 'react-router-dom';

const sections = [
  {
    title: 'Overview',
    links: [
      { to: '/', label: 'Dashboard', icon: '⊞' },
    ],
  },
  {
    title: 'Theory',
    links: [
      { to: '/theory', label: 'Theory of SAs & NVDs', icon: '◎' },
    ],
  },
  {
    title: 'Weapons',
    links: [
      { to: '/weapons/g3a3', label: 'G3A3 Rifle', icon: '▸' },
      { to: '/weapons/mp5a2', label: 'MP5A2', icon: '▸' },
      { to: '/weapons/mg1a3', label: 'MG1A3', icon: '▸' },
      { to: '/weapons/smg', label: 'SMG Chinese', icon: '▸' },
      { to: '/weapons/pistols', label: '9mm Pistols', icon: '▸' },
      { to: '/weapons/sc76', label: 'SC-76 Thunderbolt', icon: '▸' },
      { to: '/weapons/ssr', label: 'Steyr SSR', icon: '▸' },
      { to: '/weapons/aamg', label: '12.7mm AAMG', icon: '▸' },
      { to: '/weapons/grenades', label: 'Grenades', icon: '▸' },
    ],
  },
  {
    title: 'Assessment',
    links: [
      { to: '/quiz', label: 'Quiz Engine', icon: '✦' },
      { to: '/flashcards', label: 'Flashcards', icon: '🎴' },
    ],
  },
];

export default function Sidebar({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) {
  return (
    <>
      {isOpen && <div className="sidebar-overlay" onClick={onClose} />}
      <aside className={`sidebar ${isOpen ? 'sidebar--open' : ''}`} id="main-sidebar">
        {sections.map(section => (
          <div key={section.title} className="sidebar__section">
            <div className="sidebar__section-title">{section.title}</div>
            {section.links.map(link => (
              <NavLink
                key={link.to}
                to={link.to}
                end={link.to === '/'}
                className={({ isActive }) =>
                  `sidebar__link ${isActive ? 'sidebar__link--active' : ''}`
                }
                onClick={onClose}
              >
                <span className="sidebar__icon">{link.icon}</span>
                {link.label}
              </NavLink>
            ))}
          </div>
        ))}
      </aside>

      <style>{`
        .sidebar-overlay {
          display: none;
          position: fixed;
          inset: 0;
          background: rgba(0,0,0,0.5);
          z-index: 85;
        }
        @media (max-width: 1024px) {
          .sidebar-overlay { display: block; }
        }
      `}</style>
    </>
  );
}
