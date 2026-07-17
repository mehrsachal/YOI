import { useState } from 'react';
import { Link } from 'react-router-dom';
import InteractiveTrajectory from '../components/Visualizations/InteractiveTrajectory';
import InteractiveOpMech from '../components/Visualizations/InteractiveOpMech';
import InteractiveBeatenZone from '../components/Visualizations/InteractiveBeatenZone';

import InteractiveNvdSimulator from '../components/Visualizations/InteractiveNvdSimulator';

const DEFINITIONS = [
  { term: 'Barrel', def: 'A metallic tube which can be rifled or smooth.', cat: 'barrel' },
  { term: 'Bore', def: 'Inner/interior portion of the barrel.', cat: 'barrel' },
  { term: 'Cold Bore / Barrel Zero', def: 'Distinct from where a series of bullets may impact after a rifle\'s barrel has warmed up, the cold barrel zero applies only to the exact impact of the very first round, which could be several inches different. Military snipers zero their weapons for the cold barrel.', cat: 'barrel' },
  { term: 'Chamber', def: 'The rear portion of the barrel which holds the cartridge.', cat: 'barrel' },
  { term: 'Rifling', def: 'A barrel is said to be rifled when it has spiral grooves cut down the bore. There are lands between the grooves.', cat: 'barrel' },
  { term: 'Caliber', def: 'The standard diameter of the bore excluding depth of the rifling grooves.', cat: 'barrel' },
  { term: 'Twist', def: 'The distance, measured in calibers, in which the grooves make one complete turn/circuit of the bore.', cat: 'barrel' },
  { term: 'Axis of the Barrel', def: 'An imaginary line through the center of the bore from breech to muzzle.', cat: 'barrel' },
  { term: 'Line of Sight', def: 'A straight line from the firer\'s eye through the sights to the point of aim.', cat: 'barrel' },
  { term: 'Line of Fire', def: 'The direction to the target from the muzzle of a weapon.', cat: 'barrel' },
  { term: 'Jump', def: 'The angle in the vertical plane between the line of departure and the axis of the bore before firing.', cat: 'barrel' },
  { term: 'Muzzle Velocity (MV)', def: 'The velocity with which a projectile leaves the muzzle of a weapon.', cat: 'barrel' },
  { term: 'Field of Fire', def: 'Areas which are relatively free of obstructions into which a firer can fire, ideally up to the max range of his weapon.', cat: 'barrel' },
  { term: 'Line of Departure', def: 'The direction of motion of the projectile as it leaves the muzzle. It is tangent to the trajectory at the muzzle.', cat: 'motion' },
  { term: 'Trajectory', def: 'The curved path taken by a bullet through its flight. Main factors: velocity of bullet, gravity, rotation, air resistance.', cat: 'motion' },
  { term: 'Rising Branch', def: 'The part of trajectory where the bullet departs the muzzle and rises until it reaches maximum height.', cat: 'motion' },
  { term: 'Mid-Range Trajectory (MRT)', def: 'The height the bullet reaches at true midrange to the target. On a 1000 yd shot, MRT occurs at exactly 500 yds.', cat: 'motion' },
  { term: 'Maximum Ordinate', def: 'The highest point the bullet reaches along its trajectory, measured from the line of sight. Occurs farther than MRT as range increases.', cat: 'motion' },
  { term: 'Falling Branch', def: 'The part of the trajectory where the bullet, after max ordinate, begins its downward flight to the target.', cat: 'motion' },
  { term: 'Remaining Velocity', def: 'The velocity of the projectile at any specified point on the trajectory.', cat: 'motion' },
  { term: 'Drift', def: 'The movement of a projectile in a lateral direction at right angle to its principal motion, caused by air reacting with the spinning projectile.', cat: 'motion' },
  { term: 'Dangerous Space', def: 'At a particular range, the distance between the point of first catch and the first graze.', cat: 'motion' },
  { term: 'Beaten Zone', def: 'A long elliptical pattern formed by the intersection of the cone of fire with the ground. Thickest in the middle; as range increases, length decreases and width increases.', cat: 'motion' },
  { term: 'Cone of Fire', def: 'When a burst is fired, the group of trajectories formed by a single burst. Caused by vibration, ammo variation, and barrel conditions.', cat: 'motion' },
  { term: 'Case', def: 'Solid drawn brass with groove/rim for extraction. Holds bullet, primer/cap, and propellant. Corrosion resistant.', cat: 'ammo' },
  { term: 'Charge', def: 'Smokeless powder (nitrocellulose or cordite) that burns uniformly producing gases at high temp and pressure. No oxygen needed.', cat: 'ammo' },
  { term: 'Ball Bullets', def: 'Soft cores, used against personnel only.', cat: 'ammo' },
  { term: 'AP Bullets', def: 'Hardened steel core, fired against vehicles, weapons and armored targets.', cat: 'ammo' },
  { term: 'Tracer Bullets', def: 'Contain compound (barium nitrate) in rear portion that burns in flight, making trajectory visible.', cat: 'ammo' },
  { term: 'Incendiary Bullets', def: 'Contain a mixture that can be set on fire on impact. Used against targets that burn readily.', cat: 'ammo' },
  { term: 'AP Incendiary Bullets', def: 'Steel core + incendiary mixture. Can pierce soft vehicles and set them on fire.', cat: 'ammo' },
  { term: 'Internal Ballistics', def: 'Deals with firing of projectile down the bore — trigger pull to muzzle exit. Includes chamber pressure, lock time, headspace.', cat: 'ballistics' },
  { term: 'Intermediate Ballistics', def: 'Motion of projectile from muzzle exit until gas pressure drops to atmospheric pressure.', cat: 'ballistics' },
  { term: 'External Ballistics', def: 'Projectile in flight from muzzle clearing to target impact. Includes MET and ENV corrections.', cat: 'ballistics' },
  { term: 'Terminal Ballistics', def: 'Path and effects of bullet once it contacts any medium other than air.', cat: 'ballistics' },
  { term: 'Wound Ballistics', def: 'The damage caused by ammunition to the target.', cat: 'ballistics' },
  { term: 'Hang Fire', def: 'When the time interval between releasing the firing mechanism and projectile exit is longer than customary. Caused by faulty primer or propellant ignition.', cat: 'misc' },
  { term: 'Obturation', def: 'The locking or sealing off the breech to prevent gas leakage when a cartridge is fired.', cat: 'misc' },
  { term: 'Cooking Off', def: 'Self-ignition of a cartridge inside an overheated chamber.', cat: 'misc' },
  { term: 'Run Away Gun', def: 'When an automatic weapon keeps firing against the wishes of the firer.', cat: 'misc' },
  { term: 'Yaw', def: 'The angle between the axis of the projectile and its trajectory. Large yaw = low stability, short range, inaccurate.', cat: 'misc' },
];

const BALLISTICS_SEQ = [
  { name: 'Force of Explosion', desc: 'Propellant ignited by primer. Gas pressure builds to 60,000 PSI. Temperature reaches 2500-3000°C. Bullet driven out with tremendous force.' },
  { name: 'Band Engraving', desc: 'Gas pressure rises; lands of rifling cut grooves in the driving band. Process creates "band engraving" — the initial engagement of projectile with rifling.' },
  { name: 'Short Start Pressure', desc: 'Pressure required to overcome driving band resistance and start projectile movement up the bore.' },
  { name: 'Peak Pressure', desc: 'Maximum pressure reached when gas increase balances velocity increase. The highest point of internal pressure.' },
  { name: 'All Burnt Point', desc: 'All propellant consumed; no more gas being evolved. Velocity continues to increase from remaining pressure.' },
  { name: 'Effect of Rifling', desc: 'Bullet engages lands and grooves, acquiring spin. Greater chamber pressure and groove rate = greater bullet spin.' },
  { name: 'Recoil', desc: 'Equal pressure on breech drives weapon backward. Loose bolt/barrel mounting reduces felt recoil vs rigid fastening.' },
];

const EXTERNAL_FACTORS = [
  { name: 'Gravity', desc: 'Draws bullet downward at 32.2 ft/sec². Without gravity, bullet would travel in a straight line.' },
  { name: 'Air Resistance', desc: 'A 7.62mm bullet at 2700 ft/sec would travel 5000 yds in 5.5 sec without air. With air resistance, it takes 24 sec.' },
  { name: 'Wind', desc: 'Affects bullet flight considerably at all ranges except the very shortest.' },
  { name: 'Fore Body Drag', desc: 'Energy needed to displace air creates continuous drain on kinetic energy. Compression wave forms pressure wave.' },
  { name: 'Shock Wave', desc: 'When projectile exceeds speed of sound, compression waves bunch up creating a shock wave at the nose.' },
  { name: 'Base Drag', desc: 'Turbulence (wake) created behind the projectile due to low pressure region.' },
  { name: 'Skin Friction', desc: 'Additional resistance from air adhering to projectile surface.' },
];

const OP_MECHS = [
  {
    name: 'Simple Blowback',
    desc: 'Totally unlocked breech. Relies on breech block mass and return spring strength. Suitable for low-power cartridges.',
    examples: 'Pistols, some SMGs',
    advs: ['Cheap', 'Simple', 'Robust'],
    disadvs: ['Heavy moving parts', 'Fouling in chamber/body'],
  },
  {
    name: 'Delayed Blowback',
    desc: 'Mechanical delay imposed (roller or lever system) to prevent rapid bolt movement while pressure is high. Allows lighter bolt mass.',
    examples: 'G3A3, MP5A2 (Heckler & Koch roller system)',
    advs: ['Reliable', 'No separate gas system'],
    disadvs: ['Heavy moving parts', 'More fouling', 'More jerk', 'Low adaptability for different ammo'],
  },
  {
    name: 'Gas Operated (Long Stroke Piston)',
    desc: 'Piston connected directly to breech block, controls position at all times. Considerable recoiling mass.',
    examples: 'SMG Chinese (AK-47), US M-16',
    advs: ['Light working parts', 'Power controlled by gas regulators'],
    disadvs: ['Fouling', 'Erosion', 'Unsuitable for closed vehicles'],
  },
  {
    name: 'Gas Operated (Short Stroke Piston)',
    desc: 'Piston moves only a few mm, imparts energy to operating rod. Avoids large center of mass changes during firing.',
    examples: 'Various modern rifles',
    advs: ['Less effect on aim', 'Less fouling than long stroke'],
    disadvs: ['More complex mechanism'],
  },
  {
    name: 'Short Recoil',
    desc: 'Breech locked with barrel; both recoil together a short distance, then separate. Barrel returns via return spring.',
    examples: 'MG1A3',
    advs: ['High rate of fire', 'Reliable', 'No fouling', 'Suitable for closed vehicles'],
    disadvs: ['Heavy moving parts', 'Lacks power in small calibers'],
  },
];

const CATS = [
  { id: 'all', label: 'All' },
  { id: 'barrel', label: 'Barrel & Sights' },
  { id: 'motion', label: 'Projectile Motion' },
  { id: 'ammo', label: 'Ammunition' },
  { id: 'ballistics', label: 'Ballistics Types' },
  { id: 'misc', label: 'Miscellaneous' },
];

type Section = 'definitions' | 'ballistics' | 'external' | 'mechanisms' | 'nvd';

export default function TheoryPage() {
  const [activeCat, setActiveCat] = useState('all');
  const [search, setSearch] = useState('');
  const [section, setSection] = useState<Section>('definitions');

  const filtered = DEFINITIONS.filter(d =>
    (activeCat === 'all' || d.cat === activeCat) &&
    (search === '' || d.term.toLowerCase().includes(search.toLowerCase()) || d.def.toLowerCase().includes(search.toLowerCase()))
  );

  return (
    <div className="page-container animate-fade-in">
      <div className="page-header">
        <div className="page-header__breadcrumb">
          <Link to="/">Home</Link> <span>›</span> <span>Theory of SAs</span>
        </div>
        <h1 className="page-header__title">Theory of Small Arms</h1>
        <p className="page-header__desc">
          Master ballistics, ammunition types, operating mechanisms, and core definitions from the SAC-81 manual.
        </p>
      </div>

      <div className="tab-group">
        <button className={`tab-group__tab ${section === 'definitions' ? 'tab-group__tab--active' : ''}`} onClick={() => setSection('definitions')}>Definitions</button>
        <button className={`tab-group__tab ${section === 'ballistics' ? 'tab-group__tab--active' : ''}`} onClick={() => setSection('ballistics')}>Internal Ballistics</button>
        <button className={`tab-group__tab ${section === 'external' ? 'tab-group__tab--active' : ''}`} onClick={() => setSection('external')}>External Ballistics</button>
        <button className={`tab-group__tab ${section === 'mechanisms' ? 'tab-group__tab--active' : ''}`} onClick={() => setSection('mechanisms')}>Op Mechanisms</button>
        <button className={`tab-group__tab ${section === 'nvd' ? 'tab-group__tab--active' : ''}`} onClick={() => setSection('nvd')}>Night Vision</button>
      </div>

      <div className="animate-fade-in" key={section}>
        {section === 'definitions' && (
          <>
            {/* Search & Filter */}
            <div style={{ display: 'flex', gap: 'var(--space-md)', marginBottom: 'var(--space-lg)', flexWrap: 'wrap', alignItems: 'center' }}>
              <input
                type="text"
                placeholder="Search definitions..."
                value={search}
                onChange={e => setSearch(e.target.value)}
                style={{
                  flex: '1 1 200px',
                  padding: '10px 16px',
                  borderRadius: 'var(--radius-md)',
                  border: '1px solid var(--border-medium)',
                  background: 'var(--bg-tertiary)',
                  color: 'var(--text-primary)',
                  fontFamily: 'var(--font-body)',
                  fontSize: '0.9rem',
                  outline: 'none',
                }}
              />
              <div className="tab-group" style={{ marginBottom: 0 }}>
                {CATS.map(cat => (
                  <button
                    key={cat.id}
                    className={`tab-group__tab ${activeCat === cat.id ? 'tab-group__tab--active' : ''}`}
                    onClick={() => setActiveCat(cat.id)}
                  >
                    {cat.label}
                  </button>
                ))}
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '2px', borderRadius: 'var(--radius-lg)', overflow: 'hidden', border: '1px solid var(--border-dim)' }}>
              {filtered.map((d, i) => (
                <div key={i} style={{ display: 'flex', gap: 'var(--space-lg)', padding: '14px 20px', background: 'var(--bg-tertiary)', alignItems: 'flex-start' }}>
                  <span style={{ fontFamily: 'var(--font-heading)', fontWeight: 600, color: 'var(--accent-green)', minWidth: '180px', flexShrink: 0, fontSize: '0.95rem' }}>{d.term}</span>
                  <span style={{ fontSize: '0.9rem', lineHeight: 1.6 }}>{d.def}</span>
                </div>
              ))}
            </div>
            <p style={{ marginTop: 'var(--space-md)', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Showing {filtered.length} of {DEFINITIONS.length} definitions
            </p>
          </>
        )}

        {section === 'ballistics' && (
          <div className="content-section">
            <h2 className="section-title">Sequence of Internal Ballistic Events<span className="section-title__line" /></h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
              {BALLISTICS_SEQ.map((item, i) => (
                <div key={i} style={{ display: 'flex', gap: 'var(--space-md)', padding: '18px 20px', background: 'var(--bg-tertiary)', borderRadius: 'var(--radius-md)', alignItems: 'flex-start' }}>
                  <span style={{ fontFamily: 'var(--font-mono)', fontWeight: 700, color: 'var(--accent-cyan)', background: 'var(--accent-cyan-glow)', width: 32, height: 32, borderRadius: 'var(--radius-full)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, fontSize: '0.85rem' }}>{i + 1}</span>
                  <div>
                    <div style={{ fontWeight: 600, color: 'var(--text-primary)', marginBottom: 4 }}>{item.name}</div>
                    <div style={{ fontSize: '0.9rem', lineHeight: 1.7 }}>{item.desc}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {section === 'external' && (
          <div className="content-section">
            <h2 className="section-title">External Ballistics<span className="section-title__line" /></h2>
            <InteractiveTrajectory />
            <br />
            <InteractiveBeatenZone />
            <br />
            <h3 className="section-title" style={{ fontSize: '1.2rem', marginTop: 'var(--space-xl)' }}>Key Factors<span className="section-title__line" /></h3>
            <div className="grid-auto">
              {EXTERNAL_FACTORS.map((f, i) => (
                <div key={i} className="glass-card" style={{ padding: 'var(--space-lg)' }}>
                  <h3 style={{ fontSize: '1rem', marginBottom: 'var(--space-sm)', color: 'var(--accent-amber)' }}>{f.name}</h3>
                  <p style={{ fontSize: '0.9rem', lineHeight: 1.7 }}>{f.desc}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {section === 'mechanisms' && (
          <div className="content-section">
            <h2 className="section-title">Operating Mechanisms Simulator<span className="section-title__line" /></h2>
            <InteractiveOpMech />
            <br />
            <h3 className="section-title" style={{ fontSize: '1.2rem', marginTop: 'var(--space-xl)' }}>Mechanisms Comparison<span className="section-title__line" /></h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-md)' }}>
              {OP_MECHS.map((m, i) => (
                <div key={i} className="glass-card" style={{ padding: 'var(--space-xl)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 'var(--space-sm)', marginBottom: 'var(--space-md)' }}>
                    <h3 style={{ fontSize: '1.1rem' }}>{m.name}</h3>
                    <span className="badge badge-cyan">{m.examples}</span>
                  </div>
                  <p style={{ fontSize: '0.9rem', lineHeight: 1.7, marginBottom: 'var(--space-md)' }}>{m.desc}</p>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-md)' }}>
                    <div>
                      <div style={{ fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.08em', color: 'var(--accent-green)', fontWeight: 600, marginBottom: 8 }}>Advantages</div>
                      <ul style={{ listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 4 }}>
                        {m.advs.map((a, j) => <li key={j} style={{ fontSize: '0.85rem', paddingLeft: 14, position: 'relative' }}>
                          <span style={{ position: 'absolute', left: 0, color: 'var(--accent-green)' }}>+</span>{a}
                        </li>)}
                      </ul>
                    </div>
                    <div>
                      <div style={{ fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.08em', color: 'var(--accent-red)', fontWeight: 600, marginBottom: 8 }}>Disadvantages</div>
                      <ul style={{ listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 4 }}>
                        {m.disadvs.map((d, j) => <li key={j} style={{ fontSize: '0.85rem', paddingLeft: 14, position: 'relative' }}>
                          <span style={{ position: 'absolute', left: 0, color: 'var(--accent-red)' }}>−</span>{d}
                        </li>)}
                      </ul>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {section === 'nvd' && (
          <div className="content-section">
            <h2 className="section-title">Night Vision & Optics<span className="section-title__line" /></h2>
            <InteractiveNvdSimulator />
            <br />
            <div className="glass-card" style={{ padding: 'var(--space-xl)' }}>
              <h3 style={{ marginBottom: 'var(--space-md)' }}>Key Specifications (SAC-81)</h3>
              <ul style={{ listStyle: 'none', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <li style={{ background: 'rgba(0,0,0,0.05)', padding: '12px', borderRadius: '8px' }}><strong style={{ color: 'var(--accent-green)' }}>AN/PVS-5A (I²):</strong> Requires ambient light. Detection range drops heavily in starlight versus moonlight.</li>
                <li style={{ background: 'rgba(0,0,0,0.05)', padding: '12px', borderRadius: '8px' }}><strong style={{ color: 'var(--accent-amber)' }}>SKUA-LR (Thermal):</strong> Detects heat differentials. Can detect a human target at up to 2.5km regardless of ambient light.</li>
              </ul>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
