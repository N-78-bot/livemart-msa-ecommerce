package com.livemart.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    @Size(max = 50)
    private String name;
    @Pattern(regexp = "^[0-9+\\-\\s]{0,20}$", message = "유효하지 않은 전화번호 형식입니다")
    private String phoneNumber;
}
