import type { QuizQuestion } from '../types';
import { autoQuizQuestions } from './autoQuizData';

export const allQuizQuestions: QuizQuestion[] = [
  ...autoQuizQuestions,
  // ── THEORY ──
  { id: 'T01', type: 'fill-blank', category: 'theory', difficulty: 'soldier', question: 'The inner/interior portion of the barrel is called the _____.', correctAnswer: 'bore', hint: 'Not the chamber', source: 'P5' },
  { id: 'T02', type: 'mcq', category: 'theory', difficulty: 'recruit', question: 'What is the standard diameter of the bore excluding depth of rifling grooves?', options: ['Bore', 'Caliber', 'Twist', 'Rifling'], correctAnswer: 'Caliber', source: 'P5' },
  { id: 'T03', type: 'fill-blank', category: 'theory', difficulty: 'marksman', question: 'Drift is the movement of a projectile in a _____ direction at right angle to its principal motion.', correctAnswer: 'lateral', source: 'P8' },
  { id: 'T04', type: 'mcq', category: 'theory', difficulty: 'recruit', question: 'Which ballistic type deals with the projectile from trigger pull to muzzle exit?', options: ['Internal Ballistics', 'External Ballistics', 'Terminal Ballistics', 'Wound Ballistics'], correctAnswer: 'Internal Ballistics', source: 'P10' },
  { id: 'T05', type: 'true-false', category: 'theory', difficulty: 'recruit', question: 'External ballistics deals with the path and effects of bullet once it contacts a medium other than air.', isTrue: false, explanation: 'External ballistics deals with projectile in flight. Terminal ballistics deals with contact effects.', source: 'P10' },
  { id: 'T06', type: 'fill-blank', category: 'theory', difficulty: 'marksman', question: 'The highest point the bullet reaches along its trajectory, measured from the line of sight, is called _____ _____.', correctAnswer: 'maximum ordinate', source: 'P8' },
  { id: 'T07', type: 'mcq', category: 'theory', difficulty: 'soldier', question: 'What is the self-ignition of a cartridge inside an overheated chamber called?', options: ['Hang Fire', 'Cooking Off', 'Run Away Gun', 'Obturation'], correctAnswer: 'Cooking Off', source: 'P12' },
  { id: 'T08', type: 'mcq', category: 'theory', difficulty: 'soldier', question: 'What does the beaten zone shape look like?', options: ['Circle', 'Long elliptical pattern', 'Triangle', 'Square'], correctAnswer: 'Long elliptical pattern', source: 'P14' },
  { id: 'T09', type: 'true-false', category: 'theory', difficulty: 'soldier', question: 'As range increases, the beaten zone becomes longer and narrower.', isTrue: false, explanation: 'As range increases, beaten zone becomes SHORTER and WIDER.', source: 'P14' },
  { id: 'T10', type: 'fill-blank', category: 'theory', difficulty: 'marksman', question: 'The locking or sealing off the breech to prevent gas leakage when a cartridge is fired is called _____.', correctAnswer: 'obturation', source: 'P12' },
  { id: 'T11', type: 'mcq', category: 'theory', difficulty: 'recruit', question: 'What type of bullets contain barium nitrate that burns in flight making the trajectory visible?', options: ['Ball', 'AP', 'Tracer', 'Incendiary'], correctAnswer: 'Tracer', source: 'P10' },
  { id: 'T12', type: 'fill-blank', category: 'theory', difficulty: 'soldier', question: 'A 7.62mm bullet at 2700 ft/sec would travel 5000 yds in _____ seconds without air resistance.', correctAnswer: '5.5', source: 'P15' },
  { id: 'T13', type: 'mcq', category: 'theory', difficulty: 'soldier', question: 'When an automatic weapon keeps firing against the wishes of the firer, it is called:', options: ['Cooking Off', 'Run Away Gun', 'Hang Fire', 'Misfire'], correctAnswer: 'Run Away Gun', source: 'P12' },
  { id: 'T14', type: 'true-false', category: 'theory', difficulty: 'recruit', question: 'The charge (smokeless powder) requires oxygen to burn.', isTrue: false, explanation: 'Smokeless powder burns without external oxygen.', source: 'P10' },

  // ── G3A3 ──
  { id: 'G01', type: 'fill-blank', category: 'g3a3', difficulty: 'soldier', question: 'The G3A3 operates on the _____ _____ principle using roller locking.', correctAnswer: 'delayed blowback', source: 'P30' },
  { id: 'G02', type: 'mcq', category: 'g3a3', difficulty: 'recruit', question: 'What is the caliber of the G3A3?', options: ['5.56mm', '7.62mm', '9mm', '12.7mm'], correctAnswer: '7.62mm', source: 'P30' },
  { id: 'G03', type: 'fill-blank', category: 'g3a3', difficulty: 'marksman', question: 'The G3A3 has _____ grooves in its barrel.', correctAnswer: '4', source: 'P30' },
  { id: 'G04', type: 'mcq', category: 'g3a3', difficulty: 'recruit', question: 'Magazine capacity of the G3A3 is:', options: ['10 rounds', '15 rounds', '20 rounds', '30 rounds'], correctAnswer: '20 rounds', source: 'P30' },
  { id: 'G05', type: 'mcq', category: 'g3a3', difficulty: 'soldier', question: 'The muzzle velocity of the G3A3 is approximately:', options: ['600 m/s', '715 m/s', '800 m/s', '950 m/s'], correctAnswer: '800 m/s', source: 'P30' },
  { id: 'G06', type: 'fill-blank', category: 'g3a3', difficulty: 'marksman', question: 'The G3A3 has a maximum range of _____ meters.', correctAnswer: '3700', source: 'P30' },
  { id: 'G07', type: 'true-false', category: 'g3a3', difficulty: 'recruit', question: 'The G3A3 is a gas operated weapon.', isTrue: false, explanation: 'The G3A3 uses DELAYED BLOWBACK with roller locking, not gas operation.', source: 'P30' },
  { id: 'G08', type: 'mcq', category: 'g3a3', difficulty: 'soldier', question: 'The effective range of G3A3 is:', options: ['200m', '300m', '400m', '600m'], correctAnswer: '400m', source: 'P30' },
  { id: 'G09', type: 'order-steps', category: 'g3a3', difficulty: 'marksman', question: 'Arrange the cycle of operation of G3A3 in correct order:', correctOrder: ['Cocking', 'Feeding', 'Chambering', 'Locking', 'Firing', 'Unlocking', 'Extracting', 'Ejecting'], source: 'P35' },
  { id: 'G10', type: 'fill-blank', category: 'g3a3', difficulty: 'soldier', question: 'The G3A3 rear sight is a _____ type with aperture settings.', correctAnswer: 'drum', source: 'P35' },
  { id: 'G11', type: 'fill-blank', category: 'g3a3', difficulty: 'marksman', question: 'The total weight of the G3A3 unloaded is _____ kg.', correctAnswer: '4.4', source: 'P30' },
  { id: 'G12', type: 'mcq', category: 'g3a3', difficulty: 'soldier', question: 'Zeroing of the G3A3 is done at what range?', options: ['50m', '100m', '200m', '300m'], correctAnswer: '100m', source: 'P42' },

  // ── MP5A2 ──
  { id: 'M01', type: 'mcq', category: 'mp5a2', difficulty: 'recruit', question: 'What is the caliber of the MP5A2?', options: ['5.56mm', '7.62mm', '9mm Parabellum', '.45 ACP'], correctAnswer: '9mm Parabellum', source: 'P65' },
  { id: 'M02', type: 'fill-blank', category: 'mp5a2', difficulty: 'soldier', question: 'The MP5A2 has _____ modes of fire: S, E, and F.', correctAnswer: '3', source: 'P65' },
  { id: 'M03', type: 'mcq', category: 'mp5a2', difficulty: 'soldier', question: 'The CQB firing range for MP5A2 is:', options: ['10m', '25m', '50m', '100m'], correctAnswer: '25m', source: 'P65' },
  { id: 'M04', type: 'fill-blank', category: 'mp5a2', difficulty: 'marksman', question: 'The MP5A2 magazine capacity is _____ rounds.', correctAnswer: '30', source: 'P65' },
  { id: 'M05', type: 'true-false', category: 'mp5a2', difficulty: 'soldier', question: 'The MP5A2 uses a gas operated mechanism.', isTrue: false, explanation: 'The MP5A2 uses DELAYED BLOWBACK with roller locking, similar to the G3A3.', source: 'P65' },
  { id: 'M06', type: 'fill-blank', category: 'mp5a2', difficulty: 'marksman', question: 'Each click of the MP5A2 rear sight elevation moves MPI _____ cm at 25m.', correctAnswer: '1.5', source: 'P72' },
  { id: 'M07', type: 'mcq', category: 'mp5a2', difficulty: 'soldier', question: 'The CQB time limit for MP5A2 qualification is:', options: ['10 sec', '15 sec', '20 sec', '30 sec'], correctAnswer: '20 sec', source: 'P65' },

  // ── MG1A3 ──
  { id: 'MG01', type: 'mcq', category: 'mg1a3', difficulty: 'recruit', question: 'The MG1A3 operates on which principle?', options: ['Gas Operated', 'Delayed Blowback', 'Short Recoil', 'Long Recoil'], correctAnswer: 'Short Recoil', source: 'P73' },
  { id: 'MG02', type: 'fill-blank', category: 'mg1a3', difficulty: 'marksman', question: 'The recoil distance of the MG1A3 barrel and bolt assembly is _____ mm.', correctAnswer: '21', source: 'P73' },
  { id: 'MG03', type: 'mcq', category: 'mg1a3', difficulty: 'soldier', question: 'The cyclic rate of fire of MG1A3 is:', options: ['500-600 rpm', '700-900 rpm', '1100-1300 rpm', '1500+ rpm'], correctAnswer: '1100-1300 rpm', source: 'P73' },
  { id: 'MG04', type: 'fill-blank', category: 'mg1a3', difficulty: 'soldier', question: 'In slow rate of fire, the MG1A3 fires _____ rounds per minute.', correctAnswer: '70', source: 'P73' },
  { id: 'MG05', type: 'fill-blank', category: 'mg1a3', difficulty: 'marksman', question: 'The weight of the MG1A3 gun alone is _____ kg.', correctAnswer: '10.5', source: 'P73' },
  { id: 'MG06', type: 'mcq', category: 'mg1a3', difficulty: 'soldier', question: 'Effective range of MG1A3 in MG role is:', options: ['500m', '1000m', '1500m', '2000m'], correctAnswer: '2000m', source: 'P73' },
  { id: 'MG07', type: 'fill-blank', category: 'mg1a3', difficulty: 'marksman', question: 'Grazing fire is effective up to _____ m over level ground.', correctAnswer: '700', source: 'P80' },
  { id: 'MG08', type: 'mcq', category: 'mg1a3', difficulty: 'soldier', question: 'In LMG role, bursts are limited to:', options: ['3-5 rounds', '10-15 rounds', '20-30 rounds', 'Unlimited'], correctAnswer: '3-5 rounds', source: 'P82' },
  { id: 'MG09', type: 'fill-blank', category: 'mg1a3', difficulty: 'marksman', question: 'The weight of the MG1A3 steel mount is _____ kg.', correctAnswer: '14.06', source: 'P73' },
  { id: 'MG10', type: 'true-false', category: 'mg1a3', difficulty: 'recruit', question: 'The MG1A3 is belt fed.', isTrue: true, explanation: 'The MG1A3 is belt fed, capable of sustained fire.', source: 'P73' },
  { id: 'MG11', type: 'mcq', category: 'mg1a3', difficulty: 'soldier', question: 'The maximum range of MG1A3 is:', options: ['2000m', '3000m', '3700m', '4000m'], correctAnswer: '4000m', source: 'P73' },
  { id: 'MG12', type: 'fill-blank', category: 'mg1a3', difficulty: 'marksman', question: 'In the MG1A3, 1 click of elevation equals _____ inch at 100m.', correctAnswer: '1', source: 'P90' },

  // ── SNIPERS ──
  { id: 'S01', type: 'fill-blank', category: 'snipers', difficulty: 'soldier', question: 'The SC-76 Thunderbolt has an effective range of _____ meters.', correctAnswer: '1000', source: 'P148' },
  { id: 'S02', type: 'mcq', category: 'snipers', difficulty: 'recruit', question: 'The SC-76 magazine capacity is:', options: ['5 rounds', '8 rounds', '10 rounds', '20 rounds'], correctAnswer: '10 rounds', source: 'P148' },
  { id: 'S03', type: 'fill-blank', category: 'snipers', difficulty: 'marksman', question: 'The SC-76 barrel length is _____ inches.', correctAnswer: '27', source: 'P148' },
  { id: 'S04', type: 'fill-blank', category: 'snipers', difficulty: 'marksman', question: 'The Meopta ZD 6-24x56 has a main tube size of _____ mm.', correctAnswer: '30', source: 'P151' },
  { id: 'S05', type: 'mcq', category: 'snipers', difficulty: 'soldier', question: 'The SSR ZFM 6x42 telescope has how many times magnification?', options: ['4x', '6x', '8x', '10x'], correctAnswer: '6x', source: 'P155' },
  { id: 'S06', type: 'fill-blank', category: 'snipers', difficulty: 'marksman', question: 'In the ZFM 6x42 scope, 1 elevation click = _____ mil.', correctAnswer: '0.25', source: 'P155' },
  { id: 'S07', type: 'true-false', category: 'snipers', difficulty: 'soldier', question: 'The width of the inner horizontal centerline of the ZFM reticle is 1.5 mils.', isTrue: false, explanation: 'Inner horizontal is 0.15 mils. OUTER horizontal and vertical are 1.5 mils.', source: 'P156' },
  { id: 'S08', type: 'fill-blank', category: 'snipers', difficulty: 'marksman', question: 'The ghillie suit should use natural vegetation at a ratio of _____ to _____ percent natural to man-made.', correctAnswer: '60', source: 'P160' },
  { id: 'S09', type: 'mcq', category: 'snipers', difficulty: 'soldier', question: 'The three basic methods of camouflage are:', options: ['Hiding, Blending, Deceiving', 'Cover, Concealment, Movement', 'Static, Dynamic, Adaptive', 'Natural, Artificial, Combined'], correctAnswer: 'Hiding, Blending, Deceiving', source: 'P159' },
  { id: 'S10', type: 'fill-blank', category: 'snipers', difficulty: 'marksman', question: 'The SSR magazine capacity is _____ rounds compared to SC-76\'s 10 rounds.', correctAnswer: '5', source: 'P152' },

  // ── GRENADES ──
  { id: 'GR01', type: 'fill-blank', category: 'grenades', difficulty: 'soldier', question: 'The Arges grenade contains _____ steel balls.', correctAnswer: '3500', source: 'P139' },
  { id: 'GR02', type: 'mcq', category: 'grenades', difficulty: 'recruit', question: 'The filling type of the Arges grenade is:', options: ['RDX', 'TNT', 'PETN Plasticized', 'C-4'], correctAnswer: 'PETN Plasticized', source: 'P139' },
  { id: 'GR03', type: 'fill-blank', category: 'grenades', difficulty: 'marksman', question: 'The lethal radius of the Arges grenade is _____ meters.', correctAnswer: '15', source: 'P139' },
  { id: 'GR04', type: 'fill-blank', category: 'grenades', difficulty: 'soldier', question: 'Arges steel balls burst outwards with a speed of _____ m/s.', correctAnswer: '1800', source: 'P139' },
  { id: 'GR05', type: 'mcq', category: 'grenades', difficulty: 'soldier', question: 'The weight of Grenade No. 80 (WP) is:', options: ['385 gm', '485 gm', '568 gm', '650 gm'], correctAnswer: '568 gm', source: 'P140' },
  { id: 'GR06', type: 'fill-blank', category: 'grenades', difficulty: 'marksman', question: 'The Super Energa HEAT grenade has an effective fighting range of approximately _____ - _____ meters.', correctAnswer: '150', source: 'P141' },
  { id: 'GR07', type: 'fill-blank', category: 'grenades', difficulty: 'marksman', question: 'The Arges delay time is _____ seconds (±1 / -0.5 sec).', correctAnswer: '4', source: 'P139' },
  { id: 'GR08', type: 'true-false', category: 'grenades', difficulty: 'soldier', question: 'The Arges grenade body is made of metal.', isTrue: false, explanation: 'The body is made of PLASTIC (polyethylene outer, polystyrene inner core).', source: 'P139' },
  { id: 'GR09', type: 'fill-blank', category: 'grenades', difficulty: 'marksman', question: 'The Super Energa hollow charge filling weighs _____ grams.', correctAnswer: '328', source: 'P141' },

  // ── THREAT (INDIAN SAs) ──
  { id: 'TH01', type: 'mcq', category: 'threat', difficulty: 'recruit', question: 'The Glock 17 is manufactured in:', options: ['Germany', 'Austria', 'USA', 'Italy'], correctAnswer: 'Austria', source: 'P224' },
  { id: 'TH02', type: 'fill-blank', category: 'threat', difficulty: 'soldier', question: 'The AKM has a muzzle velocity of _____ m/s.', correctAnswer: '715', source: 'P227' },
  { id: 'TH03', type: 'mcq', category: 'threat', difficulty: 'soldier', question: 'The FN FAL is chambered for:', options: ['5.56x45mm', '7.62x39mm', '7.62x51mm NATO', '9mm'], correctAnswer: '7.62x51mm NATO', source: 'P227' },
  { id: 'TH04', type: 'fill-blank', category: 'threat', difficulty: 'marksman', question: 'The M4 Carbine barrel length is _____ mm.', correctAnswer: '370', source: 'P229' },
  { id: 'TH05', type: 'mcq', category: 'threat', difficulty: 'soldier', question: 'The MP5 ROF is:', options: ['550 rpm', '600 rpm', '700 rpm', '900 rpm'], correctAnswer: '700 rpm', source: 'P226' },
  { id: 'TH06', type: 'true-false', category: 'threat', difficulty: 'recruit', question: 'The Tavor TAR-21 is manufactured by Israel Military Industries.', isTrue: true, explanation: 'TAR-21 (Tavor Assault Rifle - 21st Century) is an Israeli bullpup assault rifle.', source: 'P229' },
  { id: 'TH07', type: 'fill-blank', category: 'threat', difficulty: 'marksman', question: 'The Indian SIB is authorized _____ LMGs.', correctAnswer: '40', source: 'P230' },

  // ── NVDs ──
  { id: 'N01', type: 'mcq', category: 'nvds', difficulty: 'recruit', question: 'The AN/PVS-5A field of view is:', options: ['20°', '30°', '40°', '50°'], correctAnswer: '40°', source: 'P200' },
  { id: 'N02', type: 'fill-blank', category: 'nvds', difficulty: 'soldier', question: 'The AN/PVS-5A weighs _____ grams.', correctAnswer: '850', source: 'P200' },
  { id: 'N03', type: 'mcq', category: 'nvds', difficulty: 'soldier', question: 'The AN/PVS-5A can view a man-sized target at:', options: ['50m', '100m', '150m', '200m'], correctAnswer: '150m', source: 'P200' },
  { id: 'N04', type: 'fill-blank', category: 'nvds', difficulty: 'marksman', question: 'The AN/PVS-7D provides _____ hours of night vision on two AA batteries.', correctAnswer: '40', source: 'P204' },
  { id: 'N05', type: 'true-false', category: 'nvds', difficulty: 'soldier', question: 'The SKUA-LR thermal imager can detect a human target at 2.5 km.', isTrue: true, explanation: 'SKUA-LR detection range: Human 2.5km, Vehicle 4.4km.', source: 'P207' },
  { id: 'N06', type: 'fill-blank', category: 'nvds', difficulty: 'marksman', question: 'The SKUA-LR has _____ times magnification.', correctAnswer: '4', source: 'P208' },
  { id: 'N07', type: 'mcq', category: 'nvds', difficulty: 'soldier', question: 'The AN/PVS-7D auto shuts off in daylight within approximately:', options: ['10 sec', '30 sec', '70 sec', '120 sec'], correctAnswer: '70 sec', source: 'P205' },

  // ── SADIST MODE EXPANSION ──
  { id: 'SAD01', type: 'fill-blank', category: 'g3a3', difficulty: 'marksman', question: 'The exact length of the G3A3 rifle with a fixed stock is _____ mm.', correctAnswer: '1025', source: 'Sadist' },
  { id: 'SAD02', type: 'fill-blank', category: 'g3a3', difficulty: 'marksman', question: 'The length of the G3A3 rifle with a retracted stock is _____ mm.', correctAnswer: '840', source: 'Sadist' },
  { id: 'SAD03', type: 'fill-blank', category: 'g3a3', difficulty: 'marksman', question: 'The G3A3 barrel length WITHOUT the flash hider is _____ mm.', correctAnswer: '450', source: 'Sadist' },
  { id: 'SAD04', type: 'fill-blank', category: 'g3a3', difficulty: 'soldier', question: 'The G3A3 twist rate is 1 turn in _____ mm.', correctAnswer: '305', source: 'Sadist' },
  { id: 'SAD05', type: 'fill-blank', category: 'g3a3', difficulty: 'marksman', question: 'The G3A3 sight radius is _____ mm.', correctAnswer: '572', source: 'Sadist' },
  { id: 'SAD06', type: 'fill-blank', category: 'g3a3', difficulty: 'marksman', question: 'The weight of a FULL 20-round steel magazine for the G3A3 is _____ g.', correctAnswer: '750', source: 'Sadist' },
  { id: 'SAD07', type: 'fill-blank', category: 'g3a3', difficulty: 'marksman', question: 'The weight of an EMPTY aluminium magazine for the G3A3 is _____ g.', correctAnswer: '130', source: 'Sadist' },
  
  { id: 'SAD08', type: 'fill-blank', category: 'mp5a2', difficulty: 'marksman', question: 'The exact overall length of the MP5A2 (fixed stock) is _____ mm.', correctAnswer: '680', source: 'Sadist' },
  { id: 'SAD09', type: 'fill-blank', category: 'mp5a2', difficulty: 'marksman', question: 'The exact length of the MP5A2 barrel is _____ mm.', correctAnswer: '225', source: 'Sadist' },
  { id: 'SAD10', type: 'fill-blank', category: 'mp5a2', difficulty: 'soldier', question: 'The weight of the MP5A2 weapon WITHOUT magazine is _____ kg.', correctAnswer: '2.54', source: 'Sadist' },
  { id: 'SAD11', type: 'fill-blank', category: 'mp5a2', difficulty: 'marksman', question: 'The weight of a FULL 30-round MP5 magazine is _____ kg.', correctAnswer: '0.47', source: 'Sadist' },
  { id: 'SAD12', type: 'fill-blank', category: 'mp5a2', difficulty: 'marksman', question: 'The MP5A2 muzzle velocity (Vo) is _____ m/s.', correctAnswer: '400', source: 'Sadist' },
  { id: 'SAD13', type: 'fill-blank', category: 'mp5a2', difficulty: 'marksman', question: 'The MP5A2 muzzle energy (Eo) is _____ Joules.', correctAnswer: '650', source: 'Sadist' },
  { id: 'SAD14', type: 'fill-blank', category: 'mp5a2', difficulty: 'soldier', question: 'The MP5 sight radius is _____ mm.', correctAnswer: '340', source: 'Sadist' },

  { id: 'SAD15', type: 'fill-blank', category: 'mg1a3', difficulty: 'marksman', question: 'The total length of the MG1A3 with bipod is _____ mm.', correctAnswer: '1225', source: 'Sadist' },
  { id: 'SAD16', type: 'fill-blank', category: 'mg1a3', difficulty: 'marksman', question: 'The length of the MG1A3 barrel including locking piece is _____ mm.', correctAnswer: '565', source: 'Sadist' },
  { id: 'SAD17', type: 'fill-blank', category: 'mg1a3', difficulty: 'marksman', question: 'The weight of the MG1A3 barrel is _____ kg.', correctAnswer: '1.8', source: 'Sadist' },
  { id: 'SAD18', type: 'fill-blank', category: 'mg1a3', difficulty: 'soldier', question: 'The weight of the MG1A3 bipod is _____ kg.', correctAnswer: '1.2', source: 'Sadist' },
  { id: 'SAD19', type: 'fill-blank', category: 'mg1a3', difficulty: 'marksman', question: 'The length of the MG1A3 steel mount is _____ mm.', correctAnswer: '800', source: 'Sadist' },
  { id: 'SAD20', type: 'fill-blank', category: 'mg1a3', difficulty: 'marksman', question: 'The height of the MG1A3 steel mount folded is _____ mm.', correctAnswer: '270', source: 'Sadist' },
  { id: 'SAD21', type: 'fill-blank', category: 'mg1a3', difficulty: 'soldier', question: 'The muzzle velocity of the MG1A3 is _____ m/s.', correctAnswer: '820', source: 'Sadist' },
  
  { id: 'SAD22', type: 'fill-blank', category: 'snipers', difficulty: 'marksman', question: 'The overall length of the SC-76 Thunderbolt is _____ mm.', correctAnswer: '1225', source: 'Sadist' },
  { id: 'SAD23', type: 'fill-blank', category: 'snipers', difficulty: 'soldier', question: 'The weight of the SC-76 WITHOUT telescope is _____ kg.', correctAnswer: '6.7', source: 'Sadist' },
  { id: 'SAD24', type: 'fill-blank', category: 'snipers', difficulty: 'marksman', question: 'The SC-76 trigger pull is adjustable from _____ to _____ kg (Answer format: X to Y).', correctAnswer: '1.5 to 1.8', source: 'Sadist' },
  { id: 'SAD25', type: 'fill-blank', category: 'snipers', difficulty: 'marksman', question: 'The weight of the SSR empty is _____ kg.', correctAnswer: '4.8', source: 'Sadist' },
  { id: 'SAD26', type: 'fill-blank', category: 'snipers', difficulty: 'soldier', question: 'The barrel length of the SSR is _____ mm.', correctAnswer: '600', source: 'Sadist' },

  { id: 'SAD27', type: 'fill-blank', category: 'theory', difficulty: 'marksman', question: 'For the MP5A2 at 25m, 1 click of windage correction moves the MPI _____ cm.', correctAnswer: '1.5', source: 'Sadist' },
  { id: 'SAD28', type: 'fill-blank', category: 'theory', difficulty: 'marksman', question: 'For the G3A3 at 100m, 1 click of elevation correction moves the MPI _____ cm.', correctAnswer: '3.3', source: 'Sadist' },
  { id: 'SAD29', type: 'fill-blank', category: 'theory', difficulty: 'marksman', question: 'The Beaten Zone length at 500m is approximately _____ m.', correctAnswer: '150', source: 'Sadist' },
  { id: 'SAD30', type: 'fill-blank', category: 'theory', difficulty: 'marksman', question: 'The Beaten Zone length at 2000m is approximately _____ m.', correctAnswer: '45', source: 'Sadist' },
];

export const QUIZ_CATEGORIES = [
  { id: 'sadist_mode', name: 'Sadist Mode (ALL Data)', icon: '💀', description: 'Generated verbatim from every single line of the manual. Godspeed.' },
  { id: 'theory', name: 'Theory of SAs', icon: '◎', description: 'Definitions, ballistics, and core concepts' },
  { id: 'g3a3', name: 'G3A3 Rifle', icon: '▸', description: 'Specs, cycle of operation, stoppages, zeroing' },
  { id: 'mp5a2', name: 'MP5A2', icon: '▸', description: 'Specs, stoppages, CQB parameters' },
  { id: 'mg1a3', name: 'MG1A3', icon: '▸', description: 'Specs, fire types, ROF, stoppages' },
  { id: 'snipers', name: 'Snipers', icon: '⊹', description: 'SC-76, SSR, telescope sights, field skills' },
  { id: 'grenades', name: 'Grenades', icon: '◆', description: 'Arges, WP No.80, Super Energa specs' },
  { id: 'threat', name: 'Threat Intel', icon: '⚠', description: 'Indian weapons and comparative data' },
  { id: 'nvds', name: 'NVDs & Optics', icon: '◉', description: 'AN/PVS-5A, 7D, SKUA-LR specs' },
];

export function getQuestionsByCategory(categoryId: string): QuizQuestion[] {
  return allQuizQuestions.filter(q => q.category === categoryId);
}

export function getQuestionsByDifficulty(difficulty: string): QuizQuestion[] {
  return allQuizQuestions.filter(q => q.difficulty === difficulty);
}

export function shuffleArray<T>(arr: T[]): T[] {
  const shuffled = [...arr];
  for (let i = shuffled.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
  }
  return shuffled;
}
