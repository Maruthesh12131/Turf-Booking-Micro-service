import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, expect, it, vi } from 'vitest'
import Register from '../pages/Register'

afterEach(() => vi.unstubAllGlobals())

it('keeps Sign up disabled until valid, then sends only SignupDTO fields', async () => {
  const f = vi.fn().mockImplementation(async () => new Response(JSON.stringify({ userId: 1 }), { status: 200 }))
  vi.stubGlobal('fetch', f)
  const user = userEvent.setup()
  render(<MemoryRouter><Register /></MemoryRouter>)
  const submit = screen.getByRole('button', { name: /sign up/i })
  expect(submit).toBeDisabled()
  await user.click(screen.getByLabelText(/owner/i))
  await user.type(screen.getByLabelText(/full name/i), 'Asha')
  await user.type(screen.getByLabelText(/^email/i), 'asha@example.com')
  await user.type(screen.getByLabelText(/phone/i), '+919876543210')
  await user.type(screen.getByLabelText(/^password/i), 'secret12')
  await user.type(screen.getByLabelText(/confirm password/i), 'different')
  expect(submit).toBeDisabled()
  await user.clear(screen.getByLabelText(/confirm password/i))
  await user.type(screen.getByLabelText(/confirm password/i), 'secret12')
  expect(submit).toBeEnabled()
  await user.click(submit)
  await waitFor(() => expect(f).toHaveBeenCalledTimes(1))
  const body = JSON.parse(f.mock.calls[0][1].body)
  expect(body).toEqual({ role: 'OWNER', name: 'Asha', email: 'asha@example.com', phoneNumber: '+919876543210', password: 'secret12' })
  expect(f.mock.calls[0][0]).toMatch(/\/users\/create$/)
})
