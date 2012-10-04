package com.twitter.rystsov;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;

/**
 * User: Denis Rystsov
 */
public class Kv {
    public static class KvEntity {
        public Object id;
        public Object version;
        public DBObject value;
        public DBObject updated;
        public Object tx;
    }

    private final DBCollection storage;

    public Kv(DBCollection storage) {
        this.storage = storage;
    }

    public KvEntity create(DBObject value) {
        KvEntity entity = new KvEntity();
        entity.version = 0;
        entity.value = value;
        entity.updated = null;
        entity.tx = null;

        DBObject record = new BasicDBObject();
        record.put("version", entity.version);
        record.put("value", entity.value);
        record.put("updated", entity.updated);
        record.put("tx", entity.tx);

        storage.insert(record).getLastError().throwOnError();

        entity.id = record.get("_id");

        return entity;
    }

    public KvEntity update(KvEntity entity) {
        int version = (Integer)entity.version;

        DBObject query = new BasicDBObject();
        query.put("_id", entity.id);
        query.put("version", version);

        DBObject record = new BasicDBObject();
        record.put("version", version+1);
        record.put("value", entity.value);
        record.put("updated", entity.updated);
        record.put("tx", entity.tx);

        WriteResult result = storage.update(query, record);

        result.getLastError().throwOnError();
        if (result.getN()==0) throw new RuntimeException();

        entity.version = version+1;
        return entity;
    }

    public KvEntity get(Object id) {
        KvEntity entity = new KvEntity();
        entity.id = id;

        DBObject record = storage.findOne(new BasicDBObject("_id", id));
        if (record==null) return null;

        entity.version = record.get("version");
        entity.value = (DBObject)record.get("value");
        entity.updated = (DBObject)record.get("updated");
        entity.tx = record.get("tx");

        return entity;
    }

    public void delete(KvEntity entity) {
        DBObject query = new BasicDBObject();
        query.put("_id", entity.id);
        query.put("version", entity.version);

        WriteResult result = storage.remove(query);
        result.getLastError().throwOnError();
        if (result.getN()==0) throw new RuntimeException();
    }
}
