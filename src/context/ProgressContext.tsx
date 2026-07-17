import { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import type { UserProgress, QuizProgress } from '../types';

const DEFAULT_PROGRESS: UserProgress = {
  quizProgress: {},
  studyStreak: 0,
  lastStudyDate: '',
  totalQuestionsAnswered: 0,
  totalCorrect: 0,
};

interface ProgressContextType {
  progress: UserProgress;
  updateQuizProgress: (categoryId: string, correct: number, total: number) => void;
  getOverallPercent: () => number;
  recordStudy: () => void;
}

const ProgressContext = createContext<ProgressContextType | null>(null);

export function ProgressProvider({ children }: { children: ReactNode }) {
  const [progress, setProgress] = useState<UserProgress>(() => {
    try {
      const stored = localStorage.getItem('sac81_progress');
      return stored ? JSON.parse(stored) : DEFAULT_PROGRESS;
    } catch {
      return DEFAULT_PROGRESS;
    }
  });

  useEffect(() => {
    localStorage.setItem('sac81_progress', JSON.stringify(progress));
  }, [progress]);

  const updateQuizProgress = (categoryId: string, correct: number, total: number) => {
    setProgress(prev => {
      const existing = prev.quizProgress[categoryId];
      const score = Math.round((correct / total) * 100);
      const updated: QuizProgress = {
        categoryId,
        totalQuestions: total,
        correctAnswers: correct,
        lastAttempted: new Date().toISOString(),
        bestScore: existing ? Math.max(existing.bestScore, score) : score,
        attempts: existing ? existing.attempts + 1 : 1,
      };
      return {
        ...prev,
        quizProgress: { ...prev.quizProgress, [categoryId]: updated },
        totalQuestionsAnswered: prev.totalQuestionsAnswered + total,
        totalCorrect: prev.totalCorrect + correct,
      };
    });
  };

  const getOverallPercent = () => {
    const entries = Object.values(progress.quizProgress);
    if (entries.length === 0) return 0;
    const totalBest = entries.reduce((sum, e) => sum + e.bestScore, 0);
    return Math.round(totalBest / entries.length);
  };

  const recordStudy = () => {
    const today = new Date().toISOString().split('T')[0];
    setProgress(prev => {
      const yesterday = new Date(Date.now() - 86400000).toISOString().split('T')[0];
      const streak = prev.lastStudyDate === yesterday
        ? prev.studyStreak + 1
        : prev.lastStudyDate === today
          ? prev.studyStreak
          : 1;
      return { ...prev, studyStreak: streak, lastStudyDate: today };
    });
  };

  return (
    <ProgressContext.Provider value={{ progress, updateQuizProgress, getOverallPercent, recordStudy }}>
      {children}
    </ProgressContext.Provider>
  );
}

export function useProgress() {
  const ctx = useContext(ProgressContext);
  if (!ctx) throw new Error('useProgress must be used within ProgressProvider');
  return ctx;
}
