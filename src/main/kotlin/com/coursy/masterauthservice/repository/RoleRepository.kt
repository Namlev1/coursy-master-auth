package com.coursy.masterauthservice.repository

import com.coursy.masterauthservice.model.Role
import com.coursy.masterauthservice.model.RoleName
import org.springframework.data.jpa.repository.JpaRepository

interface RoleRepository : JpaRepository<Role, Long> {
    fun findByName(name: RoleName): Role?
} 