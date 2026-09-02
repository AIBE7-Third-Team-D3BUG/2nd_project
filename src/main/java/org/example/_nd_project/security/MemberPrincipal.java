package org.example._nd_project.security;

import org.example._nd_project.member.Member;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public record MemberPrincipal(
        Long memberId,
        String email,
        String passwordHash,
        String nickname,
        String role,
        boolean enabled
) implements UserDetails, OAuth2User {

    public static MemberPrincipal from(Member member) {
        return new MemberPrincipal(
                member.getId(), member.getEmail(), member.getPasswordHash(), member.getNickname(),
                member.getRole().name(), member.getStatus().name().equals("ACTIVE")
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of();
    }

    @Override
    public String getName() {
        return String.valueOf(memberId);
    }
}
