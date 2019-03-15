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

package com.mongodb.stitch.core.services.mongodb.remote.sync.internal;

import com.mongodb.MongoNamespace;
import com.mongodb.stitch.core.internal.common.AuthMonitor;
import com.mongodb.stitch.core.internal.common.Callback;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.RawBsonDocument;


final class InstanceChangeStreamListenerImpl implements InstanceChangeStreamListener {

  private final Map<MongoNamespace, NamespaceChangeStreamListener> nsStreamers;
  private final ConcurrentMap<MongoNamespace, ReadWriteLock> nsListenerLocks;
  private final ReadWriteLock instanceLock;
  private final InstanceSynchronizationConfig instanceConfig;
  private final CoreStitchServiceClient service;
  private final NetworkMonitor networkMonitor;
  private final AuthMonitor authMonitor;

  InstanceChangeStreamListenerImpl(
      final InstanceSynchronizationConfig instanceConfig,
      final CoreStitchServiceClient service,
      final NetworkMonitor networkMonitor,
      final AuthMonitor authMonitor
  ) {
    this.instanceConfig = instanceConfig;
    this.service = service;
    this.networkMonitor = networkMonitor;
    this.authMonitor = authMonitor;
    this.nsStreamers = new HashMap<>();
    this.nsListenerLocks = new ConcurrentHashMap<>();
    this.instanceLock = new ReentrantReadWriteLock();
  }

  public void start(final MongoNamespace namespace) {
    instanceLock.writeLock().lock();
    try {
      if (nsStreamers.containsKey(namespace)) {
        nsStreamers.get(namespace).start();
      }
    } finally {
      instanceLock.writeLock().unlock();
    }
  }

  /**
   * Starts all streams.
   */
  public void start() {
    instanceLock.writeLock().lock();
    try {
      for (final Map.Entry<MongoNamespace, NamespaceChangeStreamListener> streamerEntry :
          nsStreamers.entrySet()) {
        streamerEntry.getValue().start();
      }
    } finally {
      instanceLock.writeLock().unlock();
    }
  }

  public void stop(final MongoNamespace namespace) {
    instanceLock.writeLock().lock();
    try {
      if (nsStreamers.containsKey(namespace)) {
        nsStreamers.get(namespace).stop();
      }
    } finally {
      instanceLock.writeLock().unlock();
    }
  }

  /**
   * Stops all streams.
   */
  public void stop() {
    instanceLock.writeLock().lock();
    try {
      for (final NamespaceChangeStreamListener streamer : nsStreamers.values()) {
        streamer.stop();
      }
    } finally {
      instanceLock.writeLock().unlock();
    }
  }

  public boolean isOpen(final MongoNamespace namespace) {
    instanceLock.writeLock().lock();
    try {
      if (nsStreamers.containsKey(namespace)) {
        return nsStreamers.get(namespace).isOpen();
      }
    } finally {
      instanceLock.writeLock().unlock();
    }
    return false;
  }

  public boolean areAllStreamsOpen() {
    instanceLock.writeLock().lock();
    try {
      for (final NamespaceChangeStreamListener streamer : nsStreamers.values()) {
        if (!streamer.isOpen()) {
          return false;
        }
      }
    } finally {
      instanceLock.writeLock().unlock();
    }
    return true;
  }

  @Override
  public void addWatcher(final MongoNamespace namespace,
                         final Callback<ChangeEvent<RawBsonDocument>, Object> watcher) {
    if (nsStreamers.containsKey(namespace)) {
      nsStreamers.get(namespace).addWatcher(watcher);
    }
  }

  @Override
  public void removeWatcher(final MongoNamespace namespace,
                            final Callback<ChangeEvent<RawBsonDocument>, Object> watcher) {
    if (nsStreamers.containsKey(namespace)) {
      nsStreamers.get(namespace).removeWatcher(watcher);
    }
  }

  /**
   * Requests that the given namespace be started listening to for change events.
   *
   * @param namespace the namespace to listen for change events on.
   */
  public void addNamespace(final MongoNamespace namespace) {
    this.instanceLock.writeLock().lock();
    try {
      if (this.nsStreamers.containsKey(namespace)) {
        return;
      }
      final NamespaceChangeStreamListener streamer =
          new NamespaceChangeStreamListener(
              namespace,
              instanceConfig.getNamespaceConfig(namespace),
              service,
              networkMonitor,
              authMonitor,
              getLockForNamespace(namespace));
      this.nsStreamers.put(namespace, streamer);
    } finally {
      this.instanceLock.writeLock().unlock();
    }
  }

  /**
   * Requests that the given namespace stopped being listened to for change events.
   *
   * @param namespace the namespace to stop listening for change events on.
   */
  @Override
  public void removeNamespace(final MongoNamespace namespace) {
    this.instanceLock.writeLock().lock();
    try {
      if (!this.nsStreamers.containsKey(namespace)) {
        return;
      }
      final NamespaceChangeStreamListener streamer = this.nsStreamers.get(namespace);
      streamer.stop();
      this.nsStreamers.remove(namespace);
    } finally {
      this.instanceLock.writeLock().unlock();
    }
  }

  /**
   * Returns the latest change events for a given namespace.
   *
   * @param namespace the namespace to get events for.
   * @return the latest change events for a given namespace.
   */
  public Map<BsonValue, ChangeEvent<RawBsonDocument>> getEventsForNamespace(
      final MongoNamespace namespace
  ) {
    this.instanceLock.readLock().lock();
    final NamespaceChangeStreamListener streamer;
    try {
      streamer = nsStreamers.get(namespace);
    } finally {
      this.instanceLock.readLock().unlock();
    }
    if (streamer == null) {
      return new HashMap<>();
    }
    return streamer.getEvents();
  }

  public @Nonnull ReadWriteLock getLockForNamespace(final MongoNamespace namespace) {
    this.instanceLock.readLock().lock();
    ReadWriteLock streamerLock;
    try {
      streamerLock = nsListenerLocks.get(namespace);
      if (streamerLock == null) {
        streamerLock = new ReentrantReadWriteLock();
        nsListenerLocks.put(namespace, streamerLock);
      }

      return streamerLock;
    } finally {
      this.instanceLock.readLock().unlock();
    }
  }

  /**
   * If there is an unprocessed change event for a particular document ID, fetch it from the
   * appropriate namespace change stream listener, and remove it. By reading the event here, we are
   * assuming it will be processed by the consumer.
   *
   * @return the latest unprocessed change event for the given document ID and namespace, or null
   *         if none exists.
   */
  public @Nullable ChangeEvent<RawBsonDocument> getUnprocessedEventForDocumentId(
          final MongoNamespace namespace,
          final BsonValue documentId
  ) {
    this.instanceLock.readLock().lock();
    final NamespaceChangeStreamListener streamer;
    try {
      streamer = nsStreamers.get(namespace);
    } finally {
      this.instanceLock.readLock().unlock();
    }

    if (streamer == null) {
      return null;
    }

    return streamer.getUnprocessedEventForDocumentId(documentId);
  }
}
