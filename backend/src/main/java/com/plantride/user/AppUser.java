package com.plantride.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.plantride.security.Role;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "app_user")
@Getter
@Setter
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long plantId;
    /** Employee code for employees, phone number or badge id for drivers. */
    private String loginId;
    private String name;
    private String phone;
    private String email;

    @JsonIgnore
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    private String grade;
    private Long departmentId;
    private Long defaultCostCenterId;
    /** Approver for this user's exclusive rides. */
    private Long managerId;
    /** For contract drivers: the vendor they work for. */
    private Long vendorId;
    private boolean active = true;
}
