package com.farm.smart.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 * <p>
 * 通过 Spring 注入配置后初始化静态密钥, 暴露静态方法用于 token 生成/解析。
 * 使用 JJWT 0.12.6 API, HMAC-SHA256 签名。
 *
 * @author Smart Farm Team
 */
@Slf4j
@Component
public class JwtUtils {

    /** JWT 密钥 (原始字符串, 来自 application.yml) */
    @Value("${jwt.secret}")
    private String secretStr;

    /** token 有效期 (毫秒) */
    @Value("${jwt.expiration}")
    private Long expiration;

    /** 签名密钥 (静态, 初始化后使用) */
    private static SecretKey KEY;

    /** token 有效期 (静态) */
    private static Long EXPIRATION;

    /**
     * 初始化静态密钥与有效期
     * 由 Spring 在 Bean 创建后调用, 保证配置注入完成
     */
    @PostConstruct
    public void init() {
        byte[] keyBytes = secretStr.getBytes(StandardCharsets.UTF_8);
        // Keys.hmacShaKeyFor 要求至少 32 字节(256 bit)
        KEY = Keys.hmacShaKeyFor(keyBytes);
        EXPIRATION = expiration;
        log.info("JWT 密钥初始化完成, token 有效期: {}ms", EXPIRATION);
    }

    /**
     * 生成 JWT token
     *
     * @param username 用户名 (作为 subject)
     * @param role     角色 (自定义 claim)
     * @return 签名后的 token 字符串
     */
    public static String generateToken(String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(System.currentTimeMillis() + EXPIRATION);
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(KEY)
                .compact();
    }

    /**
     * 解析 token, 返回 Claims
     *
     * @param token JWT token
     * @return Claims 载荷, 解析失败返回 null
     */
    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 token 中获取用户名 (subject)
     */
    public static String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * 从 token 中获取角色
     */
    public static String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("role", String.class) : null;
    }

    /**
     * 判断 token 是否过期
     */
    public static boolean isTokenExpired(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return true;
        }
        return claims.getExpiration().before(new Date());
    }
}
