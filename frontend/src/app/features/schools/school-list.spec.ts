import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SchoolList } from './school-list';

describe('SchoolList', () => {
  let fixture: ComponentFixture<SchoolList>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SchoolList],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(SchoolList);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('renders the schools returned by the API', async () => {
    fixture.detectChanges();

    httpMock.expectOne('/api/schools').flush({
      success: true,
      timestamp: '2026-09-05T10:00:00Z',
      traceId: 'test-trace',
      data: [
        {
          id: '11111111-1111-1111-1111-111111111111',
          code: 'DPS-RKP',
          name: 'Delhi Public School, R. K. Puram',
          board: 'CBSE',
          active: true,
        },
      ],
    });

    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Delhi Public School, R. K. Puram');
  });

  /**
   * The message must describe what happened to the reader, not to a developer.
   *
   * It used to say "Could not reach the Chalkbase API. Is the backend running on port 8080?" — a
   * local-development sentence, shown on a public deployment, for a 403 that is not a connectivity
   * failure at all. It was the first thing every user saw after signing in, because this screen was
   * also the landing page. Testing the deployed instance is what surfaced it.
   */
  it('explains the refusal in terms of the reader, not of a developer', async () => {
    fixture.detectChanges();

    httpMock.expectOne('/api/schools').flush(
      {
        success: false,
        timestamp: '2026-09-05T10:00:00Z',
        traceId: 'test-trace',
        error: { code: 'GEN_001', message: 'Something went wrong at our end.' },
      },
      { status: 500, statusText: 'Internal Server Error' },
    );

    await fixture.whenStable();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('platform register');
    expect(text).toContain('Settings');
    // Never again: this page is reached over HTTPS on a deployment where 8080 means nothing.
    expect(text).not.toContain('8080');
    expect(text).not.toContain('localhost');
  });
});
