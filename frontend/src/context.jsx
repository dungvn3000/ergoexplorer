// App-wide state: theme, toast and the tip height shown in the footer.
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ThemeContext, NotifyContext, ToastContext, HeightContext, SetHeightContext } from './app-context.js'

const savedTheme = () => {
  try {
    return localStorage.getItem('ergo-theme')
  } catch {
    return null // storage blocked (private mode, disabled cookies)
  }
}

export function AppProvider({ children }) {
  // the initial theme is set on <html> by index.html before first paint
  const [theme, setTheme] = useState(() => document.documentElement.dataset.theme || 'light')
  const [toast, setToast] = useState({ text: '', on: false })
  const [height, setHeight] = useState(null)
  const toastTimer = useRef(0)

  // <html data-theme> is updated before the state, so components that read CSS colours on render (charts) see the new theme
  const applyTheme = useCallback((next) => {
    document.documentElement.dataset.theme = next
    setTheme(next)
  }, [])

  // follow the OS theme until the user picks one
  useEffect(() => {
    const mq = matchMedia('(prefers-color-scheme: dark)')
    const onChange = (e) => {
      if (!savedTheme()) applyTheme(e.matches ? 'dark' : 'light')
    }
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [applyTheme])

  const toggleTheme = useCallback(() => {
    const next = theme === 'dark' ? 'light' : 'dark'
    try {
      localStorage.setItem('ergo-theme', next)
    } catch {
      // storage blocked: the choice lasts for this visit only
    }
    applyTheme(next)
  }, [theme, applyTheme])

  const notify = useCallback((text) => {
    setToast({ text, on: true })
    clearTimeout(toastTimer.current)
    toastTimer.current = setTimeout(() => setToast((t) => ({ ...t, on: false })), 1400)
  }, [])

  const themeValue = useMemo(() => ({ theme, toggleTheme }), [theme, toggleTheme])
  return (
    <ThemeContext.Provider value={themeValue}>
      <NotifyContext.Provider value={notify}>
        <ToastContext.Provider value={toast}>
          <SetHeightContext.Provider value={setHeight}>
            <HeightContext.Provider value={height}>{children}</HeightContext.Provider>
          </SetHeightContext.Provider>
        </ToastContext.Provider>
      </NotifyContext.Provider>
    </ThemeContext.Provider>
  )
}
