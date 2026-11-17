package org.example.pawfect.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.pawfect.global.entity.BaseTimeEntity;

@Getter
@Entity
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /** BCrypt 해시. 평문 비밀번호는 어디에도 저장하지 않는다. */
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Builder
    private Member(String email, String passwordHash, String nickname) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
    }
}
