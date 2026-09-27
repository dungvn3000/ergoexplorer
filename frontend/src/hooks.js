import { useCallback, useEffect, useEffectEvent, useState } from 'react'

/*
 * Runs an async request and tracks it: { data, error, loading, reload }.
 * - data is undefined until the first answer; null means the API answered 404.
 * - When `deps` change (a new id or page) the previous data is dropped, so a page never shows the old item.
 *   With { keepPrevious: true } (paged lists) the previous page stays on screen until the next one arrives,
 *   so the layout does not collapse and the scroll position survives paging.
 * - reload() re-fetches in place, keeping what is on screen (auto refresh, retry).
 * - Answers that arrive after a newer request started are ignored.
 */
export function useApi(fetcher, deps, { keepPrevious = false } = {}) {
  const key = JSON.stringify(deps)
  const [nonce, setNonce] = useState(0)
  // the last answer and the request (key + nonce) it belongs to; "loading" is derived from it
  const [result, setResult] = useState({ key: null, nonce: -1, data: undefined, error: null })
  const run = useEffectEvent(fetcher)

  useEffect(() => {
    let alive = true
    run().then(
      (data) => {
        if (alive) setResult({ key, nonce, data, error: null })
      },
      (error) => {
        if (alive) setResult((r) => ({ key, nonce, data: r.key === key || keepPrevious ? r.data : undefined, error }))
      },
    )
    return () => {
      alive = false
    }
  }, [key, nonce, keepPrevious])

  const reload = useCallback(() => setNonce((n) => n + 1), [])
  const current = result.key === key || keepPrevious
  return {
    data: current ? result.data : undefined,
    error: current ? result.error : null,
    loading: result.key !== key || result.nonce !== nonce,
    reload,
  }
}

export function useInterval(callback, ms) {
  const tick = useEffectEvent(callback)
  useEffect(() => {
    const id = setInterval(() => tick(), ms)
    return () => clearInterval(id)
  }, [ms])
}
