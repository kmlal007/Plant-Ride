package com.plantride.common;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface PlantScopedRepository<T> extends JpaRepository<T, Long> {

    Optional<T> findByIdAndPlantId(Long id, Long plantId);

    List<T> findByPlantIdOrderByIdAsc(Long plantId);

    default T require(Long id, Long plantId, String what) {
        return findByIdAndPlantId(id, plantId).orElseThrow(() -> ApiException.notFound(what));
    }
}
