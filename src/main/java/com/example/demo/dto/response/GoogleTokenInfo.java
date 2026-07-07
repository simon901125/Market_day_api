package com.example.demo.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoogleTokenInfo {
    private String sub;
    private String aud;
    private String email;
    private String email_verified;
    private String name;
}
