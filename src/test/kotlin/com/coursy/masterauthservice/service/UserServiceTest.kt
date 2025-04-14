package com.coursy.masterauthservice.service

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
    val userRepository = mockk<UserRepository>()
    val roleRepository = mockk<RoleRepository>()
    val passwordEncoder = mockk<PasswordEncoder>()
    val userService = UserService(userRepository, roleRepository, passwordEncoder)

    var firstName = Name.create("John").getOrNull()!!
    var lastName = Name.create("Doe").getOrNull()!!
    val email = Email.create("test@example.com").getOrNull()!!
    val password = Password.create("Password123!").getOrNull()!!
    var companyName = CompanyName.create("Test Company").getOrNull()!!
    val encryptedPassword = "encrypted_password"
    val roleName = RoleName.ROLE_USER
    var role = Role(
        id = 1L,
        name = roleName
    )

    val validRequest = RegistrationRequest.Validated(
        firstName = firstName,
        lastName = lastName,
        email = email,
        password = password,
        companyName = companyName,
        roleName = roleName
    )

    describe("UserService") {

        describe("createUser") {
            it("should create a user successfully") {
                // given
                every { userRepository.existsByEmail(email) } returns false
                every { roleRepository.findByName(roleName) } returns role
                every { passwordEncoder.encode(password.value) } returns encryptedPassword
                every { userRepository.save(any()) } answers {
                    firstArg<User>().apply { id = 1L }
                }

                // when
                val result = userService.createUser(validRequest)

                // then
                result.shouldBeRight()

                verify { userRepository.existsByEmail(email) }
                verify { roleRepository.findByName(roleName) }
                verify { passwordEncoder.encode(password.value) }
                verify {
                    userRepository.save(match {
                        it.email == email &&
                                it.password == encryptedPassword &&
                                it.firstName == firstName &&
                                it.lastName == lastName &&
                                it.companyName == companyName &&
                                it.role == role
                    })
                }
            }

            it("should return EmailAlreadyExists failure when email exists") {
                // given
                every { userRepository.existsByEmail(email) } returns true

                // when
                val result = userService.createUser(validRequest)

                // then
                result.shouldBeLeft().shouldBeInstanceOf<UserFailure.EmailAlreadyExists>()

                verify { userRepository.existsByEmail(email) }
            }

            it("should return RoleNotFound failure when role doesn't exist") {
                // given
                every { userRepository.existsByEmail(email) } returns false
                every { roleRepository.findByName(roleName) } returns null

                // when
                val result = userService.createUser(validRequest)

                // then
                result.shouldBeLeft().shouldBeInstanceOf<RoleFailure.NotFound>()

                verify { userRepository.existsByEmail(email) }
                verify { roleRepository.findByName(roleName) }
            }
        }
        describe("removeUser") {
            it("should remove user successfully") {
                // given
                val userId = 1L
                every { userRepository.existsById(userId) } returns true
                every { userRepository.removeUserById(userId) } returns Unit

                // when
                val result = userService.removeUser(userId)

                // then
                result.shouldBeRight()
                verify { userRepository.existsById(userId) }
                verify { userRepository.removeUserById(userId) }
            }

            it("should return IdNotExists failure when user doesn't exist") {
                // given
                val nonExistentId = 99L
                every { userRepository.existsById(nonExistentId) } returns false

                // when
                val result = userService.removeUser(nonExistentId)

                // then
                result.shouldBeLeft().shouldBeInstanceOf<UserFailure.IdNotExists>()
                verify { userRepository.existsById(nonExistentId) }
            }
        }
        describe("getUser") {
            it("should retrieve user successfully") {
                // given
                val userId = 1L
                val user = User(
                    id = userId,
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    password = encryptedPassword,
                    companyName = companyName,
                    role = role
                )

                every { userRepository.findById(userId) } returns Optional.of(user)

                // when
                val result = userService.getUser(userId)

                // then
                val right = result.shouldBeRight()
                right.apply {
                    this.id shouldBe userId
                    this.email shouldBe email
                    this.firstName shouldBe firstName
                    this.lastName shouldBe lastName
                    this.companyName shouldBe companyName
                }

                verify { userRepository.findById(userId) }
            }

            it("should return IdNotExists failure when user doesn't exist") {
                // given
                val nonExistentId = 99L
                every { userRepository.findById(nonExistentId) } returns Optional.empty()

                // when
                val result = userService.getUser(nonExistentId)

                // then
                result.shouldBeLeft().shouldBeInstanceOf<UserFailure.IdNotExists>()
                verify { userRepository.findById(nonExistentId) }
            }
        }
        describe("updateUser") {
            val userId = 1L
            val adminRoleName = RoleName.ROLE_ADMIN
            val adminRole = Role(id = 2L, name = adminRoleName)
            val updatedFirstName = Name.create("Jane").getOrNull()!!
            val updatedLastName = Name.create("Smith").getOrNull()!!
            val updatedCompanyName = CompanyName.create("New Company").getOrNull()!!
            val user = User(
                id = userId,
                firstName = firstName,
                lastName = lastName,
                email = email,
                password = encryptedPassword,
                companyName = companyName,
                role = role
            )
            val updatedRequest = RegistrationRequest.Validated(
                firstName = updatedFirstName,
                lastName = updatedLastName,
                email = email,
                password = password,
                companyName = updatedCompanyName,
                roleName = adminRoleName
            )

            it("should update user successfully") {
                // given
                val updateRequest = UserUpdateRequest.Validated(
                    firstName = updatedFirstName,
                    lastName = updatedLastName,
                    companyName = updatedCompanyName,
                    roleName = adminRoleName
                )
                every { userRepository.findById(userId) } returns Optional.of(user)
                every { roleRepository.findByName(adminRoleName) } returns adminRole
                every { userRepository.save(any()) } returns user.apply {
                    firstName = updatedFirstName
                    lastName = updatedLastName
                    companyName = updatedCompanyName
                    role = adminRole
                }

                // when
                val result = userService.updateUser(userId, updateRequest)

                // then
                val response = result.shouldBeRight()
                response.firstName shouldBe updatedFirstName
                response.lastName shouldBe updatedLastName
                response.companyName shouldBe updatedCompanyName
                response.roleName shouldBe adminRoleName.name

                verify { userRepository.findById(userId) }
                verify { roleRepository.findByName(adminRoleName) }
                verify {
                    userRepository.save(match { updatedUser ->
                        updatedUser.id == userId &&
                                updatedUser.firstName == updatedFirstName &&
                                updatedUser.lastName == updatedLastName &&
                                updatedUser.companyName == updatedCompanyName &&
                                updatedUser.role == adminRole
                    })
                }
            }

            it("should return IdNotExists failure when user doesn't exist") {
                // given
                val nonExistentId = 99L
                val updateRequest = UserUpdateRequest.Validated(
                    firstName = updatedFirstName,
                    lastName = null,
                    companyName = null,
                    roleName = null
                )
                every { userRepository.findById(nonExistentId) } returns Optional.empty()

                // when
                val result = userService.updateUser(nonExistentId, updateRequest)

                // then
                result.shouldBeLeft().shouldBeInstanceOf<UserFailure.IdNotExists>()
                verify { userRepository.findById(nonExistentId) }
            }

            it("should return RoleNotFound failure when role doesn't exist") {
                // given
                val updateRequest = UserUpdateRequest.Validated(
                    firstName = null,
                    lastName = null,
                    companyName = null,
                    roleName = RoleName.ROLE_SUPER_ADMIN
                )
                every { userRepository.findById(userId) } returns Optional.of(user)
                every { roleRepository.findByName(RoleName.ROLE_SUPER_ADMIN) } returns null

                // when
                val result = userService.updateUser(userId, updateRequest)

                // then
                result.shouldBeLeft().shouldBeInstanceOf<RoleFailure.NotFound>()
                verify { userRepository.findById(userId) }
                verify { roleRepository.findByName(RoleName.ROLE_SUPER_ADMIN) }
            }
        } 
    }
})