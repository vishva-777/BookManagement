# Day 69 — Add Real Authentication + Delete Functionality

## Goal
Two things built today:
1. Replace hardcoded `"admin"`/`"admin123"` in `handleLogin` with a real login
   form (actual input fields the user types into).
2. Add a working Delete button per book, wired to the backend's existing
   `DELETE /books/{id}` endpoint.

## Part 1 — Real login form

### Concepts learned
- Controlled inputs: an `<input>` becomes "controlled" by React when its
  `value` comes from state and an `onChange` handler updates that state on
  every keystroke:
  ```jsx
  <input value={username} onChange={(e) => setUsername(e.target.value)} />
  ```
- `e.target.value` — the current text typed into that specific input box,
  live, on every keystroke.
- Text-holding state starts as `""` (empty string), not `null` — same
  reasoning as `count` starting at `0`: it's the natural "nothing yet" value
  for the type of data being stored.
- `type="password"` on an `<input>` masks the typed text as dots.

### Changes made
Added two new state pairs:
```jsx
const [username, setUsername] = useState("")
const [password, setPassword] = useState("")
```

Added two controlled inputs to the JSX, before the Login button:
```jsx
<input value={username} onChange={(e) => setUsername(e.target.value)} />
<input value={password} onChange={(e) => setPassword(e.target.value)} type="password" />
```

Updated `handleLogin`'s fetch body to use live state instead of literals:
```js
body: JSON.stringify({ username: username, password: password })
```

### False starts along the way
- Twice, JSX (`<input>` / `<li>` elements) got placed **outside** the
  `return (...)` block — between a function's closing `}` and `return`.
  JSX only works inside `return (...)`'s parentheses; anywhere else is
  invalid syntax and won't compile. Both times, fixed by moving the JSX
  inside `return`.

### Confirmed working
Typing `admin` / `admin123` into the new boxes and clicking Login still
returns `200` on both `/login` and `/books`, book list renders — same
behavior as the hardcoded version, but now driven by actual user input.

## Part 2 — Delete a book

### Concepts learned
- HTTP has a dedicated verb for removal: `DELETE` (already listed in the
  backend's CORS `allowedMethods` from Day 66/68, and the backend already
  had `@DeleteMapping("/{id}")` in `BookController.java` — nothing new
  needed on the backend).
- Each book in the list needs its **own** delete button — placed inside the
  `.map()` callback's `<li>`, so it has access to that specific `book.id`.
- `onClick={() => handleDelete(book.id)}` — needs the arrow-function wrapper
  because `handleDelete` requires an argument. Without the wrapper,
  `onClick={handleDelete(book.id)}` would call the function immediately
  during render (not on click), since it's a function *call*, not a
  function *reference*. Compare to `onClick={handleLogin}`, which needs no
  wrapper because it takes no arguments.
- The backend's `DELETE /books/{id}` returns a plain `String` ("Book has
  been deleted") — same shape as the pre-fix `/login` from earlier today.
  This is fine as long as the frontend never calls `response.json()` on it.
- `.filter()` — like `.map()`, but builds a new array keeping only items
  that pass a test. To remove one book: keep every book where
  `book.id !== id` (not-equal, `!==`) — the deleted book's id gets dropped
  from the list.
- State never updates itself just because the server changed — deleting on
  the backend doesn't touch React's `books` state automatically;
  `setBooks(...)` still has to be called explicitly.

### Final `handleDelete`
```jsx
function handleDelete(id) {
    fetch('http://56.228.53.82:8090/books/' + id, {
        method: 'DELETE',
        headers: { Authorization: "Bearer " + token }
    })
        .then(() => {
            setBooks(books.filter(book => book.id !== id))
        })
}
```

Note: uses the `token` **state variable**, not `data.token` — `data` only
existed briefly inside `handleLogin`'s own `.then()` callback and isn't
accessible from `handleDelete`, a separate function.

### Delete button in the JSX
```jsx
<ul>
    {books.map(book => (
        <li key={book.id}>
            {book.title} by {book.author} - ₹{book.price}
            <button onClick={() => handleDelete(book.id)}>Delete</button>
        </li>
    ))}
</ul>
```

### False starts along the way
- Same JSX-outside-`return` mistake as Part 1 — a `<li>` with the delete
  button was pasted between `handleDelete` and `return` instead of inside
  the `.map()` callback.
- Second attempt fixed placement but left **two** separate `<li>` blocks —
  the original one (inside `.map()`, no button) and a new duplicate one
  placed after `.map()` entirely, where `book` doesn't even exist (would
  throw "book is not defined"). Fixed by merging into a single `<li>` inside
  `.map()`, with the button added directly to it.

### Confirmed working
Clicking **Delete** on a real book (id 6, "Test Book Day 68") returned `200`
on `DELETE /books/6`, and the book disappeared from the rendered list
immediately — no page refresh needed.

## Final state
- Real login form: working, same reliability as the old hardcoded version
- Delete: working end-to-end, confirmed live

## Open follow-ups (carried forward, still not started)
- Persist token across page refresh (localStorage)
- Show logged-in vs logged-out UI (hide Login form, show Logout button)
- Add/Edit book forms in the UI (currently only tested via curl)
- Handle expired/invalid token gracefully