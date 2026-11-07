package com.pleosstay.docxprobe;
import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import org.apache.poi.xwpf.usermodel.*;
public class PoiProbe extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle result=new Bundle();try{
  long start=android.os.SystemClock.elapsedRealtime();
  File file=new File(getTargetContext().getFilesDir(),"fixture.docx");
  android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(20,20,android.graphics.Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.RED);
  ByteArrayOutputStream image=new ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,image);bitmap.recycle();
  try(XWPFDocument d=new XWPFDocument()){
   XWPFRun r=d.createParagraph().createRun();r.setText("한글 원본");r.setBold(true);r.setFontSize(18);
   d.createTable(2,2).getRow(0).getCell(0).setText("표 원본");
   d.createParagraph().createRun().setText("보존 문단");
   d.createParagraph().createRun().addPicture(new ByteArrayInputStream(image.toByteArray()),Document.PICTURE_TYPE_PNG,"image.png",952500,952500);
   try(OutputStream out=new FileOutputStream(file)){d.write(out);}
  }
  try(XWPFDocument d=new XWPFDocument(new FileInputStream(file))){
   d.getParagraphs().get(0).getRuns().get(0).setText("한글 수정",0);
   XWPFRun r=d.getParagraphs().get(0).getRuns().get(0);r.setFontFamily("Noto Sans CJK KR");r.setColor("1234AB");r.setItalic(true);r.setUnderline(UnderlinePatterns.SINGLE);d.getParagraphs().get(0).setAlignment(ParagraphAlignment.CENTER);
   d.getTables().get(0).getRow(0).getCell(0).setText("셀 수정");
   android.graphics.Bitmap blue=android.graphics.Bitmap.createBitmap(20,20,android.graphics.Bitmap.Config.ARGB_8888);blue.eraseColor(android.graphics.Color.BLUE);ByteArrayOutputStream replacement=new ByteArrayOutputStream();blue.compress(android.graphics.Bitmap.CompressFormat.PNG,100,replacement);blue.recycle();
   try(OutputStream out=d.getAllPictures().get(0).getPackagePart().getOutputStream()){out.write(replacement.toByteArray());}
   try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"edited.docx"))){d.write(out);}
  }
  try(XWPFDocument d=new XWPFDocument(new FileInputStream(new File(getTargetContext().getFilesDir(),"edited.docx")))){
   if(!d.getParagraphs().get(0).getText().equals("한글 수정")||!d.getParagraphs().get(1).getText().equals("보존 문단")||!d.getTables().get(0).getRow(0).getCell(0).getText().contains("셀 수정")||d.getAllPictures().size()!=1)throw new IllegalStateException("REOPEN_MISMATCH");
  }
  result.putLong("elapsedMillis",android.os.SystemClock.elapsedRealtime()-start);
  result.putString("poi","PASS_CREATE_TEXT_STYLE_TABLE_IMAGE_REOPEN");
 }catch(Throwable e){result.putString("poi","FAIL");result.putString("error",e.toString());Throwable c=e.getCause();if(c!=null)result.putString("cause",c.toString());}
 finish(0,result);}
}
