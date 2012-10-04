package com.twitter.rystsov.engine;

import com.mongodb.DBCollection;

/**
 * User: Denis Rystsov
 */
public class Db extends Kv {
    public Db(DBCollection storage) {
        super(storage);
    }

    public KvEntity repairingGet(Object id) {
        KvEntity entity = get(id);
        if (entity==null) return entity;
        if (entity.tx==null) return entity;

        Kv.KvEntity tx = get(entity.tx);
        if (tx==null) {
            entity.value = entity.updated;
            entity.tx = null;
        } else {
            // force tx to fall on commit
            // may fall if tx have been committed
            update(tx);

            entity.updated = null;
            entity.tx = null;
        }
        update(entity);
        return repairingGet(id);
    }
}
