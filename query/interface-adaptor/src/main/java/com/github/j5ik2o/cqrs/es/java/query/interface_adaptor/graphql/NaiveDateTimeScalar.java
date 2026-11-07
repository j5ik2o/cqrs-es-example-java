package com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.graphql;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Function;

/**
 * GraphQL scalar for an ISO 8601 date and time without a timezone, serialized as {@link
 * LocalDateTime}.
 *
 * <p>The wire format matches the {@code NaiveDateTime} scalar that the common GroupChat schema
 * declares.
 */
public final class NaiveDateTimeScalar {

  public static final String NAME = "NaiveDateTime";

  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  private NaiveDateTimeScalar() {}

  public static GraphQLScalarType create() {
    return GraphQLScalarType.newScalar()
        .name(NAME)
        .description("ISO 8601 combined date and time without timezone.")
        .coercing(
            new Coercing<LocalDateTime, String>() {
              @Override
              public String serialize(Object input, GraphQLContext context, Locale locale) {
                if (input instanceof LocalDateTime value) {
                  return FORMATTER.format(value);
                }
                throw new CoercingSerializeException(
                    "Expected a java.time.LocalDateTime but was " + typeNameOf(input));
              }

              @Override
              public LocalDateTime parseValue(Object input, GraphQLContext context, Locale locale) {
                return parse(input, CoercingParseValueException::new);
              }

              @Override
              public LocalDateTime parseLiteral(
                  Value<?> input,
                  CoercedVariables variables,
                  GraphQLContext context,
                  Locale locale) {
                if (input instanceof StringValue stringValue) {
                  return parse(stringValue.getValue(), CoercingParseLiteralException::new);
                }
                throw new CoercingParseLiteralException(
                    "Expected a StringValue but was " + typeNameOf(input));
              }

              @Override
              public Value<?> valueToLiteral(Object input, GraphQLContext context, Locale locale) {
                return StringValue.of(serialize(input, context, locale));
              }

              private <E extends RuntimeException> LocalDateTime parse(
                  Object input, Function<String, E> errorFactory) {
                if (input instanceof String text) {
                  try {
                    return LocalDateTime.parse(text, FORMATTER);
                  } catch (DateTimeParseException e) {
                    throw errorFactory.apply(
                        "Invalid " + NAME + " value '" + text + "': " + e.getMessage());
                  }
                }
                throw errorFactory.apply("Expected a String but was " + typeNameOf(input));
              }
            })
        .build();
  }

  private static String typeNameOf(Object value) {
    return value == null ? "null" : value.getClass().getName();
  }
}
