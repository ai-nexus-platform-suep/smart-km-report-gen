package com.qa.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserAuthorities {

    private List<String> roles;
    private List<String> permissions;
}
