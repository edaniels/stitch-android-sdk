package com.mongodb.stitch.core.internal.common;

enum JsonTokenType {
  /**
   * An invalid token.
   */
  INVALID,

  /**
   * A begin array token (a '[').
   */
  BEGIN_ARRAY,

  /**
   * A begin object token (a '{').
   */
  BEGIN_OBJECT,

  /**
   * An end array token (a ']').
   */
  END_ARRAY,

  /**
   * A left parenthesis (a '(').
   */
  LEFT_PAREN,

  /**
   * A right parenthesis (a ')').
   */
  RIGHT_PAREN,

  /**
   * An end object token (a '}').
   */
  END_OBJECT,

  /**
   * A colon token (a ':').
   */
  COLON,

  /**
   * A comma token (a ',').
   */
  COMMA,

  /**
   * A Double token.
   */
  DOUBLE,

  /**
   * An Int32 token.
   */
  INT32,

  /**
   * And Int64 token.
   */
  INT64,

  /**
   * A regular expression token.
   */
  REGULAR_EXPRESSION,

  /**
   * A string token.
   */
  STRING,

  /**
   * An unquoted string token.
   */
  UNQUOTED_STRING,

  /**
   * An end of file token.
   */
  END_OF_FILE
}
