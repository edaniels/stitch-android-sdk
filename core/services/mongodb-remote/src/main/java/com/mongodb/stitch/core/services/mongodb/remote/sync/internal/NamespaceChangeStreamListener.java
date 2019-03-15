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
import com.mongodb.stitch.core.internal.common.BsonUtils;
import com.mongodb.stitch.core.internal.common.Callback;
import com.mongodb.stitch.core.internal.common.OperationResult;
import com.mongodb.stitch.core.internal.net.NetworkMonitor;
import com.mongodb.stitch.core.internal.net.StitchEvent;
import com.mongodb.stitch.core.internal.net.Stream;
import com.mongodb.stitch.core.services.internal.CoreStitchServiceClient;
import com.mongodb.stitch.core.services.mongodb.remote.ChangeEvent;
import com.mongodb.stitch.core.services.mongodb.remote.internal.ResultDecoders;

import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;

import javax.annotation.Nullable;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.RawBsonDocumentCodec;
import org.bson.diagnostics.Logger;
import org.bson.diagnostics.Loggers;

public class NamespaceChangeStreamListener implements Closeable {
  private static final Codec<RawBsonDocument> BSON_DOCUMENT_CODEC = new RawBsonDocumentCodec();

  private final MongoNamespace namespace;
  private final NamespaceSynchronizationConfig nsConfig;
  private final CoreStitchServiceClient service;
  private final NetworkMonitor networkMonitor;
  private final AuthMonitor authMonitor;
  private final Logger logger;
  private final Map<BsonValue, ChangeEvent<RawBsonDocument>> events;
  private Thread runnerThread;
  private ReadWriteLock nsLock;
  private final Set<Callback<ChangeEvent<RawBsonDocument>, Object>> watchers;
  private Stream<ChangeEvent<RawBsonDocument>> currentStream;

  NamespaceChangeStreamListener(
      final MongoNamespace namespace,
      final NamespaceSynchronizationConfig nsConfig,
      final CoreStitchServiceClient service,
      final NetworkMonitor networkMonitor,
      final AuthMonitor authMonitor,
      final ReadWriteLock nsLock
  ) {
    this.namespace = namespace;
    this.nsConfig = nsConfig;
    this.service = service;
    this.networkMonitor = networkMonitor;
    this.authMonitor = authMonitor;
    this.events = new HashMap<>();
    this.nsLock = nsLock;
    this.logger =
        Loggers.getLogger(
            String.format("NamespaceChangeStreamListener-%s", namespace.toString()));
    this.watchers = new HashSet<>();
  }

  /**
   * Opens the stream in a background thread.
   */
  public void start() {
    nsLock.writeLock().lock();
    try {
      if (runnerThread != null) {
        return;
      }
      runnerThread =
          new Thread(new NamespaceChangeStreamRunner(
              new WeakReference<>(this), networkMonitor, logger));
      runnerThread.start();
    } finally {
      nsLock.writeLock().unlock();
    }
  }

  /**
   * Stops the background stream thread.
   */
  public void stop() {
    if (runnerThread == null) {
      return;
    }

    runnerThread.interrupt();

    nsLock.writeLock().lock();
    try {
      if (runnerThread == null) {
        return;
      }

      this.cancel();
      this.close();

      while (runnerThread.isAlive()) {
        runnerThread.interrupt();
        try {
          runnerThread.join(1000);
        } catch (final Exception e) {
          e.printStackTrace();
          return;
        }
      }
      runnerThread = null;
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      nsLock.writeLock().unlock();
    }
  }

  void addWatcher(final Callback<ChangeEvent<RawBsonDocument>, Object> callback) {
    watchers.add(callback);
  }

  void removeWatcher(final Callback<ChangeEvent<RawBsonDocument>, Object> callback) {
    watchers.remove(callback);
  }

  private void clearWatchers() {
    for (final Callback<ChangeEvent<RawBsonDocument>, Object> watcher : watchers) {
      watcher.onComplete(
          OperationResult.failedResultOf(null));
    }
    watchers.clear();
  }

  private void cancel() {
    if (currentStream != null) {
      currentStream.cancel();
    }
  }

  @Override
  public void close() {
    if (currentStream != null) {
      try {
        currentStream.close();
      } catch (final IOException e) {
        e.printStackTrace();
      } finally {
        currentStream = null;
      }
    }

    clearWatchers();
  }

  /**
   * Whether or not the current stream is currently open.
   *
   * @return true if open, false if not
   */
  public synchronized boolean isOpen() {
    return currentStream != null && currentStream.isOpen();
  }

  /**
   * Open the event stream
   *
   * @return true if successfully opened, false if not
   */
  boolean openStream() throws InterruptedException, IOException {
    logger.info("stream START");
    final boolean isOpen;
    final Set<BsonValue> idsToWatch = nsConfig.getSynchronizedDocumentIds();

    if (!networkMonitor.isConnected()) {
      logger.info("stream END - Network disconnected");
      return false;
    }

    if (idsToWatch.isEmpty()) {
      logger.info("stream END - No synchronized documents");
      return false;
    }

    nsLock.writeLock().lockInterruptibly();
    try {
      if (!authMonitor.isLoggedIn()) {
        logger.info("stream END - Logged out");
        return false;
      }

      final Document args = new Document();
      args.put("database", namespace.getDatabaseName());
      args.put("collection", namespace.getCollectionName());
      args.put("ids", idsToWatch);

      currentStream =
        service.streamFunction(
            "watch",
            Collections.singletonList(args),
            ResultDecoders.changeEventDecoder(BSON_DOCUMENT_CODEC));

      if (currentStream != null && currentStream.isOpen()) {
        this.nsConfig.setStale(true);
        isOpen = true;
      } else {
        isOpen = false;
      }
    } finally {
      nsLock.writeLock().unlock();
    }
    return isOpen;
  }

  /**
   * Read and store the next event from an open stream. This is a blocking method.
   */
  void storeNextEvent() {
    try {
      if (currentStream != null && currentStream.isOpen()) {
        final StitchEvent<ChangeEvent<RawBsonDocument>> event = currentStream.nextEvent();
        if (event == null) {
          return;
        }

        if (event.getError() != null) {
          throw event.getError();
        }

        if (event.getData() == null) {
          return;
        }

        logger.info(String.format(Locale.US,
            "NamespaceChangeStreamListener::stream ns=%s event found: op=%s id=%s",
            nsConfig.getNamespace(), event.getData().getOperationType(), event.getData().getId()));
        nsLock.writeLock().lockInterruptibly();
        try {
          events.put(BsonUtils.getDocumentId(event.getData().getDocumentKey()), event.getData());
        } finally {
          nsLock.writeLock().unlock();
        }

        for (final Callback<ChangeEvent<RawBsonDocument>, Object> watcher : watchers) {
          watcher.onComplete(OperationResult.successfulResultOf(event.getData()));
        }
      }
    } catch (final InterruptedException | IOException ex) {
      logger.info(String.format(
          Locale.US,
          "NamespaceChangeStreamListener::stream ns=%s interrupted on "
              + "fetching next event: %s",
          nsConfig.getNamespace(),
          ex));
      logger.info("stream END – INTERRUPTED");
      Thread.currentThread().interrupt();
    } catch (final Exception ex) {
      // TODO: Emit error through DataSynchronizer as an ifc
      logger.error(String.format(
          Locale.US,
          "NamespaceChangeStreamListener::stream ns=%s exception on fetching next event: %s",
          nsConfig.getNamespace(),
          ex), ex);
      logger.info("stream END – EXCEPTION");
      final boolean wasInterrupted = Thread.currentThread().isInterrupted();
      this.close();
      if (wasInterrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * Returns the latest change events, and clears them from the change stream listener.
   *
   * @return the latest change events.
   */
  @SuppressWarnings("unchecked")
  public Map<BsonValue, ChangeEvent<RawBsonDocument>> getEvents() {
    nsLock.readLock().lock();
    final Map<BsonValue, ChangeEvent<RawBsonDocument>> events;
    try {
      events = new HashMap<>(this.events);
    } finally {
      nsLock.readLock().unlock();
    }

    nsLock.writeLock().lock();
    try {
      this.events.clear();
      return events;
    } finally {
      nsLock.writeLock().unlock();
    }
  }

  /**
   * If there is an unprocessed change event for a particular document ID, fetch it from the
   * change stream listener, and remove it. By reading the event here, we are assuming it will be
   * processed by the consumer.
   *
   * @return the latest unprocessed change event for the given document ID, or null if none exists.
   */
  public @Nullable ChangeEvent<RawBsonDocument> getUnprocessedEventForDocumentId(
      final BsonValue documentId
  ) {
    final ChangeEvent<RawBsonDocument> event;
    nsLock.readLock().lock();
    try {
      event = this.events.get(documentId);
    } finally {
      nsLock.readLock().unlock();
    }

    nsLock.writeLock().lock();
    try {
      this.events.remove(documentId);
      return event;
    } finally {
      nsLock.writeLock().unlock();
    }
  }
}
