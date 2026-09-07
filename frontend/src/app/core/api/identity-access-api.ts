import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse,
  CreateRoleRequest,
  CreateUserAccountRequest,
  GrantResponse,
  GrantRoleRequest,
  NewUserAccountResponse,
  PermissionDefinition,
  RoleResponse,
  TemporaryPasswordResponse,
  UpdateRolePermissionsRequest,
  UserAccountResponse,
  UserSummary,
} from './models';
import { unwrap } from './unwrap';

/**
 * HTTP access to `/api/access`: this school's account roster, its roles, the permission
 * catalogue, and who holds what (ADR-0005).
 *
 * No school parameter anywhere, like every tenant-scoped call: the session says which school
 * (ADR-0011). `withCredentials` on every call for the same reason.
 *
 * Split in two on the backend — `UserAccountController` behind `identity:user:manage` /
 * `identity:user:read`, `AccessController` behind `identity:role:manage` — and this service keeps
 * that shape rather than flattening it, so a caller can see at a glance which half of the screen a
 * method belongs to.
 */
@Injectable({ providedIn: 'root' })
export class IdentityAccessApi {
  private readonly http = inject(HttpClient);
  private readonly usersUrl = `${environment.apiBaseUrl}/access/users`;
  private readonly accessUrl = `${environment.apiBaseUrl}/access`;

  /* ── Accounts (identity:user:read / identity:user:manage) ────────────── */

  /** Every account this school has. Not paged: a school's own roster is not a list that grows. */
  users(): Observable<UserSummary[]> {
    return this.http
      .get<ApiResponse<UserSummary[]>>(this.usersUrl, { withCredentials: true })
      .pipe(unwrap);
  }

  /**
   * Issues a generated temporary password, returned exactly once on the response — see
   * `NewUserAccountResponse`. Not idempotent: calling this twice makes two different accounts, or
   * refuses the second with `AUTH_009` if the username was reused.
   */
  createUser(request: CreateUserAccountRequest): Observable<NewUserAccountResponse> {
    return this.http
      .post<ApiResponse<NewUserAccountResponse>>(this.usersUrl, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** Idempotent: deactivating an already-disabled account changes nothing and still answers 200. */
  deactivate(accountId: string): Observable<UserAccountResponse> {
    return this.http
      .post<ApiResponse<UserAccountResponse>>(`${this.usersUrl}/${accountId}/deactivate`, null, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** Idempotent: reactivating an already-active account changes nothing. */
  reactivate(accountId: string): Observable<UserAccountResponse> {
    return this.http
      .post<ApiResponse<UserAccountResponse>>(`${this.usersUrl}/${accountId}/reactivate`, null, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** Clears a lockout. Idempotent: unlocking an account that was never locked changes nothing. */
  unlock(accountId: string): Observable<UserAccountResponse> {
    return this.http
      .post<ApiResponse<UserAccountResponse>>(`${this.usersUrl}/${accountId}/unlock`, null, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /**
   * Issues a new temporary password and ends every session the account currently holds (ADR-0023),
   * including the caller's own if they reset their own account. Not idempotent, for the same
   * reason `createUser` is not: each call is a different secret.
   */
  resetPassword(accountId: string): Observable<TemporaryPasswordResponse> {
    return this.http
      .post<ApiResponse<TemporaryPasswordResponse>>(
        `${this.usersUrl}/${accountId}/reset-password`,
        null,
        { withCredentials: true },
      )
      .pipe(unwrap);
  }

  /* ── Roles and permissions (identity:role:manage) ─────────────────────── */

  /** Everything that could be put into a role. The same list at every school. */
  permissions(): Observable<PermissionDefinition[]> {
    return this.http
      .get<ApiResponse<PermissionDefinition[]>>(`${this.accessUrl}/permissions`, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** This school's roles, with their current permission sets. */
  roles(): Observable<RoleResponse[]> {
    return this.http
      .get<ApiResponse<RoleResponse[]>>(`${this.accessUrl}/roles`, { withCredentials: true })
      .pipe(unwrap);
  }

  createRole(request: CreateRoleRequest): Observable<RoleResponse> {
    return this.http
      .post<ApiResponse<RoleResponse>>(`${this.accessUrl}/roles`, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** A full replacement of the role's permission set, never a delta — see `UpdateRolePermissionsRequest`. */
  updateRolePermissions(
    roleId: string,
    request: UpdateRolePermissionsRequest,
  ): Observable<RoleResponse> {
    return this.http
      .put<ApiResponse<RoleResponse>>(`${this.accessUrl}/roles/${roleId}/permissions`, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** Every account currently holding this role, regardless of scope or validity window. */
  holders(roleId: string): Observable<UserSummary[]> {
    return this.http
      .get<ApiResponse<UserSummary[]>>(`${this.accessUrl}/roles/${roleId}/holders`, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** Every grant one account holds, regardless of validity window. */
  grants(accountId: string): Observable<GrantResponse[]> {
    return this.http
      .get<ApiResponse<GrantResponse[]>>(`${this.accessUrl}/users/${accountId}/grants`, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  grantRole(accountId: string, request: GrantRoleRequest): Observable<GrantResponse> {
    return this.http
      .post<ApiResponse<GrantResponse>>(`${this.accessUrl}/users/${accountId}/grants`, request, {
        withCredentials: true,
      })
      .pipe(unwrap);
  }

  /** A 204 carries no envelope at all, so nothing here may unwrap one — see `discardBody`. */
  revokeGrant(accountId: string, grantId: string): Observable<void> {
    return this.http
      .delete<ApiResponse<unknown> | null>(
        `${this.accessUrl}/users/${accountId}/grants/${grantId}`,
        { withCredentials: true },
      )
      .pipe(discardBody);
  }
}

/**
 * Throws away a success payload the contract does not pin down.
 *
 * Not `unwrapVoid`: that reads `success` off the body, and a 204 has no body to read it off. The
 * interceptor has already turned every non-2xx into an error by the time this runs, so reaching
 * here at all means the write went through. Matches `students-api.ts`'s `detachGuardian`.
 */
const discardBody = map((): void => undefined);
