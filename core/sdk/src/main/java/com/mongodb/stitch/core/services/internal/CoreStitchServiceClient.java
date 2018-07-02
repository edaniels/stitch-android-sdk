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

package com.mongodb.stitch.core.services.internal;

import java.util.List;
import javax.annotation.Nullable;
import org.bson.codecs.Decoder;
import org.bson.codecs.configuration.CodecRegistry;

public interface CoreStitchServiceClient {

  @Nullable
  String getName();

  void callFunctionInternal(
      final String name,
      final List<?> args);

  <T> T callFunctionInternal(
      final String name,
      final List<?> args,
      final Decoder<T> resultDecoder);

  <T> T callFunctionInternal(
      final String name,
      final List<?> args,
      final Class<T> resultClass);

  void callFunctionInternal(
      final String name,
      final List<?> args,
      final @Nullable Long requestTimeout);

  <T> T callFunctionInternal(
      final String name,
      final List<?> args,
      final @Nullable Long requestTimeout,
      final Decoder<T> resultDecoder);

  <T> T callFunctionInternal(
      final String name,
      final List<?> args,
      final @Nullable Long requestTimeout,
      final Class<T> resultClass);

  CodecRegistry getCodecRegistry();

  CoreStitchServiceClient withCodecRegistry(final CodecRegistry codecRegistry);
}
