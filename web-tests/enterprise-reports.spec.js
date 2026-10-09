import { test, expect } from '@playwright/test';
import { session } from './fixtures/control-room-session.js';

const report = (id, status) => ({id, status, taskTitle:'Verifica collegamento radio',operatorName:'Tecnico Bari',submittedAt:'2026-10-09T10:00:00Z',attachments:[],operatorNotes:'Controllo operativo'});
async function openReports(page) {
  await page.locator('.nav [data-view="reports"]').click();
  await page.locator('#reportCollapseAll').click();
}

test('collapsed reports keep authorized approval and removal visible across lifecycle states', async ({page}) => {
  const reports = ['SUBMITTED','APPROVAL_PENDING','APPROVED','REJECTED','DRAFT'].map(status=>report(status,status));
  const approvals=[];
  await session(page,'ADMIN',{'/reports':reports,respond(path,method){
    if(path==='/reports/SUBMITTED/approve'&&method==='POST'){approvals.push(path);reports[0].status='APPROVED';return reports[0];}
  }});
  await openReports(page);
  for(const item of reports) await expect(page.locator(`[data-report-delete="${item.id}"]`)).toBeVisible();
  await expect(page.locator('[data-report-approve="SUBMITTED"]')).toBeVisible();
  await expect(page.locator('[data-report-approve="APPROVAL_PENDING"]')).toBeVisible();
  await expect(page.locator('[data-report-approve="APPROVED"]')).toHaveCount(0);
  const row=page.locator('[data-report-id="SUBMITTED"]');
  await expect(row).toHaveClass(/report-collapsed/);
  await page.locator('[data-report-delete="SUBMITTED"]').click();
  await expect(row).toHaveClass(/report-collapsed/);
  await page.getByRole('button',{name:'Annulla',exact:true}).click();
  await expect(page.locator('[data-report-delete="SUBMITTED"]')).toBeFocused();
  await page.locator('[data-report-approve="SUBMITTED"]').click();
  await expect.poll(()=>approvals.length).toBe(1);
  await expect(page.locator('[data-report-approve="SUBMITTED"]')).toHaveCount(0);
  expect(await page.locator('#reportsList button button').count()).toBe(0);
});

for(const status of ['SUBMITTED','APPROVAL_PENDING','APPROVED','REJECTED','DRAFT']) {
  test(`removal preserves confirmation and updates list for ${status}`,async({page})=>{
    const reports=[report('remove-me',status)]; let deletes=0,release;
    const pending=new Promise(resolve=>{release=resolve;});
    await session(page,'ADMIN',{'/reports':reports,respond:async(path,method,request)=>{
      if(path==='/reports/remove-me'&&method==='DELETE'){
        deletes++;expect(request.headers()['authorization']).toBe('Bearer test-token');
        await pending;reports.splice(0);return {success:true,id:'remove-me',removed:true};
      }
    }});
    await openReports(page);await page.locator('[data-report-delete]').click();
    const dialog=page.getByRole('dialog',{name:'Elimina report'});
    await expect(dialog).toContainText('remove-me');
    await expect(dialog).toContainText('firme e storico');
    await expect(dialog.getByRole('button',{name:'Annulla'})).toBeFocused();
    await page.keyboard.press('Escape');await expect(dialog).toHaveCount(0);expect(deletes).toBe(0);
    await page.locator('[data-report-delete]').click();
    const confirm=dialog.getByRole('button',{name:'Conferma eliminazione'});
    await confirm.click();await expect(confirm).toBeDisabled();
    await expect.poll(()=>deletes).toBe(1);
    await page.keyboard.press('Escape');await expect(dialog).toBeVisible();
    release();await expect(dialog).toHaveCount(0);
    await expect(page.locator('#reportsList')).toContainText('Nessun report ricevuto');
    await expect(page.locator('#toastStack')).toContainText('Report eliminato');
    expect(deletes).toBe(1);
  });
}

test('server refusal leaves report in place and confirmation can be retried or cancelled',async({page})=>{
  await session(page,'ADMIN',{'/reports':[report('denied','APPROVED')]});
  await page.route('**/api/v1/reports/denied',route=>route.fulfill({status:403,json:{message:'Operazione non autorizzata'}}));
  await openReports(page);await page.locator('[data-report-delete]').click();
  const dialog=page.getByRole('dialog',{name:'Elimina report'});
  await dialog.getByRole('button',{name:'Conferma eliminazione'}).click();
  await expect(dialog.getByRole('alert')).toContainText('non autorizzata');
  await expect(dialog.getByRole('button',{name:'Conferma eliminazione'})).toBeEnabled();
  await expect(page.locator('[data-report-id="denied"]')).toHaveCount(1);
  await dialog.getByRole('button',{name:'Annulla'}).click();
  await expect(page.locator('[data-report-delete]')).toBeFocused();
});

for(const role of ['VIEWER','OPERATOR']) {
  test(`${role} cannot approve or remove reports`,async({page})=>{
    await session(page,role,{'/reports':[report('readonly','SUBMITTED')]});
    await openReports(page);
    await expect(page.locator('[data-report-approve]')).toHaveCount(0);
    await expect(page.locator('[data-report-delete]')).toHaveCount(0);
  });
}

test('collapsed report actions and state remain available without overflow on mobile',async({page})=>{
  await session(page,'ADMIN',{'/reports':[report('mobile','APPROVAL_PENDING')]});
  await openReports(page);
  for(const width of [1920,1440,1280,1024,768,430,390,375,320]){
    await page.setViewportSize({width,height:900});
    await expect(page.locator('[data-report-approve]')).toBeVisible();
    await expect(page.locator('[data-report-delete]')).toBeVisible();
    await expect(page.locator('#reportsList .badge')).toBeVisible();
    if(width<=700){
      await expect.poll(async()=>{
        const status=await page.locator('#reportStatusFilter').boundingBox();
        const operator=await page.locator('#reportOperatorFilter').boundingBox();
        return Math.abs(status.y-operator.y)<1&&operator.x>status.x;
      }).toBe(true);
    }
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  }
});
