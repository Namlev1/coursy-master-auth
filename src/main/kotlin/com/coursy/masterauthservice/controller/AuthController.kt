package com.coursy.masterauthservice.controller

import arrow.core.flatMap
import com.coursy.masterauthservice.dto.LoginRequest
import com.coursy.masterauthservice.service.AuthService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val httpFailureResolver: HttpFailureResolver
) {
    @PostMapping("/login")
    fun authenticateUser(@RequestBody request: LoginRequest): ResponseEntity<Any> {
        val result = request.validate().flatMap { validated -> authService.authenticateUser(validated) }

        return result.fold(
            { failure -> httpFailureResolver.handleFailure(failure) },
            { jwtResponse -> ResponseEntity.status(HttpStatus.OK).body(jwtResponse) }
        )
    }

    @GetMapping("/secret")
    fun authorizedEndpoint() = ResponseEntity
        .status(HttpStatus.OK)
        .body("You passed the authorization flow!")
}