package com.vishva007.BookManagement;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController //-->tells Spring "this class handles web requests and sends back data
public class AuthController {

    @Autowired //--> itself does only one job: it tells Spring "give me a ready-made object of this type,
    // I don't want to build it myself." That's it.
    // It doesn't check passwords, it doesn't generate tokens — it just fetches and connects the object.

    private AuthenticationManager authenticationManager; //-->this is Spring's built-in "password checker.
    // " You don't write the checking logic yourself; Spring already has a tool for it, we just plug it in here.

    @Autowired
    private JwtUtil jwtUtil; //-->this is your file from before — the ticket printer.
    // We're using it here to generate the token after login succeeds.

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        // Get actual role from authenticated user
        String role = authentication.getAuthorities()
                .stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("ROLE_USER");

        return jwtUtil.generateToken(loginRequest.getUsername(), role);
    }
}