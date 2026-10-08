// @vitest-environment jsdom -- one server answer that follows its inputs
import { describe, expect, it } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'

import useServerAnswer from './useServerAnswer'

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

describe('useServerAnswer', () => {
  it('keeps the whole answer -- a page with its total -- and is loading until it lands', async () => {
    const answer = deferred()
    const { result } = renderHook(() => useServerAnswer(() => answer.promise, ['a']))

    expect(result.current.loading).toBe(true)
    await act(async () => { answer.resolve({ content: [1, 2], totalElements: 7 }) })

    expect(result.current).toMatchObject({ loading: false, error: null, data: { content: [1, 2], totalElements: 7 } })
  })

  it('🔑 drops the answer to a question no longer asked', async () => {
    const first = deferred()
    const second = deferred()
    const { result, rerender } = renderHook(({ q }) => useServerAnswer(() => (q === 'a' ? first.promise : second.promise), [q]),
      { initialProps: { q: 'a' } })

    rerender({ q: 'b' })
    await act(async () => { second.resolve('for b') })
    await act(async () => { first.resolve('for a') })

    expect(result.current.data).toBe('for b')
  })

  it('a failure is said, and the next question clears it', async () => {
    const failed = deferred()
    const { result, rerender } = renderHook(({ q }) => useServerAnswer(() => (q === 'a' ? failed.promise : Promise.resolve('ok')), [q]),
      { initialProps: { q: 'a' } })

    await act(async () => { failed.reject(Object.assign(new Error('boom'), { kind: 'http', status: 500 })) })
    expect(result.current.error?.message).toBe('boom')

    rerender({ q: 'b' })
    await waitFor(() => expect(result.current.data).toBe('ok'))
    expect(result.current.error).toBeNull()
  })

  it('the must-pass: disabled asks nothing and holds nothing', () => {
    let asked = 0
    const { result } = renderHook(() => useServerAnswer(() => { asked += 1; return Promise.resolve('x') }, [], { enabled: false }))

    expect(asked).toBe(0)
    expect(result.current).toMatchObject({ data: null, loading: false, error: null })
  })
})
