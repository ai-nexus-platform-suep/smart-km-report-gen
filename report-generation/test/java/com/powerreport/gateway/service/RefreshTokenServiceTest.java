package com.powerreport.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerreport.gateway.entity.RefreshTokenEntity;
import com.powerreport.gateway.mapper.RefreshTokenMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenMapper refreshTokenMapper;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenMapper);
    }

    @Test
    void hashTokenIsStableSha256Hex() {
        String hash = service.hashToken("refresh-token");

        assertThat(hash).hasSize(64);
        assertThat(hash).isEqualTo(service.hashToken("refresh-token"));
        assertThat(hash).isNotEqualTo("refresh-token");
    }

    @Test
    void saveTokenStoresOnlyHash() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

        service.saveToken("alice", "refresh-token", expiresAt);

        ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenMapper).insert(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
        assertThat(captor.getValue().getTokenHash()).isEqualTo(service.hashToken("refresh-token"));
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(expiresAt);
        assertThat(captor.getValue().getRevoked()).isFalse();
    }

    @Test
    void isValidChecksPersistedHash() {
        when(refreshTokenMapper.findValidByHash(service.hashToken("refresh-token")))
                .thenReturn(new RefreshTokenEntity());

        assertThat(service.isValid("refresh-token")).isTrue();
        assertThat(service.isValid("other-token")).isFalse();
    }

    @Test
    void deleteOperationsUseMapperDeleteWrappers() {
        service.deleteToken("refresh-token");
        service.deleteByUsername("alice");
        service.cleanExpiredTokens();

        verify(refreshTokenMapper, org.mockito.Mockito.times(3)).delete(any());
    }
}
