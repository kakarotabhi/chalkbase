import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CircularSummary } from '../../core/api/models';
import { Permissions } from '../../core/auth/permissions';
import { signInWith } from '../../core/auth/session-fixture';
import { CircularList } from './circular-list';

const CIRCULARS_URL = '/api/communication/circulars';

const envelope = (data: unknown) => ({
  success: true,
  timestamp: '2026-09-07T10:00:00Z',
  traceId: 'test-trace',
  data,
});

const refusal = (code: string) => ({
  success: false,
  timestamp: '2026-09-07T10:00:00Z',
  error: { code, message: 'Refused.' },
});

const page = (content: readonly CircularSummary[]) => ({
  content,
  page: 0,
  size: 25,
  totalElements: content.length,
  totalPages: 1,
});

const circular = (over: Partial<CircularSummary> = {}): CircularSummary => ({
  id: 'circular-1',
  title: 'PTM on Saturday',
  status: 'PUBLISHED',
  requiresAcknowledgement: true,
  targetCount: 1,
  recipientCount: 32,
  acknowledgedCount: 5,
  publishedAt: '2026-09-06T09:00:00Z',
  createdAt: '2026-09-05T09:00:00Z',
  ...over,
});

describe('CircularList', () => {
  let fixture: ComponentFixture<CircularList>;
  let httpMock: HttpTestingController;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => element().textContent ?? '';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CircularList],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('says so when the caller cannot read circulars', () => {
    signInWith();
    fixture = TestBed.createComponent(CircularList);
    fixture.detectChanges();

    httpMock
      .expectOne((candidate) => candidate.url === CIRCULARS_URL)
      .flush(refusal('PERM_001'), { status: 403, statusText: 'Forbidden' });
    httpMock.expectOne('/api/academics/classes').flush(envelope([]));
    fixture.detectChanges();

    expect(text()).toContain('You do not have permission to view circulars');
  });

  it('lists a published circular with its recipient and acknowledgement counts', () => {
    signInWith(Permissions.COMMUNICATION_READ);
    fixture = TestBed.createComponent(CircularList);
    fixture.detectChanges();

    httpMock
      .expectOne((candidate) => candidate.url === CIRCULARS_URL)
      .flush(envelope(page([circular()])));
    httpMock.expectOne('/api/academics/classes').flush(envelope([]));
    fixture.detectChanges();

    expect(text()).toContain('PTM on Saturday');
    expect(text()).toContain('32 recipient(s)');
    expect(text()).toContain('5 acknowledged');
    // Composing is a separate permission: this session holds only the read one.
    expect(text()).not.toContain('Compose a circular');
  });

  it('shows the compose action to a caller who may manage circulars', () => {
    signInWith(Permissions.COMMUNICATION_READ, Permissions.COMMUNICATION_MANAGE);
    fixture = TestBed.createComponent(CircularList);
    fixture.detectChanges();

    httpMock.expectOne((candidate) => candidate.url === CIRCULARS_URL).flush(envelope(page([])));
    httpMock.expectOne('/api/academics/classes').flush(envelope([]));
    fixture.detectChanges();

    expect(text()).toContain('Compose a circular');
  });
});
