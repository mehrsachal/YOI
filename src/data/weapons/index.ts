import { g3a3Data } from './g3a3';
import { mp5a2Data } from './mp5a2';
import { mg1a3Data } from './mg1a3';
import { sc76Data, ssrData } from './snipers';
import { smgData, pistolsData, aamgData, grenadesData } from './others';
import type { WeaponData } from '../../types';

export const allWeapons: Record<string, WeaponData> = {
  g3a3: g3a3Data,
  mp5a2: mp5a2Data,
  mg1a3: mg1a3Data,
  smg: smgData,
  pistols: pistolsData,
  sc76: sc76Data,
  ssr: ssrData,
  aamg: aamgData,
  grenades: grenadesData,
};

export const weaponList = Object.values(allWeapons);

export { g3a3Data, mp5a2Data, mg1a3Data, sc76Data, ssrData, smgData, pistolsData, aamgData, grenadesData };
