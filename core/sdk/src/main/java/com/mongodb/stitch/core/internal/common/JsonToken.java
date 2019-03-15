package com.mongodb.stitch.core.internal.common;

import org.bson.BsonDouble;
import org.bson.json.JsonParseException;
import org.bson.types.Decimal128;

import static java.lang.String.format;

class JsonToken {

  private final Object value;
  private final JsonTokenType type;

  JsonToken(final JsonTokenType type, final Object value) {
    this.value = value;
    this.type = type;
  }

  public Object getValue() {
    return value;
  }

  public <T> T getValue(final Class<T> clazz) {
    try {
      if (Long.class == clazz) {
        if (value instanceof Integer) {
          return clazz.cast(((Integer) value).longValue());
        } else if (value instanceof String) {
          return clazz.cast(Long.valueOf((String) value));
        }
      } else if (Integer.class == clazz) {
        if (value instanceof String) {
          return clazz.cast(Integer.valueOf((String) value));
        }
      } else if (Double.class == clazz) {
        if (value instanceof String) {
          return clazz.cast(Double.valueOf((String) value));
        }
      } else if (Decimal128.class == clazz) {
        if (value instanceof Integer) {
          return clazz.cast(new Decimal128((Integer) value));
        } else if (value instanceof Long) {
          return clazz.cast(new Decimal128((Long) value));
        } else if (value instanceof Double) {
          return clazz.cast(new BsonDouble((Double) value).decimal128Value());
        } else if (value instanceof String) {
          return clazz.cast(Decimal128.parse((String) value));
        }
      }

      return clazz.cast(value);
    } catch (Exception e) {
      throw new JsonParseException(format("Exception converting value '%s' to type %s", value, clazz.getName()), e);
    }
  }

  public JsonTokenType getType() {
    return type;
  }
}
