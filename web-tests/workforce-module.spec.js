import {test,expect} from '@playwright/test';
import fs from 'node:fs';
const source=fs.readFileSync('frontend/control-room/features/workforce.js','utf8').replace('export function installWorkforce','window.installWorkforce = function');
const config={rules:{zone:'Europe/Rome',minRestMinutes:660,maxConsecutiveDays:5,maxWeeklyMinutes:2400,toleranceMinutes:10,solveSeconds:5},templates:[{id:'DAY',label:'Diurno',start:'08:00',end:'16:30',breakMinutes:30,coverage:1,days:[1,2,3,4,5],site:'Lecce',skill:'',night:false}],members:[{operatorId:'op-a',minMonthlyMinutes:0,maxMonthlyMinutes:12000,targetMonthlyMinutes:9600,maxWeeklyMinutes:2400,maxConsecutiveDays:5,allowNight:true,days:[1,2,3,4,5],unavailableDates:[],allowedTemplates:[],skills:[],preferredDays:[]}],assignments:[],notes:''};
async function setup(page,{emptyPlan=false,operatorCount=1}={}) {
 await page.setContent('<nav class="nav"></nav><main><section class="view"></section><footer class="footer"></footer></main>');
 await page.addStyleTag({path:'src/main/resources/static/assets/workforce.css'});
 await page.addScriptTag({content:source});
 await page.evaluate(({config,emptyPlan,operatorCount})=>{
  window.calls=[];window.version=1;
  window.RadioTechDialogs={confirm:async()=>true,prompt:async()=> 'Verifica completata'};
  window.installWorkforce({state:{authRole:'ADMIN',sessionEpoch:0},escapeHtml:s=>String(s??'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('"','&quot;'),toast:(...s)=>window.lastToast=s,
   switchView:view=>{document.querySelectorAll('.view').forEach(el=>el.classList.toggle('active',el.id===`view-${view}`));window.dispatchEvent(new CustomEvent('radiotech:view-changed',{detail:{view}}));},
   apiFetch:async(url,options={})=>{
    window.calls.push({url,options});
    if(url.endsWith('/roster/members'))return Array.from({length:operatorCount},(_,i)=>({operatorId:`op-${String.fromCharCode(97+i)}`,uid:`uid-${String.fromCharCode(97+i)}`,name:i===0?'Mario Rossi':`Operaio ${i+1}`}));
    if(url.includes('/attendance?'))return {month:'2026-10',zone:'Europe/Rome',publishedRevision:1,plannedMinutes:480,registeredMinutes:450,approvedMinutes:0,notice:'Dati di prova',sessions:[{id:'a'.repeat(64),uid:'uid-a',name:'Mario Rossi',startedAt:'2026-10-14T06:18:00Z',endedAt:'2026-10-14T14:00:00Z',workMinutes:450,breakMinutes:30,status:'OFF_DUTY',reviewStatus:'UNREVIEWED',version:1}],anomalies:[{name:'Mario Rossi',type:'LATE',date:'2026-10-14',message:'Ritardo di 18 minuti.'}]};
    if(url.endsWith('/generate'))return {generated:false,result:{status:'INFEASIBLE',diagnostics:['Copertura insufficiente.']}};
    if(url.endsWith('/publish'))return {plan:{month:'2026-10',version:++window.version,status:'PUBLISHED',config,publishedRevision:1,published:{slots:[{operatorId:'op-a',name:'Mario Rossi'}]}}};
    if(url.endsWith('/notify'))return {deliveredDevices:0,failedOperators:[],revision:1};
    if(options.method==='PUT')return {month:'2026-10',version:++window.version,status:'DRAFT',config:JSON.parse(options.body)};
    return emptyPlan?{month:'2026-10',version:0,status:'EMPTY'}:{month:'2026-10',version:window.version,status:'DRAFT',config};
   }});
 },{config,emptyPlan,operatorCount});
}

test('new company calendars include everyone from 09:00 to 16:00 and save edited hours',async({page})=>{
 await setup(page,{emptyPlan:true,operatorCount:3});await page.locator('[data-view="rostering"]').click();
 const template=page.locator('#rosterTemplates [data-template]').first();
 await expect(template.locator('[data-field="start"]')).toHaveValue('09:00');
 await expect(template.locator('[data-field="end"]')).toHaveValue('16:00');
 await expect(template.locator('[data-field="allMembers"]')).toBeChecked();
 await expect(template.locator('[data-field="coverage"]')).toBeDisabled();
 await expect(page.locator('#rosterCalendar tbody tr')).toHaveCount(3);
 await template.locator('[data-field="start"]').fill('08:30');
 await template.locator('[data-field="end"]').fill('15:30');
 await template.locator('[data-field="breakMinutes"]').fill('30');
 await page.locator('#rosterSave').click();
 await expect.poll(()=>page.evaluate(()=>window.calls.filter(c=>c.options.method==='PUT').length)).toBe(1);
 const saved=await page.evaluate(()=>JSON.parse(window.calls.find(c=>c.options.method==='PUT').options.body));
 expect(saved.members.map(m=>m.operatorId)).toEqual(['op-a','op-b','op-c']);
 expect(saved.templates[0]).toMatchObject({start:'08:30',end:'15:30',breakMinutes:30,allMembers:true});
 await expect(template.locator('[data-field="start"]')).toHaveValue('08:30');
});

test('existing drafts can apply company hours and switch back to a fixed headcount',async({page})=>{
 await setup(page);await page.locator('[data-view="rostering"]').click();
 const template=page.locator('#rosterTemplates [data-template]').first();
 await expect(template.locator('[data-field="start"]')).toHaveValue('08:00');
 await expect(template.locator('[data-field="allMembers"]')).not.toBeChecked();
 await template.locator('[data-company-hours]').click();
 await expect(template.locator('[data-field="start"]')).toHaveValue('09:00');
 await expect(template.locator('[data-field="end"]')).toHaveValue('16:00');
 await expect(template.locator('[data-field="allMembers"]')).toBeChecked();
 await page.locator('#rosterGenerate').click();
 await expect(page.locator('#rosterNotice')).toHaveText('Copertura insufficiente.');
 let saved=await page.evaluate(()=>JSON.parse(window.calls.find(c=>c.options.method==='PUT').options.body));
 expect(saved.templates[0]).toMatchObject({start:'09:00',end:'16:00',allMembers:true,breakMinutes:0});
 await template.locator('[data-field="allMembers"]').uncheck();
 await expect(template.locator('[data-field="coverage"]')).toBeEnabled();
 await page.locator('#rosterSave').click();
 await expect.poll(()=>page.evaluate(()=>window.calls.filter(c=>c.options.method==='PUT').length)).toBe(2);
 saved=await page.evaluate(()=>JSON.parse(window.calls.filter(c=>c.options.method==='PUT').at(-1).options.body));
 expect(saved.templates[0].allMembers).toBe(false);
});
test('constraint controls and manual calendar save exact versions and assignments',async({page})=>{
 await setup(page);await page.locator('[data-view="rostering"]').click();
 await expect(page.locator('#rosterRules [data-field="minRestMinutes"]')).toHaveValue('660');
 await page.locator('#rosterMembers summary').click();
 await page.locator('#rosterMembers [data-field="unavailableDates"]').fill('2026-10-15, 2026-10-16');
 await page.locator('#rosterMembers [data-list="allowedTemplates"] input').first().check();
 await page.locator('#rosterCalendar select').first().selectOption('DAY');
 await page.locator('#rosterCalendar input').first().check();await page.locator('#rosterSave').click();
 await expect.poll(()=>page.evaluate(()=>window.calls.filter(r=>r.options.method==='PUT').length)).toBe(1);
 const body=await page.evaluate(()=>JSON.parse(window.calls.find(r=>r.options.method==='PUT').options.body));
 expect(body.members[0].allowedTemplates).toEqual(['DAY']);expect(body.expectedVersion).toBe(1);expect(body.members[0].unavailableDates).toEqual(['2026-10-15','2026-10-16']);expect(body.assignments[0].locked).toBe(true);
});
test('infeasible generator exposes diagnostics and never publishes',async({page})=>{
 await setup(page);await page.locator('[data-view="rostering"]').click();await expect(page.locator('#rosterSave')).toBeEnabled();await page.locator('#rosterGenerate').click();
 await expect(page.locator('#rosterNotice')).toHaveText('Copertura insufficiente.');
 expect(await page.evaluate(()=>window.calls.some(r=>r.url.endsWith('/publish')))).toBe(false);
});
test('attendance filters survive refresh and clear on identity changes',async({page})=>{
 await setup(page);await page.locator('[data-view="attendance"]').click();await expect(page.locator('#attendanceRows')).toContainText('Mario Rossi');
 await page.locator('#attendanceOperator').selectOption('uid-a');await page.locator('#attendanceFilters button').first().click();
 await expect.poll(()=>page.evaluate(()=>window.calls.filter(r=>r.url.includes('uid=uid-a')).length)).toBe(1);
 await page.locator('#attendanceSearch').fill('inesistente');await expect(page.locator('#attendanceRows')).toContainText('Nessuna sessione');
 await page.evaluate(()=>window.dispatchEvent(new Event('radiotech:identity-changed')));await expect(page.locator('#attendanceRows')).toBeEmpty();
});
test('responsive roster confines horizontal scroll to calendar',async({page})=>{
 await page.setViewportSize({width:390,height:844});await setup(page);await page.locator('[data-view="rostering"]').click();
 await page.evaluate(()=>document.getElementById('view-rostering').classList.add('active'));
 await expect(page.locator('#rosterCalendar select').first()).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
});
