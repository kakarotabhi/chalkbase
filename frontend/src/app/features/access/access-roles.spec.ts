import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import {
  GrantResponse,
  PermissionDefinition,
  RoleResponse,
  UserSummary,
} from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { AccessRoles } from './access-roles';

const PERMISSIONS_URL = '/api/access/permissions';
const ROLES_URL = '/api/access/roles';
const USERS_URL = '/api/access/users';

const permission = (over: Partial<PermissionDefinition> = {}): PermissionDefinition => ({
  code: 'identity:role:manage',
  module: 'identity',
  label: 'Manage roles and permissions',
  description: 'Read the permission catalogue and this school’s roles.',
  ...over,
});

const role = (over: Partial<RoleResponse> = {}): RoleResponse => ({
  id: 'role-front-office',
  code: 'FRONT_OFFICE',
  name: 'Front Office',
  permissions: ['identity:role:manage'],
  ...over,
});

const holder = (over: Partial<UserSummary> = {}): UserSummary => ({
  id: 'acct-priya',
  displayName: 'Priya Sharma',
  status: 'ACTIVE',
  locked: false,
  ...over,
});

const envelope = (data: unknown) => ({
  success: true,
  timestamp: '2026-09-06T10:00:00Z',
  traceId: 'test-trace',
  data,
});

const refusal = (code: string, details?: Record<string, string>) => ({
  success: false,
  timestamp: '2026-09-06T10:00:00Z',
  error: { code, message: 'Refused.', details },
});

describe('AccessRoles', () => {
  let fixture: ComponentFixture<AccessRoles>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  const button = (label: string) =>
    Array.from(element().querySelectorAll('button')).find((candidate) =>
      (candidate.textContent ?? '').includes(label),
    ) as HTMLButtonElement;

  // An attribute selector, not `#role-perm-${code}`: a permission code is `module:resource:action`,
  // and a bare `#id` selector reads a colon as the start of a pseudo-class, matching nothing.
  const checkbox = (code: string) =>
    element().querySelector(`[id="role-perm-${code}"]`) as HTMLInputElement;

  const type = (id: string, value: string) => {
    const input = element().querySelector(`#${id}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  const arrive = (
    permissions: readonly PermissionDefinition[] = [permission()],
    roles: readonly RoleResponse[] = [role()],
  ) => {
    fixture = TestBed.createComponent(AccessRoles);
    fixture.detectChanges();
    httpMock.expectOne((request) => request.url === PERMISSIONS_URL).flush(envelope(permissions));
    httpMock
      .expectOne((request) => request.url === ROLES_URL && request.method === 'GET')
      .flush(envelope(roles));
    // The account picker's own, best-effort load (`loadPickableUsers`) — refused here, matching
    // a role manager who does not also hold `identity:user:read`, which is the more interesting
    // of the two states to hold as the default and exercises the plain-id fallback field. Every
    // test flushes it so `httpMock.verify()` does not fail on a request nothing else answered.
    httpMock
      .expectOne((request) => request.url === USERS_URL)
      .flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccessRoles],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    // Deliberately without `USER_READ`: the more interesting of the two states to hold as the
    // default, since it exercises the plain-id fallback in the grants picker — see `arrive`.
    signInWith(Permissions.ROLE_MANAGE);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('lists this school’s roles', () => {
    arrive();

    expect(text()).toContain('Front Office');
    expect(text()).toContain('1 permission');
  });

  it('shows the permission catalogue, grouped by module, once the editor is open', () => {
    arrive();

    button('Add a role').click();
    fixture.detectChanges();

    expect(text()).toContain('Manage roles and permissions');
  });

  it('explains a 403 rather than crashing', () => {
    fixture = TestBed.createComponent(AccessRoles);
    fixture.detectChanges();
    // Two independent reads (see `loadScreen`'s Javadoc), so both are flushed here.
    httpMock
      .expectOne((request) => request.url === PERMISSIONS_URL)
      .flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    httpMock
      .expectOne((request) => request.url === ROLES_URL && request.method === 'GET')
      .flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    httpMock
      .expectOne((request) => request.url === USERS_URL)
      .flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view roles');
  });

  // ── Creating a role ──────────────────────────────────────────────────────────────────────

  it('creates a role with the checked permissions, replacing nothing', () => {
    arrive();

    button('Add a role').click();
    fixture.detectChanges();

    type('role-name', 'Librarian Plus');
    checkbox('identity:role:manage').click();
    fixture.detectChanges();

    button('Save role').click();
    fixture.detectChanges();

    const created = httpMock.expectOne({ url: ROLES_URL, method: 'POST' });
    expect(created.request.body).toEqual({
      name: 'Librarian Plus',
      permissions: ['identity:role:manage'],
    });
    created.flush(envelope(role({ id: 'role-2', name: 'Librarian Plus' })));

    httpMock
      .expectOne((request) => request.url === ROLES_URL && request.method === 'GET')
      .flush(envelope([role(), role({ id: 'role-2', name: 'Librarian Plus' })]));
    fixture.detectChanges();

    expect(text()).toContain('Librarian Plus was added');
  });

  it('AUTH_012: says a role with that name already exists, on the field', () => {
    arrive();

    button('Add a role').click();
    fixture.detectChanges();
    type('role-name', 'Front Office');
    button('Save role').click();
    fixture.detectChanges();

    httpMock
      .expectOne({ url: ROLES_URL, method: 'POST' })
      .flush(refusal('AUTH_012'), { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(text()).toContain('name this close to that one already exists');
  });

  /**
   * AUTH_011, caught before the request is even sent: the signed-in test session holds only
   * `identity:role:manage` (see `beforeEach`), so a catalogue entry for a permission it does not
   * hold must say so and must not be sent when checked.
   */
  it('AUTH_011: names the unheld permission instead of sending a request that would fail', () => {
    arrive([
      permission(),
      permission({ code: 'student:student:manage', label: 'Manage students' }),
    ]);

    button('Add a role').click();
    fixture.detectChanges();

    expect(text()).toContain('Not held by you');

    type('role-name', 'Front Office Plus');
    checkbox('student:student:manage').click();
    fixture.detectChanges();

    button('Save role').click();
    fixture.detectChanges();

    expect(text()).toContain('you do not hold it yourself');
    httpMock.expectNone({ url: ROLES_URL, method: 'POST' });
  });

  // ── Editing a role's permissions ─────────────────────────────────────────────────────────

  it('replaces the whole permission set on save, not a delta', () => {
    arrive(
      [permission(), permission({ code: 'student:student:read', label: 'View students' })],
      [role({ permissions: ['identity:role:manage', 'student:student:read'] })],
    );

    button('Edit permissions').click();
    fixture.detectChanges();

    // Opening the editor also loads the impact preview's holders — see "the impact preview"
    // tests further down for what this response feeds into.
    httpMock
      .expectOne({ url: `${ROLES_URL}/role-front-office/holders`, method: 'GET' })
      .flush(envelope([]));
    fixture.detectChanges();

    // Unchecking the one this role already has — allowed even though the actor's own session
    // does not hold `student:student:read` either, because removing is never guarded.
    checkbox('student:student:read').click();
    fixture.detectChanges();

    button('Save role').click();
    fixture.detectChanges();

    const updated = httpMock.expectOne({
      url: `${ROLES_URL}/role-front-office/permissions`,
      method: 'PUT',
    });
    expect(updated.request.body).toEqual({ permissions: ['identity:role:manage'] });
    updated.flush(envelope(role({ permissions: ['identity:role:manage'] })));
    fixture.detectChanges();

    // A successful save re-reads the role list rather than trusting the one role in the response.
    httpMock
      .expectOne((request) => request.url === ROLES_URL && request.method === 'GET')
      .flush(envelope([role({ permissions: ['identity:role:manage'] })]));
  });

  // ── The impact preview (FR-004) ──────────────────────────────────────────────────────────

  it('says nobody is affected when nobody holds the role being edited', () => {
    arrive();

    button('Edit permissions').click();
    fixture.detectChanges();
    httpMock
      .expectOne({ url: `${ROLES_URL}/role-front-office/holders`, method: 'GET' })
      .flush(envelope([]));
    fixture.detectChanges();

    expect(text()).toContain('Nobody holds this role yet, so saving affects nobody directly.');
  });

  it('warns that removing a permission signs every holder out immediately', () => {
    arrive(
      [permission(), permission({ code: 'student:student:read', label: 'View students' })],
      [role({ permissions: ['identity:role:manage', 'student:student:read'] })],
    );

    button('Edit permissions').click();
    fixture.detectChanges();
    httpMock
      .expectOne({ url: `${ROLES_URL}/role-front-office/holders`, method: 'GET' })
      .flush(envelope([holder(), holder({ id: 'acct-arun', displayName: 'Arun Shetty' })]));
    fixture.detectChanges();

    expect(text()).toContain('2 accounts hold this role right now');
    expect(text()).toContain('Priya Sharma');
    expect(text()).toContain('Arun Shetty');

    // No change checked yet: the preview names holders but nothing is at stake until a box moves.
    expect(text()).not.toContain('signed out the moment you save');

    checkbox('student:student:read').click();
    fixture.detectChanges();

    expect(text()).toContain('These accounts are');
    expect(text()).toContain('signed out the moment you save');
    expect(text()).toContain('Removed: View students.');
  });

  it('says an added permission waits for the holder’s next login, not this one', () => {
    arrive(
      [permission(), permission({ code: 'student:student:read', label: 'View students' })],
      [role()],
    );

    button('Edit permissions').click();
    fixture.detectChanges();
    httpMock
      .expectOne({ url: `${ROLES_URL}/role-front-office/holders`, method: 'GET' })
      .flush(envelope([holder()]));
    fixture.detectChanges();

    checkbox('student:student:read').click();
    fixture.detectChanges();

    expect(text()).toContain('This account gains access');
    expect(text()).toContain('but not immediately');
    expect(text()).toContain('Added: View students.');
    expect(text()).not.toContain('signed out the moment you save');
  });

  // ── Grants ───────────────────────────────────────────────────────────────────────────────

  it('shows an account’s grants and words AUTH_010 as a guard when revoking', () => {
    arrive();

    // Arriving with a query-param account id is the normal path from the roster's "Manage
    // roles" link; this test drives the fallback id field instead, which every session can use.
    type('grant-account-id', 'acct-priya');
    button('Show grants').click();
    fixture.detectChanges();

    const grant: GrantResponse = {
      id: 'grant-1',
      roleId: 'role-front-office',
      roleName: 'Front Office',
      scopeType: 'SCHOOL',
    };
    httpMock
      .expectOne({ url: `${USERS_URL}/acct-priya/grants`, method: 'GET' })
      .flush(envelope([grant]));
    fixture.detectChanges();

    expect(text()).toContain('Front Office');

    button('Revoke').click();
    fixture.detectChanges();
    (
      element().querySelector('.dialog__panel .dialog__confirm button') as HTMLButtonElement
    ).click();
    fixture.detectChanges();

    httpMock
      .expectOne({ url: `${USERS_URL}/acct-priya/grants/grant-1`, method: 'DELETE' })
      .flush(refusal('AUTH_010'), { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(text()).toContain('This would leave nobody able to manage access');
  });
});
