package com.plantride.plant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.plantride.security.Role;
import com.plantride.user.AppUser;
import com.plantride.user.AppUserRepository;

/** On an empty database, creates the first plant and an admin so the admin web can be used. */
@Component
@Order(1)
public class BootstrapSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapSeeder.class);

    private final PlantRepository plants;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties props;

    public BootstrapSeeder(PlantRepository plants, AppUserRepository users, PasswordEncoder passwordEncoder,
                           BootstrapProperties props) {
        this.plants = plants;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (plants.count() > 0) {
            return;
        }
        Plant plant = new Plant();
        plant.setCode(props.plantCode());
        plant.setName(props.plantName());
        plants.save(plant);

        AppUser admin = new AppUser();
        admin.setPlantId(plant.getId());
        admin.setLoginId(props.adminLogin());
        admin.setName("Administrator");
        admin.setRole(Role.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(props.adminPassword()));
        users.save(admin);
        log.warn("Bootstrapped plant '{}' and admin '{}'. Change the admin password immediately.",
                plant.getCode(), admin.getLoginId());
    }
}
