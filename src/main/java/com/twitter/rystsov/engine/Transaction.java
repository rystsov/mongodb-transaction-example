package com.twitter.rystsov.engine;

import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.twitter.rystsov.engine.Db;
import com.twitter.rystsov.engine.Kv;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: Denis Rystsov
 */
public class Transaction {
    private final Kv.KvEntity tx;
    private final Db db;

    private Map<Kv.KvEntity, DBCollection> log = new HashMap<Kv.KvEntity, DBCollection>();

    public Transaction(Db db, Kv.KvEntity tx) {
        this.tx = tx;
        this.db = db;
    }

    public void change(DBCollection storage, Kv.KvEntity entity, DBObject value) {
        entity.tx = tx.id;
        entity.updated = value;

        log.put(db.update(storage, entity), storage);
    }

    public void commit(DBCollection txStorage) {
        // if this operation pass, tx will be committed
        db.delete(txStorage, tx);
        // tx is committed, this is just a clean up
        try {
            for (Kv.KvEntity entity : log.keySet()) {
                entity.value = entity.updated;
                entity.updated = null;
                entity.tx = null;
                db.update(log.get(entity), entity);
            }
        } catch (Exception e) { }
    }
}
