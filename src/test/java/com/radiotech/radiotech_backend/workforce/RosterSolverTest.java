package com.radiotech.radiotech_backend.workforce;

import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RosterSolverTest {
    private Roster.Rules rules(int rest) {return new Roster.Rules("Europe/Rome",rest,5,2400,10,5);}
    private Roster.Member member(String id,boolean nights,List<String> unavailable) {return new Roster.Member(id,0,12000,0,2400,5,nights,List.of(1,2,3,4,5,6,7),unavailable,List.of(),List.of(),List.of());}
    private Roster.Template day(int coverage) {return new Roster.Template("DAY","Diurno","08:00","16:30",30,coverage,List.of(1,2,3,4,5),"Sede Lecce","",false);}
    @Test void generationMeetsCoverageAndNeverAssignsUnavailableOperator() {
        var month=YearMonth.of(2026,10);var c=new Roster.Config(rules(660),List.of(day(1)),List.of(member("a",false,List.of("2026-10-01")),member("b",false,List.of())),List.of(),0,"");
        var result=new RosterSolver().solve(month,c,List.of(),false);
        assertTrue(Set.of("OPTIMAL","FEASIBLE").contains(result.status()));assertEquals(22,result.assignments().size());
        assertFalse(result.assignments().stream().anyMatch(a->a.operatorId().equals("a")&&a.date().equals("2026-10-01")));
        var validate=new Roster.Config(c.rules(),c.templates(),c.members(),result.assignments(),0,"");
        assertTrue(Set.of("OPTIMAL","FEASIBLE").contains(new RosterSolver().solve(month,validate,List.of(),true).status()));
    }
    @Test void impossibleCoverageReportsFailureInsteadOfRelaxingRules() {
        var c=new Roster.Config(rules(660),List.of(day(2)),List.of(member("a",false,List.of())),List.of(),0,"");
        var result=new RosterSolver().solve(YearMonth.of(2026,10),c,List.of(),false);
        assertEquals("INFEASIBLE",result.status());assertFalse(result.diagnostics().isEmpty());assertTrue(result.assignments().isEmpty());
    }
    @Test void lockedUnavailableAssignmentIsRejected() {
        var a=new Roster.Assignment("a","DAY","2026-10-01",true);
        var c=new Roster.Config(rules(660),List.of(day(1)),List.of(member("a",false,List.of("2026-10-01")),member("b",false,List.of())),List.of(a),0,"");
        assertEquals("INFEASIBLE",new RosterSolver().solve(YearMonth.of(2026,10),c,List.of(),false).status());
    }
    @Test void overnightRestIncludesNeighbouringMonthAndDstDuration() {
        var night=new Roster.Template("N","Notte","22:00","06:00",0,1,List.of(1,2,3,4,5,6,7),"","",true);
        var slot=Roster.slot(new Roster.Assignment("a","N","2026-10-24",true),night,rules(660),"uid-a","Mario");
        assertEquals(540,slot.workMinutes()); // Fall-back night has nine actual hours.
        var tomorrow=Roster.slot(new Roster.Assignment("a","DAY","2026-10-25",true),day(1),rules(660),"uid-a","Mario");
        assertTrue(RosterSolver.incompatible(slot,tomorrow,660));
        var boundary=Roster.slot(new Roster.Assignment("a","N","2026-09-30",true),night,rules(660),"uid-a","Mario");
        var c=new Roster.Config(rules(660),List.of(day(1)),List.of(member("a",true,List.of())),List.of(new Roster.Assignment("a","DAY","2026-10-01",true)),0,"");
        assertEquals("INFEASIBLE",new RosterSolver().solve(YearMonth.of(2026,10),c,List.of(boundary),false).status());
    }
    @Test void manualMissingCoverageCannotBePublished() {
        var c=new Roster.Config(rules(660),List.of(day(1)),List.of(member("a",false,List.of())),List.of(),0,"");
        assertEquals("INFEASIBLE",new RosterSolver().solve(YearMonth.of(2026,10),c,List.of(),true).status());
    }
    @Test void springGapIsExplicitAndOvernightAbsenceIsRespected() {
        var t=new Roster.Template("GAP","Cambio ora","02:30","06:00",0,1,List.of(7),"","",false);
        assertThrows(IllegalArgumentException.class,()->Roster.slot(new Roster.Assignment("a","GAP","2026-03-29",false),t,rules(660),"a","A"));
        var n=new Roster.Template("N","Notte","22:00","06:00",0,1,List.of(1,2,3,4,5,6,7),"","",true);
        var c=new Roster.Config(rules(660),List.of(n),List.of(member("a",true,List.of("2026-10-02"))),List.of(new Roster.Assignment("a","N","2026-10-01",true)),0,"");
        assertEquals("INFEASIBLE",new RosterSolver().solve(YearMonth.of(2026,10),c,List.of(),false).status());
    }
    @Test void personalPdfHasAllDatesVersionAndOvernightMarker() throws Exception {
        var n=new Roster.Template("N","Notte tecnica","22:00","06:00",30,1,List.of(1,2,3,4,5,6,7),"Centro operativo Lecce","",true);
        var slots=new ArrayList<Map<String,Object>>();
        for(int d=1;d<=31;d++) {
            var s=Roster.slot(new Roster.Assignment("a","N",String.format("2026-10-%02d",d),false),n,rules(660),"uid-a","Operatore con nome molto lungo e caratteri accentati È À");
            slots.add(Map.of("date",s.date(),"label",s.label(),"site",s.site(),"startAt",s.startAt(),"endAt",s.endAt(),"breakMinutes",s.breakMinutes(),"workMinutes",s.workMinutes()));
        }
        var bytes=RosterPdf.render(Map.of("month","2026-10","revision",3,"publishedAt","2026-09-25T10:00:00Z","zone","Europe/Rome","operatorName","Mario Rossi","slots",slots));
        try(var doc=Loader.loadPDF(bytes)) {
            assertTrue(doc.getNumberOfPages() <= 2);String text=new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Versione 3"));assertTrue(text.contains("31 sab"));assertTrue(text.contains("+1"));assertTrue(text.contains("Mario Rossi"));
        }
        Files.createDirectories(Path.of("build/workforce-qa"));Files.write(Path.of("build/workforce-qa/turni-personali.pdf"),bytes);
    }
}
