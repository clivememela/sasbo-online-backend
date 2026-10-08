import { PDFDocument } from 'pdf-lib';
import fs from 'fs';

async function inspect() {
  const pdfBytes = fs.readFileSync('./src/assets/documents/Membership Application.pdf');
  const pdfDoc = await PDFDocument.load(pdfBytes);
  const form = pdfDoc.getForm();
  const fields = form.getFields();
  console.log(`Total fields: ${fields.length}`);
  fields.forEach(field => {
    const type = field.constructor.name;
    const name = field.getName();
    console.log(`${type}: ${name}`);
    try {
      const widgets = field.acroField.getWidgets();
      if (widgets.length > 0) {
        const widget = widgets[0];
        const rect = widget.getRectangle();
        console.log(`  Position: [${rect[0]}, ${rect[1]}, ${rect[2]}, ${rect[3]}]`);
      }
    } catch (e) {
      // ignore
    }
  });
}

inspect().catch(console.error);
