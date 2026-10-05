import {test} from 'node:test';
import assert from 'node:assert/strict';
import {MediaCache} from '../server/media-cache.mjs';

test('bounded image cache evicts oldest record and reports missing references explicitly',()=>{
 const cache=new MediaCache(6),bytes=n=>Buffer.alloc(n,1);
 cache.put('session-a/hash1',bytes(2),'image/png');
 cache.put('session-b/hash2',bytes(3),'image/jpeg');
 assert.equal(cache.put('session-a/hash1',bytes(2),'image/png'),false);
 cache.put('session-a/hash3',bytes(3),'image/webp');
 assert.equal(cache.get('session-a/hash1'),undefined);
 assert.equal(cache.get('session-b/hash2').mimeType,'image/jpeg');
 assert.equal(cache.get('session-a/hash3').data.length,3);
 assert.equal(cache.bytes,6);
});

test('cache rejects invalid records without mutating capacity or entries',()=>{
 const cache=new MediaCache(4);
 assert.throws(()=>cache.put('x',Buffer.alloc(5),'image/png'),/Invalid cached media/);
 assert.throws(()=>cache.put('x','base64','image/png'),/Invalid cached media/);
 assert.throws(()=>new MediaCache(0),/Invalid media cache capacity/);
 assert.equal(cache.items.size,0);assert.equal(cache.bytes,0);
});
