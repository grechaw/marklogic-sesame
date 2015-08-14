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
package com.marklogic.semantics.sesame.query;

import com.marklogic.client.FailedRequestException;
import com.marklogic.client.ForbiddenUserException;
import com.marklogic.semantics.sesame.client.MarkLogicClient;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.Update;
import org.openrdf.query.UpdateExecutionException;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sparql.query.SPARQLQueryBindingSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * update query
 *
 * @author James Fuller
 */
public class MarkLogicUpdateQuery extends MarkLogicQuery implements Update,MarkLogicQueryDependent {

    protected final Logger logger = LoggerFactory.getLogger(MarkLogicUpdateQuery.class);

    /**
     *  constructor
     *
     * @param client
     * @param bindingSet
     * @param baseUri
     * @param queryString
     */
    public MarkLogicUpdateQuery(MarkLogicClient client, SPARQLQueryBindingSet bindingSet, String baseUri, String queryString) {
        super(client, bindingSet, baseUri, queryString);
    }

    /**
     *
      * @throws UpdateExecutionException
     */
    @Override
    public void execute() throws UpdateExecutionException {
        try {
            getMarkLogicClient().sendUpdateQuery(getQueryString(), getBindings(), getIncludeInferred(), getBaseURI());
        }catch(ForbiddenUserException e){
            throw new UpdateExecutionException(e);
        } catch (RepositoryException e) {
            e.printStackTrace();
        } catch (MalformedQueryException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch(FailedRequestException e){
            throw new UpdateExecutionException(e.getMessage(), e);
        }
    }

}