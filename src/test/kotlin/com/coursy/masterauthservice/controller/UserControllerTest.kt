package com.coursy.masterauthservice.controller

import com.coursy.masterauthservice.model.RoleName
import com.coursy.masterauthservice.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class UserControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val mapper: ObjectMapper,
    private val userRepo: UserRepository,
    private val fixtures: ControllerTestFixtures
) {

    @Nested
    inner class Authorization {
        @Test
        fun `user should access user endpoint`() {
            // given
            val jwt = fixtures.setupAccount(RoleName.ROLE_USER)

            // when
            val response = mockMvc.post(fixtures.userUrl) {
                contentType = MediaType.APPLICATION_JSON
                content = null
                header("Authorization", "Bearer $jwt")
            }

            // then
            response.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `admin should access user endpoint`() {
            // given
            val jwt = fixtures.setupAccount(RoleName.ROLE_ADMIN)

            // when
            val response = mockMvc.post(fixtures.userUrl) {
                contentType = MediaType.APPLICATION_JSON
                content = null
                header("Authorization", "Bearer $jwt")
            }

            // then
            response.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `super-admin should access user endpoint`() {
            // given
            val jwt = fixtures.setupAccount(RoleName.ROLE_SUPER_ADMIN)

            // when
            val response = mockMvc.post(fixtures.userUrl) {
                contentType = MediaType.APPLICATION_JSON
                content = null
                header("Authorization", "Bearer $jwt")
            }

            // then
            response.andExpect {
                status { isBadRequest() }
            }
        }
    }

    @Nested
    inner class `User registration` {
        @Test
        fun `should save the user`() {
            // given
            val registrationRequest = fixtures.regularUserRequest

            // when
            val response = mockMvc.post(fixtures.userUrl) {
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(registrationRequest)
            }

            // then
            response.andExpect {
                status { isCreated() }
            }

            val savedUser = userRepo.findByEmail(registrationRequest.email)
            assertNotNull(savedUser)
            assertEquals(registrationRequest.firstName, savedUser?.firstName?.value)
            assertEquals(registrationRequest.lastName, savedUser?.lastName?.value)
        }

        @Test
        fun `should not save 2 users with the same email`() {
            // given
            val registrationRequest = fixtures.regularUserRequest

            // when
            val firstResponse = mockMvc.post(fixtures.userUrl) {
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(registrationRequest)
            }

            val secondResponse = mockMvc.post(fixtures.userUrl) {
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(registrationRequest)
            }

            // then
            firstResponse.andExpect {
                status { isCreated() }
            }
            secondResponse.andExpect {
                status { isConflict() }
            }

            val users = userRepo.findAll()
            assertEquals(1, users.size)
            assertEquals(registrationRequest.email, users[0].email.value)
        }

        @Test
        fun `should create regular user when admin role requested`() {
            val registrationRequest = fixtures.adminRequest

            // when
            val adminResponse = mockMvc.post(fixtures.userUrl) {
                contentType = MediaType.APPLICATION_JSON
                content = mapper.writeValueAsString(registrationRequest)
            }

            // then
            adminResponse.andExpect {
                status { isCreated() }
            }
            val user = userRepo.findByEmail(registrationRequest.email)
            assertEquals(RoleName.ROLE_USER, user?.role?.name)
        }
    }
}