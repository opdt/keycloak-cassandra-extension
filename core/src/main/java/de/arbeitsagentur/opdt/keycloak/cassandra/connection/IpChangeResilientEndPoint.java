package de.arbeitsagentur.opdt.keycloak.cassandra.connection;

import com.datastax.oss.driver.api.core.metadata.EndPoint;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.Objects;

public class IpChangeResilientEndPoint implements EndPoint, Serializable {
  private static final long serialVersionUID = 1L;
  private final String hostname;
  private final int port;
  private final String metricPrefix;

  public IpChangeResilientEndPoint(String hostname, int port) {
    this.hostname = hostname;
    this.port = port;
    this.metricPrefix = buildMetricPrefix(hostname, port);
  }

  @NonNull
  public InetSocketAddress resolve() {
    return new InetSocketAddress(hostname, port);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IpChangeResilientEndPoint that = (IpChangeResilientEndPoint) o;
    return port == that.port && Objects.equals(hostname, that.hostname);
  }

  @Override
  public int hashCode() {
    return Objects.hash(hostname, port);
  }

  public String toString() {
    return hostname + ":" + port;
  }

  @NonNull
  public String asMetricPrefix() {
    return this.metricPrefix;
  }

  private static String buildMetricPrefix(String hostname, int port) {
    return hostname.replace('.', '_') + ':' + port;
  }
}
