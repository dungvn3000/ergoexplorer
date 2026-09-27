import { createContext, useContext } from 'react'

// App-wide state, split so a change re-renders only the components that read that piece (provided by <AppProvider>).
export const ThemeContext = createContext(null) // { theme, toggleTheme }
export const NotifyContext = createContext(null) // notify(text) — stable, so copy buttons never re-render because of the toast
export const ToastContext = createContext(null) // { text, on } — read only by <Toast>
export const HeightContext = createContext(null) // tip height shown in the footer
export const SetHeightContext = createContext(null) // setHeight — stable, for pages that know the height

export const useTheme = () => useContext(ThemeContext)
export const useNotify = () => useContext(NotifyContext)
export const useToast = () => useContext(ToastContext)
export const useHeight = () => useContext(HeightContext)
export const useSetHeight = () => useContext(SetHeightContext)
