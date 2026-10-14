package com.radiotech.radiotech_backend.dossier;

import java.time.Instant;
import java.util.*;

/** Canonical, bounded input. Client-supplied tenant/author/status fields are never trusted. */
public final class DossierRules {
    private DossierRules() {}
    public static final Set<String> SECTIONS=Set.of("identity","configuration","measurements","dependencies","documents","photos","components","issues","history","access");
    public static String text(Object value,int max) {
        String s=Objects.toString(value,"").trim();
        if(s.length()>max||s.chars().anyMatch(c->c<32&&c!='\n'&&c!='\t'))throw bad("Testo non valido o troppo lungo.");
        return s;
    }
    public static String required(Map<String,Object> d,String key,int max) {String s=text(d.get(key),max);if(s.isBlank())throw bad("Campo obbligatorio: "+key);return s;}
    public static String id(Object value) {String s=text(value,128);if(!s.matches("[A-Za-z0-9_-]{1,128}"))throw bad("Riferimento non valido.");return s;}
    public static double number(Object v) {if(!(v instanceof Number n)||!Double.isFinite(n.doubleValue()))throw bad("Numero non valido.");return n.doubleValue();}
    public static long version(Object v) {return v instanceof Number n?n.longValue():0;}
    public static String date(Object value) {String s=text(value,40);try{Instant t=Instant.parse(s);if(t.isAfter(Instant.now().plusSeconds(300)))throw bad("Data futura non consentita.");return t.toString();}catch(java.time.format.DateTimeParseException e){throw bad("Data e ora non valide.");}}
    @SuppressWarnings("unchecked") public static Map<String,Object> map(Object v) {if(!(v instanceof Map<?,?>))throw bad("Oggetto richiesto.");return new LinkedHashMap<>((Map<String,Object>)v);}
    @SuppressWarnings("unchecked") public static List<Map<String,Object>> rows(Map<String,Object> d,String k) {return new ArrayList<>((List<Map<String,Object>>)d.getOrDefault(k,List.of()));}
    public static Map<String,Object> strings(Object value,int max) {Map<String,Object> out=new TreeMap<>();var m=map(value);if(m.size()>max)throw bad("Troppi parametri.");m.forEach((k,v)->{String key=text(k,80);if(key.isBlank()||key.contains("."))throw bad("Nome parametro non valido.");out.put(key,text(v,1000));});return out;}
    public static Map<String,Object> canonical(String kind,Map<String,Object> d) {
        Map<String,Object> r=new LinkedHashMap<>();
        switch(kind) {
            case "access" -> {for(String k:List.of("address","contact","hours","parking","keyInstructions","route","restrictions"))r.put(k,text(d.get(k),2000));}
            case "configuration" -> {r.put("label",required(d,"label",120));r.put("reason",required(d,"reason",2000));r.put("values",strings(d.get("values"),60));}
            case "measurements" -> {
                r.put("metric",required(d,"metric",80));r.put("unit",text(d.get("unit"),20));r.put("value",number(d.get("value")));r.put("measuredAt",date(d.get("measuredAt")));r.put("notes",text(d.get("notes"),1000));r.put("source","MANUAL");
                for(String k:List.of("low","high"))if(d.get(k)!=null&&!"".equals(d.get(k)))r.put(k,number(d.get(k)));
                if(r.containsKey("low")&&r.containsKey("high")&&number(r.get("low"))>number(r.get("high")))throw bad("Soglia minima superiore alla massima.");
                double v=number(r.get("value"));r.put("outsideThreshold",r.containsKey("low")&&v<number(r.get("low"))||r.containsKey("high")&&v>number(r.get("high")));
            }
            case "dependencies" -> {r.put("antennaId",id(d.get("antennaId")));r.put("label",required(d,"label",120));String type=required(d,"type",20);if(!Set.of("RADIO","CABLE","POWER","FIBER").contains(type))throw bad("Tipo di collegamento non valido.");r.put("type",type);r.put("notes",text(d.get("notes"),1000));}
            case "documents","photos" -> {
                r.put("title",required(d,"title",160));String file=required(d,"fileId",64);if(!file.matches("[a-f0-9]{64}"))throw bad("Allegato non valido.");r.put("fileId",file);r.put("notes",text(d.get("notes"),2000));
                if(kind.equals("photos")){
                    Object raw=d.getOrDefault("annotations",List.of());if(!(raw instanceof List<?> a)||a.size()>40)throw bad("Massimo 40 annotazioni.");var marks=new ArrayList<Map<String,Object>>();
                    for(Object v:a){var m=map(v);String type=required(m,"type",10);if(!Set.of("ARROW","RECT","TEXT").contains(type))throw bad("Annotazione non valida.");var mark=new LinkedHashMap<String,Object>();mark.put("type",type);mark.put("text",text(m.get("text"),200));for(String k:List.of("x","y","x2","y2")){double n=number(m.get(k));if(n<0||n>1)throw bad("Coordinate fuori immagine.");mark.put(k,n);}marks.add(mark);}r.put("annotations",marks);
                }
            }
            case "components" -> {for(String k:List.of("name","model","serial","installedAt","warrantyUntil","notes"))r.put(k,k.equals("name")?required(d,k,160):text(d.get(k),1000));}
            case "issues" -> {r.put("title",required(d,"title",160));r.put("notes",required(d,"notes",3000));String s=text(d.getOrDefault("status","OPEN"),20);if(!Set.of("OPEN","RESOLVED").contains(s))throw bad("Stato problema non valido.");r.put("status",s);}
            default -> throw bad("Sezione non valida.");
        }
        return r;
    }
    public static Map<String,Object> empty() {var d=new LinkedHashMap<String,Object>();d.put("version",0L);d.put("access",Map.of());for(String k:List.of("configuration","measurements","dependencies","documents","photos","components","issues","proposals"))d.put(k,List.of());return d;}
    /** Public output is allowlisted, never a dump of the internal dossier or report payload. */
    public static Map<String,Object> publicSnapshot(Map<String,Object> source,List<String> sections) {
        if(sections==null||sections.isEmpty()||sections.size()>SECTIONS.size()||!SECTIONS.containsAll(sections))throw bad("Seleziona sezioni valide.");
        var out=new LinkedHashMap<String,Object>();out.put("name",source.get("name"));out.put("snapshotAt",Instant.now().toString());
        for(String key:new LinkedHashSet<>(sections)) {
            Object value=source.get(key);if(value==null)continue;
            if(key.equals("access")||key.equals("identity")){out.put(key,map(value));continue;}
            var items=rows(source,key);var clean=new ArrayList<Map<String,Object>>();
            for(var item:items){var c=new LinkedHashMap<>(item);for(String f:List.of("actor","uid","tenantId","createdBy","operatorUid","operatorId"))c.remove(f);clean.add(c);}out.put(key,clean);
        }
        return out;
    }
    public static IllegalArgumentException bad(String message){return new IllegalArgumentException(message);}
}
