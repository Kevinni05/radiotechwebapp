package com.radiotech.radiotech_backend.workforce;

import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import org.springframework.stereotype.Component;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Single-worker bounded CP-SAT jobs avoid oversubscribing the API host. */
@Component
public class RosterSolver {
    private static final Semaphore CAPACITY = new Semaphore(1);
    private record Choice(Roster.Assignment assignment, Roster.Slot slot, BoolVar variable) {}
    public Roster.Result solve(YearMonth month, Roster.Config config, List<Roster.Slot> boundary, boolean validateOnly) {
        if(!CAPACITY.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Generatore occupato. Riprova tra poco.");
        try { return run(month, config, boundary, validateOnly); } finally { CAPACITY.release(); }
    }
    private Roster.Result run(YearMonth month, Roster.Config c, List<Roster.Slot> boundary, boolean validateOnly) {
        Loader.loadNativeLibraries();
        CpModel model = new CpModel(); var choices = new ArrayList<Choice>(); var warnings = new ArrayList<String>();
        Set<String> templates = new HashSet<>(), members = new HashSet<>(), assigned = new HashSet<>();
        for(var t:c.templates()) if(!templates.add(t.id())) throw new IllegalArgumentException("Modello turno duplicato.");
        for(var m:c.members()) if(!members.add(m.operatorId())) throw new IllegalArgumentException("Operatore duplicato.");
        Set<String> locks = new HashSet<>();
        for(var a:c.assignments()) {
            if(!month.equals(YearMonth.from(LocalDate.parse(a.date()))) || !members.contains(a.operatorId()) || !templates.contains(a.templateId()))
                throw new IllegalArgumentException("Assegnazione fuori mese o con riferimenti non validi.");
            if(!assigned.add(a.operatorId()+":"+a.date())) throw new IllegalArgumentException("Due turni nello stesso giorno per un operatore.");
            if(a.locked() || validateOnly) locks.add(key(a));
        }
        for(int day=1;day<=month.lengthOfMonth();day++) {
            LocalDate date=month.atDay(day); int dow=date.getDayOfWeek().getValue();
            for(var t:c.templates()) {
                if(!t.days().contains(dow)) continue;
                var coverage=LinearExpr.newBuilder(); int candidates=0;
                for(var member:c.members()) {
                    var a=new Roster.Assignment(member.operatorId(),t.id(),date.toString(),locks.contains(member.operatorId()+":"+date+":"+t.id()));
                    var s=Roster.slot(a,t,c.rules(),"","");
                    boolean eligible=member.days().contains(dow) && !member.unavailableDates().contains(date.toString())
                            && (member.allowNight() || !t.night()) && (member.allowedTemplates().isEmpty() || member.allowedTemplates().contains(t.id()))
                            && (t.skill()==null || t.skill().isBlank() || member.skills().contains(t.skill()));
                    if(!eligible) continue;
                    LocalDate lastDay=Instant.parse(s.endAt()).minusNanos(1).atZone(ZoneId.of(c.rules().zone())).toLocalDate();
                    if(member.unavailableDates().contains(lastDay.toString())) continue;
                    BoolVar variable=model.newBoolVar(key(a)); coverage.add(variable); candidates++;
                    choices.add(new Choice(a,s,variable));
                    if(locks.contains(key(a))) model.addEquality(variable,1);
                    else if(validateOnly) model.addEquality(variable,0);
                }
                model.addEquality(coverage,t.coverage());
                if(candidates<t.coverage()) warnings.add(date+" / "+t.label()+": servono "+t.coverage()+" operatori, disponibili "+candidates+".");
            }
        }
        Set<String> available=new HashSet<>(); choices.forEach(x->available.add(key(x.assignment())));
        for(String lock:locks) if(!available.contains(lock)) warnings.add("Turno bloccato incompatibile con disponibilità o vincoli: "+lock);
        if(!warnings.isEmpty()) return new Roster.Result("INFEASIBLE",List.of(),warnings,0);
        var objective=LinearExpr.newBuilder();
        for(var m:c.members()) {
            var personal=choices.stream().filter(x->x.assignment().operatorId().equals(m.operatorId())).toList();
            var fixed=boundary.stream().filter(x->x.operatorId().equals(m.operatorId())).toList();
            for(int day=1;day<=month.lengthOfMonth();day++) {
                String date=month.atDay(day).toString(); var daily=LinearExpr.newBuilder();
                personal.stream().filter(x->x.assignment().date().equals(date)).forEach(x->daily.add(x.variable()));
                model.addLessOrEqual(daily,1);
            }
            int rest=c.rules().minRestMinutes();
            for(int i=0;i<personal.size();i++) {
                var a=personal.get(i);
                for(int j=i+1;j<personal.size();j++) {
                    var b=personal.get(j);
                    if(incompatible(a.slot(),b.slot(),rest)) model.addLessOrEqual(LinearExpr.sum(new LinearArgument[]{a.variable(),b.variable()}),1);
                }
                for(var b:fixed) if(incompatible(a.slot(),b,rest)) model.addEquality(a.variable(),0);
            }
            var total=LinearExpr.newBuilder(); personal.forEach(x->total.addTerm(x.variable(),x.slot().workMinutes()));
            model.addLinearConstraint(total,m.minMonthlyMinutes(),m.maxMonthlyMinutes());
            if(m.targetMonthlyMinutes()>0) {
                IntVar deviation=model.newIntVar(0,44640,"target-"+m.operatorId());
                model.addAbsEquality(deviation,LinearExpr.newBuilder().add(total).add(-m.targetMonthlyMinutes()));
                objective.addTerm(deviation,2);
            }
            int consecutive=Math.min(m.maxConsecutiveDays(),c.rules().maxConsecutiveDays());
            for(LocalDate start=month.atDay(1).minusDays(consecutive);!start.isAfter(month.atEndOfMonth());start=start.plusDays(1)) {
                var expr=LinearExpr.newBuilder(); long count=0;
                LocalDate end=start.plusDays(consecutive);
                for(var x:personal) if(inRange(x.assignment().date(),start,end)) expr.add(x.variable());
                for(var x:fixed) if(inRange(x.date(),start,end)) count++;
                model.addLessOrEqual(expr,consecutive-count);
            }
            // Split paid minutes at local midnight, including overnight shifts across ISO weeks.
            for(LocalDate week=month.atDay(1).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));!week.isAfter(month.atEndOfMonth());week=week.plusWeeks(1)) {
                var expr=LinearExpr.newBuilder(); long fixedMinutes=0;
                for(var x:personal) expr.addTerm(x.variable(),weekMinutes(x.slot(),week,c.rules().zone()));
                for(var x:fixed) fixedMinutes+=weekMinutes(x,week,c.rules().zone());
                model.addLessOrEqual(expr,Math.min(m.maxWeeklyMinutes(),c.rules().maxWeeklyMinutes())-fixedMinutes);
            }
            for(var x:personal) {
                int dow=LocalDate.parse(x.assignment().date()).getDayOfWeek().getValue();
                if(!m.preferredDays().isEmpty() && !m.preferredDays().contains(dow)) objective.addTerm(x.variable(),60);
                // Stable draft edits are preferred over unnecessary reassignment.
                if(c.assignments().stream().anyMatch(a->key(a).equals(key(x.assignment())))) objective.addTerm(x.variable(),-5);
            }
        }
        // Minimise spread of nights and weekends across participating employees.
        for(boolean night:List.of(true,false)) {
            var counts=new ArrayList<IntVar>();
            for(var m:c.members()) {
                var count=model.newIntVar(0,31,(night?"nights-":"weekends-")+m.operatorId()); var expr=LinearExpr.newBuilder();
                choices.stream().filter(x->x.assignment().operatorId().equals(m.operatorId()) && (night?x.slot().night():LocalDate.parse(x.slot().date()).getDayOfWeek().getValue()>=6)).forEach(x->expr.add(x.variable()));
                model.addEquality(count,expr); counts.add(count);
            }
            var high=model.newIntVar(0,31,"max"+night); var low=model.newIntVar(0,31,"min"+night);
            model.addMaxEquality(high,counts.toArray(IntVar[]::new)); model.addMinEquality(low,counts.toArray(IntVar[]::new));
            objective.addTerm(high,120).addTerm(low,-120);
        }
        model.minimize(objective);
        CpSolver solver=new CpSolver(); solver.getParameters().setMaxTimeInSeconds(c.rules().solveSeconds()).setNumSearchWorkers(1);
        CpSolverStatus status=solver.solve(model);
        if(status==CpSolverStatus.OPTIMAL || status==CpSolverStatus.FEASIBLE) {
            var assignments=choices.stream().filter(x->solver.booleanValue(x.variable())).map(Choice::assignment).toList();
            return new Roster.Result(status.name(),assignments,List.of(),solver.wallTime());
        }
        String diagnostic=status==CpSolverStatus.INFEASIBLE?"Nessun calendario soddisfa tutti i vincoli. Verifica ore, riposi, giorni consecutivi, competenze e turni bloccati.":"Tempo di ricerca esaurito senza una soluzione valida; aumenta il limite o rivedi i vincoli.";
        return new Roster.Result(status.name(),List.of(),List.of(diagnostic),solver.wallTime());
    }
    public static String key(Roster.Assignment a) { return a.operatorId()+":"+a.date()+":"+a.templateId(); }
    private static boolean inRange(String date,LocalDate start,LocalDate end) { LocalDate d=LocalDate.parse(date); return !d.isBefore(start)&&!d.isAfter(end); }
    static boolean incompatible(Roster.Slot a,Roster.Slot b,int rest) {
        Instant as=Instant.parse(a.startAt()),ae=Instant.parse(a.endAt()),bs=Instant.parse(b.startAt()),be=Instant.parse(b.endAt());
        if(as.isAfter(bs)) return Duration.between(be,as).toMinutes()<rest;
        return Duration.between(ae,bs).toMinutes()<rest;
    }
    static long weekMinutes(Roster.Slot slot,LocalDate week,String zone) {
        Instant start=Instant.parse(slot.startAt()),end=Instant.parse(slot.endAt());
        Instant ws=week.atStartOfDay(ZoneId.of(zone)).toInstant(),we=week.plusDays(7).atStartOfDay(ZoneId.of(zone)).toInstant();
        Instant from=start.isAfter(ws)?start:ws,to=end.isBefore(we)?end:we;
        long overlap=Math.max(0,Duration.between(from,to).toMinutes()), duration=Duration.between(start,end).toMinutes();
        // Distribute the planned pause proportionally; the roster does not specify its exact time.
        return overlap==0?0:Math.round((double)overlap*slot.workMinutes()/duration);
    }
}
