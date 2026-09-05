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

    httpMock.expectOne('/api/v1/schools').flush([
      {
        id: '11111111-1111-1111-1111-111111111111',
        code: 'DPS-RKP',
        name: 'Delhi Public School, R. K. Puram',
        board: 'CBSE',
        active: true,
      },
    ]);

    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Delhi Public School, R. K. Puram');
  });

  it('shows an error message when the API is unreachable', async () => {
    fixture.detectChanges();

    httpMock
      .expectOne('/api/v1/schools')
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Could not reach the Chalkbase API');
  });
});
