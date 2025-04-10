package com.coursy.masterauthservice.dto

import com.coursy.masterauthservice.type.CompanyName
import com.coursy.masterauthservice.type.Email
import com.coursy.masterauthservice.type.Name

data class UserResponse(
    val id: Long,
    val email: Email,
    val firstName: Name,
    val lastName: Name,
    val companyName: CompanyName?,
)