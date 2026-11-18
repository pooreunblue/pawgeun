package org.example.pawgeun.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.pawgeun.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 스프링 전체를 띄우고(H2) 필터 체인까지 통과시키는 통합 테스트. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;

    @BeforeEach
    void clean() {
        memberRepository.deleteAll();
    }

    private static String signupJson(String email, String password, String nickname) {
        return """
                {"email":"%s","password":"%s","nickname":"%s"}""".formatted(email, password, nickname);
    }

    private void signUp(String email) throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(email, "password1", "뭉치")))
                .andExpect(status().isCreated());
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.accessToken");
    }

    @Test
    void 가입하면_201과_회원정보를_받고_비밀번호는_해시로_저장된다() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("Mung@Example.com", "password1", "뭉치")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("mung@example.com"))
                .andExpect(jsonPath("$.nickname").value("뭉치"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        String stored = memberRepository.findByEmail("mung@example.com").orElseThrow().getPasswordHash();
        assertThat(stored).startsWith("$2").isNotEqualTo("password1");
    }

    @Test
    void 같은_이메일로_다시_가입하면_409() throws Exception {
        signUp("a@example.com");

        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("A@example.com", "password1", "다른이")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"));
    }

    @Test
    void 입력이_잘못되면_400() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("not-an-email", "short", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 로그인하면_토큰으로_내_정보를_볼_수_있다() throws Exception {
        signUp("a@example.com");
        String token = loginAndGetToken("a@example.com", "password1");

        mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("a@example.com"));
    }

    @Test
    void 비밀번호가_틀리면_401_LOGIN_FAILED() throws Exception {
        signUp("a@example.com");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.com\",\"password\":\"wrong-pass1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void 없는_이메일도_같은_오류를_돌려준다() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"password1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void 토큰_없이_보호된_주소에_접근하면_401() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 잘못된_토큰이면_401_INVALID_TOKEN() throws Exception {
        mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void 탈퇴하면_회원이_삭제되고_토큰은_더_이상_쓸_수_없다() throws Exception {
        signUp("a@example.com");
        String token = loginAndGetToken("a@example.com", "password1");

        mockMvc.perform(delete("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(memberRepository.count()).isZero();
        mockMvc.perform(get("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 허용되지_않는_메서드는_405() throws Exception {
        String token = loginAfterSignUp();
        mockMvc.perform(post("/api/members/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void 지원하지_않는_Content_Type은_415() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void 없는_주소는_404() throws Exception {
        String token = loginAfterSignUp();
        mockMvc.perform(get("/api/nothing").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private String loginAfterSignUp() throws Exception {
        signUp("a@test.com");
        return loginAndGetToken("a@test.com", "password1");
    }
}
