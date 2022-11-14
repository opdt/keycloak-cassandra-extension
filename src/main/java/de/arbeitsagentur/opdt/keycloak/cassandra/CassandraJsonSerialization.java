package de.arbeitsagentur.opdt.keycloak.cassandra;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;

public class CassandraJsonSerialization {
  public static final ObjectMapper mapper = new ObjectMapper();

  static {
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
  }

  public static String writeValueAsString(Object obj) throws IOException {
    return mapper.writeValueAsString(obj);
  }

  public static <T> T readValue(String bytes, Class<T> type) throws IOException {
    return mapper.readValue(bytes, type);
  }

  public static <T> T readValue(String string, TypeReference<T> type) throws IOException {
    return mapper.readValue(string, type);
  }

}
