package com.twitter.rystsov.engine;

import com.mongodb.DBObject;
import com.twitter.rystsov.engine.Db;
import com.twitter.rystsov.engine.Kv;

import java.util.HashSet;
import java.util.Set;

/**
 * User: Denis Rystsov
 */
public class Transaction {
    private final Kv.KvEntity tx;
    private final Db db;
    private Set<Kv.KvEntity> log = new HashSet<Kv.KvEntity>();

    public Transaction(Db db, Kv.KvEntity tx) {
        this.tx = tx;
        this.db = db;
    }

    public void change(Kv.KvEntity entity, DBObject value) {
        entity.tx = tx.id;
        entity.updated = value;

        log.add(db.update(entity));
    }

    public void commit() {
        // if this operation pass, tx will be committed
        db.delete(tx);
        // tx is committed, this is just a clean up
        try {
            for (Kv.KvEntity entity : log) {
                entity.value = entity.updated;
                entity.updated = null;
                entity.tx = null;
                db.update(entity);
            }
        } catch (Exception e) { }
    }
}
