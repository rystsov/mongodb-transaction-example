## ACID transactions in MongoDB

It seems that MongoDB doesn't support transaction. There are a lot of similar answers to questions
related to transactions in MongoDB:

> MongoDB doesn't support complex multi-document transactions. If that is something you absolutely 
need it probably isn't a great fit for you.

> If transactions are required, perhaps NoSQL is not for you. Time to go back to ACID relational databases.

> MongoDB does a lot of things well, but transactions is not one of those things.

It would be really sad if that were true. But actually MongoDB provides all abilities to implement ACID and 
lock-free transactions on the client side. 

This repository contains a program that uses those abilities and this document describes the algorithm if you
want to implement it on your own. 

*My language of choise is Java, so sory if you were expecting something else.*

### Data model

One of the features that differs MongoDB from the other NoSQL solutions is compare-and-set.
This is execty what we need to add ACID transactions to MongoDB. If you are using another NoSQL solution
that supports CAS (like HBase, Project Voldemort or ZooKeeper) you can use this approach too.

> **How can I use CAS**

> This is a mechanism that prevents updating of an object if the object has been changed by another client after
you read object but before you are tring to update it. That must be familiar to you if you have ever used a 
version control system and your colleague succeeded to commit before you.

Suppose we want to design a model for bank account, there are a one of possible data stractures and a operation to 
change it.

```javascript
// bank account
var gov = {
  _id : ObjectId(".."),
  name : "gov",
  balance : 600
}
// a changing of an account
db.accounts.update( 
  { _id:ObjectId("...") }, 
  { name:"gov", balance:550 }
);
```

Obviously this model ignores the problem of concurrent modification described above, so there is a possibility that
a client overwrites a version of object that he hasn't seen. Let's fix it with a CAS.

```javascript
// CAS guarded bank account
var gov = {
  _id : ObjectId(".."),
  version : 0,
  value : {
    name : "gov",
    balance : 600
  }
}
// a changing of an account with respect to version
db.accounts.update({ 
    _id: ObjectId("..."), version: 0
  },{ 
    version : 1, 
    value : { name:"gov", balance:550 } 
});
```

The new field "version" was added, also fields "name" and "balance" were extracted to subobject in order to separate
business and utility data.

Since our model is CAS guarded we should check that any our change to any object will be accepted, hopefully
MongoDB returns the number of record affected by the change, so we can check whether our object was updated or 
update was refused due to a concurrent modification.

**Let's assume that any of our object has version and any modification to an object is procceeded 
with respect to that version. Hereinafter I omit that but you should remember that any modification
to any object can be refused.**

Actually adding the "version" field to the model is not enough to introduce transaction. We must alter our model
one more time:

```javascript
var gov = {
  _id : ObjectId(".."),
  version : 0,
  value : {
    name : "gov",
    balance : 600
  },
  updated : null,
  tx : null
}
```

Fields "updated" and "tx" were added. Just like the "version" field those fields are utility too which are 
being used during transaction. The structure of "updated" is equel to "value" or null. It is representing an 
altered version of object during transaction. "tx" is an ObjectId-typed object, it is a foreign key for "_id" 
field of an object representing transaction. An object representing transaction is also CAS guadred.

### The algorithm

It is easy to describe an algorithm, but it is harder to describe an algorithm in a way that its correctness 
is obvious. So at first I'll make some statements, defs and properties about the algorithm. I expect you to
return to them after I introduce the algorithm and expect you to say something like "Oh, now I see that is true".

- "value" always contains a state that was actual sometime in the past
- a read operation can change data in the base
- the read operation is idempotent
- a object can be in one of three states: clean — c, dirty uncommitted — d, dirty committed — dc
- when object start participating in transaction it must be in the "c" state
- possible transitions between states: c→d, d→c, d→dc, dc→c
- possible transitions during transaction: c→d, d→dc, dc→c
- a possible transition during reads: d→c
- if there was an d→c transition, the transaction initiated c→d transition must fall on commit
- after each operation the database has consistent state
- when read falls we must reread
- if a transaction falls before commit we should start new transaction
- if a transaction falls during commit we should check if it has passed 
  and it if hasn't we should repeat the whole thansacion
- if a transaction has passed then the tx object is removed

#### States

An object has a **clean state** when a transaction has successfully passed: its "value" contains new data 
and "updated" and "tx" are null.

An object has a **dirty uncommitted state** during transaction: "updated" contains new version, "tx" reffers to
object representing transaction and that object exists.

The third state is a **dirty committed state** it describe case when transaction is committed but hasn't yet clean
its utility data yet: "value" and "updated" contains new version of an object, "tx" reffers to
object representing transaction, but that object is deleted.

#### Transaction

1. Читаем объекты, которые участвуют в транзакции
2. Создаем объект представляющий транзакцию (tx)
3. Пишем в updated каждого объекта новую значение, а в tx — tx._id
4. Удаляем объект tx
5. Пишем в value каждого объекта значение из updated, а tx и updated обнуляем

#### Read

1. Читаем объект
2. Если он чистый — возвращаем его
3. Если грязный закомиченный — пишем в value значение из updated, а tx и updated обнуляем
4. Если грязное незакомиченный — изменяем версию tx, обнуляем updated и tx
5. Переходим на шаг №1

Для тех кому теперь не очивидна корректность, домашнее задание — проверить, что выполняются все 
свойства и утверждения, а затем используя их доказать ACID ☺

### Заключение

Мы добавили в MongoDB транзакции, а ребята со stackoverflow оказались не компетентны. На самом деле 
у наших транзакций есть несколько свойств, о которых нужно помнить:
- при записи должен достигаться кворум (см. w) чтобы переживать падение машин
- транзакции оптимистические, проэтому при изменении объекта с высокой частотой из 
  разных потоков ихлучше не использовать
- для изменения n объектов в одной транзакции используется 2n+2 запросов 
