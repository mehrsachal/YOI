const fs = require('fs');

const text = fs.readFileSync('pdf_extract.txt', 'utf8');

// Simple sentence splitter
const sentences = text.replace(/\n/g, ' ').split(/(?<=[.?!])\s+(?=[A-Z0-9])/);

const questions = [];
let idCounter = 1;

for (const sentence of sentences) {
  const cleanSentence = sentence.trim().replace(/\s+/g, ' ');
  if (cleanSentence.length < 30 || cleanSentence.length > 300) continue;
  if (cleanSentence.includes('--- PAGE')) continue;
  if (cleanSentence.includes('RESTD')) continue; // Headers/footers

  // Find numbers (with optional units like mm, m, kg, m/s)
  const numberMatches = Array.from(cleanSentence.matchAll(/\b\d+(\.\d+)?(?:mm|m|kg|g|m\/s|rpm|rds|yds|sec|fps)?\b/gi));
  
  // Find uppercase words / Acronyms
  const acronymMatches = Array.from(cleanSentence.matchAll(/\b[A-Z0-9-]{3,}\b/g));

  const targets = [];
  numberMatches.forEach(m => targets.push(m[0]));
  acronymMatches.forEach(m => targets.push(m[0]));

  // Remove duplicates
  const uniqueTargets = [...new Set(targets)];

  for (const target of uniqueTargets) {
    // Only generate a fill in the blank if it's a good target
    if (target.length < 2) continue;
    
    // Create fill blank by replacing target with '_____'
    // Escape target for regex
    const escapedTarget = target.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const regex = new RegExp(`\\b${escapedTarget}\\b`, 'g');
    
    const questionText = cleanSentence.replace(regex, '_____');
    
    if (questionText !== cleanSentence) {
      questions.push({
        id: `AUTO${idCounter++}`,
        type: 'fill-blank',
        category: 'theory', // We'll just dump them in theory or sadist category
        difficulty: 'sadist',
        question: questionText,
        correctAnswer: target,
        source: 'Auto-Extracted'
      });
    }
  }
}

// Generate TS file
let tsContent = `import type { QuizQuestion } from '../types';\n\nexport const autoQuizQuestions: QuizQuestion[] = [\n`;

questions.forEach(q => {
  tsContent += `  { id: '${q.id}', type: '${q.type}', category: 'sadist_mode', difficulty: '${q.difficulty}', question: ${JSON.stringify(q.question)}, correctAnswer: ${JSON.stringify(q.correctAnswer)}, source: '${q.source}' },\n`;
});

tsContent += `];\n`;

fs.writeFileSync('src/data/autoQuizData.ts', tsContent);
console.log(`Generated ${questions.length} auto questions!`);
