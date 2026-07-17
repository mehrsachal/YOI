import type { WeaponData } from '../../types';

export const smgData: WeaponData = {
  id: 'smg',
  name: 'Sub Machine Gun (Chinese Type 56)',
  shortName: 'SMG Chinese',
  category: 'smg',
  origin: 'China',
  caliber: '7.62 x 39mm',
  description: 'The Chinese Type 56 SMG (based on the AK-47/AKS design) is a gas operated, selective fire weapon used by the Pakistan Army. It employs a long stroke piston gas operating system and is known for its ruggedness and reliability in all conditions.',
  specs: [
    { label: 'Caliber', value: '7.62 x 39', unit: 'mm' },
    { label: 'Operating Principle', value: 'Gas Operated (Long Stroke Piston)', unit: '' },
    { label: 'Modes of Fire', value: 'Semi-Auto / Full Auto', unit: '' },
    { label: 'Magazine Capacity', value: '30', unit: 'rounds' },
    { label: 'Muzzle Velocity', value: '715', unit: 'm/s' },
    { label: 'Effective Range', value: '400', unit: 'm' },
    { label: 'Rate of Fire (cyclic)', value: '600', unit: 'rpm' },
  ],
  mainParts: [
    { name: 'Receiver', subParts: [] },
    { name: 'Barrel', subParts: ['Gas Port', 'Front Sight'] },
    { name: 'Gas Piston (Long Stroke)', subParts: [] },
    { name: 'Bolt Carrier Group', subParts: ['Bolt', 'Bolt Carrier', 'Firing Pin'] },
    { name: 'Trigger Mechanism', subParts: ['Trigger', 'Selector Lever', 'Safety'] },
    { name: 'Recoil Spring', subParts: [] },
    { name: 'Stock', subParts: ['Folding/Fixed Stock'] },
    { name: 'Magazine (30-round)', subParts: [] },
    { name: 'Handguard', subParts: [] },
  ],
  characteristics: [
    'Gas operated with long stroke piston',
    'Selective fire: semi-automatic and full automatic',
    'Very reliable in all environmental conditions',
    'Robust and simple construction',
    'Effective for close to medium range engagements',
  ],
  additionalInfo: [
    {
      title: 'Uphill/Downhill Shooting',
      content: 'When firing uphill or downhill, the effective gravitational pull on the bullet is reduced. This causes the bullet to strike HIGH relative to the point of aim. The steeper the angle, the greater the error. Correction: Aim LOW for both uphill and downhill shots. Use the horizontal distance to the target (not the slant distance) for sight settings.',
    },
  ],
};

export const pistolsData: WeaponData = {
  id: 'pistols',
  name: '9mm Pistols (Norinco NP-22 & Sarsilmaz)',
  shortName: '9mm Pistols',
  category: 'pistol',
  origin: 'China / Turkey',
  caliber: '9 x 19mm Parabellum',
  description: 'The Pakistan Army uses 9mm pistols including the Norinco NP-22 (China) and Sarsilmaz (Turkey). These semi-automatic pistols are issued for personal protection and close-range engagement.',
  specs: [
    { label: 'Caliber', value: '9 x 19', unit: 'mm Parabellum' },
    { label: 'Operating Principle', value: 'Short Recoil / Blow Back', unit: '' },
    { label: 'Action', value: 'Semi-Automatic', unit: '' },
    { label: 'Effective Range', value: '50', unit: 'm' },
  ],
  mainParts: [
    { name: 'Slide', subParts: ['Firing Pin', 'Extractor', 'Rear Sight'] },
    { name: 'Barrel', subParts: ['Chamber', 'Rifling'] },
    { name: 'Frame/Receiver', subParts: ['Trigger Guard', 'Magazine Well'] },
    { name: 'Trigger Mechanism', subParts: ['Trigger', 'Sear', 'Hammer', 'Safety'] },
    { name: 'Recoil Spring', subParts: ['Guide Rod'] },
    { name: 'Magazine', subParts: ['Follower', 'Spring', 'Floor Plate'] },
    { name: 'Grip', subParts: [] },
  ],
  characteristics: [
    'Semi-automatic, magazine-fed',
    'Compact and easy to carry',
    'Effective for personal protection and CQB',
    'Can be fired from Isosceles and Weaver stances',
  ],
  additionalInfo: [
    {
      title: 'Isosceles Stance',
      content: 'Feet shoulder width apart, weight slightly forward over bent knees. Upper body squared to target. Both arms slightly bent or straight. Shoulders forward of hips. Master hand holds pistol, support hand wraps around.',
    },
    {
      title: 'Weaver Stance',
      content: 'Feet in traditional boxing position. Lean forward slightly at hips. Shooting side foot behind forward foot. Both arms bent or straight. Shoulders in line with forward knee.',
    },
  ],
};

export const aamgData: WeaponData = {
  id: 'aamg',
  name: '12.7mm Anti-Aircraft Machine Gun',
  shortName: '12.7mm AAMG',
  category: 'aamg',
  origin: 'Various',
  caliber: '12.7 x 108mm',
  description: 'The 12.7mm Anti-Aircraft Machine Gun is a heavy automatic weapon used for engaging both ground and aerial targets. It provides significant firepower at extended ranges and is employed in defensive, offensive, and sub-conventional operations.',
  specs: [
    { label: 'Caliber', value: '12.7', unit: 'mm' },
    { label: 'Type', value: 'Heavy Machine Gun', unit: '' },
    { label: 'Role', value: 'Anti-Aircraft / Ground Support', unit: '' },
  ],
  mainParts: [
    { name: 'Receiver', subParts: [] },
    { name: 'Barrel', subParts: [] },
    { name: 'Bolt Assembly', subParts: [] },
    { name: 'Feed Mechanism', subParts: [] },
    { name: 'Trigger Mechanism', subParts: [] },
    { name: 'Mount', subParts: [] },
    { name: 'AA Sight (97 x SW)', subParts: [] },
  ],
  characteristics: [
    'Heavy automatic weapon for sustained fire',
    'Effective against both ground and aerial targets',
    'Employed in defense, attack, and SCW operations',
    'Uses AA Sight (97 x SW) for aerial engagement',
  ],
  additionalInfo: [
    {
      title: 'AA Sight Squares',
      content: 'The AA sight has 7 squares formed by 3 parallel and 2 vertical bars. Squares 3, 5, 7 for fast aircraft. Squares 1, 2, 4, 6 for slow aircraft/helicopters. Sq 1: All aircraft diving at gun position. Sq 2: Helicopter flying over and away. Sq 3: Fast jets flying away. Sq 4: Helicopters flying right to left. Sq 5: Fast aircraft right to left ahead. Sq 6: Slow aircraft left to right ahead. Sq 7: Fast jets left to right.',
    },
    {
      title: 'Engagement Distance',
      content: 'When using any square, fire opens when the aircraft completely fits within the square. At this point, the distance is approximately 900m.',
    },
  ],
};

export const grenadesData: WeaponData = {
  id: 'grenades',
  name: 'Grenades (Arges, WP No.80, Super Energa)',
  shortName: 'Grenades',
  category: 'grenade',
  origin: 'Various',
  caliber: 'N/A',
  description: 'The SAC-81 covers three types of grenades: the Arges HE hand grenade (anti-personnel), Grenade No. 80 WP (white phosphorus smoke/incendiary), and the 75mm Super Energa HEAT rifle grenade (anti-tank).',
  specs: [
    { label: 'Arges — Total Weight', value: '485', unit: 'grams (±30)' },
    { label: 'Arges — Max Diameter', value: '1660', unit: 'mm (±1)' },
    { label: 'Arges — Total Length', value: '115', unit: 'mm (±2)' },
    { label: 'Arges — Filling Weight', value: '65', unit: 'grams (±5)' },
    { label: 'Arges — Filling Type', value: 'PETN Plasticized', unit: '' },
    { label: 'Arges — Delay Time', value: '4 sec', unit: '(±1 / -0.5 sec)' },
    { label: 'Arges — Lethal Radius', value: '15', unit: 'm' },
    { label: 'Arges — Steel Balls', value: '3500', unit: '(2.5-3mm dia)' },
    { label: 'Arges — Ball Velocity', value: '1800', unit: 'm/s' },
    { label: 'WP No.80 — Weight', value: '568', unit: 'grams' },
    { label: 'WP No.80 — Fuze Delay', value: '2.5-4', unit: 'seconds' },
    { label: 'Super Energa — Filling', value: 'PETN 90/10', unit: '(328 grams)' },
    { label: 'Super Energa — Booster', value: '35', unit: 'grams' },
    { label: 'Super Energa — Max Range', value: '550', unit: 'm' },
    { label: 'Super Energa — Effective Range', value: '150-200', unit: 'm' },
    { label: 'Super Energa — Propellant', value: '7.8g double base', unit: '' },
  ],
  mainParts: [
    { name: 'Arges HE Grenade', subParts: ['Plastic Body (Polyethylene)', 'Core (Polystyrene, 3500 steel balls)', 'PETN Filling (65g)', 'Firing Mechanism (Striker, Cocking Spring, Spring Bolt, Safety Lever, Safety Pin, Ring)', 'Detonator Assembly (Cap, Delay Tube, Detonator)'] },
    { name: 'Grenade No. 80 (WP)', subParts: ['Tinned Plate Body', 'White Phosphorus Filling', 'Mechanism Holder (Striker & Spring)', 'Striker Lever', 'Safety Pin', 'Igniter Set (0.22-inch cap, cap chamber, safety fuze, detonator)'] },
    { name: 'Super Energa 75mm HEAT', subParts: ['Aluminium Alloy Body (Ogive + Metal Cone)', 'Tail Tube with Fin Assembly', 'Percussion Inertia Fuze', 'Hollow Charge Filling (PETN 328g)', 'Booster Relay Charge (35g)', 'Main Detonator (15.5g)', 'Propellant Motor (7.8g)', 'Safety Shroud', 'Cartridge RG 7.62mm (1.6g propellant)'] },
  ],
  characteristics: [
    'Arges: Anti-personnel, plastic body, 3500 steel balls at 1800 m/s, 15m lethal radius',
    'WP No. 80: White phosphorus smoke/incendiary, ignites on exposure to air',
    'Super Energa: Anti-tank HEAT rifle grenade, fired from rifle using special 7.62mm cartridge',
    'All grenades have safety devices for storage and transport',
  ],
  additionalInfo: [
    {
      title: 'Arges Delay Composition',
      content: 'Barium Chromate ~60%, Potassium Perchlorate ~14%, Nickel Zirconium ~26%. Delay: 3.5 to 5 seconds.',
    },
    {
      title: 'WP Recognition Markings',
      content: '1) 12.5mm white band about 50mm from shoulder. 2) Letters "WP" in black 9.5mm high. 3) Letters "WP" in white 19mm high on opposite sides.',
    },
    {
      title: 'Super Energa Safety Devices',
      content: '1) Plastic Protecting Cap (nose cover) — removed at time of firing only. 2) Six Steel Balls — hold striker and detonator housing via arming sleeve. 3) Safety Shroud — blocks passage between detonators during storage/transport.',
    },
    {
      title: 'Throwing Principles',
      content: '1) Throw at high angle. 2) Keep left hand in line with target. 3) Watch the fall of grenade. 4) Left-handed throwers stand on right side of line.',
    },
  ],
};
