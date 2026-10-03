package com.plantride.notification;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    List<DeviceToken> findByUserId(Long userId);

    Optional<DeviceToken> findByToken(String token);

    @Transactional
    void deleteByTokenIn(Collection<String> tokens);

    @Transactional
    void deleteByUserIdAndToken(Long userId, String token);
}
