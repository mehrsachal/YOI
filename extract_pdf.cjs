const fs = require('fs');
const pdfjsLib = require('pdfjs-dist/build/pdf.js');

async function extract() {
  const data = new Uint8Array(fs.readFileSync('../Methodology SAC-81.pdf'));
  const doc = await pdfjsLib.getDocument({ data }).promise;
  console.log('Total pages:', doc.numPages);

  let fullText = '';
  for (let i = 1; i <= doc.numPages; i++) {
    const page = await doc.getPage(i);
    const content = await page.getTextContent();
    const text = content.items.map(item => item.str).join(' ');
    fullText += `\n--- PAGE ${i} ---\n${text}\n`;
  }

  fs.writeFileSync('pdf_extract.txt', fullText);
  console.log('Total chars:', fullText.length);
  console.log('\n=== FIRST 10000 CHARS ===\n');
  console.log(fullText.substring(0, 10000));
}

extract().catch(console.error);
