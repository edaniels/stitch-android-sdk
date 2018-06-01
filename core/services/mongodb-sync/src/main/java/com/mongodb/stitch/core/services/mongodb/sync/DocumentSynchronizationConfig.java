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

package com.mongodb.stitch.core.services.mongodb.sync;

import javax.annotation.Nullable;
import org.bson.BsonValue;

/**
 * The synchronization configuration for a document.
 */
public interface DocumentSynchronizationConfig {

  /**
   * Returns the _id of the document being synchronized.
   *
   * @return the _id of the document being synchronized.
   */
  BsonValue getDocumentId();

  /**
   * Returns the conflict resolver for this document, if any.
   *
   * @return the conflict resolver for this document.
   */
  @Nullable
  SyncConflictResolver getConflictResolver();

  /**
   * Returns whether or not this document has pending writes that have not yet been committed
   * remotely.
   *
   * @return whether or not this document has pending writes that have not yet been committed
   *         remotely.
   */
  boolean hasPendingWrites();
}
