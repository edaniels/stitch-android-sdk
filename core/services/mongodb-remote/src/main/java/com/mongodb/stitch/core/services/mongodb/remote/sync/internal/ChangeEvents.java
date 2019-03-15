/*
 * Copyright 2018-present MongoDB, Inc.
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

package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import static com.mongodb.stitch.core.services.mongodb.remote.sync.internal.DataSynchronizer.sanitizeDocument;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent;
import com.mongodb.stitch.core.services.mongodb.remote.OperationType;
import com.mongodb.stitch.core.services.mongodb.remote.UpdateDescription;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.RawBsonDocument;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;

public final class ChangeEvents {
  /**
   * Generates a change event for a local insert of the given document in the given namespace.
   *
   * @param namespace the namespace where the document was inserted.
   * @param document the document that was inserted.
   * @return a change event for a local insert of the given document in the given namespace.
   */
  static ChangeEvent<RawBsonDocument> changeEventForLocalInsert(
      final MongoNamespace namespace,
      final RawBsonDocument document,
      final boolean writePending
  ) {
    final BsonValue docId = BsonUtils.getDocumentId(document);
    return new ChangeEvent<>(
        new BsonDocument(),
        OperationType.INSERT,
        document,
        namespace,
        new BsonDocument("_id", docId),
        null,
        writePending);
  }

  /**
   * Generates a change event for a local update of a document in the given namespace referring
   * to the given document _id.
   *
   * @param namespace the namespace where the document was inserted.
   * @param documentId the _id of the document that was updated.
   * @param update the update specifier.
   * @return a change event for a local update of a document in the given namespace referring
   *         to the given document _id.
   */
  static ChangeEvent<RawBsonDocument> changeEventForLocalUpdate(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final UpdateDescription update,
      final RawBsonDocument fullDocumentAfterUpdate,
      final boolean writePending
  ) {
    return new ChangeEvent<>(
        new BsonDocument(),
        OperationType.UPDATE,
        fullDocumentAfterUpdate,
        namespace,
        new BsonDocument("_id", documentId),
        update,
        writePending);
  }

  /**
   * Generates a change event for a local replacement of a document in the given namespace referring
   * to the given document _id.
   *
   * @param namespace the namespace where the document was inserted.
   * @param documentId the _id of the document that was updated.
   * @param document the replacement document.
   * @return a change event for a local replacement of a document in the given namespace referring
   *         to the given document _id.
   */
  static ChangeEvent<RawBsonDocument> changeEventForLocalReplace(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final RawBsonDocument document,
      final boolean writePending
  ) {
    return new ChangeEvent<>(
        new BsonDocument(),
        OperationType.REPLACE,
        document,
        namespace,
        new BsonDocument("_id", documentId),
        null,
        writePending);
  }

  /**
   * Generates a change event for a local deletion of a document in the given namespace referring
   * to the given document _id.
   *
   * @param namespace the namespace where the document was inserted.
   * @param documentId the _id of the document that was updated.
   * @return a change event for a local deletion of a document in the given namespace referring
   *         to the given document _id.
   */
  static ChangeEvent<RawBsonDocument> changeEventForLocalDelete(
      final MongoNamespace namespace,
      final BsonValue documentId,
      final boolean writePending
  ) {
    return new ChangeEvent<>(
        new BsonDocument(),
        OperationType.DELETE,
        null,
        namespace,
        new BsonDocument("_id", documentId),
        null,
        writePending);
  }

  /**
   * Transforms a {@link ChangeEvent} into one that can be used by a user defined conflict resolver.
   * @param event the event to transform.
   * @param codec the codec to use to transform any documents specific to the collection.
   * @return the transformed {@link ChangeEvent}
   */
  static ChangeEvent transformChangeEventForUser(
      final ChangeEvent<RawBsonDocument> event,
      final Codec codec
  ) {
    return new ChangeEvent<>(
        event.getId(),
        event.getOperationType(),
        event.getFullDocument() == null ? null : codec.decode(
            sanitizeDocument(event.getFullDocument()).asBsonReader(),
            DecoderContext.builder().build()),
        event.getNamespace(),
        event.getDocumentKey(),
        event.getUpdateDescription(),
        event.hasUncommittedWrites());
  }
}
