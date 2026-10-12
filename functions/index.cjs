'use strict';
const {onDocumentWritten}=require('firebase-functions/v2/firestore');
const {initializeApp}=require('firebase-admin/app');
const {getFirestore}=require('firebase-admin/firestore');
const {collections,reconcile}=require('./projection.cjs');
initializeApp();
for(const collection of collections) exports[`project_${collection}`]=onDocumentWritten({document:`${collection}/{id}`,region:'europe-west1',retry:true,maxInstances:3},event=>reconcile(getFirestore(),collection,event.params.id));
