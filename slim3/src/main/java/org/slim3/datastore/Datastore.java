/*
 * Copyright 2004-2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.slim3.datastore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.slim3.util.ClassUtil;
import org.slim3.util.Cleanable;
import org.slim3.util.Cleaner;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.DatastoreTimeoutException;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.Transaction;

/**
 * A class to access datastore.
 * 
 * @author higa
 * @since 3.0
 * 
 */
public final class Datastore {

    static final int MAX_RETRY = 10;

    private static Logger logger = Logger.getLogger(Datastore.class.getName());

    private static ConcurrentHashMap<String, ModelMeta<?>> modelMetaCache =
        new ConcurrentHashMap<String, ModelMeta<?>>(87);

    private static volatile boolean initialized = false;

    static {
        initialize();
    }

    private static void initialize() {
        Cleaner.add(new Cleanable() {
            public void clean() {
                modelMetaCache.clear();
                initialized = false;
            }
        });
        initialized = true;
    }

    /**
     * Begins a transaction.
     * 
     * @return a begun transaction
     */
    public static Transaction beginTransaction() {
        DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        return ds.beginTransaction();
    }

    /**
     * Commits the transaction.
     * 
     * @param tx
     *            the transaction
     * @throws NullPointerException
     *             if the tx parameter is null
     * @throws IllegalArgumentException
     *             if the transaction is not active
     */
    public static void commit(Transaction tx) throws NullPointerException,
            IllegalArgumentException {
        if (tx == null) {
            throw new NullPointerException("The tx parameter is null.");
        }
        if (!tx.isActive()) {
            throw new IllegalArgumentException(
                "The transaction must be active.");
        }
        commitInternal(tx);
    }

    private static void commitInternal(Transaction tx) {
        try {
            tx.commit();
        } catch (DatastoreTimeoutException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            for (int i = 0; i < MAX_RETRY; i++) {
                try {
                    tx.commit();
                    return;
                } catch (DatastoreTimeoutException e2) {
                    logger.log(Level.WARNING, "Retry("
                        + i
                        + "): "
                        + e2.getMessage(), e2);
                }
            }
            throw e;
        }
    }

    /**
     * Rolls back the transaction.
     * 
     * @param tx
     *            the transaction
     * @throws NullPointerException
     *             if the tx parameter is null
     * @throws IllegalArgumentException
     *             if the transaction is not active
     */
    public static void rollback(Transaction tx) throws NullPointerException,
            IllegalArgumentException {
        if (tx == null) {
            throw new NullPointerException("The tx parameter is null.");
        }
        if (!tx.isActive()) {
            throw new IllegalArgumentException(
                "The transaction must be active.");
        }
        rollbackInternal(tx);
    }

    private static void rollbackInternal(Transaction tx) {
        try {
            tx.rollback();
        } catch (DatastoreTimeoutException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            for (int i = 0; i < MAX_RETRY; i++) {
                try {
                    tx.rollback();
                    return;
                } catch (DatastoreTimeoutException e2) {
                    logger.log(Level.WARNING, "Retry("
                        + i
                        + "): "
                        + e2.getMessage(), e2);
                }
            }
            throw e;
        }
    }

    /**
     * Returns a model specified by the key. If there is a current transaction,
     * this operation will execute within that transaction.
     * 
     * @param <M>
     *            the model type
     * @param modelMeta
     *            the meta data of model
     * @param key
     *            the key
     * @return a model specified by the key
     * @throws NullPointerException
     *             if the modelMeta parameter is null
     * @throws EntityNotFoundRuntimeException
     *             if no entity specified by the key could be found
     */
    public static <M> M get(ModelMeta<M> modelMeta, Key key)
            throws NullPointerException, EntityNotFoundRuntimeException {
        if (modelMeta == null) {
            throw new NullPointerException("The modelMeta parameter is null.");
        }
        Entity entity = getEntity(key);
        return modelMeta.entityToModel(entity);
    }

    /**
     * Returns a model specified by the key within the provided transaction.
     * 
     * @param <M>
     *            the model type
     * @param tx
     *            the transaction
     * @param modelMeta
     *            the meta data of model
     * @param key
     *            the key
     * @return a model specified by the key
     * @throws NullPointerException
     *             if the modelMeta parameter is null
     * @throws EntityNotFoundRuntimeException
     *             if no entity specified by the key could be found
     */
    public static <M> M get(Transaction tx, ModelMeta<M> modelMeta, Key key)
            throws NullPointerException, EntityNotFoundRuntimeException {
        if (modelMeta == null) {
            throw new NullPointerException("The modelMeta parameter is null.");
        }
        Entity entity = getEntity(tx, key);
        return modelMeta.entityToModel(entity);
    }

    /**
     * Returns models specified by the keys. If there is a current transaction,
     * this operation will execute within that transaction.
     * 
     * @param <M>
     *            the model type
     * @param modelMeta
     *            the meta data of model
     * @param keys
     *            the keys
     * @return models specified by the keys
     * @throws NullPointerException
     *             if the modelMeta parameter is null
     */
    public static <M> List<M> get(ModelMeta<M> modelMeta, Iterable<Key> keys)
            throws NullPointerException {
        return mapToList(modelMeta, keys, getEntitiesAsMap(keys));
    }

    /**
     * Returns models specified by the keys. If there is a current transaction,
     * this operation will execute within that transaction.
     * 
     * @param <M>
     *            the model type
     * @param modelMeta
     *            the meta data of model
     * @param keys
     *            the keys
     * @return models specified by the keys
     * @throws NullPointerException
     *             if the modelMeta parameter is null
     */
    public static <M> List<M> get(ModelMeta<M> modelMeta, Key... keys)
            throws NullPointerException {
        return mapToList(modelMeta, Arrays.asList(keys), getEntitiesAsMap(keys));
    }

    /**
     * Returns models specified by the keys within the provided transaction.
     * 
     * @param <M>
     *            the model type
     * @param tx
     *            the transaction
     * @param modelMeta
     *            the meta data of model
     * @param keys
     *            the keys
     * @return models specified by the keys
     * @throws NullPointerException
     *             if the modelMeta parameter is null
     * @throws EntityNotFoundRuntimeException
     *             if no entity specified by the key could be found
     */
    public static <M> List<M> get(Transaction tx, ModelMeta<M> modelMeta,
            Iterable<Key> keys) throws NullPointerException,
            EntityNotFoundRuntimeException {
        if (modelMeta == null) {
            throw new NullPointerException("The modelMeta parameter is null.");
        }
        return mapToList(modelMeta, keys, getEntitiesAsMap(tx, keys));
    }

    /**
     * Returns models specified by the keys within the provided transaction.
     * 
     * @param <M>
     *            the model type
     * @param tx
     *            the transaction
     * @param modelMeta
     *            the meta data of model
     * @param keys
     *            the keys
     * @return models specified by the keys
     * @throws NullPointerException
     *             if the tx parameter is null or if the modelMeta parameter is
     *             null
     * @throws EntityNotFoundRuntimeException
     *             if no entity specified by the key could be found
     */
    public static <M> List<M> get(Transaction tx, ModelMeta<M> modelMeta,
            Key... keys) throws NullPointerException,
            EntityNotFoundRuntimeException {
        if (modelMeta == null) {
            throw new NullPointerException("The modelMeta parameter is null.");
        }
        return mapToList(modelMeta, Arrays.asList(keys), getEntitiesAsMap(
            tx,
            keys));
    }

    /**
     * Returns models specified by the keys. If there is a current transaction,
     * this operation will execute within that transaction.
     * 
     * @param <M>
     *            the model type
     * @param modelMeta
     *            the meta data of model
     * @param keys
     *            the keys
     * @return models specified by the keys
     * @throws NullPointerException
     *             if the modelMeta parameter is null
     */
    public static <M> Map<Key, M> getAsMap(ModelMeta<M> modelMeta,
            Iterable<Key> keys) throws NullPointerException {
        return mapToMap(modelMeta, getEntitiesAsMap(keys));
    }

    /**
     * Returns models specified by the keys. If there is a current transaction,
     * this operation will execute within that transaction.
     * 
     * @param <M>
     *            the model type
     * @param modelMeta
     *            the meta data of model
     * @param keys
     *            the keys
     * @return models specified by the keys
     * @throws NullPointerException
     *             if the modelMeta parameter is null
     */
    public static <M> Map<Key, M> getAsMap(ModelMeta<M> modelMeta, Key... keys)
            throws NullPointerException {
        return mapToMap(modelMeta, getEntitiesAsMap(keys));
    }

    /**
     * Returns models specified by the keys within the provided transaction.
     * 
     * @param <M>
     *            the model type
     * @param tx
     *            the transaction
     * @param modelMeta
     *            the meta data of model
     * @param keys
     *            the keys
     * @return entities specified by the key
     * @throws NullPointerException
     *             if the tx parameter is null or if the modelMeta parameter is
     *             null or if the keys parameter is null
     * @throws IllegalArgumentException
     *             if the transaction is not active
     */
    public static <M> Map<Key, M> getAsMap(Transaction tx,
            ModelMeta<M> modelMeta, Iterable<Key> keys)
            throws NullPointerException {
        if (modelMeta == null) {
            throw new NullPointerException("The modelMeta parameter is null.");
        }
        return mapToMap(modelMeta, getEntitiesAsMap(tx, keys));
    }

    /**
     * Returns models specified by the keys within the provided transaction.
     * 
     * @param <M>
     *            the model type
     * @param tx
     *            the transaction
     * @param modelMeta
     *            the meta data of model
     * @param keys
     *            the keys
     * @return entities specified by the key
     * @throws NullPointerException
     *             if the tx parameter is null or if the modelMeta parameter is
     *             null or if the keys parameter is null
     * @throws IllegalArgumentException
     *             if the transaction is not active
     */
    public static <M> Map<Key, M> getAsMap(Transaction tx,
            ModelMeta<M> modelMeta, Key... keys) throws NullPointerException {
        if (modelMeta == null) {
            throw new NullPointerException("The modelMeta parameter is null.");
        }
        return mapToMap(modelMeta, getEntitiesAsMap(tx, keys));
    }

    private static <M> List<M> mapToList(ModelMeta<M> modelMeta,
            Iterable<Key> keys, Map<Key, Entity> map) {
        List<M> list = new ArrayList<M>(map.size());
        for (Key key : keys) {
            Entity entity = map.get(key);
            if (entity != null) {
                list.add(modelMeta.entityToModel(entity));
            }
        }
        return list;
    }

    private static <M> Map<Key, M> mapToMap(ModelMeta<M> modelMeta,
            Map<Key, Entity> map) {
        Map<Key, M> modelMap = new HashMap<Key, M>(map.size());
        for (Key key : map.keySet()) {
            Entity entity = map.get(key);
            modelMap.put(key, modelMeta.entityToModel(entity));
        }
        return modelMap;
    }

    /**
     * Returns an entity specified by the key. If there is a current
     * transaction, this operation will execute within that transaction.
     * 
     * @param key
     *            the key
     * @return an entity specified by the key
     * @throws NullPointerException
     *             if the key parameter is null
     * @throws EntityNotFoundRuntimeException
     *             if no entity specified by the key could be found
     */
    public static Entity getEntity(Key key) throws NullPointerException,
            EntityNotFoundRuntimeException {
        if (key == null) {
            throw new NullPointerException("The key parameter is null.");
        }
        DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        return getInternal(ds, key);
    }

    /**
     * Returns an entity specified by the key within the provided transaction.
     * 
     * @param tx
     *            the transaction
     * @param key
     *            the key
     * @return an entity specified by the key
     * @throws NullPointerException
     *             if the tx parameter is null or if the key parameter is null
     * @throws IllegalArgumentException
     *             if the transaction is not active
     * @throws EntityNotFoundRuntimeException
     *             if no entity specified by the key could be found
     */
    public static Entity getEntity(Transaction tx, Key key)
            throws NullPointerException, IllegalArgumentException,
            EntityNotFoundRuntimeException {
        if (tx == null) {
            throw new NullPointerException("The tx parameter is null.");
        }
        if (key == null) {
            throw new NullPointerException("The key parameter is null.");
        }
        if (!tx.isActive()) {
            throw new IllegalArgumentException(
                "The transaction must be active.");
        }
        DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        return getInternal(ds, tx, key);
    }

    /**
     * Returns entities specified by the keys. If there is a current
     * transaction, this operation will execute within that transaction.
     * 
     * @param keys
     *            the keys
     * @return entities specified by the key
     * @throws NullPointerException
     *             if the keys parameter is null
     */
    public static List<Entity> getEntities(Iterable<Key> keys)
            throws NullPointerException {
        return mapToList(keys, getEntitiesAsMap(keys));
    }

    /**
     * Returns entities specified by the keys. If there is a current
     * transaction, this operation will execute within that transaction.
     * 
     * @param keys
     *            the keys
     * @return entities specified by the key
     */
    public static List<Entity> getEntities(Key... keys) {
        return getEntities(Arrays.asList(keys));
    }

    /**
     * Returns entities specified by the keys within the provided transaction.
     * 
     * @param tx
     *            the transaction
     * @param keys
     *            the keys
     * @return entities specified by the key
     * @throws NullPointerException
     *             if the tx parameter is null or if the keys parameter is null
     * @throws IllegalArgumentException
     *             if the transaction is not active
     */
    public static List<Entity> getEntities(Transaction tx, Iterable<Key> keys)
            throws NullPointerException {
        return mapToList(keys, getEntitiesAsMap(tx, keys));
    }

    /**
     * Returns entities specified by the keys within the provided transaction.
     * 
     * @param tx
     *            the transaction
     * @param keys
     *            the keys
     * @return entities specified by the key
     * @throws NullPointerException
     *             if the tx parameter is null
     * @throws IllegalArgumentException
     *             if the transaction is not active
     */
    public static List<Entity> getEntities(Transaction tx, Key... keys)
            throws NullPointerException {
        return getEntities(tx, Arrays.asList(keys));
    }

    private static List<Entity> mapToList(Iterable<Key> keys,
            Map<Key, Entity> map) {
        List<Entity> list = new ArrayList<Entity>(map.size());
        for (Key key : keys) {
            Entity entity = map.get(key);
            if (entity != null) {
                list.add(entity);
            }
        }
        return list;
    }

    /**
     * Returns entities specified by the keys. If there is a current
     * transaction, this operation will execute within that transaction.
     * 
     * @param keys
     *            the keys
     * @return entities specified by the key
     * @throws NullPointerException
     *             if the keys parameter is null
     */
    public static Map<Key, Entity> getEntitiesAsMap(Iterable<Key> keys)
            throws NullPointerException {
        if (keys == null) {
            throw new NullPointerException("The keys parameter is null.");
        }
        DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        return getInternal(ds, keys);
    }

    /**
     * Returns entities specified by the keys. If there is a current
     * transaction, this operation will execute within that transaction.
     * 
     * @param keys
     *            the keys
     * @return entities specified by the key
     */
    public static Map<Key, Entity> getEntitiesAsMap(Key... keys) {
        return getEntitiesAsMap(Arrays.asList(keys));
    }

    /**
     * Returns entities specified by the keys within the provided transaction.
     * 
     * @param tx
     *            the transaction
     * @param keys
     *            the keys
     * @return entities specified by the key
     * @throws NullPointerException
     *             if the tx parameter is null or if the keys parameter is null
     * @throws IllegalArgumentException
     *             if the transaction is not active
     */
    public static Map<Key, Entity> getEntitiesAsMap(Transaction tx,
            Iterable<Key> keys) throws NullPointerException {
        if (tx == null) {
            throw new NullPointerException("The tx parameter is null.");
        }
        if (keys == null) {
            throw new NullPointerException("The keys parameter is null.");
        }
        if (!tx.isActive()) {
            throw new IllegalArgumentException(
                "The transaction must be active.");
        }
        DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        return getInternal(ds, tx, keys);
    }

    /**
     * Returns entities specified by the keys within the provided transaction.
     * 
     * @param tx
     *            the transaction
     * @param keys
     *            the keys
     * @return entities specified by the key
     * @throws NullPointerException
     *             if the tx parameter is null
     * @throws IllegalArgumentException
     *             if the transaction is not active
     */
    public static Map<Key, Entity> getEntitiesAsMap(Transaction tx, Key... keys)
            throws NullPointerException, IllegalArgumentException {
        return getEntitiesAsMap(tx, Arrays.asList(keys));
    }

    private static Entity getInternal(DatastoreService ds, Key key) {
        try {
            try {
                return ds.get(key);
            } catch (DatastoreTimeoutException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
                for (int i = 0; i < MAX_RETRY; i++) {
                    try {
                        return ds.get(key);
                    } catch (DatastoreTimeoutException e2) {
                        logger.log(Level.WARNING, "Retry("
                            + i
                            + "): "
                            + e2.getMessage(), e2);
                    }
                }
                throw e;
            }
        } catch (EntityNotFoundException cause) {
            throw new EntityNotFoundRuntimeException(key, cause);
        }
    }

    private static Entity getInternal(DatastoreService ds, Transaction tx,
            Key key) {
        try {
            try {
                return ds.get(tx, key);
            } catch (DatastoreTimeoutException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
                for (int i = 0; i < MAX_RETRY; i++) {
                    try {
                        return ds.get(tx, key);
                    } catch (DatastoreTimeoutException e2) {
                        logger.log(Level.WARNING, "Retry("
                            + i
                            + "): "
                            + e2.getMessage(), e2);
                    }
                }
                throw e;
            }
        } catch (EntityNotFoundException cause) {
            throw new EntityNotFoundRuntimeException(key, cause);
        }
    }

    private static Map<Key, Entity> getInternal(DatastoreService ds,
            Iterable<Key> keys) {
        try {
            return ds.get(keys);
        } catch (DatastoreTimeoutException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            for (int i = 0; i < MAX_RETRY; i++) {
                try {
                    return ds.get(keys);
                } catch (DatastoreTimeoutException e2) {
                    logger.log(Level.WARNING, "Retry("
                        + i
                        + "): "
                        + e2.getMessage(), e2);
                }
            }
            throw e;
        }
    }

    private static Map<Key, Entity> getInternal(DatastoreService ds,
            Transaction tx, Iterable<Key> keys) {
        try {
            return ds.get(tx, keys);
        } catch (DatastoreTimeoutException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            for (int i = 0; i < MAX_RETRY; i++) {
                try {
                    return ds.get(tx, keys);
                } catch (DatastoreTimeoutException e2) {
                    logger.log(Level.WARNING, "Retry("
                        + i
                        + "): "
                        + e2.getMessage(), e2);
                }
            }
            throw e;
        }
    }

    /**
     * Puts the model to datastore. If there is a current transaction, this
     * operation will execute within that transaction.
     * 
     * @param model
     *            the model
     * @return a key
     * @throws NullPointerException
     *             if the model parameter is null
     */
    public static Key put(Object model) throws NullPointerException {
        if (model == null) {
            throw new NullPointerException("The model parameter is null.");
        }
        ModelMeta<?> modelMeta = getModelMeta(model.getClass());
        Entity entity = modelMeta.modelToEntity(model);
        return putEntity(entity);
    }

    /**
     * Puts the model to datastore within the provided transaction.
     * 
     * @param tx
     *            the transaction
     * @param model
     *            the model
     * @return a key
     * @throws NullPointerException
     *             if the tx parameter is null or if the model parameter is null
     * @throws IllegalArgumentException
     *             if the transaction is not active
     */
    public static Key put(Transaction tx, Object model)
            throws NullPointerException {
        if (model == null) {
            throw new NullPointerException("The model parameter is null.");
        }
        ModelMeta<?> modelMeta = getModelMeta(model.getClass());
        Entity entity = modelMeta.modelToEntity(model);
        return putEntity(tx, entity);
    }

    /**
     * Puts the models to datastore. If there is a current transaction, this
     * operation will execute within that transaction.
     * 
     * @param models
     *            the models
     * @return a list of keys
     * @throws NullPointerException
     *             if the models parameter is null
     */
    public static List<Key> put(Iterable<?> models) throws NullPointerException {
        if (models == null) {
            throw new NullPointerException("The models parameter is null.");
        }
        return putEntities(modelsToEntities(models));
    }

    /**
     * Puts the models to datastore. If there is a current transaction, this
     * operation will execute within that transaction.
     * 
     * @param models
     *            the models
     * @return a list of keys
     */
    public static List<Key> put(Object... models) {
        return put(Arrays.asList(models));
    }

    /**
     * Puts the models to datastore within the provided transaction.
     * 
     * @param tx
     *            the transaction
     * @param models
     *            the models
     * @return a list of keys
     * @throws NullPointerException
     *             if the tx parameter is null or if the models parameter is
     *             null
     * @throws IllegalArgumentException
     *             if the transaction is not active
     */
    public static List<Key> put(Transaction tx, Iterable<?> models)
            throws NullPointerException, IllegalArgumentException {
        if (models == null) {
            throw new NullPointerException("The models parameter is null.");
        }
        return putEntities(tx, modelsToEntities(models));
    }

    /**
     * Puts the models to datastore within the provided transaction.
     * 
     * @param tx
     *            the transaction
     * @param models
     *            the models
     * @return a list of keys
     * @throws NullPointerException
     *             if the tx parameter is null
     * @throws IllegalArgumentException
     *             if the transaction is not active
     */
    public static List<Key> put(Transaction tx, Object... models)
            throws NullPointerException, IllegalArgumentException {
        return put(tx, Arrays.asList(models));
    }

    private static List<Entity> modelsToEntities(Iterable<?> models)
            throws NullPointerException {
        List<Entity> entities = new ArrayList<Entity>();
        for (Object model : models) {
            if (model == null) {
                throw new NullPointerException(
                    "The element of the models is null.");
            }
            ModelMeta<?> modelMeta = getModelMeta(model.getClass());
            Entity entity = modelMeta.modelToEntity(model);
            entities.add(entity);
        }
        return entities;
    }

    /**
     * Puts the entity to datastore. If there is a current transaction, this
     * operation will execute within that transaction.
     * 
     * @param entity
     *            the entity
     * @return a key
     * @throws NullPointerException
     *             if the entity parameter is null
     */
    public static Key putEntity(Entity entity) throws NullPointerException {
        if (entity == null) {
            throw new NullPointerException("The entity parameter is null.");
        }
        DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        return putInternal(ds, entity);
    }

    /**
     * Puts the entity to datastore within the provided transaction.
     * 
     * @param tx
     *            the transaction
     * @param entity
     *            the entity
     * @return a key
     * @throws NullPointerException
     *             if the tx parameter is null or if the entity parameter is
     *             null
     * @throws IllegalArgumentException
     *             if the transaction is not active
     */
    public static Key putEntity(Transaction tx, Entity entity)
            throws NullPointerException, IllegalArgumentException {
        if (tx == null) {
            throw new NullPointerException("The tx parameter is null.");
        }
        if (entity == null) {
            throw new NullPointerException("The entity parameter is null.");
        }
        if (!tx.isActive()) {
            throw new IllegalArgumentException(
                "The transaction must be active.");
        }
        DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        return putInternal(ds, tx, entity);
    }

    /**
     * Puts the entities to datastore. If there is a current transaction, this
     * operation will execute within that transaction.
     * 
     * @param entities
     *            the entities
     * @return a list of keys
     * @throws NullPointerException
     *             if the entities parameter is null
     */
    public static List<Key> putEntities(Iterable<Entity> entities)
            throws NullPointerException {
        if (entities == null) {
            throw new NullPointerException("The entities parameter is null.");
        }
        DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        return putInternal(ds, entities);
    }

    /**
     * Puts the entities to datastore. If there is a current transaction, this
     * operation will execute within that transaction.
     * 
     * @param entities
     *            the entities
     * @return a list of keys
     */
    public static List<Key> putEntities(Entity... entities) {
        return putEntities(Arrays.asList(entities));
    }

    /**
     * Puts the entities to datastore within the provided transaction.
     * 
     * @param tx
     *            the transaction
     * @param entities
     *            the entities
     * @return a list of keys
     * @throws NullPointerException
     *             if the tx parameter is null or if the entities parameter is
     *             null
     * @throws IllegalArgumentException
     *             if the transaction is not active
     */
    public static List<Key> putEntities(Transaction tx,
            Iterable<Entity> entities) throws NullPointerException,
            IllegalArgumentException {
        if (tx == null) {
            throw new NullPointerException("The tx parameter is null.");
        }
        if (entities == null) {
            throw new NullPointerException("The entities parameter is null.");
        }
        if (!tx.isActive()) {
            throw new IllegalArgumentException(
                "The transaction must be active.");
        }
        DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        return putInternal(ds, tx, entities);
    }

    /**
     * Puts the entities to datastore within the provided transaction.
     * 
     * @param tx
     *            the transaction
     * @param entities
     *            the entities
     * @return a list of keys
     * @throws NullPointerException
     *             if the tx parameter is null
     * @throws IllegalArgumentException
     *             if the transaction is not active
     */
    public static List<Key> putEntities(Transaction tx, Entity... entities)
            throws NullPointerException, IllegalArgumentException {
        return putEntities(tx, Arrays.asList(entities));
    }

    private static Key putInternal(DatastoreService ds, Entity entity) {
        try {
            return ds.put(entity);
        } catch (DatastoreTimeoutException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            for (int i = 0; i < MAX_RETRY; i++) {
                try {
                    return ds.put(entity);
                } catch (DatastoreTimeoutException e2) {
                    logger.log(Level.WARNING, "Retry("
                        + i
                        + "): "
                        + e2.getMessage(), e2);
                }
            }
            throw e;
        }
    }

    private static Key putInternal(DatastoreService ds, Transaction tx,
            Entity entity) {
        try {
            return ds.put(tx, entity);
        } catch (DatastoreTimeoutException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            for (int i = 0; i < MAX_RETRY; i++) {
                try {
                    return ds.put(tx, entity);
                } catch (DatastoreTimeoutException e2) {
                    logger.log(Level.WARNING, "Retry("
                        + i
                        + "): "
                        + e2.getMessage(), e2);
                }
            }
            throw e;
        }
    }

    private static List<Key> putInternal(DatastoreService ds,
            Iterable<Entity> entities) {
        try {
            return ds.put(entities);
        } catch (DatastoreTimeoutException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            for (int i = 0; i < MAX_RETRY; i++) {
                try {
                    return ds.put(entities);
                } catch (DatastoreTimeoutException e2) {
                    logger.log(Level.WARNING, "Retry("
                        + i
                        + "): "
                        + e2.getMessage(), e2);
                }
            }
            throw e;
        }
    }

    private static List<Key> putInternal(DatastoreService ds, Transaction tx,
            Iterable<Entity> entities) {
        try {
            return ds.put(tx, entities);
        } catch (DatastoreTimeoutException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            for (int i = 0; i < MAX_RETRY; i++) {
                try {
                    return ds.put(tx, entities);
                } catch (DatastoreTimeoutException e2) {
                    logger.log(Level.WARNING, "Retry("
                        + i
                        + "): "
                        + e2.getMessage(), e2);
                }
            }
            throw e;
        }
    }

    /**
     * Deletes entities specified by the keys. If there is a current
     * transaction, this operation will execute within that transaction.
     * 
     * @param keys
     *            the keys
     * @throws NullPointerException
     *             if the keys parameter is null
     */
    public static void delete(Iterable<Key> keys) throws NullPointerException {
        if (keys == null) {
            throw new NullPointerException("The keys parameter is null.");
        }
        DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        deleteInternal(ds, keys);
    }

    /**
     * Deletes entities specified by the keys. If there is a current
     * transaction, this operation will execute within that transaction.
     * 
     * @param keys
     *            the keys
     */
    public static void delete(Key... keys) {
        delete(Arrays.asList(keys));
    }

    /**
     * Deletes entities specified by the keys within the provided transaction.
     * 
     * @param tx
     *            the transaction
     * @param keys
     *            the keys
     * @throws NullPointerException
     *             if the tx parameter is null or if the keys parameter is null
     * @throws IllegalArgumentException
     *             if the transaction is not active
     */
    public static void delete(Transaction tx, Iterable<Key> keys)
            throws NullPointerException, IllegalArgumentException {
        if (tx == null) {
            throw new NullPointerException("The tx parameter is null.");
        }
        if (keys == null) {
            throw new NullPointerException("The keys parameter is null.");
        }
        if (!tx.isActive()) {
            throw new IllegalArgumentException(
                "The transaction must be active.");
        }
        DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        deleteInternal(ds, tx, keys);
    }

    /**
     * Deletes entities specified by the keys within the provided transaction.
     * 
     * @param tx
     *            the transaction
     * @param keys
     *            the keys
     * @throws NullPointerException
     *             if the tx parameter is null
     * @throws IllegalArgumentException
     *             if the transaction is not active
     */
    public static void delete(Transaction tx, Key... keys)
            throws NullPointerException, IllegalArgumentException {
        delete(tx, Arrays.asList(keys));
    }

    private static void deleteInternal(DatastoreService ds, Iterable<Key> keys) {
        try {
            ds.delete(keys);
        } catch (DatastoreTimeoutException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            for (int i = 0; i < MAX_RETRY; i++) {
                try {
                    ds.delete(keys);
                    return;
                } catch (DatastoreTimeoutException e2) {
                    logger.log(Level.WARNING, "Retry("
                        + i
                        + "): "
                        + e2.getMessage(), e2);
                }
            }
            throw e;
        }
    }

    private static void deleteInternal(DatastoreService ds, Transaction tx,
            Iterable<Key> keys) {
        try {
            ds.delete(tx, keys);
        } catch (DatastoreTimeoutException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            for (int i = 0; i < MAX_RETRY; i++) {
                try {
                    ds.delete(tx, keys);
                    return;
                } catch (DatastoreTimeoutException e2) {
                    logger.log(Level.WARNING, "Retry("
                        + i
                        + "): "
                        + e2.getMessage(), e2);
                }
            }
            throw e;
        }
    }

    /**
     * Returns a {@link SelectQuery}.
     * 
     * @param <M>
     *            the model type
     * @param modelMeta
     *            the meta data of model
     * @return a {@link SelectQuery}
     */
    public static final <M> SelectQuery<M> query(ModelMeta<M> modelMeta) {
        return new SelectQuery<M>(modelMeta);
    }

    /**
     * Returns a meta data of the model.
     * 
     * @param modelClass
     *            the model class
     * @return a meta data of the model
     */
    protected static ModelMeta<?> getModelMeta(Class<?> modelClass) {
        if (!initialized) {
            initialize();
        }
        ModelMeta<?> modelMeta = modelMetaCache.get(modelClass.getName());
        if (modelMeta != null) {
            return modelMeta;
        }
        modelMeta = createModelMeta(modelClass);
        ModelMeta<?> old =
            modelMetaCache.putIfAbsent(modelClass.getName(), modelMeta);
        return old != null ? old : modelMeta;
    }

    /**
     * Creates a new meta data of the model.
     * 
     * @param modelClass
     *            the model class
     * @return a new meta data of the model
     */
    protected static ModelMeta<?> createModelMeta(Class<?> modelClass) {
        try {
            String metaClassName =
                modelClass.getName().replace(".model.", ".meta.") + "Meta";
            return ClassUtil.newInstance(metaClassName, Thread
                .currentThread()
                .getContextClassLoader());
        } catch (Throwable cause) {
            throw new IllegalArgumentException("The meta data of the model("
                + modelClass.getName()
                + ") is not found.");
        }
    }

    private Datastore() {
    }
}