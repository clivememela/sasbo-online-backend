import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './assets/styles/index.css'
import App from './App.jsx'
import { ThemeProvider } from './context/ThemeContext'
import { AuthProvider } from './auth/AuthContext'
import { installSessionFetch } from './services/api'

/**
 * R-ROB-J. A GLOBAL `error` listener, narrowed to the one message it was added for.
 *
 * 🔴 As written it called `preventDefault()` on a window-level error event, which
 * marks the error HANDLED. The `?.includes(...)` guard meant it only did so for the
 * browser-extension message -- but the listener itself still ran for EVERY error in
 * the application, on the capture path, before anything else could see one.
 *
 * What it suppresses is real and not ours: a Chrome extension's content script
 * losing its message channel ("A listener indicated an asynchronous response...").
 * It is noise from software the user installed, it says nothing about this app, and
 * it cannot be fixed from here.
 *
 * So it stays, but its narrowness is now the FIRST thing the next reader sees: the
 * early return says "this listener is about one extension message" before it says
 * anything else, where before that had to be inferred from an optional chain inside
 * a conditional. It remains a bubble-phase listener (the default), so anything that
 * genuinely wants to see an error first still can.
 */
const EXTENSION_CHANNEL_ERROR = 'A listener indicated an asynchronous response'

window.addEventListener('error', (event) => {
  if (!event.message?.includes(EXTENSION_CHANNEL_ERROR)) return
  event.preventDefault()
})

// Before the first render, so not one request -- the sign-in mode's included -- leaves without the session's token.
installSessionFetch()

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <ThemeProvider>
        {/* 🔴 The Google Maps APIProvider USED TO BE HERE, around the whole app, so all 27
            routes loaded a third-party script and shipped the API key -- including the ones
            with no address field anywhere on them. It is on the three routes that use it
            now: see `components/form/MapsScope.jsx` and its three usages in `App.jsx`
            (R-SEC-D / R-PERF-G). */}
        <AuthProvider>
          <App />
        </AuthProvider>
      </ThemeProvider>
    </BrowserRouter>
  </StrictMode>,
)
