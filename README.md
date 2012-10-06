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

На самом деле добавление версии — это не все изменения, которые нужно провести над моделью, чтобы 
она поддерживала транзакции, полностью измененная модель выглядит так:

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

Добавились поля — updated и tx. Это служебные данные, которые используются в процессе транзакции. 
По структуре updated совпадает с value, по смыслу — это изменная версия объекта, которая превратиться 
в value, если транзакция пройдет; tx — это объект класса ObjectId — foreign key для _id объекта, 
представляющий транзакцию. Объект представляющий транзакцию так же находиться под защитой CAS.

### Алгоритм

Объяснить алгоритм просто, объяснить его так, что его корректность была очевидна, сложнее; 
поэтому придеться то, что некоторыми сущностями я буду оперировать до того, как их определю.

Ниже идут верные утверждения, определения и свойства из которых позже будет составлен алгоритм.

- value всегда содержит состояние, которое было верным на какой-то момент в прошлом
- операция чтения может изменять данные в базе
- операция чтения идемпотентна
- объект может быть в трех состояниях: чистое — c, грязное незакомиченное — d, грязное закомиченное — dc
- в транзакции изменяются только объекты в состоянии: c
- возможные переходы между состояниями: c →d, d→c, d→dc, dc→c
- переходы инициированные транзакцей: c →d, d→dc, dc→c
- возможный переход при чтении: d→c
- если произошел переход d→c, то транзакция, внутри которой был переход c →d, упадет при коммите
- любая операция при работе с базой может упасть
- упавшию операцию чтения нужно повторить
- при упавшей записи нужно начать новую транзакцию
- при упавшем коммите нужно проверить прошел ли он, если нет — повторить транзакцию заново
- транзакция прошла, если объект представляющий транзакцию (_id = tx) удален

#### Состояния

**Чистое состояние** описывает объект после успешной транзакции: value содержит данные, а upated и tx — null.

**Грязное незакомиченное состояние** описывает объект в момент транзакции, updated содержит новую 
версию, а tx — _id объекта представляющего транзакцию, этот объект существует.

**Грязное закомиченное состояние** описывает объект после успешной транзакции, но которя упала до того, 
как успела подчистить за собой, updated содержит новую версию, tx — _id объекта представляющего транзакцию, 
но сам объект уже удален.

#### Транзакция

1. Читаем объекты, которые участвуют в транзакции
2. Создаем объект представляющий транзакцию (tx)
3. Пишем в updated каждого объекта новую значение, а в tx — tx._id
4. Удаляем объект tx
5. Пишем в value каждого объекта значение из updated, а tx и updated обнуляем

#### Чтение

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
