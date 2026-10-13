package com.ticketing.user.controller;

import com.ticketing.user.dto.CreateUserRequest;
import com.ticketing.user.dto.UpdateUserRequest;
import com.ticketing.user.dto.UserResponse;
import com.ticketing.user.exception.ApiError;
import com.ticketing.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ceo tok kroz HTTP -> controller -> service -> JPA -> prava PostgreSQL baza u Docker kontejneru.
 * Flyway migracije se izvršavaju nad tom bazom, a ddl-auto=validate proverava da se poklapaju sa entitetima.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class UserControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    private RestTestClient client;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void createAndFetchUser() {
        UserResponse created = client.post().uri("/api/users")
                .body(new CreateUserRequest("Ana", "Anić", "Ana@Example.com"))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("Location")
                .expectBody(UserResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.email()).isEqualTo("ana@example.com");
        assertThat(created.createdAt()).isNotNull();

        client.get().uri("/api/users/{id}", created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponse.class)
                .value(user -> assertThat(user.firstName()).isEqualTo("Ana"));

        client.get().uri("/api/users")
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponse[].class)
                .value(users -> assertThat(users).hasSize(1));
    }

    @Test
    void duplicateEmailReturnsConflict() {
        createUser("ana@example.com");

        client.post().uri("/api/users")
                .body(new CreateUserRequest("Druga", "Ana", "ANA@example.com"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(ApiError.class)
                .value(error -> assertThat(error.message()).contains("ana@example.com"));
    }

    @Test
    void invalidRequestReturnsBadRequestWithFieldErrors() {
        client.post().uri("/api/users")
                .body(new CreateUserRequest("", "Anić", "not-an-email"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ApiError.class)
                .value(error -> assertThat(error.fieldErrors()).containsKeys("firstName", "email"));
    }

    @Test
    void unknownUserReturnsNotFound() {
        client.get().uri("/api/users/{id}", 999_999)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ApiError.class)
                .value(error -> {
                    assertThat(error.status()).isEqualTo(404);
                    assertThat(error.path()).isEqualTo("/api/users/999999");
                });
    }

    @Test
    void updateUser() {
        UserResponse created = createUser("ana@example.com");

        client.put().uri("/api/users/{id}", created.id())
                .body(new UpdateUserRequest("Ana", "Petrović", "ana.petrovic@example.com"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponse.class)
                .value(user -> {
                    assertThat(user.lastName()).isEqualTo("Petrović");
                    assertThat(user.email()).isEqualTo("ana.petrovic@example.com");
                });

        assertThat(userRepository.findById(created.id()))
                .hasValueSatisfying(user -> assertThat(user.getLastName()).isEqualTo("Petrović"));
    }

    @Test
    void updateToEmailOfAnotherUserReturnsConflict() {
        createUser("ana@example.com");
        UserResponse marko = createUser("marko@example.com");

        client.put().uri("/api/users/{id}", marko.id())
                .body(new UpdateUserRequest("Marko", "Marković", "ana@example.com"))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    private UserResponse createUser(String email) {
        return client.post().uri("/api/users")
                .body(new CreateUserRequest("Test", "User", email))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponse.class)
                .returnResult()
                .getResponseBody();
    }

}
