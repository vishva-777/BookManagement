package com.vishva007.BookManagement;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization"); //-->Reads the header the client sent —
        // expects something like Authorization: Bearer eyJhbGci...


        //Guard's first check: is there even a badge?
        // If no header, or it doesn't start with "Bearer ",
        // this whole block is skipped — meaning no authentication gets set.
        // Request continues anonymously.
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);//-->  removes "Bearer " (7 characters)
            //leaves just the token and (step:3)it goes to jwtUtil

            try {
                if (jwtUtil.validateToken(token)) { //-->Asks the ticket-checker: is this signature valid and not expired?

                    //Pulls both pieces of identity out of the token payload — no DB call happening here, notice.
                    String username = jwtUtil.extractUsername(token);
                    String role = jwtUtil.extractRole(token);

                    // pass role as authority
                    List<SimpleGrantedAuthority> authorities =
                            List.of(new SimpleGrantedAuthority(role));

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    username, null, authorities); // ← pass authorities

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (JwtException e) {
                // invalid token - do nothing
            }
        }

        filterChain.doFilter(request, response);//-->Passes control onward — to the next filter, and eventually your Controller.
    }
}