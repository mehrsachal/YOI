// ============================================================
// SAC-81 Gun Mastery App — Type Definitions
// ============================================================

export interface WeaponSpec {
  label: string;
  value: string;
  unit?: string;
}

export interface WeaponPart {
  name: string;
  subParts?: string[];
}

export interface Stoppage {
  type: string;
  causes: string[];
  remedies: string[];
}

export interface ZeroingStep {
  step: number;
  instruction: string;
  note?: string;
}

export interface WeaponData {
  id: string;
  name: string;
  shortName: string;
  category: 'rifle' | 'smg' | 'mg' | 'pistol' | 'sniper' | 'aamg' | 'grenade';
  origin: string;
  caliber: string;
  description: string;
  image?: string;
  specs: WeaponSpec[];
  mainParts: WeaponPart[];
  characteristics: string[];
  functions?: string[];
  stoppages?: Stoppage[];
  zeroing?: {
    definition: string;
    occasions: string[];
    steps: ZeroingStep[];
  };
  ratesOfFire?: {
    mode: string;
    rate: string;
    description: string;
  }[];
  additionalInfo?: { title: string; content: string }[];
}

// Theory types
export interface Definition {
  term: string;
  definition: string;
  category: 'barrel' | 'motion' | 'ammo' | 'misc' | 'ballistics';
}

export interface BallisticsType {
  name: string;
  description: string;
  details: string[];
}

// Quiz types
export type QuestionType = 'mcq' | 'fill-blank' | 'true-false' | 'match-pairs' | 'order-steps';
export type DifficultyLevel = 'recruit' | 'soldier' | 'marksman';

export interface QuizQuestion {
  id: string;
  type: QuestionType;
  category: string;
  difficulty: DifficultyLevel;
  question: string;
  // For MCQ
  options?: string[];
  correctAnswer: string | string[];
  // For fill-blank
  blankWord?: string;
  // For match-pairs
  pairs?: { left: string; right: string }[];
  // For order-steps
  correctOrder?: string[];
  // For true-false
  isTrue?: boolean;
  explanation?: string;
  hint?: string;
  source?: string; // Page reference from PDF
}

export interface QuizCategory {
  id: string;
  name: string;
  icon: string;
  description: string;
  questions: QuizQuestion[];
}

export interface QuizProgress {
  categoryId: string;
  totalQuestions: number;
  correctAnswers: number;
  lastAttempted: string; // ISO date
  bestScore: number;
  attempts: number;
}

export interface UserProgress {
  quizProgress: Record<string, QuizProgress>;
  studyStreak: number;
  lastStudyDate: string;
  totalQuestionsAnswered: number;
  totalCorrect: number;
}

// Threat data types
export interface ThreatWeapon {
  name: string;
  type: string;
  origin: string;
  designer?: string;
  caliber: string;
  weight: string;
  length: string;
  barrelLength: string;
  action?: string;
  rof?: string;
  mv: string;
  effRange: string;
  feedSystem: string;
  sights: string;
  inService?: string;
}

// NVD types
export interface NVDDevice {
  id: string;
  name: string;
  type: 'goggles' | 'sight' | 'thermal';
  description: string;
  specs: WeaponSpec[];
  components: string[];
  operatingProcedure: string[];
  maintenance: string[];
}

// Sniper types
export interface SniperTopic {
  id: string;
  title: string;
  content: string[];
  subtopics?: { title: string; points: string[] }[];
}

// Navigation
export interface NavItem {
  path: string;
  label: string;
  icon: string;
  children?: NavItem[];
}
