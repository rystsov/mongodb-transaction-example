package com.twitter.rystsov.engine;

import com.mongodb.DBCollection;

/**
 * User: Denis Rystsov
 */
public class Db extends Kv {
    public KvEntity repairingGet(DBCollection storage, Object id) {
        KvEntity entity = get(storage, id);
        if (entity==null) return entity;
        if (entity.tx==null) return entity;

        Kv.KvEntity tx = get(storage, entity.tx);
        if (tx==null) {
            entity.value = entity.updated;
            entity.tx = null;
        } else {
            // force tx to fall on commit
            // may fall if tx have been committed
            update(storage, tx);

            entity.updated = null;
            entity.tx = null;
        }
        update(storage, entity);
        return repairingGet(storage, id);
    }
}
