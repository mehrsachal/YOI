import { readFileSync, writeFileSync } from 'fs';
import { getDocument } from 'pdfjs-dist/legacy/build/pdf.mjs';

const data = new Uint8Array(readFileSync('../Methodology SAC-81.pdf'));
const doc = await getDocument({ data }).promise;
console.log('Total pages:', doc.numPages);

let fullText = '';
for (let i = 1; i <= doc.numPages; i++) {
  const page = await doc.getPage(i);
  const content = await page.getTextContent();
  const text = content.items.map(item => item.str).join(' ');
  fullText += `\n--- PAGE ${i} ---\n${text}\n`;
}

writeFileSync('pdf_extract.txt', fullText);
console.log('Total chars:', fullText.length);
console.log('\n=== FIRST 10000 CHARS ===\n');
console.log(fullText.substring(0, 10000));
