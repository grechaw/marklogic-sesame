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

import com.marklogic.semantics.sesame.client.MarkLogicClient;
import com.marklogic.semantics.sesame.query.*;
import info.aduna.iteration.*;
import org.openrdf.IsolationLevel;
import org.openrdf.IsolationLevels;
import org.openrdf.model.*;
import org.openrdf.model.impl.StatementImpl;
import org.openrdf.query.*;
import org.openrdf.query.impl.DatasetImpl;
import org.openrdf.query.parser.QueryParserUtil;
import org.openrdf.query.parser.sparql.SPARQLUtil;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.repository.UnknownTransactionStateException;
import org.openrdf.repository.base.RepositoryConnectionBase;
import org.openrdf.repository.sparql.query.SPARQLQueryBindingSet;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandler;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.Iterator;

import static org.openrdf.query.QueryLanguage.SPARQL;

/**
 *
 * @author James Fuller
 */
public class MarkLogicRepositoryConnection extends RepositoryConnectionBase implements RepositoryConnection,MarkLogicRepositoryConnectionDependent {

    protected final Logger logger = LoggerFactory.getLogger(MarkLogicRepositoryConnection.class);

    private static final String EVERYTHING = "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }";

    private static final String EVERYTHING_WITH_GRAPH = "SELECT * WHERE {  ?s ?p ?o . OPTIONAL { GRAPH ?ctx { ?s ?p ?o } } }";

    private static final String SOMETHING = "ASK { ?s ?p ?o }";

    private static final String NAMEDGRAPHS = "SELECT DISTINCT ?_ WHERE { GRAPH ?_ { ?s ?p ?o } }";

    private final boolean quadMode;

    private MarkLogicClient client;

    //constructor
    public MarkLogicRepositoryConnection(MarkLogicRepository repository, MarkLogicClient client, boolean quadMode) {
        super(repository);
        this.client = client;
        this.quadMode = true;
        client.setValueFactory(repository.getValueFactory());
    }

    // valuefactory
    @Override
    public ValueFactory getValueFactory() {
        return client.getValueFactory();
    }
    public void setValueFactory(ValueFactory f) {
        client.setValueFactory(f);
    }

    // prepareQuery entrypoint
    @Override
    public Query prepareQuery(String queryString) throws RepositoryException, MalformedQueryException {
        return prepareQuery(QueryLanguage.SPARQL, queryString, null);
    }
    @Override
    public Query prepareQuery(String queryString, String baseURI) throws RepositoryException, MalformedQueryException {
        return prepareQuery(QueryLanguage.SPARQL, queryString, baseURI);
    }
    @Override
    public Query prepareQuery(QueryLanguage queryLanguage, String queryString) throws RepositoryException, MalformedQueryException {
        return prepareQuery(queryLanguage, queryString, null);
    }
    @Override
    public MarkLogicQuery prepareQuery(QueryLanguage queryLanguage, String queryString, String baseURI)
            throws RepositoryException, MalformedQueryException
    {
        // function routing based on query form
        if (SPARQL.equals(queryLanguage)) {
            String queryStringWithoutProlog = QueryParserUtil.removeSPARQLQueryProlog(queryString).toUpperCase();
            if (queryStringWithoutProlog.startsWith("SELECT")) {
                return prepareTupleQuery(queryLanguage, queryString, baseURI);   //must be a TupleQuery
            }
            else if (queryStringWithoutProlog.startsWith("ASK")) {
                return prepareBooleanQuery(queryLanguage, queryString, baseURI); //must be a BooleanQuery
            }
            else {
                return prepareGraphQuery(queryLanguage, queryString, baseURI);   //all the rest use GraphQuery
            }
        }
        throw new UnsupportedOperationException("Unsupported query language " + queryLanguage.getName());
    }

    // prepareTupleQuery
    @Override
    public MarkLogicTupleQuery prepareTupleQuery(String queryString) throws RepositoryException, MalformedQueryException {
        return prepareTupleQuery(QueryLanguage.SPARQL, queryString);
    }
    @Override
    public MarkLogicTupleQuery prepareTupleQuery(String queryString,String baseURI) throws RepositoryException, MalformedQueryException {
        return prepareTupleQuery(QueryLanguage.SPARQL, queryString,baseURI);
    }
    @Override
    public MarkLogicTupleQuery prepareTupleQuery(QueryLanguage queryLanguage, String queryString) throws RepositoryException, MalformedQueryException {
        return prepareTupleQuery(queryLanguage, queryString, null);
    }
    @Override
    public MarkLogicTupleQuery prepareTupleQuery(QueryLanguage queryLanguage, String queryString, String baseURI) throws RepositoryException, MalformedQueryException {
        if (QueryLanguage.SPARQL.equals(queryLanguage)) {
            return new MarkLogicTupleQuery(client, new SPARQLQueryBindingSet(), baseURI, queryString);
        }
        throw new UnsupportedQueryLanguageException("Unsupported query language " + queryLanguage.getName());
    }

    // prepareGraphQuery
    @Override
    public MarkLogicGraphQuery prepareGraphQuery(String queryString) throws RepositoryException, MalformedQueryException {
        return prepareGraphQuery(QueryLanguage.SPARQL, queryString, null);
    }
    @Override
    public MarkLogicGraphQuery prepareGraphQuery(String queryString,String baseURI) throws RepositoryException, MalformedQueryException {
        return prepareGraphQuery(QueryLanguage.SPARQL, queryString, baseURI);
    }
    @Override
    public MarkLogicGraphQuery prepareGraphQuery(QueryLanguage queryLanguage, String queryString) throws RepositoryException, MalformedQueryException {
        return prepareGraphQuery(queryLanguage, queryString, null);
    }
    @Override
    public MarkLogicGraphQuery prepareGraphQuery(QueryLanguage queryLanguage, String queryString, String baseURI)
            throws RepositoryException, MalformedQueryException
    {
        if (QueryLanguage.SPARQL.equals(queryLanguage)) {
            return new MarkLogicGraphQuery(client, new SPARQLQueryBindingSet(), baseURI, queryString);
        }
        throw new UnsupportedQueryLanguageException("Unsupported query language " + queryLanguage.getName());
    }

    // prepareBooleanQuery
    @Override
    public MarkLogicBooleanQuery prepareBooleanQuery(String queryString) throws RepositoryException, MalformedQueryException {
        return prepareBooleanQuery(QueryLanguage.SPARQL, queryString, null);
    }
    @Override
    public MarkLogicBooleanQuery prepareBooleanQuery(String queryString,String baseURI) throws RepositoryException, MalformedQueryException {
        return prepareBooleanQuery(QueryLanguage.SPARQL, queryString, baseURI);
    }
    @Override
    public MarkLogicBooleanQuery prepareBooleanQuery(QueryLanguage queryLanguage, String queryString) throws RepositoryException, MalformedQueryException {
        return prepareBooleanQuery(queryLanguage, queryString, null);
    }
    @Override
    public MarkLogicBooleanQuery prepareBooleanQuery(QueryLanguage queryLanguage, String queryString, String baseURI) throws RepositoryException, MalformedQueryException {
        if (QueryLanguage.SPARQL.equals(queryLanguage)) {
            return new MarkLogicBooleanQuery(client, new SPARQLQueryBindingSet(), baseURI, queryString);
        }
        throw new UnsupportedQueryLanguageException("Unsupported query language " + queryLanguage.getName());
    }

    // prepareUpdate
    @Override
    public MarkLogicUpdateQuery prepareUpdate(String queryString) throws RepositoryException, MalformedQueryException {
        return prepareUpdate(QueryLanguage.SPARQL, queryString, null);
    }
    @Override
    public MarkLogicUpdateQuery prepareUpdate(String queryString,String baseURI) throws RepositoryException, MalformedQueryException {
        return prepareUpdate(QueryLanguage.SPARQL, queryString, baseURI);
    }
    @Override
    public MarkLogicUpdateQuery prepareUpdate(QueryLanguage queryLanguage, String queryString) throws RepositoryException, MalformedQueryException {
       return prepareUpdate(queryLanguage, queryString, null);
    }
    @Override
    public MarkLogicUpdateQuery prepareUpdate(QueryLanguage queryLanguage, String queryString, String baseURI) throws RepositoryException, MalformedQueryException {
        if (QueryLanguage.SPARQL.equals(queryLanguage)) {
            return new MarkLogicUpdateQuery(client, new SPARQLQueryBindingSet(), baseURI, queryString);
        }
        throw new UnsupportedQueryLanguageException("Unsupported query language " + queryLanguage.getName());
    }

    // get list of contexts (graphs)
    @Override
    public RepositoryResult<Resource> getContextIDs() throws RepositoryException {

        try{
            String queryString = "SELECT DISTINCT ?_ WHERE { GRAPH ?_ { ?s ?p ?o } }";
            TupleQuery tupleQuery = prepareTupleQuery(QueryLanguage.SPARQL, queryString);
            TupleQueryResult result = tupleQuery.evaluate();
            return
                    new RepositoryResult<Resource>(
                            new ExceptionConvertingIteration<Resource, RepositoryException>(
                                    new ConvertingIteration<BindingSet, Resource, QueryEvaluationException>(result) {

                                        @Override
                                        protected Resource convert(BindingSet bindings)
                                                throws QueryEvaluationException {
                                            return (Resource) bindings.getValue("_");
                                        }
                                    }) {

                                @Override
                                protected RepositoryException convert(Exception e) {
                                    return new RepositoryException(e);
                                }
                            });

        } catch (MalformedQueryException e) {
            throw new RepositoryException(e);
        } catch (QueryEvaluationException e) {
            throw new RepositoryException(e);
        }
    }

    // statements
    @Override
    public RepositoryResult<Statement> getStatements(Resource subj, URI pred, Value obj, boolean includeInferred, Resource... contexts) throws RepositoryException {
        try {
            if (isQuadMode()) {
                StringBuilder sb= new StringBuilder();
                if(contexts.length !=0){
                    sb.append("SELECT * WHERE { ");

                    for (int i = 0; i < contexts.length; i++)
                    {
                        sb.append("GRAPH <"+ contexts[i].stringValue()+"> {?s ?p ?o .} ");
                    }
                    sb.append("}");
                }else {
                    sb.append(EVERYTHING_WITH_GRAPH);
                }

                TupleQuery tupleQuery = prepareTupleQuery(sb.toString());
                setBindings(tupleQuery, subj, pred, obj, contexts);
                tupleQuery.setIncludeInferred(includeInferred);
                TupleQueryResult qRes = tupleQuery.evaluate();
                return new RepositoryResult<Statement>(
                        new ExceptionConvertingIteration<Statement, RepositoryException>(
                                toStatementIteration(qRes, subj, pred, obj)) {
                            @Override
                            protected RepositoryException convert(Exception e) {
                                return new RepositoryException(e);
                            }
                        });
            }
            if (subj != null && pred != null && obj != null) {
                if (hasStatement(subj, pred, obj, includeInferred, contexts)) {
                    Statement st = new StatementImpl(subj, pred, obj);
                    CloseableIteration<Statement, RepositoryException> cursor;
                    cursor = new SingletonIteration<Statement, RepositoryException>(st);
                    return new RepositoryResult<Statement>(cursor);
                } else {
                    return new RepositoryResult<Statement>(new EmptyIteration<Statement, RepositoryException>());
                }
            }

            GraphQuery query = prepareGraphQuery(EVERYTHING);
            setBindings(query, subj, pred, obj, contexts);
            GraphQueryResult result = query.evaluate();
            return new RepositoryResult<Statement>(
                    new ExceptionConvertingIteration<Statement, RepositoryException>(result) {

                        @Override
                        protected RepositoryException convert(Exception e) {
                            return new RepositoryException(e);
                        }
                    });
        } catch (MalformedQueryException e) {
            throw new RepositoryException(e);
        } catch (QueryEvaluationException e) {
            throw new RepositoryException(e);
        }
    }
    @Override
    public boolean hasStatement(Statement st, boolean includeInferred, Resource... contexts) throws RepositoryException {
        return hasStatement(st.getSubject(),st.getPredicate(),st.getObject(),includeInferred,contexts); //TBD
    }
    @Override
    public boolean hasStatement(Resource subject, URI predicate, Value object, boolean includeInferred, Resource... contexts) throws RepositoryException {
        try {
            StringBuilder ob = new StringBuilder();
            if (object instanceof Literal) {
                Literal lit = (Literal)object;
                ob.append("\"");
                ob.append(SPARQLUtil.encodeString(lit.getLabel()));
                ob.append("\"");
                ob.append("^^<" + lit.getDatatype().stringValue() + ">");
                ob.append(" ");
            }else {
                ob.append("<" + object.stringValue() + "> ");
            }
            StringBuilder sb = new StringBuilder();
            if(contexts.length !=0) {
                //if (baseURI != null) sb.append("BASE <" + baseURI + ">\n");
                sb.append("ASK { ");

                for (int i = 0; i < contexts.length; i++) {
                    sb.append("GRAPH <" + contexts[i].stringValue() + "> {<" + subject.stringValue() + "> <" + predicate.stringValue() + "> " + ob.toString() + " .} ");
                }
                sb.append("}");
            }else{
                sb.append(SOMETHING);
            }
            BooleanQuery query = prepareBooleanQuery(sb.toString(), null);
            setBindings(query, subject, predicate, object, contexts);
            return query.evaluate();
        }
        catch (MalformedQueryException e) {
            throw new RepositoryException(e);
        }
        catch (QueryEvaluationException e) {
            throw new RepositoryException(e);
        }
    }

    // export
    @Override
    public void exportStatements(Resource subject, URI predicate, Value object, boolean includeInferred, RDFHandler handler, Resource... contexts) throws RepositoryException, RDFHandlerException {
        try {
            StringBuilder ob = new StringBuilder();
            StringBuilder sb = new StringBuilder();

            if(object != null && object instanceof Literal) {
                if (object instanceof Literal) {
                    Literal lit = (Literal) object;
                    ob.append("\"");
                    ob.append(SPARQLUtil.encodeString(lit.getLabel()));
                    ob.append("\"");
                    ob.append("^^<" + lit.getDatatype().stringValue() + ">");
                    ob.append(" ");
                } else {
                    ob.append("<" + object.stringValue() + "> ");
                }
                    //if (baseURI != null) sb.append("BASE <" + baseURI + ">\n");
                sb.append("CONSTRUCT {?s ?p "+ob.toString()+"} WHERE {");
                if(contexts.length !=0) {
                    for (int i = 0; i < contexts.length; i++) {
                        sb.append("GRAPH <" + contexts[i].stringValue() + "> {?s ?p " + ob.toString() + " .} ");
                    }
                    sb.append("}");
                }else{
                    sb.append("?s ?p "+ob.toString()+" }");
                }
            }else{
                sb.append("CONSTRUCT {?s ?p ?o} WHERE {");
                if(contexts.length !=0) {
                    for (int i = 0; i < contexts.length; i++) {
                        sb.append("GRAPH <" + contexts[i].stringValue() + "> {?s ?p " + ob.toString() + " .} ");
                    }
                    sb.append("}");
                }else{
                    sb.append("?s ?p "+ob.toString()+" }");
                }
            }

            GraphQuery query = prepareGraphQuery(sb.toString());
            setBindings(query, subject, predicate, object, contexts);
            query.evaluate(handler);
        }
        catch (MalformedQueryException e) {
            throw new RepositoryException(e);
        }
        catch (QueryEvaluationException e) {
            throw new RepositoryException(e);
        }
    }
    @Override
    public void export(RDFHandler handler, Resource... contexts) throws RepositoryException, RDFHandlerException {
        exportStatements(null, null, null, true,handler);
    }

    // number of triples in repository context (graph)
    @Override
    public long size(Resource... contexts)  {
        try {
            StringBuilder sb= new StringBuilder();
            if(contexts.length !=0){
                sb.append("SELECT * WHERE { ");

                for (int i = 0; i < contexts.length; i++)
                {
                    sb.append("GRAPH <"+ contexts[i].stringValue()+"> {?s ?p ?o .} ");
                }
                sb.append("}");
            }else {
                sb.append(EVERYTHING_WITH_GRAPH);
            }
            TupleQuery tupleQuery = prepareTupleQuery(sb.toString());
            //tupleQuery.setIncludeInferred(includeInferred);
            TupleQueryResult qRes = tupleQuery.evaluate();
            long i = 0;
            while (qRes.hasNext()) {
                qRes.next();
                i++;
            }
            return i;
        } catch (MalformedQueryException e) {
            e.printStackTrace();
        } catch (QueryEvaluationException e) {
            e.printStackTrace();
        } catch (RepositoryException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // remove context (graph)
    @Override
    public void clear(Resource... contexts) throws RepositoryException {
        if(contexts.length != 0){
            client.sendClear(contexts);
        }else{
            client.sendClearAll();
        }
    }

    // is repository empty
    @Override
    public boolean isEmpty() throws RepositoryException {
        return size() == 0;
    }

    //transactions
    @Override
    public boolean isActive() throws UnknownTransactionStateException, RepositoryException {
        return client.isActiveTransaction();
    }
    @Override
    public boolean isAutoCommit() throws RepositoryException {
        return client.isActiveTransaction() == false;
    }
    @Override
    public void setAutoCommit(boolean autoCommit) throws RepositoryException {
        try {
            client.setAutoCommit();
        } catch (MarkLogicTransactionException e) {
            e.printStackTrace();
        }
    }
    @Override
    public IsolationLevel getIsolationLevel() {
        return IsolationLevels.SNAPSHOT;
    }
    @Override
    public void setIsolationLevel(IsolationLevel level) throws IllegalStateException {
        if(level != IsolationLevels.SNAPSHOT){
         throw new IllegalStateException();
        }
    }
    @Override
    public void begin() throws RepositoryException {
        client.openTransaction();
    }
    @Override
    public void begin(IsolationLevel level) throws RepositoryException {
        setIsolationLevel(level);
        begin();
    }
    @Override
    public void commit() throws RepositoryException {
        client.commitTransaction();
    }
    @Override
    public void rollback() throws RepositoryException {
        client.rollbackTransaction();
    }

    // add
    @Override
    public void add(InputStream in, String baseURI, RDFFormat dataFormat, Resource... contexts) throws IOException, RDFParseException, RepositoryException {
        client.sendAdd(in, baseURI, dataFormat, contexts);
    }
    @Override
    public void add(File file, String baseURI, RDFFormat dataFormat, Resource... contexts) throws IOException, RDFParseException, RepositoryException {
        client.sendAdd(file, baseURI, dataFormat, contexts);
    }
    @Override
    public void add(Reader reader, String baseURI, RDFFormat dataFormat, Resource... contexts) throws IOException, RDFParseException, RepositoryException {
        client.sendAdd(reader,baseURI,dataFormat,contexts);
    }
    @Override
    public void add(URL url, String baseURI, RDFFormat dataFormat, Resource... contexts) throws IOException, RDFParseException, RepositoryException {
        InputStream in = new URL(url.toString()).openStream(); //TBD- naive impl, will need refactoring
        client.sendAdd(in,baseURI,dataFormat,contexts);
    }
    @Override
    public void add(Resource subject, URI predicate, Value object, Resource... contexts) throws RepositoryException {
        client.sendAdd(null,subject, predicate, object, contexts);
    }
    @Override
    public void add(Statement st, Resource... contexts) throws RepositoryException {
        client.sendAdd(null,st.getSubject(), st.getPredicate(), st.getObject(), mergeResource(st.getContext(), contexts));
    }
    @Override
    public void add(Iterable<? extends Statement> statements, Resource... contexts) throws RepositoryException {
        Iterator <? extends Statement> iter = statements.iterator();
        while(iter.hasNext()){
            Statement st = iter.next();
            client.sendAdd(null,st.getSubject(), st.getPredicate(), st.getObject(), mergeResource(st.getContext(),contexts));
        }
    }
    @Override
    public <E extends Exception> void add(Iteration<? extends Statement, E> statements, Resource... contexts) throws RepositoryException, E {
        while(statements.hasNext()){
            Statement st = statements.next();
            client.sendAdd(null,st.getSubject(), st.getPredicate(), st.getObject(), mergeResource(st.getContext(),contexts));
        }
    }

    // remove
    @Override
    public void remove(Resource subject, URI predicate, Value object, Resource... contexts) throws RepositoryException {
        client.sendRemove(null,subject,predicate,object,contexts);
    }
    @Override
    public void remove(Statement st, Resource... contexts) throws RepositoryException {
        client.sendRemove(null,st.getSubject(),st.getPredicate(),st.getObject(),mergeResource(st.getContext(),contexts));
    }
    @Override
    public void remove(Iterable<? extends Statement> statements, Resource... contexts) throws RepositoryException {
        Iterator <? extends Statement> iter = statements.iterator();
        while(iter.hasNext()){
            Statement st = iter.next();
            client.sendRemove(null, st.getSubject(), st.getPredicate(), st.getObject(), mergeResource(st.getContext(),contexts));
        }
    }
    @Override
    public <E extends Exception> void remove(Iteration<? extends Statement, E> statements, Resource... contexts) throws RepositoryException, E {
        while(statements.hasNext()){
            Statement st = statements.next();
            client.sendRemove(null, st.getSubject(), st.getPredicate(), st.getObject(), mergeResource(st.getContext(),contexts));
        }
    }

    // without commit
    @Override
    protected void addWithoutCommit(Resource subject, URI predicate, Value object, Resource... contexts) throws RepositoryException {
        add(subject, predicate, object, contexts);
    }
    @Override
    protected void removeWithoutCommit(Resource subject, URI predicate, Value object, Resource... contexts) throws RepositoryException {
        remove(subject, predicate, object, contexts);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // not in scope for 1.0.0 /////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public RepositoryResult<Namespace> getNamespaces() throws RepositoryException {
        return null;
    }

    @Override
    public String getNamespace(String prefix) throws RepositoryException {
        return null;
    }
    @Override
    public void setNamespace(String prefix, String name) throws RepositoryException {
    }
    @Override
    public void removeNamespace(String prefix) throws RepositoryException {
    }
    @Override
    public void clearNamespaces() throws RepositoryException {
    }
    ///////////////////////////////////////////////////////////////////////////////////////////////



    ///////////////////////////////////////////////////////////////////////////////////////////////
    // private ////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////

    private void setBindings(Query query, Resource subj, URI pred, Value obj, Resource... contexts)
            throws RepositoryException {
        if (subj != null) {
            query.setBinding("s", subj);
        }
        if (pred != null) {
            query.setBinding("p", pred);
        }
        if (obj != null) {

            Value o;
            if (obj instanceof Literal) {
                Literal lit = (Literal)obj;
                o = getValueFactory().createLiteral(lit.stringValue(),lit.getDatatype().toString());
                query.setBinding("o", o);
            }else{
                query.setBinding("o", obj);
            }
        }
        if (contexts != null && contexts.length > 0) {
            DatasetImpl dataset = new DatasetImpl();
            for (Resource ctx : contexts) {
                if (ctx == null || ctx instanceof URI) {
                    dataset.addDefaultGraph((URI) ctx);
                } else {
                    throw new RepositoryException("Contexts must be URIs");
                }
            }
            query.setDataset(dataset);
        }
    }

    private boolean isQuadMode() {
        return quadMode;
    }

    private static Resource[] mergeResource(Resource o, Resource... arr) {
        if(o != null) {
            Resource[] newArray = new Resource[arr.length + 1];
            newArray[0] = o;
            System.arraycopy(arr, 0, newArray, 1, arr.length);
            return newArray;
        }else{
            return arr;
        }

    }

    private Iteration<Statement, QueryEvaluationException> toStatementIteration(TupleQueryResult iter, final Resource subj, final URI pred, final Value obj) {
        return new ConvertingIteration<BindingSet, Statement, QueryEvaluationException>(iter) {
            @Override
            protected Statement convert(BindingSet b) throws QueryEvaluationException {
                Resource s = subj == null ? (Resource) b.getValue("s") : subj;
                URI p = pred == null ? getValueFactory().createURI(b.getValue("p").stringValue()) : pred;
                Value o;
                if (obj instanceof Literal) {
                    Literal lit = (Literal)b.getValue("o");
                    o = getValueFactory().createLiteral(lit.stringValue(),lit.getDatatype().toString());
                }else{
                    o = b.getValue("o");
                }
                Resource ctx = (Resource) b.getValue("ctx");

                return getValueFactory().createStatement(s, p, o, ctx);
            }
        };
    }
    ///////////////////////////////////////////////////////////////////////////////////////////////

}