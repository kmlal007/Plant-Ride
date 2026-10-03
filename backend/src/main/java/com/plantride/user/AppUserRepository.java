package com.plantride.user;

import java.util.Optional;

import com.plantride.common.PlantScopedRepository;

public interface AppUserRepository extends PlantScopedRepository<AppUser> {

    Optional<AppUser> findByLoginId(String loginId);
}
