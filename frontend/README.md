# Chat Frontend (Angular)

## ⚠️ WARNING

**This code is for sample/demo purposes only. It is NOT intended for production deployment.**
- No authentication, security, or input validation is included.
- CORS, error handling, and API usage are minimal and for demonstration only.
- Do not use as-is in any production environment.

---

## 1. How to Install and Run the Frontend

1. **Navigate to the frontend directory:**
   ```sh
   cd frontend/chat-frontend/client
   ```
2. **Install dependencies:**
   ```sh
   npm install
   ```
3. **Start the development server:**
   ```sh
   npx ng serve --open
   ```
   This will open the app in your browser (usually at http://localhost:4200/).

4. **Backend API:**
   - The frontend expects a backend API running at `http://localhost:8000/chat`.
   - Make sure your backend is running and CORS is enabled for `http://localhost:4200`.

---

## 2. How to Modify the Home (Chat) Page

- The main chat UI is in:
  - `src/app/chat.component.html` (HTML template)
  - `src/app/chat.component.ts` (TypeScript logic)
  - `src/app/chat.component.css` (Styling)

- To change the look, text, or layout:
  1. Edit `chat.component.html` for structure and content.
  2. Edit `chat.component.css` for styles and colors.
  3. Edit `chat.component.ts` for logic (e.g., API calls, message handling).

- The chat page is the default route. If you want to add more pages or navigation, update the Angular routing in `src/app/app.routes.ts`.

---

## 3. Additional Notes

- This project uses Angular standalone components (Angular 16+).
- For any issues, check the browser console and ensure the backend is running and accessible.

---

**Again: This code is for learning and demo only. Do not use in production!**
