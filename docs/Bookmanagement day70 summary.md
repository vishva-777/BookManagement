# Day 70 — Complete the Project

Wrap-up day for the whole Days 61-70 microservices + React project block,
before moving into interview prep (Day 71+). Plan: polish existing features
→ fill in missing CRUD in the UI → write documentation.

---

## Phase 1 — Polish (persistence, logout, hide/show UI)

### Goal
Three gaps left over from Days 66-69: logging in required doing it fresh on
every page refresh, there was no way to log out, and the login form stayed
visible even after logging in.

### 1. Persist login across page refresh
**Problem:** `token` only lived in React state — a refresh wiped it clean.

**Fix, two parts:**
- In `handleLogin`, right after `setToken(data.token)`, also save it:
  ```js
  localStorage.setItem("token", data.token)
  ```
- In the previously-empty `useEffect`, restore it on page load:
  ```js
  useEffect(() => {
      if (localStorage.getItem("token")) {
          setToken(localStorage.getItem("token"))
          fetch('http://56.228.53.82:8090/books', {
              headers: { Authorization: "Bearer " + localStorage.getItem("token") }
          })
              .then(response => response.json())
              .then(booksData => { if (Array.isArray(booksData)) setBooks(booksData) })
      }
  }, [])
  ```
  Uses `localStorage.getItem("token")` directly in the header (not the
  `token` state variable) — same reason as Day 67's `data.token` vs `token`:
  state hasn't finished updating yet at that exact point.

**Confirmed:** refreshing the page after login shows the book list
automatically, no re-login needed.

### 2. Logout
```js
function handleLogout() {
    setToken(null)
    localStorage.removeItem("token")
    setBooks([])
}
```
Clears both React state and `localStorage`, plus the book list, so a refresh
afterward stays logged out. Added `<button onClick={handleLogout}>Logout</button>`
— no `() =>` wrapper needed since it takes no argument (same as `handleLogin`).

**False start:** first attempt used `localStorage: localStorage.removeItem(...)`
(a stray colon/label syntax) and `books(setBooks([]))` (calling `books` as a
function). Fixed to plain statements: `localStorage.removeItem("token")` and
`setBooks([])`.

### 3. Hide/show UI based on login state
```jsx
{token === null ? (
    <>
        <input value={username} onChange={(e) => setUsername(e.target.value)} />
        <input value={password} onChange={(e) => setPassword(e.target.value)} type="password" />
        <button onClick={handleLogin}>Login</button>
    </>
) : (
    <button onClick={handleLogout}>Logout</button>
)}
```
Uses a **Fragment** (`<>...</>`) to group the three logged-out elements,
since JSX requires exactly one parent per branch. `<ul>` (the book list)
stays outside the ternary — it's just empty when logged out, so no harm
leaving it unconditional.

**Confirmed:** logged-out view shows only the login form; logged-in view
shows only Logout (+ book list).

### Red herring bug
After wiring up the hide/show UI, login started failing with a `500
BadCredentialsException`. Turned out to be a **typo in the password field**
(dot count didn't match `admin123`'s length) — not a code bug. Retyping
fixed it immediately. Good reminder: always check the simple explanation
before assuming the code broke.

---

## Phase 2 — CRUD forms in the UI (previously curl-only)

### Add a book

Four new state pairs, all `useState("")`:
```js
const [newTitle, setNewTitle] = useState("")
const [newDescription, setNewDescription] = useState("")
const [newPrice, setNewPrice] = useState("")
const [newAuthorId, setNewAuthorId] = useState("")
```

```js
function handleAddBook() {
    fetch('http://56.228.53.82:8090/books', {
        method: 'POST',
        headers: { Authorization: "Bearer " + token, 'Content-Type': 'application/json' },
        body: JSON.stringify({
            title: newTitle,
            description: newDescription,
            price: newPrice,
            author: { id: newAuthorId }
        })
    })
        .then(response => response.json())
        .then(newBook => {
            setBooks([...books, newBook])
        })
}
```

Key points:
- `author` must be a **nested object** (`{ id: ... }`), matching
  `Book.java`'s `@ManyToOne` relationship — not a plain value.
- `[...books, newBook]` uses the **spread operator** to build a new array
  containing every existing book plus the new one — different from
  `.filter()` (removes) and `setBooks(newBook)` (would wipe out everything
  else).

**False starts:**
- Used shorthand (`{ title, description, price }`) which only works when a
  variable is literally named `title` — but the actual variables are
  `newTitle` etc. Had to use explicit `key: value` pairs.
- Initially placed the whole Add Book form inside the **logged-out** ternary
  branch — would have sent `Authorization: Bearer null`, and the form would
  vanish the moment you actually logged in (exactly the wrong branch). Moved
  it into the logged-in branch, alongside Logout.

**Confirmed:** submitting a new book appears in the list immediately, no
refresh needed.

### Edit / Update a book

Design: reuse the same form used for Add. One new state tracks which book is
being edited:
```js
const [editingId, setEditingId] = useState(null)
```

```js
function handleEditClick(book) {
    setNewTitle(book.title)
    setNewDescription(book.description)
    setNewPrice(book.price)
    setEditingId(book.id)
}
```
Deliberately does **not** pre-fill `newAuthorId` — see Known Limitations
below.

```js
function handleUpdateBook() {
    fetch('http://56.228.53.82:8090/books/' + editingId, {
        method: 'PUT',
        headers: { Authorization: "Bearer " + token, 'Content-Type': 'application/json' },
        body: JSON.stringify({
            title: newTitle,
            description: newDescription,
            price: newPrice,
            author: { id: newAuthorId }
        })
    })
        .then(response => response.json())
        .then(updatedBook => {
            setBooks(books.map(book => book.id === editingId ? updatedBook : book))
            setEditingId(null)
        })
}
```
`.map()` here **replaces** just the matching book, leaving every other book
untouched — a third distinct array operation alongside `.filter()` (Delete)
and spread (Add).

Submit button switches between Add and Update:
```jsx
{editingId === null ? (
    <button onClick={handleAddBook}>Add Book</button>
) : (
    <button onClick={handleUpdateBook}>Update Book</button>
)}
```
Edit button per book: `<button onClick={() => handleEditClick(book)}>Edit</button>`.

### Known limitation: author ID can't be pre-filled
`Book.java`'s `author` field is annotated `@JsonBackReference` — this
prevents an infinite JSON loop (Book → Author → Books → Author → ...) by
excluding `author` from every `/books` GET response entirely. Confirmed:
`book.author` always renders blank in the list (`"by -"`).

Consequence: when editing, there's no way to know the book's current author
id from the frontend's data. The form leaves Author ID blank/unchanged, and
the user must manually retype it before saving.

### Bug hunt: Update Book returning 500
Clicking Update Book failed with `500 Internal Server Error`. Diagnosed via:
```
journalctl -u bookmanagement --since "-5min" --no-pager | grep -E "^Sep.*java\[PID\]: [A-Za-z.]+Exception"
```
Real error:
```
DataIntegrityViolationException: could not execute statement
[Cannot add or update a child row: a foreign key constraint fails
(`Bookdb`.`books`, CONSTRAINT ... FOREIGN KEY (`author_id`) REFERENCES `author` (`id`))]
```

**Root cause:** the Author ID typed into the form (`8`, later also `7`)
doesn't correspond to a real row in the `author` table. Verified via
`GET /author` with a valid token — the only real author id in the database
is **`3`** (Sun Tzu). This was a second "red herring" bug this session —
correct database behavior rejecting bad input, not a code defect.

**Fix:** always use `3` as the Author ID until more authors are added to the
database. Retested with `3` — Update Book succeeded, price change reflected
immediately in the list, submit button correctly reset to "Add Book".

**Also learned along the way:** JWTs expire. An old token saved in
`localStorage` from a previous session returned `403` on retry — had to log
out and back in to get a fresh one before `/author` worked.

---

## Phase 3 — Documentation

Wrote a project-level `README.md` covering:
- Architecture diagram (frontend → gateway → Eureka/backend → RDS, plus SQS)
- Tech stack
- Feature list
- All four repos (BookManagement, eureka-server, api-gateway,
  bookmanagement-fronted) with purpose and CI/CD status
- Local setup instructions for both backend and frontend
- Full API reference table
- Known limitations (author-id pre-fill gap, no CI/CD for two services, no
  HTTPS on the S3 site, memory constraints)
- Short project history tying Days 61-70 together

---

## Also fixed this session (infra, not code)
`bookmanagement-fronted` had **never been a git repository** — all of Days
66-70's React work existed only on the laptop, never version-controlled or
backed up. Fixed: created a new GitHub repo
(`vishva-777/bookmanagement-fronted`), ran `git init`, committed all 16
files, added the remote, and pushed. Now properly tracked going forward.

---

## Final state — Day 70 complete
- Persistence, logout, hide/show UI: all working
- Full CRUD (Create, Read, Update, Delete) reachable entirely from the UI
- Frontend now version-controlled and backed up on GitHub
- Project README written

This closes out the Days 61-70 microservices/React project block. Next:
Day 71 — AWS interview questions.