package com.powerreport.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerreport.gateway.dto.LoginResult;
import com.powerreport.gateway.entity.SysUserEntity;
import com.powerreport.gateway.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(sysUserMapper);
    }

    @Test
    void registerHashesPasswordAndPersistsEnabledUser() {
        when(sysUserMapper.selectOne(any())).thenReturn(null);

        boolean created = userService.register("alice", "secret123", "ROLE_USER");

        ArgumentCaptor<SysUserEntity> captor = ArgumentCaptor.forClass(SysUserEntity.class);
        verify(sysUserMapper).insert(captor.capture());
        assertThat(created).isTrue();
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
        assertThat(captor.getValue().getPassword()).isNotEqualTo("secret123");
        assertThat(new BCryptPasswordEncoder().matches("secret123", captor.getValue().getPassword())).isTrue();
        assertThat(captor.getValue().getEnabled()).isTrue();
    }

    @Test
    void registerReturnsFalseWhenUsernameAlreadyExists() {
        when(sysUserMapper.selectOne(any())).thenReturn(user("alice", "encoded", true));

        assertThat(userService.register("alice", "secret123", "ROLE_USER")).isFalse();
    }

    @Test
    void loginDistinguishesSuccessDisabledAndBadPassword() {
        String encoded = new BCryptPasswordEncoder().encode("secret123");
        when(sysUserMapper.selectOne(any()))
                .thenReturn(user("alice", encoded, true))
                .thenReturn(user("alice", encoded, false))
                .thenReturn(user("alice", encoded, true));

        LoginResult success = userService.login("alice", "secret123");
        LoginResult disabled = userService.login("alice", "secret123");
        LoginResult badPassword = userService.login("alice", "wrong");

        assertThat(success.isSuccess()).isTrue();
        assertThat(disabled.getCode()).isEqualTo(LoginResult.USER_DISABLED);
        assertThat(badPassword.getCode()).isEqualTo(LoginResult.PASSWORD_ERROR);
    }

    private SysUserEntity user(String username, String password, boolean enabled) {
        SysUserEntity user = new SysUserEntity();
        user.setUsername(username);
        user.setPassword(password);
        user.setRoles("ROLE_USER");
        user.setEnabled(enabled);
        return user;
    }
}
