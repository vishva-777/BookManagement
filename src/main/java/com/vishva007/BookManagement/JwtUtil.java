package com.vishva007.BookManagement;


//bringing in the tool that builds and reads tokens (from the JJWT library you just added).
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;

@Component
public class JwtUtil {

     @Value("${JWT_SECRET}")
     private String SECRET;


    private Key getKey() {
        return Keys.hmacShaKeyFor(Base64.getEncoder().encode(SECRET.getBytes()));
    }

    private final long EXPIRATION = 1000 * 60 * 60;

    //this is the "ticket printer." Takes a username, builds a token:
    // sets subject (who this ticket belongs to), issued-at (when it was made),
    // expiration (when it dies), signs it with your key,
    // and .compact() squishes it all into the final header.payload.signature string.

    public String generateToken(String username, String role) { //-->step8:Takes two inputs:
        // the username (e.g. "admin") and the role string (e.g. "ROLE_ADMIN") —
        // this is what AuthController passes in after a successful login.
        return Jwts.builder()//-->.builder() gives you a builder object—think of it as an empty form you fill in
                // step by step before finalizing.
                .setSubject(username)
                .claim("role", role)//--> Here you're adding "role": "ROLE_ADMIN" into the payload.
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(getKey())//-->getKey(), which reads from your JWT_SECRET environment variable
                .compact();//-->Takes the fully-built token — header, payload, signature — and squishes it all into the
                // final single string format:
                // header.payload.signature,
                // each part separately Base64-encoded and joined by dots.
                // This is the actual JWT string that gets returned to the client.
    }
    //the "ticket reader." Given a token, it unlocks the payload using key and pulls out the username (getSubject()).A
    public String extractUsername(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // NEW METHOD - extract role from token
    public String extractRole(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("role", String.class);
    }

    //the "ticket checker." Tries to unlock and read the token.
    // If anything's wrong (expired, tampered, bad signature) it throws a JwtException, caught here, returns false.
    // If it unlocks cleanly, returns true.
    public boolean validateToken(String token) {
        try {
            //step:3 tries to verify signature if signature is match r unmatch(throw exception)
            Jwts.parserBuilder().setSigningKey(getKey()).build().parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}