/*
 * Copyright 2018-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.stitch.core.services.mongodb.sync.internal;

import com.mongodb.stitch.core.services.mongodb.remote.RemoteDeleteResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteInsertOneResult;
import com.mongodb.stitch.core.services.mongodb.remote.RemoteUpdateResult;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteFindIterable;
import com.mongodb.stitch.core.services.mongodb.remote.internal.CoreRemoteMongoCollection;
import com.mongodb.stitch.core.services.mongodb.sync.ChangeEventListener;
import com.mongodb.stitch.core.services.mongodb.sync.DocumentSynchronizationConfig;
import com.mongodb.stitch.core.services.mongodb.sync.SyncConflictResolver;
import java.util.Set;
import javax.annotation.Nullable;
import org.bson.BsonValue;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

// TODO: Seamless offline CRUD parity.
public interface CoreSyncMongoCollection<DocumentT> extends CoreRemoteMongoCollection<DocumentT> {

  /**
   * Create a new CoreSyncMongoCollection instance with a different default class to cast any
   * documents returned from the database into.
   *
   * @param clazz the default class to cast any documents returned from the database into.
   * @param <NewDocumentT> the type that the new collection will encode documents from and decode
   *                      documents to.
   * @return a new CoreSyncMongoCollection instance with the different default class
   */
  <NewDocumentT> CoreSyncMongoCollection<NewDocumentT> withDocumentClass(
      final Class<NewDocumentT> clazz);

  /**
   * Create a new CoreSyncMongoCollection instance with a different codec registry.
   *
   * @param codecRegistry the new {@link org.bson.codecs.configuration.CodecRegistry} for the
   *                      collection.
   * @return a new CoreSyncMongoCollection instance with the different codec registry
   */
  CoreSyncMongoCollection<DocumentT> withCodecRegistry(final CodecRegistry codecRegistry);

  /**
   * Requests that the given document _id be synchronized.
   * @param documentId the document _id to synchronize.
   * @param conflictResolver the conflict resolver to invoke when a conflict happens between local
   *                         and remote events.
   */
  void sync(final BsonValue documentId, final SyncConflictResolver<DocumentT> conflictResolver);

  /**
   * Requests that the given document _id be synchronized.
   * @param documentId the document _id to synchronize.
   * @param conflictResolver the conflict resolver to invoke when a conflict happens between local
   *                         and remote events.
   * @param eventListener the event listener to invoke when a a change event happens for the
   *                      document.
   */
  void sync(
      final BsonValue documentId,
      final SyncConflictResolver<DocumentT> conflictResolver,
      final ChangeEventListener<DocumentT> eventListener);

  /**
   * Returns the set of synchronized documents in a namespace.
   *
   * @return the set of synchronized documents in a namespace.
   */
  Set<DocumentSynchronizationConfig> getSynchronizedDocuments();

  /**
   * Stops synchronizing the given document _id. Any uncommitted writes will be lost.
   *
   * @param documentId the _id of the document to desynchronize.
   */
  void desync(final BsonValue documentId);

  /**
   * Finds all documents in the collection.
   *
   * @return the find iterable interface
   */
  CoreRemoteFindIterable<DocumentT> find();

  /**
   * Finds all documents in the collection.
   *
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  <ResultT> CoreRemoteFindIterable<ResultT> find(final Class<ResultT> resultClass);

  /**
   * Finds all documents in the collection.
   *
   * @param filter the query filter
   * @return the find iterable interface
   */
  CoreRemoteFindIterable<DocumentT> find(final Bson filter);

  /**
   * Finds all documents in the collection.
   *
   * @param filter      the query filter
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the find iterable interface
   */
  <ResultT> CoreRemoteFindIterable<ResultT> find(
      final Bson filter,
      final Class<ResultT> resultClass
  );

  /**
   * Finds a single document by the given id. It is first searched for in the local synchronized
   * cache and if not found and there is internet connectivity, it is searched for remotely.
   *
   * @param documentId the _id of the document to search for.
   * @return the document if found locally or remotely.
   */
  @Nullable
  DocumentT findOneById(final BsonValue documentId);

  /**
   * Finds a single document by the given id. It is first searched for in the local synchronized
   * cache and if not found and there is internet connectivity, it is searched for remotely.
   *
   * @param documentId the _id of the document to search for.
   * @param resultClass the class to decode each document into
   * @param <ResultT>   the target document type of the iterable.
   * @return the document if found locally or remotely.
   */
  @Nullable
  <ResultT> ResultT findOneById(final BsonValue documentId, final Class<ResultT> resultClass);

  /**
   * Updates a document by the given id. It is first searched for in the local synchronized cache
   * and if not found and there is internet connectivity, it is searched for remotely.
   *
   * @param documentId the _id of the document to search for.
   * @param update the update specifier.
   * @return the result of the local or remote update.
   */
  RemoteUpdateResult updateOneById(
      final BsonValue documentId, final Bson update);

  /**
   * Inserts a single document and begins to synchronize it.
   *
   * @param document the document to insert and synchronize.
   * @param conflictResolver the conflict resolver to invoke when a conflict happens between local
   *                         and remote events.
   * @return the result of the insertion.
   */
  RemoteInsertOneResult insertOneAndSync(
      final DocumentT document, final SyncConflictResolver<DocumentT> conflictResolver);

  /**
   * Inserts a single document and begins to synchronize it.
   *
   * @param document the document to insert and synchronize.
   * @param conflictResolver the conflict resolver to invoke when a conflict happens between local
   *                         and remote events.
   * @param eventListener the event listener to invoke when a a change event happens for the
   *                      document.
   * @return the result of the insertion.
   */
  RemoteInsertOneResult insertOneAndSync(
      final DocumentT document,
      final SyncConflictResolver<DocumentT> conflictResolver,
      final ChangeEventListener<DocumentT> eventListener);

  /**
   * Deletes a single document by the given id. It is first searched for in the local synchronized
   * cache and if not found and there is internet connectivity, it is searched for remotely.
   *
   * @param documentId the _id of the document to search for.
   * @return the result of the local or remote update.
   */
  RemoteDeleteResult deleteOneById(final BsonValue documentId);
}
