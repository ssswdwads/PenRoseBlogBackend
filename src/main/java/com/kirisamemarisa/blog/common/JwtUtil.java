package com.kirisamemarisa.blog.common;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Claims;
import java.util.Date;
import java.security.Key;

public class JwtUtil {
    //HS256 算法的密钥
    private static final Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    //JWT有效期
    private static final long EXPIRATION = 1000L * 60 * 60 * 24 * 7; // 7天

    //生成JWT的方法，包含userId和username
    public static String generateToken(Long userId, String username) {
        return Jwts.builder()//JWT构建器

                //头部
                .setSubject(username)//主题，用户名标识
                .claim("userId", userId) // 将userId写入JWT的payload
                .claim("username", username) // 将username写入JWT的payload
                .setIssuedAt(new Date())//JWT签发时间
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))//JWT设置过期时间
                .signWith(key)//使用HS256算法和密钥签名

                .compact();//生成JWT
    }

    //解析JWT的方法
    public static Claims parseToken(String token) {
        return Jwts.parserBuilder()//解析器构建器
                .setSigningKey(key)//设置密钥
                .build()//构建
                .parseClaimsJws(token)//解析、验证JWT
                .getBody();//获取JWT的主体部分（Claims）声明体
    }

    //验证JWT是否有效
    public static boolean isTokenValid(String token) {
        try {
            //获取JWT声明体
            Claims claims = parseToken(token);
            //检查JWT是否过期
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
