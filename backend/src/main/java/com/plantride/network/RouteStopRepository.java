package com.plantride.network;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {

    List<RouteStop> findByRouteIdOrderBySeqAsc(Long routeId);

    List<RouteStop> findByRouteIdInOrderByRouteIdAscSeqAsc(Collection<Long> routeIds);

    List<RouteStop> findByStopId(Long stopId);

    void deleteByRouteId(Long routeId);
}
