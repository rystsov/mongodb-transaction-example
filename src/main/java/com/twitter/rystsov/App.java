package com.twitter.rystsov;

import com.mongodb.*;
import com.mongodb.util.JSON;
import com.twitter.rystsov.engine.Db;
import com.twitter.rystsov.engine.Kv;
import com.twitter.rystsov.engine.Transaction;

import java.net.UnknownHostException;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) throws UnknownHostException {
        Mongo mongo = new Mongo("localhost", 27017);
        mongo.setWriteConcern(WriteConcern.SAFE);
        DB bank = mongo.getDB("bank");
        DBCollection accounts = bank.getCollection("accounts");
        DBCollection txs = bank.getCollection("txs");

        Db db = new Db();

        // init
        Kv.KvEntity roc = db.create(accounts, (DBObject) JSON.parse("{ name : 'roc', balance : 100 }"));
        Kv.KvEntity gov = db.create(accounts, (DBObject) JSON.parse("{ name : 'gov', balance : 700 }"));

        Transaction transaction = new Transaction(db, db.create(txs, null));
        transaction.change(accounts, roc, (DBObject) JSON.parse("{ name : 'roc', balance : 50 }"));
        transaction.change(accounts, gov, (DBObject) JSON.parse("{ name : 'gov', balance : 750 }"));
        transaction.commit(txs);
    }
}
