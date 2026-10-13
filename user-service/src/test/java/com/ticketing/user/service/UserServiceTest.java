package com.ticketing.user.service;

import com.ticketing.user.dto.CreateUserRequest;
import com.ticketing.user.dto.UpdateUserRequest;
import com.ticketing.user.dto.UserResponse;
import com.ticketing.user.exception.EmailAlreadyExistsException;
import com.ticketing.user.exception.UserNotFoundException;
import com.ticketing.user.model.User;
import com.ticketing.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void createSavesUserWithNormalizedEmail() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.create(
                new CreateUserRequest(" Ana ", "Anić", "  Ana@Example.COM "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ana@example.com");
        assertThat(saved.getValue().getFirstName()).isEqualTo("Ana");
        assertThat(response.email()).isEqualTo("ana@example.com");
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("ana@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(new CreateUserRequest("Ana", "Anić", "ana@example.com")))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void findByIdThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(42L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void updateChangesUserFields() {
        User existing = new User("Ana", "Anić", "ana@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailAndIdNot("ana.new@example.com", 1L)).thenReturn(false);

        UserResponse response = userService.update(1L,
                new UpdateUserRequest("Ana", "Petrović", "Ana.New@example.com"));

        assertThat(existing.getLastName()).isEqualTo("Petrović");
        assertThat(existing.getEmail()).isEqualTo("ana.new@example.com");
        assertThat(response.lastName()).isEqualTo("Petrović");
    }

    @Test
    void updateRejectsEmailOfAnotherUser() {
        User existing = new User("Ana", "Anić", "ana@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailAndIdNot("marko@example.com", 1L)).thenReturn(true);

        assertThatThrownBy(() -> userService.update(1L,
                new UpdateUserRequest("Ana", "Anić", "marko@example.com")))
                .isInstanceOf(EmailAlreadyExistsException.class);
        assertThat(existing.getEmail()).isEqualTo("ana@example.com");
    }

    @Test
    void updateThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(7L,
                new UpdateUserRequest("Ana", "Anić", "ana@example.com")))
                .isInstanceOf(UserNotFoundException.class);
    }

}
