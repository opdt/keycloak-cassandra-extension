package de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.*;

import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.AdminEventEntity;
import de.arbeitsagentur.opdt.keycloak.cassandra.event.persistence.entities.EventEntity;
import org.keycloak.events.Event;
import org.keycloak.events.EventQuery;
import org.keycloak.events.EventType;
import lombok.Data;
import java.util.Date;
import java.util.List;
import java.util.LinkedList;
import java.util.stream.Stream;

@Data
class CassandraEventQuery implements EventQuery {

  private final EventDao dao;
  
  private List<String> types;
  private String realmId;
  private String clientId;
  private String userId;
  private Date fromDate;
  private Date toDate;
  private String ipAddress;
  private Integer firstResult;
  private Integer maxResults;
  private boolean orderByDescTime = true;

  CassandraEventQuery(EventDao dao) {
    this.dao = dao;
  }
  
  @Override
  public EventQuery type(EventType... typesArr) {
    types = new LinkedList<String>();
    for (EventType e : typesArr) {
      types.add(e.toString());
    }
    return this;
  }

  @Override
  public EventQuery realm(String realmId) {
    this.realmId = realmId;
    return this;
  }

  @Override
  public EventQuery client(String clientId) {
    this.clientId = clientId;
    return this;
  }

  @Override
  public EventQuery user(String userId) {
    this.userId = userId;
    return this;
  }

  @Override
  public EventQuery fromDate(Date fromDate) {
    this.fromDate = fromDate;
    return this;
  }

  @Override
  public EventQuery toDate(Date toDate) {
    this.toDate = toDate;
    return this;
  }
    
  @Override
  public EventQuery ipAddress(String ipAddress) {
    this.ipAddress = ipAddress;
    return this;
  }

  @Override
  public EventQuery firstResult(int firstResult) {
    this.firstResult = firstResult;
    return this;
  }

  @Override
  public EventQuery maxResults(int maxResults) {
    this.maxResults = maxResults;
    return this;
  }

  @Override
  public EventQuery orderByDescTime() {
    orderByDescTime = true;
    return this;
  }

  @Override
  public EventQuery orderByAscTime() {
    orderByDescTime = false;
    return this;
  }

  @Override
  public Stream<Event> getResultStream() {
    /* jpa
    if (!predicates.isEmpty()) {
      cq.where(cb.and(predicates.toArray(new Predicate[predicates.size()])));
    }

    if(orderByDescTime) {
      cq.orderBy(cb.desc(root.get("time")));
    } else {
      cq.orderBy(cb.asc(root.get("time")));
    }

    TypedQuery<EventEntity> query = em.createQuery(cq);

    return closing(paginateQuery(query, firstResult, maxResults).getResultStream().map(JpaEventStoreProvider::convertEvent));
    */

    return null;
    
    /* suggestion for how to get a stream 
      
    ResultSet rs = this.getResultSet(); // Takes <1 second

StreamSupport.stream(
    Spliterators.spliteratorUnknownSize(
                rs.iterator(), Spliterator.ORDERED), false)
       .parallel().forEach(this::processRow);
    */
  }
  
}
