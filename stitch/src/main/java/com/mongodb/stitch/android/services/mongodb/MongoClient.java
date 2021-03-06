package com.mongodb.stitch.android.services.mongodb;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.mongodb.stitch.android.StitchClient;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * MongoClient provides a simple wrapper around pipelines to enable CRUD usage of
 * a MongoDB service.
 */
public class MongoClient {
    private static final String TAG = "Stitch-MongoDB";

    private final StitchClient _stitchClient;
    private final String _service;

    /**
     * @param stitchClient The client to execute with.
     * @param service    The name of the MongoDB service.
     */
    public MongoClient(final StitchClient stitchClient, final String service) {
        _stitchClient = stitchClient;
        _service = service;
    }

    /**
     * Gets a database.
     *
     * @param name The name of the database.
     * @return A reference to the database.
     */
    public Database getDatabase(final String name) {
        return new Database(this, name);
    }

    /**
     * Database represents a reference to a MongoDB database accessed through Stitch.
     */
    public static class Database {
        private final MongoClient _client;
        private final String _dbName;

        /**
         * @param client The client to which this database is referenced by.
         * @param dbName The name of the database.
         */
        public Database(final MongoClient client, final String dbName) {
            _client = client;
            _dbName = dbName;
        }

        /**
         * Gets a collection in this database.
         *
         * @param name The name of the collection.
         * @return A reference to the collection.
         */
        public Collection getCollection(final String name) {
            return new Collection(this, name);
        }
    }

    /**
     * Collection represents a reference to a MongoDB collection accessed through Stitch.
     */
    public static class Collection {
        private final Database _database;
        private final String _collName;

        /**
         * @param database The database to which this collection is contained in.
         * @param collName The name of the collection.
         */
        public Collection(final Database database, final String collName) {
            _database = database;
            _collName = collName;
        }

        /**
         * Finds documents matching a query up to the specified limit.
         *
         * @param query      The query specifier.
         * @param limit      The maximum amount of matching documents to accept.
         * @return A task containing the matched documents that can be resolved upon completion
         * of the request.
         */
        public Task<List<Document>> find(final Document query, final Integer limit) {
            return find(query, null, limit);
        }

        /**
         * Finds and projects documents matching a query up to the specified limit.
         *
         * @param query      The query specifier.
         * @param projection The projection document.
         * @param limit      The maximum amount of matching documents to accept.
         * @return A task containing the matched and projected documents that can be resolved upon completion
         * of the request.
         */
        public Task<List<Document>> find(final Document query, final Document projection, final Integer limit) {
            Document doc = new Document(Parameters.QUERY, query);
            doc.put(Parameters.DATABASE, _database._dbName);
            doc.put(Parameters.COLLECTION, _collName);
            doc.put(Parameters.LIMIT, limit);

            if (projection != null) {
                doc.put(Parameters.PROJECT, projection);
            }

            return _database._client._stitchClient.executeServiceFunction(
                "find", _database._client._service, doc
            ).continueWith(new Continuation<Object, List<Document>>() {
                @Override
                public List<Document> then(@NonNull Task<Object> task) throws Exception {
                    if (task.isSuccessful()) {
                        final List<Object> objects = (List<Object>) task.getResult();
                        final List<Document> docs = new ArrayList<>(objects.size());
                        for (final Object obj : objects) {
                            docs.add((Document) obj);
                        }
                        return docs;
                    } else {
                        Log.e(TAG, "Error while executing function", task.getException());
                        throw task.getException();
                    }
                }
            });
        }

        /**
         * Counts the number of documents matching a query up to the specified limit.
         *
         * @param query      The query specifier.
         * @return A task containing the number of matched documents that can be resolved upon completion
         * of the request.
         */
        public Task<Long> count(final Document query) {
            return count(query, null);
        }

        /**
         * Counts the number of documents matching a query up to the specified limit.
         *
         * @param query      The query specifier.
         * @return A task containing the number of matched documents that can be resolved upon completion
         * of the request.
         */
        public Task<Long> count(final Document query, final Document projection) {
            Document doc = new Document(Parameters.QUERY, query);
            doc.put(Parameters.DATABASE, _database._dbName);
            doc.put(Parameters.COLLECTION, _collName);

            if (projection != null) {
                doc.put(Parameters.PROJECT, projection);
            }

            return _database._client._stitchClient.executeServiceFunction(
                    "count", _database._client._service, doc
            ).continueWith(new Continuation<Object, Long>() {
                @Override
                public Long then(@NonNull Task<Object> task) throws Exception {
                    if (task.isSuccessful()) {
                        final Object result = task.getResult();
                        if (result instanceof Integer) {
                            return Long.valueOf((Integer) result);
                        }
                        return (Long) result;
                    } else {
                        Log.e(TAG, "Error while executing function", task.getException());
                        throw task.getException();
                    }
                }
            });
        }

        /**
         * Updates a single document matching a query
         *
         * @param query  The query specifier.
         * @param update The update specifier.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Document> updateOne(final Document query, final Document update) {
            return updateOne(query, update, false);
        }

        /**
         * Updates a single document matching the query specifier.
         *
         * @param query  The query specifier.
         * @param update The update specifier.
         * @param upsert Whether or not to upsert if the query matches no documents.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Document> updateOne(final Document query,
                                        final Document update,
                                        final boolean upsert) {
            Document doc = new Document(Parameters.QUERY, query);
            doc.put(Parameters.DATABASE, _database._dbName);
            doc.put(Parameters.COLLECTION, _collName);
            doc.put(Parameters.UPDATE, update);
            doc.put(Parameters.UPSERT, upsert);

            return _database._client._stitchClient.executeServiceFunction(
                    "updateOne", _database._client._service, doc
            ).continueWith(new Continuation<Object, Document>() {
                @Override
                public Document then(@NonNull Task<Object> task) throws Exception {
                    if (task.isSuccessful()) {
                        return (Document) task.getResult();
                    } else {
                        Log.e(TAG, "Error while executing function", task.getException());
                        throw task.getException();
                    }
                }
            });
        }

        /**
         * Updates many documents matching a query specifier.
         *
         * @param query  The query specifier.
         * @param update The update specifier.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Document> updateMany(final Document query, final Document update) {
            return updateMany(query, update, false);
        }

        /**
         * Updates many documents matching a query specifier.
         *
         * @param query  The query specifier.
         * @param update The update specifier.
         * @param upsert Whether or not to upsert if the query matches no documents.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Document> updateMany(final Document query,
                                         final Document update,
                                         final boolean upsert) {
            Document doc = new Document(Parameters.QUERY, query);
            doc.put(Parameters.DATABASE, _database._dbName);
            doc.put(Parameters.COLLECTION, _collName);
            doc.put(Parameters.UPDATE, update);
            doc.put(Parameters.UPSERT, upsert);
            doc.put(Parameters.MULTI, true);

            return _database._client._stitchClient.executeServiceFunction(
                    "updateMany", _database._client._service, doc
            ).continueWith(new Continuation<Object, Document>() {
                @Override
                public Document then(@NonNull Task<Object> task) throws Exception {
                    if (task.isSuccessful()) {
                        return (Document) task.getResult();
                    } else {
                        Log.e(TAG, "Error while executing function", task.getException());
                        throw task.getException();
                    }
                }
            });
        }

        /**
         * Inserts a single document.
         *
         * @param document The document to insert.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Document> insertOne(final Document document) {
            final Document doc = new Document("document", document);
            doc.put(Parameters.DATABASE, _database._dbName);
            doc.put(Parameters.COLLECTION, _collName);

            return _database._client._stitchClient.executeServiceFunction(
                    "insertOne", _database._client._service, doc
            ).continueWith(new Continuation<Object, Document>() {
                @Override
                public Document then(@NonNull Task<Object> task) throws Exception {
                    if (task.isSuccessful()) {
                        return (Document) task.getResult();
                    } else {
                        Log.e(TAG, "Error while executing function", task.getException());
                        throw task.getException();
                    }
                }
            });
        }

        /**
         * Inserts many documents.
         *
         * @param documents The list of documents to insert.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Document> insertMany(final List<Document> documents) {
            Document doc = new Document("documents", documents);
            doc.put(Parameters.DATABASE, _database._dbName);
            doc.put(Parameters.COLLECTION, _collName);

            return _database._client._stitchClient.executeServiceFunction(
                    "insertMany", _database._client._service, doc
            ).continueWith(new Continuation<Object, Document>() {
                @Override
                public Document then(@NonNull Task<Object> task) throws Exception {
                    if (task.isSuccessful()) {
                        return (Document) task.getResult();
                    } else {
                        Log.e(TAG, "Error while executing function", task.getException());
                        throw task.getException();
                    }
                }
            });
        }

        /**
         * Deletes a single document matching a query specifier.
         *
         * @param query The query specifier.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Document> deleteOne(final Document query) {
            Document doc = new Document(Parameters.QUERY, query);
            doc.put(Parameters.DATABASE, _database._dbName);
            doc.put(Parameters.SINGLE_DOCUMENT, true);
            doc.put(Parameters.COLLECTION, _collName);

            return _database._client._stitchClient.executeServiceFunction(
                    "deleteOne", _database._client._service, doc
            ).continueWith(new Continuation<Object, Document>() {
                @Override
                public Document then(@NonNull Task<Object> task) throws Exception {
                    if (task.isSuccessful()) {
                        return (Document) task.getResult();
                    } else {
                        Log.e(TAG, "Error while executing function", task.getException());
                        throw task.getException();
                    }
                }
            });
        }

        /**
         * Deletes many document matching a query specifier.
         *
         * @param query The query specifier.
         * @return A task that can be resolved upon completion of the request.
         */
        public Task<Document> deleteMany(final Document query) {
            final Document doc = new Document(Parameters.QUERY, query);
            doc.put(Parameters.DATABASE, _database._dbName);
            doc.put(Parameters.COLLECTION, _collName);
            doc.put(Parameters.SINGLE_DOCUMENT, false);

            return _database._client._stitchClient.executeServiceFunction(
                    "deleteMany", _database._client._service, doc
            ).continueWith(new Continuation<Object, Document>() {
                @Override
                public Document then(@NonNull Task<Object> task) throws Exception {
                    if (task.isSuccessful()) {
                        return (Document) task.getResult();
                    } else {
                        Log.e(TAG, "Error while executing function", task.getException());
                        throw task.getException();
                    }
                }
            });
        }

        private static class Parameters {
            private static final String DATABASE = "database";
            private static final String COLLECTION = "collection";
            private static final String QUERY = "query";
            private static final String UPDATE = "update";
            private static final String UPSERT = "upsert";
            private static final String MULTI = "multi";
            private static final String PROJECT = "project";
            private static final String SINGLE_DOCUMENT = "singleDoc";
            private static final String LIMIT = "limit";
        }
    }
}
