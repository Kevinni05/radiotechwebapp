package com.radiotech.radiotech_backend.service;

import java.util.*;

/** Shared, server-authoritative form definitions for web and mobile. */
public final class ProCatalog {
    private ProCatalog() {}
    public record Field(String key, String label, String type, boolean required) {}
    public record Module(String id, String title, List<Field> fields, List<String> actions, boolean operator, boolean writable) {
        public Module(String id,String title,List<Field> fields,List<String> actions,boolean operator){this(id,title,fields,actions,operator,!Set.of("analytics","audit","orders","deliveries").contains(id));}
    }
    private static Field f(String key, String label, String type, boolean required) { return new Field(key,label,type,required); }
    private static Module m(String id, String title, boolean operator, String actions, Field... fields) {
        return new Module(id,title,List.of(fields),actions.isBlank()?List.of():List.of(actions.split(",")),operator);
    }
    public static final List<Module> MODULES = List.of(
        new Module("devices","Dispositivi e sessioni",List.of(f("uid","Account","text",false),f("platform","Piattaforma","text",false),f("lastLoginAt","Ultimo accesso","text",false)),List.of("REVOKE"),true,false),
        m("analytics","Indicatori e consuntivi",false,"",f("value","Valore","text",false),f("notice","Copertura dati","text",false)),
        m("audit","Audit e modifiche",false,"",f("action","Azione","text",false),f("actor","Utente","text",false),f("timestamp","Data","text",false),f("resource","Sezione","text",false),f("before","Prima","textarea",false),f("after","Dopo","textarea",false)),
        m("orders","Ordini e consegne",false,"",f("supplierId","Fornitore","text",false),f("inventoryId","Ricambio","text",false),f("quantity","Quantità","number",false),f("unitPrice","Prezzo EUR","number",false),f("approvedBy","Approvato da","text",false)),
        m("dispatch","Centrale di dispatch",false,"ASSIGN,ARCHIVE",f("name","Intervento pianificato","text",true),f("taskId","Incarico","ref:tasks",true),f("operatorId","Operatore","ref:operators",true),f("startAt","Inizio","datetime",true),f("endAt","Fine","datetime",true),f("requiredSkills","Competenze richieste (codici separati da virgola)","text",false),f("availabilityOverride","Autorizza fuori turno / pausa","select:NO,YES",true)),
        m("clients","Clienti e contatti",false,"ARCHIVE", f("name","Ragione sociale","text",true),f("email","Email","email",false),f("phone","Telefono","text",false),f("contact","Referente","text",false),f("notes","Note","textarea",false)),
        m("sites","Sedi e accessi",false,"ARCHIVE",f("name","Sede","text",true),f("clientId","Cliente","ref:clients",true),f("address","Indirizzo","text",true),f("accessInstructions","Istruzioni di accesso","textarea",false)),
        m("contracts","Contratti e SLA",false,"ARCHIVE",f("name","Contratto","text",true),f("clientId","Cliente","ref:clients",true),f("siteId","Sede","ref:sites",false),f("responseMinutes","Presa in carico (minuti)","positive",true),f("resolutionMinutes","Risoluzione (minuti)","positive",true),f("budget","Budget EUR","number",false),f("endAt","Scadenza","datetime",false)),
        m("plans","Manutenzione ricorrente",false,"GENERATE,ARCHIVE",f("name","Piano","text",true),f("siteId","Sede","ref:sites",true),f("antennaId","Antenna","ref:antennas",true),f("operatorId","Operatore","ref:operators",true),f("intervalDays","Periodicità (giorni)","positive",true),f("nextDueAt","Prossima scadenza","datetime",true),f("checklist","Checklist (una voce per riga)","textarea",true)),
        m("suppliers","Fornitori",false,"ARCHIVE",f("name","Fornitore","text",true),f("email","Email","email",false),f("phone","Telefono","text",false),f("leadTimeDays","Tempi di consegna (giorni)","number",false)),
        m("purchases","Richieste di acquisto",false,"SUBMIT,APPROVE,REJECT,RECEIVE",f("name","Acquisto","text",true),f("supplierId","Fornitore","ref:suppliers",true),f("inventoryId","Ricambio","ref:inventory",true),f("quantity","Quantità","positive",true),f("unitPrice","Prezzo unitario EUR","number",true),f("expectedAt","Consegna prevista","datetime",false)),
        m("costs","Costi e consuntivi",true,"SUBMIT,APPROVE,REJECT",f("name","Voce di costo","text",true),f("taskId","Incarico","ref:tasks",true),f("contractId","Contratto","ref:contracts",false),f("kind","Categoria","select:LABOUR,TRAVEL,MATERIAL,EXTERNAL",true),f("amount","Importo EUR","number",true),f("hours","Ore","number",false),f("notes","Dettagli","textarea",false)),
        m("documents","Documentazione tecnica",true,"PUBLISH,ARCHIVE",f("name","Documento","text",true),f("siteId","Sede","ref:sites",true),f("antennaId","Antenna","ref:antennas",false),f("audience","Visibilità","select:INTERNAL,CUSTOMER",true),f("content","Contenuto / istruzioni","textarea",true)),
        m("handovers","Passaggi di consegne",true,"ACKNOWLEDGE,ARCHIVE",f("name","Consegna","text",true),f("taskId","Incarico","ref:tasks",true),f("recipientUid","UID destinatario","text",true),f("notes","Problemi aperti e indicazioni","textarea",true)),
        m("vanstock","Dotazioni tecnici",true,"LOAD,RESERVE,CONSUME,RETURN",f("name","Dotazione","text",true),f("inventoryId","Ricambio","ref:inventory",true),f("operatorId","Operatore","ref:operators",true)),
        m("requests","Richieste di assistenza",false,"ACKNOWLEDGE,ASSIGN,RESOLVE",f("name","Richiesta","text",true),f("clientId","Cliente","ref:clients",true),f("siteId","Sede","ref:sites",true),f("antennaId","Antenna","ref:antennas",false),f("operatorId","Operatore","ref:operators",false),f("contractId","Contratto","ref:contracts",false),f("priority","Priorità","select:LOW,MEDIUM,HIGH,CRITICAL",true),f("description","Descrizione","textarea",true)),
        m("sla","Monitoraggio SLA",false,"ACKNOWLEDGE,PAUSE,RESUME,RESOLVE",f("name","Pratica SLA","text",true),f("taskId","Incarico","ref:tasks",true),f("contractId","Contratto","ref:contracts",true)),
        m("integrations","Integrazioni e webhook",false,"ENABLE,DISABLE,ARCHIVE",f("name","Integrazione","text",true),f("endpoint","Endpoint HTTPS","url",true),f("events","Eventi (es. purchases.APPROVE oppure *)","text",true)),
        m("deliveries","Consegne webhook",false,"",f("attempts","Tentativi","number",false),f("responseCode","Risposta HTTP","number",false),f("lastAttemptAt","Ultimo tentativo","text",false),f("lastError","Esito","text",false))
    );
    public static Module module(String id) {
        return MODULES.stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Sezione non valida."));
    }
    public static Map<String,Object> validate(Module module, Map<String,Object> input) {
        var result = new LinkedHashMap<String,Object>();
        for (Field field: module.fields()) {
            Object value = input.get(field.key());
            if (value == null || value.toString().isBlank()) {
                if (field.required()) throw new IllegalArgumentException(field.label()+": obbligatorio.");
                continue;
            }
            if (field.type().equals("number") || field.type().equals("positive")) {
                double number;
                try { number = Double.parseDouble(value.toString()); } catch (NumberFormatException e) { throw new IllegalArgumentException(field.label()+": numero non valido."); }
                if (!Double.isFinite(number) || number < 0 || number > 1_000_000_000 || (field.type().equals("positive") && number <= 0)) throw new IllegalArgumentException(field.label()+": valore fuori intervallo.");
                if (Set.of("quantity","intervalDays","responseMinutes","resolutionMinutes","leadTimeDays").contains(field.key()) && number != Math.rint(number)) throw new IllegalArgumentException(field.label()+": usare un numero intero.");
                result.put(field.key(),number);
            } else {
                String text = value.toString().trim();
                int max = field.type().equals("textarea") ? 32000 : 500;
                if (text.length()>max) throw new IllegalArgumentException(field.label()+": testo troppo lungo.");
                if (field.type().startsWith("ref:") && !text.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("Riferimento non valido.");
                if (field.type().startsWith("select:") && !List.of(field.type().substring(7).split(",")).contains(text)) throw new IllegalArgumentException(field.label()+": scelta non valida.");
                if (field.type().equals("datetime")) { try { java.time.Instant.parse(text); } catch (Exception e) { throw new IllegalArgumentException(field.label()+": data non valida."); } }
                if (field.type().equals("url")) { var uri=java.net.URI.create(text); if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalArgumentException("Endpoint HTTPS non valido."); }
                if (field.type().equals("email") && !text.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Email non valida.");
                result.put(field.key(),text);
            }
        }
        for (String key: input.keySet()) if (module.fields().stream().noneMatch(f -> f.key().equals(key))) throw new IllegalArgumentException("Campo non modificabile: "+key);
        return result;
    }
}
