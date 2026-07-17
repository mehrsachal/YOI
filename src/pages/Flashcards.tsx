import { useState } from 'react';
import { allQuizQuestions, QUIZ_CATEGORIES } from '../data/quizData';
import type { QuizQuestion } from '../types';

export default function Flashcards() {
  const [activeCategory, setActiveCategory] = useState<string>('all');
  const [currentIndex, setCurrentIndex] = useState(0);
  const [flipped, setFlipped] = useState(false);

  const filteredQuestions = activeCategory === 'all' 
    ? allQuizQuestions 
    : allQuizQuestions.filter(q => q.category === activeCategory);

  const currentQuestion: QuizQuestion | undefined = filteredQuestions[currentIndex];

  const handleNext = () => {
    setFlipped(false);
    setCurrentIndex((prev) => (prev + 1) % filteredQuestions.length);
  };

  const handlePrev = () => {
    setFlipped(false);
    setCurrentIndex((prev) => (prev - 1 + filteredQuestions.length) % filteredQuestions.length);
  };

  return (
    <div className="page-container animate-fade-in">
      <div className="page-header">
        <h1 className="page-header__title">Flashcards (Spaced Repetition)</h1>
        <p className="page-header__desc">
          Rapid-fire review of all SAC-81 manual data. Click the card to flip and reveal the exact answer.
        </p>
      </div>

      <div style={{ marginBottom: 'var(--space-xl)', display: 'flex', gap: '8px', overflowX: 'auto', paddingBottom: '8px' }}>
        <button 
          className={`btn ${activeCategory === 'all' ? 'btn-primary' : 'btn-secondary'}`}
          onClick={() => { setActiveCategory('all'); setCurrentIndex(0); setFlipped(false); }}
        >
          All
        </button>
        {QUIZ_CATEGORIES.map(cat => (
          <button 
            key={cat.id}
            className={`btn ${activeCategory === cat.id ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => { setActiveCategory(cat.id); setCurrentIndex(0); setFlipped(false); }}
            style={{ whiteSpace: 'nowrap' }}
          >
            {cat.icon} {cat.name}
          </button>
        ))}
      </div>

      {currentQuestion ? (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
          <div style={{ fontSize: '0.9rem', color: 'var(--text-muted)', marginBottom: 'var(--space-md)' }}>
            Card {currentIndex + 1} of {filteredQuestions.length} — {currentQuestion.difficulty.toUpperCase()}
          </div>

          <div 
            onClick={() => setFlipped(!flipped)}
            style={{
              width: '100%',
              maxWidth: 600,
              height: 350,
              perspective: 1000,
              cursor: 'pointer',
              marginBottom: 'var(--space-xl)'
            }}
          >
            <div style={{
              width: '100%',
              height: '100%',
              position: 'relative',
              transition: 'transform 0.6s cubic-bezier(0.4, 0, 0.2, 1)',
              transformStyle: 'preserve-3d',
              transform: flipped ? 'rotateY(180deg)' : 'rotateY(0deg)'
            }}>
              {/* Front */}
              <div className="glass-card" style={{
                position: 'absolute', inset: 0,
                backfaceVisibility: 'hidden',
                display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
                padding: 'var(--space-lg)', textAlign: 'center',
                border: '1px solid var(--border-medium)',
                wordBreak: 'break-word',
                overflowY: 'auto'
              }}>
                <span className="badge" style={{ position: 'absolute', top: 12, right: 12, fontSize: '0.65rem' }}>Ref: {currentQuestion.source}</span>
                <h2 style={{ fontSize: 'clamp(1rem, 5vw, 1.4rem)', lineHeight: 1.5, color: 'var(--text-primary)' }}>
                  {currentQuestion.type === 'fill-blank' ? currentQuestion.question.replace('_____', '_______') : currentQuestion.question}
                </h2>
                <div style={{ position: 'absolute', bottom: 20, color: 'var(--text-muted)', fontSize: '0.8rem' }}>Click to flip</div>
              </div>

              {/* Back */}
              <div className="glass-card" style={{
                position: 'absolute', inset: 0,
                backfaceVisibility: 'hidden',
                display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
                padding: 'var(--space-lg)', textAlign: 'center',
                transform: 'rotateY(180deg)',
                background: 'rgba(5, 150, 105, 0.05)',
                border: '1px solid var(--accent-green)',
                wordBreak: 'break-word',
                overflowY: 'auto'
              }}>
                <h2 style={{ fontSize: 'clamp(1.2rem, 6vw, 2rem)', color: 'var(--accent-green)', fontWeight: 700, fontFamily: 'var(--font-mono)' }}>
                  {currentQuestion.correctAnswer 
                    ? (Array.isArray(currentQuestion.correctAnswer) ? currentQuestion.correctAnswer.join(', ') : currentQuestion.correctAnswer.toString())
                    : (currentQuestion.type === 'true-false' ? (currentQuestion.isTrue ? 'True' : 'False') : (currentQuestion.correctOrder ? currentQuestion.correctOrder.join(' → ') : 'N/A'))}
                </h2>
                {currentQuestion.explanation && (
                  <p style={{ marginTop: 'var(--space-lg)', color: 'var(--text-secondary)', fontStyle: 'italic', fontSize: '0.95rem' }}>
                    {currentQuestion.explanation}
                  </p>
                )}
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', gap: 'var(--space-lg)' }}>
            <button className="btn btn-secondary btn-lg" onClick={handlePrev}>← Prev</button>
            <button className="btn btn-primary btn-lg" onClick={handleNext}>Next →</button>
          </div>
        </div>
      ) : (
        <div style={{ textAlign: 'center', color: 'var(--text-muted)' }}>No questions available for this category.</div>
      )}
    </div>
  );
}
