import { ApiClient } from './ApiClient.js'

/**
 * Users, positions and roles (the October build's sign-in), every route `USERS_ADMIN`:
 *
 *   GET  /api/admin/users?page&size&q   a page of users: {content, page, size, totalElements, totalPages}
 *   POST /api/admin/users               {username, displayName, email, positionCode, roleCode?, scope:{type, codes}}
 *                                       -> 201 {user, temporaryPassword}
 *   PUT  /api/admin/users/{id}          {displayName, email, positionCode, roleCode, scope, active, version} -> {user}
 *
 * A user row is the contract's `user` plus `active`, `version`, `locked` and `lockedUntil`.
 *
 * 🔴 THERE IS NO DELETE: a user keeps their name on the work they did. So a user a journey creates is CLOSED when it
 * ends (`close`) -- signed out, unable to sign in -- which is what an administrator does with one, and the most this
 * application allows.
 */
export class UsersApi extends ApiClient {
  /** The users whose username, name or e-mail matches `q` -- one page. */
  async find(q) {
    return (await this.json(await this.get(`/api/admin/users?page=0&size=20&q=${encodeURIComponent(q)}`))).content
  }

  /** The one user whose username is `username`, as the server holds it now (usernames are kept in lower case). */
  async byUsername(username) {
    const found = (await this.find(username)).filter((user) => user.username.toLowerCase() === username.toLowerCase())
    if (found.length !== 1) throw new Error(`Expected one user named ${username}; found ${found.length}`)
    return found[0]
  }

  /** Close a user's account, at the version it has now: they are signed out and cannot sign in again. */
  async close(username) {
    const user = await this.byUsername(username)
    if (!user.active) return
    await this.put(`/api/admin/users/${user.id}`, {
      displayName: user.displayName,
      email: user.email,
      positionCode: user.position?.code,
      roleCode: user.role?.code ?? null,
      scope: user.scope,
      active: false,
      version: user.version,
    })
  }

  /** The users list, answered as it came -- `{ status, body }` -- for a journey asserting a refusal. */
  async attemptList() {
    const response = await this.get('/api/admin/users?page=0&size=20', { expectOk: false })
    return { status: response.status(), body: await response.json().catch(() => ({})) }
  }
}
