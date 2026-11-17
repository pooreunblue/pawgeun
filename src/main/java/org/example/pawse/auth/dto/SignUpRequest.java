package org.example.pawse.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank @Email @Size(max = 100)
        String email,

        // BCrypt는 72바이트까지만 읽는다. 바이트=글자 수가 되도록 ASCII 출력 가능 문자만 허용한다.
        @NotBlank
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,64}$",
                message = "영문과 숫자를 포함한 8~64자(공백 제외, 영문/숫자/특수문자)여야 합니다.")
        String password,

        @NotBlank @Size(min = 2, max = 30)
        String nickname
) {
}
