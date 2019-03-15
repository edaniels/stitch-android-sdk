package com.mongodb.stitch.core.internal.common;

import org.bson.json.JsonParseException;

import java.io.IOException;
import java.io.InputStream;

class JsonBuffer {

  private final InputStream buffer;
  private int position;
  private boolean eof;

  JsonBuffer(final InputStream buffer) {
    this.buffer = buffer;
  }

  public int getPosition() {
    return position;
  }

  public void setPosition(final int position) {
    this.position = position;
  }

  public int read() {
    if (eof) {
      throw new JsonParseException("Trying to read past EOF.");
    }

    final int currChar;
    try {
      currChar = buffer.read();
    } catch (final IOException e) {
      throw new JsonParseException(e);
    }
    position++;
    if (currChar == -1) {
      eof = true;
    }
    return currChar;
  }

  public void unread(final int c) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  public String substring(final int beginIndex) {
    throw new UnsupportedOperationException("not yet implemented");
  }

  public String substring(final int beginIndex, final int endIndex) {
    throw new UnsupportedOperationException("not yet implemented");
  }
}