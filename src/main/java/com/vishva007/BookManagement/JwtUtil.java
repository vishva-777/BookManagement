package com.vishva007.BookManagement;


//bringing in the tool that build and reads token (from JJWT library)
import io.jsonwebtoken.*;

//a helper tool to generate a proper secret key
import io.jsonwebtoken.security.Keys;
import java.security.Key;

import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Date;
import java.util.Date;

@Component
public class JwtUtil {

    //fixed secret string - later this moves to application.properties
    private final String SECRET = "your-fixed-secret-string-here-make-it-long-and-random";

    //Convert the SECRET string into a usable Key object for signing/verifying
    private final Key key = Keys.hmacShaKeyFor(Base64.getEncoder().encode(SECRET.getBytes()));

    // Token valid for 1 hour
    private final long EXPIRATION = 1000 * 60 * 60;

    //Generate token for username
    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(key)
                .compact();
    }

    //Extract username from token
    public String extractUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    //validate token
    public boolean validateToken(String token) {
        try{
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}