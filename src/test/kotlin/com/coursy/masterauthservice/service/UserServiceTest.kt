package com.coursy.masterauthservice.service

import com.coursy.masterauthservice.dto.ChangePasswordRequest
import com.coursy.masterauthservice.dto.RegistrationRequest
import com.coursy.masterauthservice.dto.UserUpdateRequest
import com.coursy.masterauthservice.failure.RoleFailure
import com.coursy.masterauthservice.failure.UserFailure
import com.coursy.masterauthservice.model.Role
import com.coursy.masterauthservice.model.RoleName
import com.coursy.masterauthservice.model.User
import com.coursy.masterauthservice.repository.RoleRepository
import com.coursy.masterauthservice.repository.UserRepository
import com.coursy.masterauthservice.type.CompanyName
import com.coursy.masterauthservice.type.Email
import com.coursy.masterauthservice.type.Name
import com.coursy.masterauthservice.type.Password
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.*

class UserServiceTest : DescribeSpec({

    class TestFixtures {
        // Common values
        val userId = 1L
        val nonExistentId = 99L
        val encryptedPassword = "encrypted_password"

        // User data
        val firstName = Name.create("John").getOrNull()!!
        val lastName = Name.create("Doe").getOrNull()!!
        val email = Email.create("test@example.com").getOrNull()!!
        val password = Password.create("Password123!").getOrNull()!!
        val companyName = CompanyName.create("Test Company").getOrNull()!!

        // Updated user data
        val updatedFirstName = Name.create("Jane").getOrNull()!!
        val updatedLastName = Name.create("Smith").getOrNull()!!
        val updatedCompanyName = CompanyName.create("New Company").getOrNull()!!
        val updatedPassword = Password.create("pa\$\$w0RD").getOrNull()!!

        // Roles
        val userRoleName = RoleName.ROLE_USER
        val userRole = Role(id = 1L, name = userRoleName)

        val adminRoleName = RoleName.ROLE_ADMIN
        val adminRole = Role(id = 2L, name = adminRoleName)

        val superAdminRoleName = RoleName.ROLE_SUPER_ADMIN

        // Request objects
        val validRegistrationRequest by lazy {
            RegistrationRequest.Validated(
                firstName = firstName,
                lastName = lastName,
                email = email,
                password = password,
                companyName = companyName,
                roleName = userRoleName
            )
        }

        val fullUpdateRequest by lazy {
            UserUpdateRequest.Validated(
                firstName = updatedFirstName,
                lastName = updatedLastName,
                companyName = updatedCompanyName,
                roleName = adminRoleName
            )
        }

        val partialUpdateRequest by lazy {
            UserUpdateRequest.Validated(
                firstName = updatedFirstName,
                lastName = null,
                companyName = null,
                roleName = null
            )
        }

        val roleOnlyUpdateRequest by lazy {
            UserUpdateRequest.Validated(
                firstName = null,
                lastName = null,
                companyName = null,
                roleName = superAdminRoleName
            )
        }

        val updatePasswordRequest by lazy {
            ChangePasswordRequest.Validated(
                password = updatedPassword
            )
        }

        // User object
        fun createUser(
            id: Long = userId,
            firstName: Name = this.firstName,
            lastName: Name = this.lastName,
            email: Email = this.email,
            password: String = encryptedPassword,
            companyName: CompanyName = this.companyName,
            role: Role = userRole
        ) = User(
            id = id,
            firstName = firstName,
            lastName = lastName,
            email = email,
            password = password,
            companyName = companyName,
            role = role
        )
    }

    // Mocks
    val userRepository = mockk<UserRepository>()
    val roleRepository = mockk<RoleRepository>()
    val passwordEncoder = mockk<PasswordEncoder>()

    // System under test
    val userService = UserService(userRepository, roleRepository, passwordEncoder)

    val fixtures = TestFixtures()

    describe("UserService") {

        describe("User Creation") {
            context("when creating a user with valid data") {
                it("should create a user successfully") {
                    // given
                    val request = fixtures.validRegistrationRequest

                    every { userRepository.existsByEmail(fixtures.email) } returns false
                    every { roleRepository.findByName(fixtures.userRoleName) } returns fixtures.userRole
                    every { passwordEncoder.encode(fixtures.password.value) } returns fixtures.encryptedPassword
                    every { userRepository.save(any()) } answers {
                        firstArg<User>().apply { id = fixtures.userId }
                    }

                    // when
                    val result = userService.createUser(request)

                    // then
                    result.shouldBeRight()

                    verify { userRepository.existsByEmail(fixtures.email) }
                    verify { roleRepository.findByName(fixtures.userRoleName) }
                    verify { passwordEncoder.encode(fixtures.password.value) }
                    verify {
                        userRepository.save(match {
                            it.email == fixtures.email &&
                                    it.password == fixtures.encryptedPassword &&
                                    it.firstName == fixtures.firstName &&
                                    it.lastName == fixtures.lastName &&
                                    it.companyName == fixtures.companyName &&
                                    it.role == fixtures.userRole
                        })
                    }
                }
            }

            context("when email already exists") {
                it("should return EmailAlreadyExists failure") {
                    // given
                    val request = fixtures.validRegistrationRequest
                    every { userRepository.existsByEmail(fixtures.email) } returns true

                    // when
                    val result = userService.createUser(request)

                    // then
                    result.shouldBeLeft().shouldBeInstanceOf<UserFailure.EmailAlreadyExists>()
                    verify { userRepository.existsByEmail(fixtures.email) }
                }
            }

            context("when role doesn't exist") {
                it("should return RoleNotFound failure") {
                    // given
                    val request = fixtures.validRegistrationRequest
                    every { userRepository.existsByEmail(fixtures.email) } returns false
                    every { roleRepository.findByName(fixtures.userRoleName) } returns null

                    // when
                    val result = userService.createUser(request)

                    // then
                    result.shouldBeLeft().shouldBeInstanceOf<RoleFailure.NotFound>()
                    verify { userRepository.existsByEmail(fixtures.email) }
                    verify { roleRepository.findByName(fixtures.userRoleName) }
                }
            }
        }

        describe("User Removal") {
            context("when removing an existing user") {
                it("should remove user successfully") {
                    // given
                    val userId = fixtures.userId
                    every { userRepository.existsById(userId) } returns true
                    every { userRepository.removeUserById(userId) } returns Unit

                    // when
                    val result = userService.removeUser(userId)

                    // then
                    result.shouldBeRight()
                    verify { userRepository.existsById(userId) }
                    verify { userRepository.removeUserById(userId) }
                }
            }

            context("when removing a non-existent user") {
                it("should return IdNotExists failure") {
                    // given
                    val nonExistentId = fixtures.nonExistentId
                    every { userRepository.existsById(nonExistentId) } returns false

                    // when
                    val result = userService.removeUser(nonExistentId)

                    // then
                    result.shouldBeLeft().shouldBeInstanceOf<UserFailure.IdNotExists>()
                    verify { userRepository.existsById(nonExistentId) }
                }
            }
        }

        describe("User Retrieval") {
            context("when retrieving an existing user") {
                it("should retrieve user successfully") {
                    // given
                    val userId = fixtures.userId
                    val user = fixtures.createUser()

                    every { userRepository.findById(userId) } returns Optional.of(user)

                    // when
                    val result = userService.getUser(userId)

                    // then
                    val right = result.shouldBeRight()
                    right.apply {
                        this.id shouldBe userId
                        this.email shouldBe fixtures.email
                        this.firstName shouldBe fixtures.firstName
                        this.lastName shouldBe fixtures.lastName
                        this.companyName shouldBe fixtures.companyName
                    }

                    verify { userRepository.findById(userId) }
                }
            }

            context("when retrieving a non-existent user") {
                it("should return IdNotExists failure") {
                    // given
                    val nonExistentId = fixtures.nonExistentId
                    every { userRepository.findById(nonExistentId) } returns Optional.empty()

                    // when
                    val result = userService.getUser(nonExistentId)

                    // then
                    result.shouldBeLeft().shouldBeInstanceOf<UserFailure.IdNotExists>()
                    verify { userRepository.findById(nonExistentId) }
                }
            }
        }

        describe("User Update") {
            context("when updating all user fields") {
                it("should update user successfully") {
                    // given
                    val userId = fixtures.userId
                    val updateRequest = fixtures.fullUpdateRequest
                    val user = fixtures.createUser()

                    every { userRepository.findById(userId) } returns Optional.of(user)
                    every { roleRepository.findByName(fixtures.adminRoleName) } returns fixtures.adminRole
                    every { userRepository.save(any()) } returns user.apply {
                        firstName = fixtures.updatedFirstName
                        lastName = fixtures.updatedLastName
                        companyName = fixtures.updatedCompanyName
                        role = fixtures.adminRole
                    }

                    // when
                    val result = userService.updateUser(userId, updateRequest)

                    // then
                    val response = result.shouldBeRight()
                    response.firstName shouldBe fixtures.updatedFirstName
                    response.lastName shouldBe fixtures.updatedLastName
                    response.companyName shouldBe fixtures.updatedCompanyName
                    response.roleName shouldBe fixtures.adminRoleName.name

                    verify { userRepository.findById(userId) }
                    verify { roleRepository.findByName(fixtures.adminRoleName) }
                    verify {
                        userRepository.save(match { updatedUser ->
                            updatedUser.id == userId &&
                                    updatedUser.firstName == fixtures.updatedFirstName &&
                                    updatedUser.lastName == fixtures.updatedLastName &&
                                    updatedUser.companyName == fixtures.updatedCompanyName &&
                                    updatedUser.role == fixtures.adminRole
                        })
                    }
                }
            }

            context("when user doesn't exist") {
                it("should return IdNotExists failure") {
                    // given
                    val nonExistentId = fixtures.nonExistentId
                    val updateRequest = fixtures.partialUpdateRequest

                    every { userRepository.findById(nonExistentId) } returns Optional.empty()

                    // when
                    val result = userService.updateUser(nonExistentId, updateRequest)

                    // then
                    result.shouldBeLeft().shouldBeInstanceOf<UserFailure.IdNotExists>()
                    verify { userRepository.findById(nonExistentId) }
                }
            }

            context("when role doesn't exist") {
                it("should return RoleNotFound failure") {
                    // given
                    val userId = fixtures.userId
                    val updateRequest = fixtures.roleOnlyUpdateRequest
                    val user = fixtures.createUser()

                    every { userRepository.findById(userId) } returns Optional.of(user)
                    every { roleRepository.findByName(fixtures.superAdminRoleName) } returns null

                    // when
                    val result = userService.updateUser(userId, updateRequest)

                    // then
                    result.shouldBeLeft().shouldBeInstanceOf<RoleFailure.NotFound>()
                    verify { userRepository.findById(userId) }
                    verify { roleRepository.findByName(fixtures.superAdminRoleName) }
                }
            }

            context("when updating user password") {
                it("should update user password successfully") {
                    // given
                    val userId = fixtures.userId
                    val request = fixtures.updatePasswordRequest
                    val user = fixtures.createUser()

                    every { userRepository.findById(userId) } returns Optional.of(user)
                    every { passwordEncoder.encode(any()) } returns fixtures.encryptedPassword
                    every { userRepository.save(any()) } returns user.apply {
                        password = fixtures.encryptedPassword
                    }

                    // when
                    val result = userService.updatePassword(userId, request)

                    // then
                    result.shouldBeRight()
                    verify { userRepository.findById(userId) }

                }

                it("should return Password failure") {
                    // given
                    val userId = fixtures.userId
                    val request = fixtures.updatePasswordRequest

                    every { userRepository.findById(userId) } returns Optional.empty()
                   
                    // when
                    val result = userService.updatePassword(userId, request)

                    // then
                    result.shouldBeLeft()
                        .shouldBeInstanceOf<UserFailure>()
                }

            }
        }
    }
})