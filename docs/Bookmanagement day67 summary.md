# Day 67 — Connect React + Spring Boot (use the token)

## Goal
Day 66 got a real JWT into `token` state on login, but `/books` still returned
403 because nothing was actually sending that token along. Today: attach the
token to `/books` so the book list finally renders.

## Concepts learned

### The `Authorization` header
- HTTP has a dedicated header for "here's my credential": `Authorization`.
- Format: the literal word `Bearer`, a space, then the token —
  `"Bearer " + token`.
- Same shape as `Content-Type` from Day 66 (a header key/value pair), just a
  different header doing a different job — `Content-Type` describes the data
  being sent, `Authorization` proves who's sending it.

### Why `/books` couldn't just stay in `useEffect`
- `useEffect(() => {...}, [])` fires once, **immediately** on page load.
- At that exact moment `token` is still `null` (login hasn't happened yet).
- Sending `/books` at that point would send `"Bearer null"` — still 403.
- Conclusion: `/books` can't fire on page load anymore. It has to fire only
  **after** login succeeds.

### Why `/books` had to be *nested*, not just placed after `/login`
- Two separate `fetch()` calls one after another are **not** guaranteed to
  run in order — `fetch()` is asynchronous.
- Chaining with `.then()` is what guarantees "only run this after the
  previous step finished."
- So the `/books` fetch had to go **inside** `/login`'s `.then()` block, not
  as a second independent statement.

### Why `data.token` instead of the `token` state variable
- `setToken(data.token)` does not update the `token` variable instantly —
  React state updates aren't synchronous.
- If the `/books` fetch (running immediately after `setToken`) tried to read
  `token`, it would still see the *old* value (`null`), not the fresh one.
- Fix: read the token straight from `data.token` (the raw response data),
  which already has the real value, instead of trusting the `token` state
  variable at that exact moment.

## Final `handleLogin`
```jsx
function handleLogin() {
    fetch('http://56.228.53.82:8090/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: "admin", password: "admin123" })
    })
        .then(response => response.json())
        .then(data => {
            setToken(data.token)
            fetch('http://56.228.53.82:8090/books', {
                headers: { Authorization: "Bearer " + data.token }
            })
                .then(response => response.json())
                .then(booksData => {
                    if (Array.isArray(booksData)) {
                        setBooks(booksData)
                    }
                })
        })
}
```

`useEffect` is now empty — `/books` no longer fires automatically on page
load at all; it only fires as part of the login flow above.

```jsx
useEffect(() => {

}, [])
```

## Bugs/false starts hit along the way
- First attempt placed the new `/books` fetch as a **second, separate**
  `fetch(...)` statement directly below the `/login` fetch inside
  `handleLogin` — not chained, so it could fire before login finished
  (token still `null`). Fixed by nesting it inside `/login`'s `.then()`.
- A messy manual edit briefly left a dangling `headers: { ... }` line with no
  `fetch(...)` wrapping it (broken syntax) — rebuilt the whole function
  cleanly from scratch rather than patching piece by piece.

## Final confirmed state
Clicking **Login** now:
1. Sends credentials to `/login`, gets back a JWT
2. Stores the JWT in `token` state
3. Immediately uses that same JWT to call `/books` with the `Authorization`
   header
4. Book list actually renders on the page

Day 67 goal fully met — this was the missing piece from Day 66.

## Open follow-up for next session
- Book list and login work, but everything still lives inside one click
  handler — no separate "logged in" state/UI, no persisting the token across
  page refreshes (e.g. localStorage), no logout, no handling of an expired
  token. Any of these would be reasonable next steps.