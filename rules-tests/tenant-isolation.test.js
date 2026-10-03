import { readFile } from 'node:fs/promises';
import { after, before, beforeEach, test } from 'node:test';
import {
    assertFails,
    assertSucceeds,
    initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
    collection,
    deleteDoc,
    doc,
    getDoc,
    getDocs,
    setDoc,
    updateDoc,
    where,
    query,
} from 'firebase/firestore';
import { getBytes, ref, uploadBytes } from 'firebase/storage';

const projectId = 'demo-radiotech-tenant-isolation';
let environment;

before(async () => {
    const [firestoreRules, storageRules] = await Promise.all([
        readFile(new URL('../firestore.rules', import.meta.url), 'utf8'),
        readFile(new URL('../storage.rules', import.meta.url), 'utf8'),
    ]);
    environment = await initializeTestEnvironment({
        projectId,
        firestore: { rules: firestoreRules },
        storage: { rules: storageRules },
    });
});

beforeEach(async () => {
    await environment.clearFirestore();
    await environment.withSecurityRulesDisabled(async (context) => {
        const database = context.firestore();
        await setDoc(doc(database, 'tasks/task-a'), {
            tenantId: 'tenant-a',
            title: 'Tenant A task',
            operatorFirebaseUid: 'user-a',
        });
        await setDoc(doc(database, 'tasks/task-b'), {
            tenantId: 'tenant-b',
            title: 'Tenant B task',
            operatorFirebaseUid: 'user-b',
        });
        const tenantCollections = [
            'operators',
            'antennas',
            'maintenanceReports',
            'inventory',
            'alerts',
            'notificationHistory',
            'auditLogs',
            'users',
        ];
        for (const collectionName of tenantCollections) {
            const tenantARecord = {
                tenantId: 'tenant-a',
                title: `Tenant A ${collectionName}`,
            };
            const tenantBRecord = {
                tenantId: 'tenant-b',
                title: `Tenant B ${collectionName}`,
            };
            if (collectionName === 'notificationHistory') {
                tenantARecord.target = 'operator-a';
                tenantBRecord.target = 'operator-b';
            }
            await setDoc(doc(database, `${collectionName}/${collectionName}-a`), {
                ...tenantARecord,
            });
            await setDoc(doc(database, `${collectionName}/${collectionName}-b`), {
                ...tenantBRecord,
            });
        }
        await setDoc(doc(database, 'notificationHistory/broadcast'), {
            tenantId: 'tenant-a',
            target: 'BROADCAST',
            title: 'Tenant A broadcast',
        });
    });
});

after(async () => {
    await environment?.cleanup();
});

test('Tenant A can read its task but cannot read or query Tenant B', async () => {
    const database = environment.authenticatedContext('user-a', {
        role: 'ADMIN',
        tenantId: 'tenant-a',
    }).firestore();

    await assertSucceeds(getDoc(doc(database, 'tasks/task-a')));
    await assertFails(getDoc(doc(database, 'tasks/task-b')));
    await assertSucceeds(getDocs(query(
        collection(database, 'tasks'),
        where('tenantId', '==', 'tenant-a'),
    )));
    await assertFails(getDocs(query(
        collection(database, 'tasks'),
        where('tenantId', '==', 'tenant-b'),
    )));
});

test('Tenant A cannot modify Tenant B or create a document claiming Tenant B', async () => {
    const database = environment.authenticatedContext('user-a', {
        role: 'ADMIN',
        tenantId: 'tenant-a',
    }).firestore();

    await assertFails(updateDoc(doc(database, 'tasks/task-b'), { title: 'stolen' }));
    await assertFails(deleteDoc(doc(database, 'tasks/task-b')));
    await assertFails(setDoc(doc(database, 'tasks/spoofed'), {
        tenantId: 'tenant-b',
        title: 'spoofed tenant',
    }));
    await assertFails(setDoc(doc(database, 'tasks/own'), {
        tenantId: 'tenant-a',
        title: 'business writes must use the validated API',
    }));
});

test('Storage report paths are bound to the signed tenant claim', async () => {
    const storage = environment.authenticatedContext('user-a', {
        role: 'OPERATOR',
        tenantId: 'tenant-a',
    }).storage();
    const ownFile = ref(storage, 'tenants/tenant-a/maintenance-reports/user-a/photo.png');
    const foreignFile = ref(storage, 'tenants/tenant-b/maintenance-reports/user-a/photo.png');
    const file = new Uint8Array([1, 2, 3]);

    await assertSucceeds(uploadBytes(ownFile, file, { contentType: 'image/png' }));
    await assertSucceeds(getBytes(ownFile));
    await assertFails(uploadBytes(foreignFile, file, { contentType: 'image/png' }));
});

test('Tenant A cannot read another tenant across protected collections', async () => {
    const database = environment.authenticatedContext('user-a', {
        role: 'ADMIN',
        tenantId: 'tenant-a',
    }).firestore();
    const tenantCollections = [
        'operators',
        'antennas',
        'maintenanceReports',
        'inventory',
        'alerts',
        'notificationHistory',
        'auditLogs',
        'users',
    ];

    for (const collectionName of tenantCollections) {
        await assertSucceeds(getDoc(doc(database, `${collectionName}/${collectionName}-a`)));
        await assertFails(getDoc(doc(database, `${collectionName}/${collectionName}-b`)));
    }
});

test('Authenticated requests without a tenant claim fail closed', async () => {
    const database = environment.authenticatedContext('user-without-tenant', {
        role: 'ADMIN',
    }).firestore();

    await assertFails(getDoc(doc(database, 'tasks/task-a')));
    await assertFails(setDoc(doc(database, 'tasks/no-tenant'), {
        tenantId: 'tenant-a',
        title: 'must be denied',
    }));
});

test('VIEWER can read within its tenant but cannot write', async () => {
    const database = environment.authenticatedContext('viewer-a', {
        role: 'VIEWER',
        tenantId: 'tenant-a',
    }).firestore();

    await assertSucceeds(getDoc(doc(database, 'tasks/task-a')));
    await assertFails(getDoc(doc(database, 'tasks/task-b')));
    await assertSucceeds(getDoc(doc(database, 'auditLogs/auditLogs-a')));
    await assertFails(updateDoc(doc(database, 'tasks/task-a'), { status: 'COMPLETED' }));
    await assertFails(setDoc(doc(database, 'maintenanceReports/operator-report'), {
        tenantId: 'tenant-a',
        operatorId: 'viewer-a',
        status: 'SUBMITTED',
    }));
    await assertFails(setDoc(doc(database, 'tasks/viewer-create'), {
        tenantId: 'tenant-a',
        title: 'viewer cannot create',
    }));
});

test('OPERATOR can read only tasks assigned to its Firebase UID', async () => {
    const database = environment.authenticatedContext('user-a', {
        role: 'OPERATOR',
        tenantId: 'tenant-a',
        operatorId: 'operator-a',
    }).firestore();

    await assertSucceeds(getDoc(doc(database, 'tasks/task-a')));
    await assertFails(getDoc(doc(database, 'tasks/task-b')));
    await assertSucceeds(getDoc(doc(database, 'antennas/antennas-a')));
    await assertFails(getDoc(doc(database, 'auditLogs/auditLogs-a')));
    await assertSucceeds(getDoc(doc(database, 'notificationHistory/broadcast')));
    await assertSucceeds(getDoc(doc(database, 'notificationHistory/notificationHistory-a')));
    await assertFails(getDoc(doc(database, 'notificationHistory/notificationHistory-b')));
});

test('Managed business documents are writable only through the backend API', async () => {
    const database = environment.authenticatedContext('manager-a', {
        role: 'ADMIN',
        tenantId: 'tenant-a',
    }).firestore();
    const managedCollections = [
        'operators',
        'antennas',
        'maintenanceReports',
        'inventory',
        'alerts',
        'users',
    ];

    for (const collectionName of managedCollections) {
        await assertFails(updateDoc(doc(database, `${collectionName}/${collectionName}-a`), {
            title: 'direct client write must be denied',
        }));
    }
});