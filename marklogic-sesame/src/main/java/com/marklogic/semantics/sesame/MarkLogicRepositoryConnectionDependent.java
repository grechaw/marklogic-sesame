/*
 * Copyright 2015 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * A library that enables access to a MarkLogic-backed triple-store via the
 * Sesame API.
 */
package com.marklogic.semantics.sesame;

import info.aduna.iteration.Iteration;
import org.openrdf.model.Statement;
import org.openrdf.query.*;
import org.openrdf.repository.RepositoryException;

/**
 * interface which defines MarkLogic specific overrides
 */
public interface MarkLogicRepositoryConnectionDependent {

    public Query prepareQuery(String queryString) throws RepositoryException, MalformedQueryException;
    public Query prepareQuery(String queryString, String baseURI) throws RepositoryException, MalformedQueryException;

    public TupleQuery prepareTupleQuery(String queryString) throws RepositoryException, MalformedQueryException;
    public TupleQuery prepareTupleQuery(String queryString,String baseURI) throws RepositoryException, MalformedQueryException;

    public Update prepareUpdate(String queryString) throws RepositoryException, MalformedQueryException;
    public Update prepareUpdate(String queryString, String baseURI) throws RepositoryException, MalformedQueryException;

    public BooleanQuery prepareBooleanQuery(String queryString) throws RepositoryException, MalformedQueryException;
    public BooleanQuery prepareBooleanQuery(String queryString, String baseURI) throws RepositoryException, MalformedQueryException;

    public GraphQuery prepareGraphQuery(String queryString) throws RepositoryException, MalformedQueryException;
    public GraphQuery prepareGraphQuery(String queryString, String baseURI) throws RepositoryException, MalformedQueryException;

    public void clear() throws RepositoryException;
    public long size();

    public void remove(Iterable<? extends Statement> statements) throws RepositoryException;
    public <E extends Exception> void remove(Iteration<? extends Statement, E> statements) throws RepositoryException, E;
}