# Day 66 — React Frontend + Login Flow

## Goal
Wire up a React login flow so the frontend can authenticate and eventually call
`/books` with a valid JWT.

## What was built

### 1. React project setup
- Installed Node.js LTS + npm
- Created Vite + React project: `bookmanagement-fronted` (typo kept as-is, it's the real folder name)

### 2. React concepts learned
- `useState`: a React-watched variable. Calling `setX(value)` does two things,
  in order — (1) updates the stored value, (2) triggers a re-render.
- `useEffect(() => {...}, [])`: runs once when the component first loads.
- Difference between `GET` (asking for data) and `POST` (handing data over) —
  `fetch()` needs a body + headers for POST, and the body must be a **string**
  (`JSON.stringify(...)`) because only text/bytes travel over the network, not
  live JS objects.
- `response.json()` — converts the raw fetch response into usable JSON (the
  reverse of `JSON.stringify()`).
- `onClick={handleFn}` — pass the function itself, not a function call, so
  React calls it only on click.

### 3. Final `App.jsx` login flow
```jsx
import { useState, useEffect } from 'react'
import './App.css'

function App() {
    const [books, setBooks] = useState([])
    const [token, setToken] = useState(null)

    useEffect(() => {
        fetch('http://56.228.53.82:8090/books')
            .then(response => response.json())
            .then(data => {
                if (Array.isArray(data)) {
                    setBooks(data)
                }
            })
    }, [])

    function handleLogin() {
        fetch('http://56.228.53.82:8090/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: "admin", password: "admin123" })
        })
            .then(response => response.json())
            .then(data => setToken(data.token))
    }

    return (
        <div>
            <h1>BookManagement</h1>
            <button onClick={handleLogin}>Login</button>
            <ul>
                {books.map(book => (
                    <li key={book.id}>
                        {book.title} by {book.author} - ₹{book.price}
                    </li>
                ))}
            </ul>
        </div>
    )
}

export default App
```

## Bugs hit and fixed (three separate, unrelated root causes)

### Bug 1 — CORS blocked the login POST
**Symptom:** `net::ERR_FAILED`, preflight `403`, no CORS headers on the response.
**Cause:** `SecurityConfig.java` had no `.cors(...)` configuration at all.
**Fix:** added to the security filter chain:
```java
.cors(Customizer.withDefaults())
```
plus a bean defining which origins are trusted:
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("http://localhost:5173"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```
Imports needed: `org.springframework.security.config.Customizer`,
`org.springframework.web.cors.CorsConfigurationSource`,
`org.springframework.web.cors.CorsConfiguration`,
`org.springframework.web.cors.UrlBasedCorsConfigurationSource`, `java.util.List`.

### Bug 2 — RDS was stopped
**Symptom:** After the CORS fix deployed, `bookmanagement` crash-looped with
`Unable to determine Dialect without JDBC metadata`.
**Cause:** `vishva-database-3` (RDS) had been manually stopped ~7 hours earlier
to save cost, and forgotten. Confirmed via RDS "Recent events" log
(`DB instance stopped` at 13:41, restarted at 20:43–20:46).
**Fix:** started RDS from the AWS Console, waited for `Available`.

### Bug 3 — SecretsManagerInitializer left disabled (the *real* root cause)
**Symptom:** Even after RDS came back up, `bookmanagement` kept crash-looping
with the same Hibernate dialect error.
**Diagnosis:** `journalctl -u bookmanagement -n 200 --no-pager | grep -i "caused by"`
revealed the real error underneath the generic one:
```
Access denied for user '${DB_USERNAME}'@'172.31.40.111' (using password: YES)
```
The `${DB_USERNAME}` placeholder was never substituted — meaning
`SecretsManagerInitializer` (which fetches real DB creds from AWS Secrets
Manager) never ran. Found the cause in `BookManagementApplication.java`:
```java
//app.addInitializers(new com.vishva007.BookManagement.config.SecretsManagerInitializer());
```
This line had been commented out during local testing (a known recurring
habit — noted back on Day 64 too) and never uncommented before the last push.
**Fix:** uncommented the line, committed, pushed. Confirmed stable
(`active (running)`, 2min57s+ uptime, no restart loop).

### Bug 4 (bonus, pre-existing, not login-related) — `/books` 403 crashed the whole page
**Symptom:** Page would flash the "BookManagement" title + Login button, then
go completely blank.
**Cause:** the original `/books` `.then(data => setBooks(data))` stored
*whatever* the server returned — including a 403 error object — directly into
`books` state. `books.map(...)` then crashed trying to `.map()` over a
non-array, unmounting `<App>` entirely (`Uncaught TypeError: books.map is not
a function`).
**Fix:** guard before storing:
```js
.then(data => {
    if (Array.isArray(data)) {
        setBooks(data)
    }
})
```
This bug existed since the very first `/books` fetch was written — unrelated
to anything added this session, just never triggered/noticed before.

## Final confirmed state
- CORS: working — `login` request returns `200` in Network tab (preflight `200` too)
- Login: working — POST `/login` returns a real JWT
  (`eyJhbGci...` — header.payload.signature, decodes to `sub: admin`, `role: ROLE_ADMIN`)
- `token` state: populated correctly after login
- Page no longer crashes even while `/books` still returns 403 (expected —
  token isn't wired into that fetch yet)

## Startup order (established this session, for every EC2/RDS stop-start cycle)
1. Start RDS first, wait for **Available**
2. Start EC2
3. SSH in, verify all three services with
   `sudo systemctl status eureka-server` / `bookmanagement` / `api-gateway`
   — watch for a suspiciously low uptime ("Ns ago") as a crash-loop red flag
4. Only then start the React dev server: `cd bookmanagement-fronted && npm run dev`

## Open follow-up for next session
Attach the JWT `token` to the `/books` fetch (`Authorization: Bearer <token>`
header) so the book list actually renders after login — this is the very next
step, not yet started.