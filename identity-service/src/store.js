export class MemoryStore {
  data = new Map();
  queue = Promise.resolve();
  async get(key) { return structuredClone(this.data.get(key)); }
  async list(collection) {
    return [...this.data].filter(([key]) => key.startsWith(`${collection}/`)).map(([key, value]) => ({ ...structuredClone(value), id: key.split('/')[1] }));
  }
  async transaction(fn) {
    const previous = this.queue;
    let release;
    this.queue = new Promise(resolve => { release = resolve; });
    await previous;
    const changes = new Map();
    try {
      const result = await fn({ get: async key => structuredClone(changes.has(key) ? changes.get(key) : this.data.get(key)),
        set: (key, value) => changes.set(key, structuredClone(value)) });
      for (const [key, value] of changes) this.data.set(key, value);
      return result;
    } finally { release(); }
  }
}

export class FirestoreStore {
  constructor(db) { this.db = db; }
  async get(key) { return (await this.db.doc(key).get()).data(); }
  async list(collection) {
    return (await this.db.collection(collection).get()).docs.map(doc => ({ ...doc.data(), id: doc.id }));
  }
  async transaction(fn) {
    return this.db.runTransaction(async transaction => {
      // Delay writes until all reads have completed, as required by Firestore.
      const writes = new Map();
      const result = await fn({ get: async key => writes.has(key) ? structuredClone(writes.get(key)) : (await transaction.get(this.db.doc(key))).data(),
        set: (key, value) => writes.set(key, value) });
      for (const [key, value] of writes) transaction.set(this.db.doc(key), value);
      return result;
    });
  }
}
