/*
 * Copyright the original author or authors.
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
import java.util.List;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;

/**
 * A query class for select.
 * 
 * @author higa
 * @param <M>
 *            the model type
 * @since 3.0
 * 
 */
public class SelectQuery<M> {

    /**
     * The meta data of model.
     */
    protected ModelMeta<M> modelMeta;

    /**
     * The ancestor key.
     */
    protected Key ancestorKey;

    /**
     * The filter criteria.
     */
    protected FilterCriterion[] filterCriteria;

    /**
     * Whether this query returns keys only.
     */
    protected boolean keysOnly = false;

    /**
     * Constructor.
     * 
     * @param modelMeta
     *            the meta data of model
     * @throws NullPointerException
     *             if the modelMeta parameter is null
     */
    public SelectQuery(ModelMeta<M> modelMeta) throws NullPointerException {
        if (modelMeta == null) {
            throw new NullPointerException("The modelMeta parameter is null.");
        }
        this.modelMeta = modelMeta;
    }

    /**
     * Constructor.
     * 
     * @param modelMeta
     *            the meta data of model
     * @param ancestorKey
     *            the ancestor key
     * @throws NullPointerException
     *             if the modelMeta parameter is null or if the ancestorKey
     *             parameter is null
     */
    public SelectQuery(ModelMeta<M> modelMeta, Key ancestorKey)
            throws NullPointerException {
        if (modelMeta == null) {
            throw new NullPointerException("The modelMeta parameter is null.");
        }
        if (ancestorKey == null) {
            throw new NullPointerException("The ancestorKey parameter is null.");
        }
        this.modelMeta = modelMeta;
        this.ancestorKey = ancestorKey;
    }

    /**
     * Adds the filter criteria.
     * 
     * @param criteria
     *            the filter criteria
     * @return this instance
     */
    public SelectQuery<M> filter(FilterCriterion... criteria) {
        this.filterCriteria = criteria;
        return this;
    }

    /**
     * Returns the result as a list.
     * 
     * @return the result as a list
     */
    public List<M> asList() {
        PreparedQuery query = prepareQuery();
        List<Entity> entityList = query.asList(fetchOptions());
        List<M> ret = new ArrayList<M>(entityList.size());
        for (Entity e : entityList) {
            ret.add(modelMeta.entityToModel(e));
        }
        return ret;
    }

    /**
     * Returns the single result.
     * 
     * @return the single result
     */
    public M asSingle() {
        PreparedQuery query = prepareQuery();
        Entity entity = query.asSingleEntity();
        if (entity == null) {
            return null;
        }
        return modelMeta.entityToModel(entity);
    }

    /**
     * Returns the result as a list of key.
     * 
     * @return the result as a list of key
     */
    public List<Key> asKeyList() {
        keysOnly = true;
        PreparedQuery query = prepareQuery();
        List<Entity> entityList = query.asList(fetchOptions());
        List<Key> ret = new ArrayList<Key>(entityList.size());
        for (Entity e : entityList) {
            ret.add(e.getKey());
        }
        return ret;
    }

    /**
     * Creates a new query.
     * 
     * @return a new query.
     */
    protected PreparedQuery prepareQuery() {
        Query query = new Query(modelMeta.getModelClass().getSimpleName());
        applyFilter(query);
        applyKeysOnly(query);
        DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
        return ds.prepare(query);
    }

    /**
     * Returns fetch options.
     * 
     * @return fetch options
     */
    protected FetchOptions fetchOptions() {
        return FetchOptions.Builder.withOffset(0);
    }

    /**
     * Applies the filter to the query.
     * 
     * @param query
     *            the query
     * 
     */
    protected void applyFilter(Query query) {
        if (filterCriteria == null) {
            return;
        }
        for (FilterCriterion c : filterCriteria) {
            if (c == null) {
                continue;
            }
            c.apply(query);
        }
    }

    /**
     * Applies the filter to the query.
     * 
     * @param query
     *            the query
     * 
     */
    protected void applyKeysOnly(Query query) {
        if (keysOnly) {
            query.setKeysOnly();
        }
    }
}