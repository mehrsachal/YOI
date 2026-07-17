import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { QUIZ_CATEGORIES, getQuestionsByCategory, shuffleArray } from '../data/quizData';
import { useProgress } from '../context/ProgressContext';
import type { QuizQuestion, DifficultyLevel } from '../types';

type QuizState = 'menu' | 'active' | 'results';

export default function QuizEngine() {
  const { progress, updateQuizProgress, recordStudy } = useProgress();
  const [state, setState] = useState<QuizState>('menu');
  const [selectedCategory, setSelectedCategory] = useState<string>('');
  const [difficulty, setDifficulty] = useState<DifficultyLevel>('soldier');
  
  // Active quiz state
  const [questions, setQuestions] = useState<QuizQuestion[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [score, setScore] = useState(0);
  const [answers, setAnswers] = useState<Record<number, string | boolean | string[]>>({});
  const [currentInput, setCurrentInput] = useState('');
  const [showHint, setShowHint] = useState(false);
  const [isAnswered, setIsAnswered] = useState(false);
  const [timeLeft, setTimeLeft] = useState<number | null>(null);

  // Timer for Marksman mode
  useEffect(() => {
    if (state === 'active' && difficulty === 'marksman' && !isAnswered && timeLeft !== null && timeLeft > 0) {
      const timer = setTimeout(() => setTimeLeft(timeLeft - 1), 1000);
      return () => clearTimeout(timer);
    } else if (timeLeft === 0 && !isAnswered) {
      handleCheckAnswer(true); // Time's up
    }
  }, [state, difficulty, isAnswered, timeLeft]);

  const startQuiz = (categoryId: string) => {
    const catQuestions = getQuestionsByCategory(categoryId);
    // Filter by difficulty or include lower difficulties
    const available = catQuestions.filter(q => 
      difficulty === 'marksman' ? true : 
      difficulty === 'soldier' ? q.difficulty === 'soldier' || q.difficulty === 'recruit' :
      q.difficulty === 'recruit'
    );
    
    if (available.length === 0) {
      alert("No questions available for this difficulty level in this category.");
      return;
    }

    setQuestions(shuffleArray(available).slice(0, 10)); // Max 10 questions per run
    setSelectedCategory(categoryId);
    setCurrentIndex(0);
    setScore(0);
    setAnswers({});
    setCurrentInput('');
    setShowHint(false);
    setIsAnswered(false);
    setTimeLeft(difficulty === 'marksman' ? 15 : null); // 15 seconds per question for marksman
    setState('active');
    recordStudy();
  };

  const handleCheckAnswer = (timeout = false) => {
    const q = questions[currentIndex];
    let isCorrect = false;

    if (!timeout) {
      if (q.type === 'mcq' || q.type === 'fill-blank') {
        // Very strict comparison for fill-blank (verbatim cramming)
        const userStr = currentInput.trim().toLowerCase();
        const correctStr = (q.correctAnswer as string).trim().toLowerCase();
        isCorrect = userStr === correctStr;
      } else if (q.type === 'true-false') {
        // currentInput is stored as string 'true' or 'false'
        isCorrect = (currentInput === 'true') === q.isTrue;
      }
    }

    setAnswers(prev => ({ ...prev, [currentIndex]: isCorrect ? 'correct' : 'incorrect' }));
    if (isCorrect) setScore(s => s + 1);
    setIsAnswered(true);
  };

  const nextQuestion = () => {
    if (currentIndex < questions.length - 1) {
      setCurrentIndex(i => i + 1);
      setCurrentInput('');
      setShowHint(false);
      setIsAnswered(false);
      setTimeLeft(difficulty === 'marksman' ? 15 : null);
    } else {
      updateQuizProgress(selectedCategory, score, questions.length);
      setState('results');
    }
  };

  if (state === 'menu') {
    return (
      <div className="page-container animate-fade-in">
        <div className="page-header">
          <div className="page-header__breadcrumb">
            <Link to="/">Home</Link> <span>›</span> <span>Quiz Engine</span>
          </div>
          <h1 className="page-header__title">Quiz Engine</h1>
          <p className="page-header__desc">
            Test your SAC-81 knowledge. The app expects verbatim answers for fill-in-the-blank questions to ensure perfect memorization.
          </p>
        </div>

        <div className="content-section">
          <div className="glass-card" style={{ padding: 'var(--space-xl)', marginBottom: 'var(--space-2xl)' }}>
            <h2 style={{ fontSize: '1.2rem', marginBottom: 'var(--space-md)' }}>Select Difficulty</h2>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 'var(--space-md)' }}>
              {[
                { id: 'recruit', label: 'Recruit', desc: 'Basic MCQs, hints available.' },
                { id: 'soldier', label: 'Soldier', desc: 'Fill-in-the-blanks, strict matching.' },
                { id: 'marksman', label: 'Marksman', desc: 'All types, 15 seconds per question.' },
              ].map(d => (
                <button
                  key={d.id}
                  className={`glass-card ${difficulty === d.id ? 'glass-card--glow-green' : ''}`}
                  style={{
                    padding: 'var(--space-md)',
                    textAlign: 'left',
                    cursor: 'pointer',
                    border: difficulty === d.id ? '2px solid var(--accent-green)' : '1px solid var(--border-subtle)'
                  }}
                  onClick={() => setDifficulty(d.id as DifficultyLevel)}
                >
                  <div style={{ fontSize: '1.1rem', fontWeight: 600, color: difficulty === d.id ? 'var(--accent-green)' : 'var(--text-primary)' }}>{d.label}</div>
                  <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: 4 }}>{d.desc}</div>
                </button>
              ))}
            </div>
          </div>

          <h2 className="section-title">Select Category<span className="section-title__line" /></h2>
          <div className="grid-auto">
            {QUIZ_CATEGORIES.map(cat => {
              const prog = progress.quizProgress[cat.id];
              return (
                <div key={cat.id} className="glass-card" style={{ padding: 'var(--space-lg)', display: 'flex', flexDirection: 'column' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-sm)', marginBottom: 'var(--space-sm)' }}>
                    <span style={{ fontSize: '1.5rem', color: 'var(--accent-green)' }}>{cat.icon}</span>
                    <h3 style={{ fontSize: '1.1rem' }}>{cat.name}</h3>
                  </div>
                  <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', flex: 1 }}>{cat.description}</p>
                  
                  {prog && (
                    <div style={{ margin: 'var(--space-md) 0', padding: '8px', background: 'rgba(74, 222, 128, 0.05)', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border-dim)' }}>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', display: 'flex', justifyContent: 'space-between' }}>
                        <span>Best Score</span>
                        <span style={{ color: prog.bestScore >= 80 ? 'var(--accent-green)' : 'var(--accent-amber)', fontWeight: 600 }}>{prog.bestScore}%</span>
                      </div>
                    </div>
                  )}

                  <button className="btn btn-primary" style={{ width: '100%', justifyContent: 'center', marginTop: prog ? 0 : 'var(--space-md)' }} onClick={() => startQuiz(cat.id)}>
                    Start Quiz
                  </button>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    );
  }

  if (state === 'active') {
    const q = questions[currentIndex];
    
    return (
      <div className="page-container animate-fade-in" style={{ maxWidth: 800 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-lg)' }}>
          <div style={{ fontSize: '0.9rem', color: 'var(--text-muted)' }}>Question {currentIndex + 1} of {questions.length}</div>
          {timeLeft !== null && (
            <div style={{ 
              fontSize: '1.2rem', 
              fontFamily: 'var(--font-mono)', 
              fontWeight: 700,
              color: timeLeft <= 5 ? 'var(--accent-red)' : 'var(--accent-cyan)'
            }}>
              00:{timeLeft.toString().padStart(2, '0')}
            </div>
          )}
          <button className="btn btn-ghost btn-sm" onClick={() => setState('menu')}>Quit</button>
        </div>

        <div className="glass-card" style={{ padding: 'var(--space-2xl)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 'var(--space-md)' }}>
            <span className="badge badge-amber">{q.difficulty.toUpperCase()}</span>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Ref: {q.source}</span>
          </div>

          <h2 style={{ fontSize: '1.5rem', lineHeight: 1.5, marginBottom: 'var(--space-xl)' }}>
            {q.type === 'fill-blank' ? q.question.replace('_____', '_______') : q.question}
          </h2>

          <div style={{ marginBottom: 'var(--space-xl)' }}>
            {q.type === 'mcq' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-sm)' }}>
                {q.options!.map((opt, i) => (
                  <button
                    key={i}
                    disabled={isAnswered}
                    className={`btn ${currentInput === opt ? 'btn-primary' : 'btn-secondary'}`}
                    style={{ 
                      justifyContent: 'flex-start', 
                      padding: '16px 20px', 
                      fontSize: '1rem',
                      background: isAnswered && opt === q.correctAnswer ? 'var(--accent-green-bg)' : 
                                 isAnswered && currentInput === opt && opt !== q.correctAnswer ? 'var(--accent-red-glow)' : ''
                    }}
                    onClick={() => setCurrentInput(opt)}
                  >
                    <span style={{ width: 24, height: 24, borderRadius: '50%', background: 'rgba(255,255,255,0.1)', display: 'flex', alignItems: 'center', justifyContent: 'center', marginRight: 12, fontSize: '0.8rem' }}>
                      {String.fromCharCode(65 + i)}
                    </span>
                    {opt}
                  </button>
                ))}
              </div>
            )}

            {q.type === 'true-false' && (
              <div style={{ display: 'flex', gap: 'var(--space-md)' }}>
                {['true', 'false'].map((opt) => (
                  <button
                    key={opt}
                    disabled={isAnswered}
                    className={`btn ${currentInput === opt ? 'btn-primary' : 'btn-secondary'}`}
                    style={{ 
                      flex: 1, 
                      justifyContent: 'center', 
                      padding: '16px',
                      background: isAnswered && (opt === 'true') === q.isTrue ? 'var(--accent-green-bg)' :
                                 isAnswered && currentInput === opt && (opt === 'true') !== q.isTrue ? 'var(--accent-red-glow)' : ''
                    }}
                    onClick={() => setCurrentInput(opt)}
                  >
                    {opt.toUpperCase()}
                  </button>
                ))}
              </div>
            )}

            {q.type === 'fill-blank' && (
              <div>
                <input
                  type="text"
                  disabled={isAnswered}
                  autoFocus
                  placeholder="Type the exact word(s)..."
                  value={currentInput}
                  onChange={(e) => setCurrentInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && currentInput.trim()) handleCheckAnswer();
                  }}
                  style={{
                    width: '100%',
                    padding: '16px 20px',
                    fontSize: '1.2rem',
                    background: 'var(--bg-secondary)',
                    border: '1px solid var(--border-medium)',
                    borderRadius: 'var(--radius-md)',
                    color: 'var(--text-primary)',
                    fontFamily: 'var(--font-mono)',
                    outline: 'none',
                    borderColor: isAnswered ? (answers[currentIndex] === 'correct' ? 'var(--accent-green)' : 'var(--accent-red)') : 'var(--border-medium)'
                  }}
                />
              </div>
            )}
          </div>

          {!isAnswered ? (
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              {q.hint && difficulty !== 'marksman' ? (
                <button 
                  className="btn btn-ghost btn-sm" 
                  onClick={() => setShowHint(true)}
                  disabled={showHint}
                >
                  💡 {showHint ? q.hint : 'Show Hint'}
                </button>
              ) : <div />}
              
              <button 
                className="btn btn-primary" 
                disabled={!currentInput.trim()} 
                onClick={() => handleCheckAnswer(false)}
              >
                Submit Answer
              </button>
            </div>
          ) : (
            <div className="animate-fade-in-up" style={{
              padding: '20px',
              borderRadius: 'var(--radius-md)',
              background: answers[currentIndex] === 'correct' ? 'var(--accent-green-bg)' : 'var(--accent-red-glow)',
              border: `1px solid ${answers[currentIndex] === 'correct' ? 'var(--accent-green)' : 'var(--accent-red)'}`,
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '12px' }}>
                <span style={{ fontSize: '1.5rem' }}>
                  {answers[currentIndex] === 'correct' ? '✅' : '❌'}
                </span>
                <span style={{ fontSize: '1.2rem', fontWeight: 600, color: answers[currentIndex] === 'correct' ? 'var(--accent-green)' : 'var(--accent-red)' }}>
                  {answers[currentIndex] === 'correct' ? 'Correct!' : 'Incorrect'}
                </span>
              </div>
              
              {answers[currentIndex] !== 'correct' && (
                <div style={{ marginBottom: '12px', fontSize: '1rem' }}>
                  The correct answer is: <strong style={{ color: 'var(--text-primary)' }}>
                    {q.correctAnswer ? (Array.isArray(q.correctAnswer) ? q.correctAnswer.join(', ') : q.correctAnswer.toString()) : (q.type === 'true-false' ? (q.isTrue ? 'True' : 'False') : (q.correctOrder ? q.correctOrder.join(' → ') : 'N/A'))}
                  </strong>
                </div>
              )}
              
              {q.explanation && (
                <div style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', fontStyle: 'italic', borderTop: '1px solid rgba(255,255,255,0.1)', paddingTop: '12px' }}>
                  {q.explanation}
                </div>
              )}

              <div style={{ marginTop: '20px', display: 'flex', justifyContent: 'flex-end' }}>
                <button className="btn btn-primary" onClick={nextQuestion}>
                  {currentIndex < questions.length - 1 ? 'Next Question →' : 'See Results →'}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    );
  }

  // Results State
  return (
    <div className="page-container animate-fade-in" style={{ maxWidth: 600 }}>
      <div className="glass-card" style={{ padding: 'var(--space-3xl)', textAlign: 'center' }}>
        <h1 style={{ fontSize: '2.5rem', marginBottom: 'var(--space-sm)' }}>Quiz Complete!</h1>
        <p style={{ color: 'var(--text-muted)', marginBottom: 'var(--space-2xl)' }}>Category: {QUIZ_CATEGORIES.find(c => c.id === selectedCategory)?.name}</p>

        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 'var(--space-2xl)' }}>
          <div style={{ position: 'relative', width: 200, height: 200, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <svg viewBox="0 0 120 120" style={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }}>
              <circle cx="60" cy="60" r="54" fill="none" stroke="var(--border-dim)" strokeWidth="8" />
              <circle
                cx="60" cy="60" r="54"
                fill="none"
                stroke={score / questions.length >= 0.8 ? 'var(--accent-green)' : score / questions.length >= 0.5 ? 'var(--accent-amber)' : 'var(--accent-red)'}
                strokeWidth="8"
                strokeLinecap="round"
                strokeDasharray={`${2 * Math.PI * 54}`}
                strokeDashoffset={`${2 * Math.PI * 54 * (1 - score / questions.length)}`}
                transform="rotate(-90 60 60)"
                style={{ transition: 'stroke-dashoffset 1.5s cubic-bezier(0.34, 1.56, 0.64, 1)' }}
              />
            </svg>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
              <span style={{ fontSize: '3rem', fontWeight: 800, fontFamily: 'var(--font-heading)', lineHeight: 1 }}>{Math.round((score / questions.length) * 100)}%</span>
              <span style={{ fontSize: '1rem', color: 'var(--text-muted)' }}>{score} / {questions.length}</span>
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 'var(--space-md)', justifyContent: 'center' }}>
          <button className="btn btn-secondary" onClick={() => setState('menu')}>
            Back to Categories
          </button>
          <button className="btn btn-primary" onClick={() => startQuiz(selectedCategory)}>
            Retry Quiz
          </button>
        </div>
      </div>
    </div>
  );
}
