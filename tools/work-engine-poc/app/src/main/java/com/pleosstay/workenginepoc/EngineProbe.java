package com.pleosstay.workenginepoc;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.json.JSONObject;
import org.json.JSONArray;

/** Isolated approved trial: original synthetic nonconfidential fixtures only. No network permission. */
public final class EngineProbe extends Instrumentation {
    private File dir;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        dir = new File(getTargetContext().getFilesDir(), "evaluation-output");
        dir.mkdirs();
        JSONObject report = new JSONObject();
        try {
            report.put("api", Build.VERSION.SDK_INT);
            report.put("abi", Build.SUPPORTED_ABIS[0]);
            report.put("evaluationOnly", true);
            report.put("probeRevision", 3);
            report.put("pdfVersion", "23.2-android.via.java");
            report.put("wordsVersion", "25.9-android.via.java");
            try { pdf(); report.put("pdf", "PASS_SMOKE_REQUIRES_INDEPENDENT_CONTENT_AND_VISUAL_REVIEW"); }
            catch (Throwable e) { report.put("pdf", "FAIL"); report.put("pdfError", e.getClass().getName()); report.put("pdfCauses", causes(e)); }
            try { docx(); report.put("docx", "PASS_SMOKE_REQUIRES_EXTERNAL_WORD_VISUAL_REVIEW"); }
            catch (Throwable e) { report.put("docx", "FAIL"); report.put("docxError", e.getClass().getName()); report.put("docxCauses", causes(e)); }
            report.put("license", "UNLICENSED_TRIAL_OUTPUT_MAY_INCLUDE_WATERMARKS");
            write(new File(dir, "report.json"), report.toString(2).getBytes(StandardCharsets.UTF_8));
            Bundle result = new Bundle(); result.putString("report", report.toString());
            finish(0, result);
        } catch (Throwable e) { Bundle error = new Bundle(); error.putString("error", e.getClass().getName()); finish(1, error); }
    }

    private byte[] image(int color) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes); bitmap.recycle(); return bytes.toByteArray();
    }
    private void pdf() throws Exception {
        File platformOriginal = new File(dir, "fixture-platform-original.pdf");
        PdfDocument fixture = new PdfDocument();
        Paint paint = new Paint(); paint.setColor(Color.BLACK); paint.setTextSize(20);
        Bitmap red = android.graphics.BitmapFactory.decodeByteArray(image(Color.RED), 0, image(Color.RED).length);
        for (int i = 1; i <= 3; i++) {
            PdfDocument.Page page = fixture.startPage(new PdfDocument.PageInfo.Builder(595, 842, i).create());
            Canvas canvas = page.getCanvas(); canvas.drawColor(Color.WHITE);
            canvas.drawText("ORIGINAL_BODY_" + i, 40, 70, paint);
            canvas.drawText("한글 원본 문서", 40, 110, paint);
            canvas.drawBitmap(red, 40, 150, paint);
            fixture.finishPage(page);
        }
        try (FileOutputStream output = new FileOutputStream(platformOriginal)) { fixture.writeTo(output); }
        fixture.close(); red.recycle();

        // Preserve the platform-generated CJK fixture for diagnostic comparison. A tiny independently
        // authored standard PDF avoids conflating a Skia font-mapping issue with engine edit support.
        com.aspose.pdf.Document platform = new com.aspose.pdf.Document(platformOriginal.getAbsolutePath());
        try {
            com.aspose.pdf.TextAbsorber platformText = new com.aspose.pdf.TextAbsorber(); platform.getPages().accept(platformText);
            write(new File(dir, "platform-extracted.txt"), platformText.getText().getBytes(StandardCharsets.UTF_8));
        } finally { platform.dispose(); }
        File original = new File(dir, "fixture-standard-original.pdf");
        standardPdf(original);

        com.aspose.pdf.Document doc = new com.aspose.pdf.Document(original.getAbsolutePath());
        try {
            com.aspose.pdf.TextAbsorber before = new com.aspose.pdf.TextAbsorber(); doc.getPages().accept(before);
            write(new File(dir, "standard-original-extracted.txt"), before.getText().getBytes(StandardCharsets.UTF_8));
            com.aspose.pdf.TextFragmentAbsorber absorber = new com.aspose.pdf.TextFragmentAbsorber("ORIGINAL_BODY_1");
            doc.getPages().accept(absorber);
            ensure(absorber.getTextFragments().size() > 0, "PDF_NO_ORIGINAL_TEXT_OBJECT");
            for (com.aspose.pdf.TextFragment fragment : absorber.getTextFragments()) fragment.setText("EDITED_BODY_ONE");
            ensure(doc.getPages().get_Item(1).getResources().getImages().size() > 0, "PDF_NO_IMAGE_OBJECT");
            doc.getPages().get_Item(1).getResources().getImages().replace(1, new ByteArrayInputStream(image(Color.BLUE)));
            doc.getPages().insert(1, doc.getPages().get_Item(3)); doc.getPages().delete(4);
            doc.getPages().add().getParagraphs().add(new com.aspose.pdf.TextFragment("NEW_PAGE_FOUR"));
            doc.getPages().delete(3);
            ensure(doc.getPages().size() == 3, "PDF_PAGE_OPERATIONS_FAILED");
            doc.save(new File(dir, "edited.pdf").getAbsolutePath());
        } finally { doc.dispose(); }
        com.aspose.pdf.Document reopened = new com.aspose.pdf.Document(new File(dir, "edited.pdf").getAbsolutePath());
        try {
            com.aspose.pdf.TextAbsorber text = new com.aspose.pdf.TextAbsorber(); reopened.getPages().accept(text);
            write(new File(dir, "aspose-extracted.txt"), text.getText().getBytes(StandardCharsets.UTF_8));
            // The synthetic expected tokens contain no spaces. Extraction can inject glyph spacing;
            // normalize only in assertions, preserving original bytes and the full raw diagnostic.
            String normalized = text.getText().replaceAll("\\s+", "");
            ensure(normalized.contains("EDITED_BODY_ONE") && !normalized.contains("ORIGINAL_BODY_1"), "PDF_BODY_REPLACE_FAILED");
            ensure(normalized.contains("NEW_PAGE_FOUR") && !normalized.contains("ORIGINAL_BODY_2"), "PDF_PAGE_CONTENT_FAILED");
            String[] expected = {"ORIGINAL_BODY_3", "EDITED_BODY_ONE", "NEW_PAGE_FOUR"};
            for (int i = 0; i < expected.length; i++) {
                com.aspose.pdf.TextAbsorber pageText = new com.aspose.pdf.TextAbsorber(); pageText.visit(reopened.getPages().get_Item(i + 1));
                write(new File(dir, "aspose-page-" + (i + 1) + "-extracted.txt"), pageText.getText().getBytes(StandardCharsets.UTF_8));
                ensure(pageText.getText().replaceAll("\\s+", "").contains(expected[i]), "PDF_PAGE_ORDER_FAILED_" + (i + 1));
            }
        } finally { reopened.dispose(); }
        // Independent Android rendering, not the candidate engine's own renderer.
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(new File(dir, "edited.pdf"), ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(descriptor)) {
            ensure(renderer.getPageCount() == 3, "ANDROID_PDF_REOPEN_FAILED");
            for (int i = 0; i < renderer.getPageCount(); i++) {
                try (PdfRenderer.Page page = renderer.openPage(i)) {
                    Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    try (FileOutputStream out = new FileOutputStream(new File(dir, "edited-page-" + (i + 1) + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
                    bitmap.recycle();
                }
            }
        }
    }

    private void docx() throws Exception {
        File original = new File(dir, "fixture-original.docx");
        com.aspose.words.Document document = new com.aspose.words.Document();
        com.aspose.words.DocumentBuilder builder = new com.aspose.words.DocumentBuilder(document);
        builder.getFont().setName("Noto Sans CJK KR"); builder.getFont().setNameFarEast("Noto Sans CJK KR");
        builder.getFont().setBold(true); builder.getFont().setSize(18); builder.writeln("ORIGINAL_DOCX 한글 문서");
        builder.getFont().setBold(false); builder.getFont().setSize(11);
        builder.startTable(); builder.insertCell(); builder.write("CELL_ONE"); builder.insertCell(); builder.write("CELL_TWO"); builder.endRow(); builder.endTable();
        builder.insertImage(image(Color.RED)); document.save(original.getAbsolutePath());
        com.aspose.words.Document edited = new com.aspose.words.Document(original.getAbsolutePath());
        ensure(edited.getRange().replace("ORIGINAL_DOCX", "EDITED_DOCX") > 0, "DOCX_TEXT_NOT_CHANGED");
        ensure(edited.getRange().replace("한글 문서", "한글 수정 문서") > 0, "DOCX_CJK_NOT_CHANGED");
        com.aspose.words.Table table = (com.aspose.words.Table) edited.getChildNodes(com.aspose.words.NodeType.TABLE, true).get(0);
        table.getFirstRow().getFirstCell().getFirstParagraph().getRuns().get(0).setText("EDITED_CELL");
        com.aspose.words.Shape shape = null;
        com.aspose.words.NodeCollection shapes = edited.getChildNodes(com.aspose.words.NodeType.SHAPE, true);
        for (int i = 0; i < shapes.getCount(); i++) {
            com.aspose.words.Shape candidate = (com.aspose.words.Shape) shapes.get(i);
            if (candidate.hasImage()) { shape = candidate; break; }
        }
        ensure(shape != null, "DOCX_NO_REAL_IMAGE");
        shape.getImageData().setImage(new ByteArrayInputStream(image(Color.BLUE))); shape.setWidth(120); shape.setHeight(90);
        com.aspose.words.Run first = bodyRun(edited);
        first.getFont().setColor(Color.BLUE); first.getFont().setBold(true); first.getFont().setSize(22);
        File output = new File(dir, "edited.docx"); edited.save(output.getAbsolutePath());
        com.aspose.words.Document reopened = new com.aspose.words.Document(output.getAbsolutePath());
        ensure(reopened.getText().contains("EDITED_DOCX") && reopened.getText().contains("한글 수정 문서") && reopened.getText().contains("EDITED_CELL"), "DOCX_CONTENT_NOT_RETAINED");
        com.aspose.words.Font font = bodyRun(reopened).getFont();
        ensure(font.getBold() && font.getSize() == 22 && font.getColor() == Color.BLUE, "DOCX_BODY_STYLE_NOT_RETAINED");
        ensure(reopened.getChildNodes(com.aspose.words.NodeType.TABLE, true).getCount() >= 1 && reopened.getChildNodes(com.aspose.words.NodeType.SHAPE, true).getCount() >= 1, "DOCX_STRUCTURE_NOT_RETAINED");
        // ZIP/OOXML check is independent of the candidate model; external Word rendering still required.
        try (ZipFile zip = new ZipFile(output)) {
            ZipEntry entry = zip.getEntry("word/document.xml"); ensure(entry != null, "DOCX_MAIN_PART_MISSING");
            String xml = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            ensure(xml.contains("EDITED_DOCX") && xml.contains("<w:tbl") && xml.contains("<w:drawing"), "DOCX_OOXML_MISSING");
        }
        reopened.save(new File(dir, "edited-docx-preview.pdf").getAbsolutePath(), com.aspose.words.SaveFormat.PDF);
    }
    private static com.aspose.words.Run bodyRun(com.aspose.words.Document document) throws Exception {
        com.aspose.words.NodeCollection runs = document.getChildNodes(com.aspose.words.NodeType.RUN, true);
        for (int i = 0; i < runs.getCount(); i++) {
            com.aspose.words.Run run = (com.aspose.words.Run) runs.get(i);
            if (run.getText().contains("EDITED_DOCX")) return run;
        }
        throw new IllegalStateException("DOCX_BODY_RUN_MISSING");
    }
    private static JSONArray causes(Throwable error) throws Exception {
        JSONArray result = new JSONArray();
        for (int i = 0; error != null && i < 8; i++, error = error.getCause()) {
            String message = String.valueOf(error.getMessage());
            result.put(new JSONObject().put("type", error.getClass().getName()).put("message", message.substring(0, Math.min(message.length(), 1600))));
        }
        return result;
    }
    private static void standardPdf(File target) throws Exception {
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        int[] offset = new int[11];
        pdf.write("%PDF-1.4\n%Synthetic original fixture\n".getBytes(StandardCharsets.US_ASCII));
        object(pdf, offset, 1, "<< /Type /Catalog /Pages 2 0 R >>".getBytes(StandardCharsets.US_ASCII));
        object(pdf, offset, 2, "<< /Type /Pages /Kids [5 0 R 7 0 R 9 0 R] /Count 3 >>".getBytes(StandardCharsets.US_ASCII));
        object(pdf, offset, 3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>".getBytes(StandardCharsets.US_ASCII));
        byte[] rgb = new byte[80 * 60 * 3]; for (int i = 0; i < rgb.length; i += 3) rgb[i] = (byte)255;
        ByteArrayOutputStream image = new ByteArrayOutputStream();
        image.write(("<< /Type /XObject /Subtype /Image /Width 80 /Height 60 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Length " + rgb.length + " >>\nstream\n").getBytes(StandardCharsets.US_ASCII));
        image.write(rgb); image.write("\nendstream".getBytes(StandardCharsets.US_ASCII)); object(pdf, offset, 4, image.toByteArray());
        for (int page = 1; page <= 3; page++) {
            int object = 5 + (page - 1) * 2;
            object(pdf, offset, object, ("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> /XObject << /Im1 4 0 R >> >> /Contents " + (object + 1) + " 0 R >>").getBytes(StandardCharsets.US_ASCII));
            byte[] content = ("BT /F1 20 Tf 40 772 Td (ORIGINAL_BODY_" + page + ") Tj ET\nq 80 0 0 60 40 632 cm /Im1 Do Q\n").getBytes(StandardCharsets.US_ASCII);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            stream.write(("<< /Length " + content.length + " >>\nstream\n").getBytes(StandardCharsets.US_ASCII)); stream.write(content); stream.write("endstream".getBytes(StandardCharsets.US_ASCII));
            object(pdf, offset, object + 1, stream.toByteArray());
        }
        int xref = pdf.size(); pdf.write("xref\n0 11\n0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
        for (int i = 1; i <= 10; i++) pdf.write(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offset[i]).getBytes(StandardCharsets.US_ASCII));
        pdf.write(("trailer\n<< /Size 11 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));
        write(target, pdf.toByteArray());
    }
    private static void object(ByteArrayOutputStream pdf, int[] offsets, int id, byte[] content) throws Exception {
        offsets[id] = pdf.size(); pdf.write((id + " 0 obj\n").getBytes(StandardCharsets.US_ASCII)); pdf.write(content); pdf.write("\nendobj\n".getBytes(StandardCharsets.US_ASCII));
    }
    private static void write(File file, byte[] bytes) throws Exception { try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); } }
    private static void ensure(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
}
