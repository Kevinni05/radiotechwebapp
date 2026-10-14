package com.radiotech.radiotech_backend.workforce;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Personal A4 calendar; long shift labels and sites wrap without losing information. */
public final class RosterPdf {
    private RosterPdf() {}
    private static final float[] X={36,112,320,381,451,514};
    public static byte[] render(Map<String,Object> plan) throws IOException {
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()) {
            PDFont regular=font(doc,"Regular"),bold=font(doc,"Bold");
            String name=Objects.toString(plan.get("operatorName"),"Operatore"),month=plan.get("month").toString();ZoneId zone=ZoneId.of(plan.get("zone").toString());
            var slots=RosterService.rows(plan,"slots").stream().sorted(Comparator.comparing(s->s.get("date").toString())).toList();
            PDPageContentStream cs=null;
            try {
                cs=page(doc,plan,regular,bold,name,slots);float y=634;YearMonth ym=YearMonth.parse(month);
                for(int day=1;day<=ym.lengthOfMonth();day++) {
                    LocalDate date=ym.atDay(day);final String d=date.toString();var row=slots.stream().filter(s->d.equals(s.get("date"))).findFirst().orElse(null);
                    var lines=wrap(row==null?"Riposo / nessun turno":row.get("label")+" · "+row.get("site"),regular,8,198);
                    float height=Math.max(18,lines.size()*10+6);
                    if(y-height<80) {cs.close();cs=page(doc,plan,regular,bold,name,slots);y=634;}
                    if(day%2==0) {cs.setNonStrokingColor(.96f,.97f,.98f);cs.addRect(32,y-height+12,531,height);cs.fill();}
                    cs.setNonStrokingColor(.12f,.15f,.2f);text(cs,regular,8,X[0],y,date.format(DateTimeFormatter.ofPattern("dd EEE",Locale.ITALIAN)));
                    for(int line=0;line<lines.size();line++)text(cs,regular,8,X[1],y-line*10,lines.get(line));
                    if(row!=null) {
                        var start=Instant.parse(row.get("startAt").toString()).atZone(zone);var end=Instant.parse(row.get("endAt").toString()).atZone(zone);
                        text(cs,regular,8,X[2],y,start.format(DateTimeFormatter.ofPattern("HH:mm")));
                        text(cs,regular,8,X[3],y,end.format(DateTimeFormatter.ofPattern("HH:mm"))+(!start.toLocalDate().equals(end.toLocalDate())?" +1":""));
                        text(cs,regular,8,X[4],y,row.get("breakMinutes")+" min");text(cs,regular,8,X[5],y,hours(RosterService.number(row.get("workMinutes"))));
                    }
                    y-=height;
                }
            } finally {if(cs!=null)cs.close();}
            for(int i=0;i<doc.getNumberOfPages();i++)try(var footer=new PDPageContentStream(doc,doc.getPage(i),PDPageContentStream.AppendMode.APPEND,true)) {
                footer.setNonStrokingColor(.12f,.15f,.2f);
                text(footer,regular,8,36,65,"+1: uscita il giorno successivo. Le ore previste escludono la pausa indicata.");
                text(footer,regular,8,36,51,"Documento personale della versione pubblicata. Verifica nell'app eventuali aggiornamenti.");
                text(footer,regular,8,36,31,"RADIO TECH · "+month+" · Versione "+plan.get("revision"));text(footer,regular,8,510,31,(i+1)+" / "+doc.getNumberOfPages());
            }
            doc.getDocumentInformation().setTitle("Turni "+name+" · "+month);doc.getDocumentInformation().setAuthor("RADIO TECH");doc.save(out);return out.toByteArray();
        }
    }
    private static PDPageContentStream page(PDDocument doc,Map<String,Object> plan,PDFont regular,PDFont bold,String name,List<Map<String,Object>> slots) throws IOException {
        var page=new PDPage(PDRectangle.A4);doc.addPage(page);var cs=new PDPageContentStream(doc,page);cs.setNonStrokingColor(22/255f,29/255f,43/255f);cs.addRect(0,716,595,126);cs.fill();
        cs.setNonStrokingColor(1f,1f,1f);text(cs,bold,20,36,798,"RADIO TECH");text(cs,regular,12,36,776,"PIANIFICAZIONE PERSONALE · "+plan.get("month"));
        var lines=wrap(name,regular,11,510);for(int i=0;i<lines.size();i++)text(cs,regular,11,36,752-i*13,lines.get(i));
        cs.setNonStrokingColor(.12f,.15f,.2f);ZoneId zone=ZoneId.of(plan.get("zone").toString());
        String stamp=Instant.parse(plan.get("publishedAt").toString()).atZone(zone).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        text(cs,regular,9,36,700,"Versione "+plan.get("revision")+" · Pubblicata "+stamp+" · "+zone);
        long total=slots.stream().mapToLong(s->RosterService.number(s.get("workMinutes"))).sum();text(cs,bold,12,36,679,slots.size()+" turni · "+hours(total)+" ore previste");
        cs.setNonStrokingColor(.92f,.94f,.96f);cs.addRect(32,650,531,22);cs.fill();cs.setNonStrokingColor(.12f,.15f,.2f);
        String[] headers={"GIORNO","TURNO / SEDE","ENTRATA","USCITA","PAUSA","ORE"};for(int i=0;i<headers.length;i++)text(cs,bold,8,X[i],658,headers[i]);return cs;
    }
    private static PDFont font(PDDocument doc,String style) throws IOException {try(var in=RosterPdf.class.getResourceAsStream("/static/assets/pdfjs/standard_fonts/LiberationSans-"+style+".ttf")){if(in==null)throw new IOException("Font PDF non disponibile.");return PDType0Font.load(doc,in);}}
    private static String hours(long minutes){return String.format(Locale.ITALIAN,"%d:%02d",minutes/60,minutes%60);}
    private static String safe(String value,PDFont font) throws IOException {var out=new StringBuilder();for(int cp:value.replaceAll("[\\r\\n\\t]"," ").codePoints().toArray()){String s=new String(Character.toChars(cp));try{font.encode(s);out.append(s);}catch(IllegalArgumentException e){out.append('?');}}return out.toString();}
    private static List<String> wrap(String value,PDFont font,float size,float width) throws IOException {
        String s=safe(value,font);var lines=new ArrayList<String>();StringBuilder line=new StringBuilder();
        for(int cp:s.codePoints().toArray()) {String part=new String(Character.toChars(cp));if(!line.isEmpty()&&font.getStringWidth(line+part)/1000*size>width){lines.add(line.toString().strip());line.setLength(0);}line.append(part);}
        if(!line.isEmpty())lines.add(line.toString().strip());return lines.isEmpty()?List.of(""):lines;
    }
    private static void text(PDPageContentStream cs,PDFont font,float size,float x,float y,String value) throws IOException {cs.beginText();cs.setFont(font,size);cs.newLineAtOffset(x,y);cs.showText(safe(value,font));cs.endText();}
}
