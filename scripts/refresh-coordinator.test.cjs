const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const context = { window: {} };
vm.runInNewContext(fs.readFileSync('src/main/resources/static/assets/refresh-coordinator.js', 'utf8'), context);
const create = context.window.RadioTechRefreshCoordinator;

test('background refresh is suppressed during hidden or editing state; manual refresh still works', async () => {
    let allowed = false, calls = 0;
    const coordinator = create({ run: async () => { calls++; }, canRefresh: () => allowed });
    await coordinator.refresh(true);
    assert.equal(calls, 0);
    await coordinator.refresh(false);
    assert.equal(calls, 1);
    allowed = true;
    await coordinator.refresh(true);
    assert.equal(calls, 2);
});
test('overlapping manual and automatic refreshes share one request and can recover after rejection', async () => {
    let release, calls = 0;
    const coordinator = create({
        run: () => { calls++; return new Promise((resolve, reject) => { release = reject; }); },
        canRefresh: () => true,
    });
    const first = coordinator.refresh(true);
    const second = coordinator.refresh(false);
    assert.equal(first, second);
    await Promise.resolve();
    assert.equal(calls, 1);
    release(new Error('network failure'));
    await assert.rejects(first);
    const third = coordinator.refresh(false);
    await Promise.resolve();
    assert.equal(calls, 2);
    release(new Error('second failure'));
    await assert.rejects(third);
});
