package com.twitter.rystsov;

import com.mongodb.*;
import com.mongodb.util.JSON;

import java.net.UnknownHostException;

/**
 * Hello world!
 *
 */
public class App 
{


    static void init(DBCollection accounts) {
        BasicDBObject gov = new BasicDBObject();
	    gov.put("name", "gov");
        gov.put("version", 0);
		gov.put("value", 600);
        gov.put("updated", null);
        accounts.insert(gov);

        BasicDBObject roc = new BasicDBObject();
	    roc.put("name", "roc");
        roc.put("version", 0);
		roc.put("value", 100);
        roc.put("updated", null);
        accounts.insert(roc);
    }

    static void change(DBCollection accounts) {
        BasicDBObject query;

        query = new BasicDBObject();
	    query.put("name", "gov");
        DBObject gov = accounts.findOne(query);

        query = new BasicDBObject();
	    query.put("_id", gov.get("_id"));
        query.put("version", gov.get("version"));

        gov.put("version", ((Integer)gov.get("version")) + 1);
        gov.put("value", 700);

        accounts.update(query, gov);
    }

    public static void main( String[] args ) throws UnknownHostException {
        Mongo mongo = new Mongo("localhost", 27017);
        mongo.setWriteConcern(WriteConcern.SAFE);
        DB bank = mongo.getDB("bank");
        DBCollection accounts = bank.getCollection("accounts");

        Db db = new Db(accounts);

        Kv.KvEntity roc = db.create((DBObject) JSON.parse("{ name : 'roc', balance : 100 }"));
        Kv.KvEntity gov = db.create((DBObject) JSON.parse("{ name : 'gov', balance : 700 }"));

        Transaction transaction = new Transaction(db, db.create(null));
        transaction.change(roc, (DBObject) JSON.parse("{ name : 'roc', balance : 50 }"));
        transaction.change(gov, (DBObject) JSON.parse("{ name : 'gov', balance : 750 }"));
        transaction.commit();
    }
}
