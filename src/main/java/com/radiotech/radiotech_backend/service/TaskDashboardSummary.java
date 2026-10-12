package com.radiotech.radiotech_backend.service;

import com.google.cloud.firestore.*;
import com.radiotech.radiotech_backend.model.TaskStatus;
import java.util.*;

/** Aggregations avoid transferring closed task archives; unfamiliar legacy values keep the old reader. */
final class TaskDashboardSummary {
    record Summary(int total,int completed,int cancelled,List<QueryDocumentSnapshot> active) {}
    private static final List<String> ALIASES=List.of("CREATED","NEW","ASSIGNED","PENDENTE","ACCEPTED","ACCETTATO","EN_ROUTE","IN_TRANSITO","ROUTE","CHECKED_IN","CHECK_IN","CHECKEDIN","IN_PROGRESS","IN_CORSO","IN_LAVORAZIONE","WAITING","IN_ATTESA","COMPLETED","COMPLETATO","REPORT_SUBMITTED","REPORT","SUBMITTED","APPROVED","APPROVATO","CLOSED","CHIUSO","CANCELLED","ANNULLATO","CANCELED");
    static Summary read(Firestore db,String tenant)throws Exception {
        Query source=db.collection("tasks").whereEqualTo("tenantId",tenant);
        int total=Math.toIntExact(source.count().get().get().getCount());
        if(total==0)return new Summary(0,0,0,List.of());
        var raw=new LinkedHashSet<String>();for(var status:TaskStatus.values())raw.add(status.name());
        // Normalized installations use a small indexed status set. Expand only for legacy data.
        int recognized=count(source,new ArrayList<>(raw));
        if(recognized!=total){raw.clear();for(String alias:ALIASES){raw.add(alias);raw.add(alias.toLowerCase(Locale.ROOT));}recognized=count(source,new ArrayList<>(raw));}
        // Null, arbitrary casing and unrecognized legacy statuses retain exact previous behavior.
        if(recognized!=total)return null;
        var completed=new ArrayList<String>();var cancelled=new ArrayList<String>();var active=new ArrayList<String>();
        for(String value:raw){TaskStatus status=TaskStatus.parse(value);if(Set.of(TaskStatus.COMPLETED,TaskStatus.APPROVED,TaskStatus.CLOSED).contains(status))completed.add(value);else if(status==TaskStatus.CANCELLED)cancelled.add(value);else if(status!=TaskStatus.REPORT_SUBMITTED)active.add(value);}
        var documents=new ArrayList<QueryDocumentSnapshot>();
        for(int offset=0;offset<active.size();offset+=30){Query query=source.whereIn("status",active.subList(offset,Math.min(offset+30,active.size()))).select("status","dueAt").orderBy(FieldPath.documentId());String after=null;
            while(true){var page=(after==null?query:query.startAfter(after)).limit(500).get().get();documents.addAll(page.getDocuments());if(page.size()<500)break;after=page.getDocuments().getLast().getId();}
        }
        return new Summary(total,count(source,completed),count(source,cancelled),documents);
    }
    private static int count(Query source,List<String> values)throws Exception {
        long total=0;for(int offset=0;offset<values.size();offset+=30)total+=source.whereIn("status",values.subList(offset,Math.min(offset+30,values.size()))).count().get().get().getCount();return Math.toIntExact(total);
    }
}
